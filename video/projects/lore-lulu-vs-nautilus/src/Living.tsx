import React, {useEffect, useLayoutEffect, useRef, useState} from 'react';
import {continueRender, delayRender, staticFile} from 'remotion';

// Copied from lore-yunara-vs-xayah/src/Living.tsx (projects don't import each other); maps from pipeline/living_maps.py.
// «Living picture»: one still drawn through a WebGL shader with its depth/fog maps:
// depth parallax, breathing near plane, flowing mist, pulsing glow, ember flicker, RGB split, diagonal cut, fog wipe.
export type LivingProps = {
	dir: string; // public/<dir>/img|maps|glow/<img>.png
	img: string;
	uvOff: [number, number]; // screen (0..1, y down) -> image uv: uvOff + s * uvScale
	uvScale: [number, number];
	t: number; // scene seconds
	par: [number, number]; // parallax camera offset, about -1.5..1.5
	focus: [number, number]; // image uv the near plane breathes around
	red?: number; // lilac-pink light flows too (maps B), red-hot pixels (helmet window) flicker
	flow?: number; // mist flow strength
	glow?: number; // additive glow strength
	rgb?: number; // RGB split, px
	split?: number; // diagonal cut: halves slide apart, px
	splitLine?: number; // white line on the cut 0..1
	wipe?: number; // fog wipe 0 (visible) .. 1.12 (gone)
	seed?: number;
	style?: React.CSSProperties;
};

const VS = `attribute vec2 p; varying vec2 vUv;
void main(){ vUv = vec2(p.x * 0.5 + 0.5, 0.5 - p.y * 0.5); gl_Position = vec4(p, 0.0, 1.0); }`;

const FS = `precision highp float;
varying vec2 vUv;
uniform sampler2D uImg, uMaps, uGlow;
uniform vec2 uRes, uUvOff, uUvScale, uPar, uFocus;
uniform float uT, uRed, uFlow, uGlowAmt, uRgb, uSplit, uSplitLine, uWipe, uSeed;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
	vec2 i = floor(p), f = fract(p), u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 4; i++) { s += a * noise(p); p = p * 2.03 + 17.1; a *= 0.5; } return s; }

vec3 tap(vec2 uv, vec2 o) {
	if (uRgb <= 0.0) return texture2D(uImg, uv).rgb;
	return vec3(texture2D(uImg, uv + o).r, texture2D(uImg, uv).g, texture2D(uImg, uv - o).b);
}

void main() {
	vec2 px = vUv * uRes;
	// diagonal cut: the two halves slide along the slash
	vec2 tang = normalize(vec2(1.0, 0.8));
	vec2 nrm = vec2(-tang.y, tang.x);
	float side = dot(px - 0.5 * uRes, nrm);
	px -= sign(side) * tang * uSplit;
	vec2 uv = uUvOff + (px / uRes) * uUvScale;

	// depth parallax (two refinement steps): near planes move more than far ones
	float d = texture2D(uMaps, uv).r;
	d = texture2D(uMaps, uv + uPar * (d - 0.5) * 0.04).r;
	uv += uPar * (d - 0.5) * 0.04;
	// near plane breathes 1.000..1.006, ~4 s
	uv = uFocus + (uv - uFocus) / (1.0 + 0.006 * (0.5 + 0.5 * sin(uT * 1.5708)) * smoothstep(0.6, 0.75, d));

	// flowing mist: flow map (two phases, cross-faded) + slow noise wobble, only inside the smooth mist mask
	vec3 m = texture2D(uMaps, uv).rgb;
	float fm = clamp(m.g + uRed * m.b, 0.0, 1.0) * uFlow;
	vec2 dir = (vec2(fbm(uv * 2.0 + vec2(0.0, uT * 0.03)), fbm(uv * 2.0 + vec2(5.2, 1.3) - uT * 0.03)) - 0.5) * 2.4 + vec2(0.45, -0.15);
	vec2 wob = (vec2(fbm(uv * 3.0 + uT * 0.15), fbm(uv * 3.0 + vec2(3.7, 8.1) + uT * 0.15)) - 0.5) * 0.012 * fm;
	float ph0 = fract(uT * 0.22), ph1 = fract(uT * 0.22 + 0.5);
	vec2 o = vec2(uRgb / uRes.x * uUvScale.x, 0.0);
	vec3 col = mix(tap(uv - dir * ph0 * 0.016 * fm + wob, o), tap(uv - dir * ph1 * 0.016 * fm + wob, o), abs(ph0 - 0.5) * 2.0);

	// living light: very bright saturated pixels pulse ±15 % with their own phase, plus a soft additive glow
	float mx = max(col.r, max(col.g, col.b)), mn = min(col.r, min(col.g, col.b));
	float sat = (mx - mn) / max(mx, 1e-3);
	float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
	float lit = max(smoothstep(0.4, 0.7, sat) * smoothstep(0.6, 0.85, mx), smoothstep(0.75, 0.95, lum));
	float ph = noise(uv * 4.0 + uSeed) * 6.2832;
	col *= 1.0 + 0.15 * sin(uT * 2.6 + ph) * lit;
	col += texture2D(uGlow, uv).rgb * uGlowAmt * (0.6 + 0.3 * sin(uT * 1.9 + noise(uv * 2.5) * 6.2832));
	// embers / sparks flicker in red scenes
	float ember = lit * smoothstep(0.15, 0.35, col.r - max(col.g, col.b));
	col *= 1.0 + uRed * 0.45 * (noise(uv * 90.0 + vec2(uT * 7.0, -uT * 5.0)) - 0.5) * ember;

	// white line on the cut
	col += vec3(1.0, 0.95, 0.9) * exp(-abs(side) / 4.0) * uSplitLine;

	// fog wipe out: noise-shaped dissolve with a jade edge
	float n = fbm(vUv * vec2(2.4, 4.2) + uSeed);
	float a = smoothstep(uWipe - 0.12, uWipe, n);
	float edge = (1.0 - smoothstep(0.0, 0.07, abs(n - uWipe + 0.05))) * step(0.001, uWipe);
	col += vec3(0.18, 0.95, 0.63) * edge * 0.9;
	gl_FragColor = vec4(clamp(col, 0.0, 1.0) * a, a);
}`;

type GLState = {gl: WebGLRenderingContext; prog: WebGLProgram; loc: (n: string) => WebGLUniformLocation | null};

const compile = (gl: WebGLRenderingContext, type: number, src: string) => {
	const s = gl.createShader(type)!;
	gl.shaderSource(s, src);
	gl.compileShader(s);
	if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s) ?? 'shader');
	return s;
};
const loadImage = (src: string) =>
	new Promise<HTMLImageElement>((res, rej) => {
		const im = new Image();
		im.onload = () => res(im);
		im.onerror = () => rej(new Error(`load ${src}`));
		im.src = src;
	});

const draw = (st: GLState, p: LivingProps) => {
	const {gl, loc} = st;
	gl.viewport(0, 0, 1080, 1920);
	gl.uniform2f(loc('uRes'), 1080, 1920);
	gl.uniform2f(loc('uUvOff'), ...p.uvOff);
	gl.uniform2f(loc('uUvScale'), ...p.uvScale);
	gl.uniform2f(loc('uPar'), ...p.par);
	gl.uniform2f(loc('uFocus'), ...p.focus);
	gl.uniform1f(loc('uT'), p.t);
	gl.uniform1f(loc('uRed'), p.red ?? 0);
	gl.uniform1f(loc('uFlow'), p.flow ?? 1);
	gl.uniform1f(loc('uGlowAmt'), p.glow ?? 0.8);
	gl.uniform1f(loc('uRgb'), p.rgb ?? 0);
	gl.uniform1f(loc('uSplit'), p.split ?? 0);
	gl.uniform1f(loc('uSplitLine'), p.splitLine ?? 0);
	gl.uniform1f(loc('uWipe'), p.wipe ?? 0);
	gl.uniform1f(loc('uSeed'), p.seed ?? 0);
	gl.clearColor(0, 0, 0, 0);
	gl.clear(gl.COLOR_BUFFER_BIT);
	gl.drawArrays(gl.TRIANGLES, 0, 3);
};

export const Living: React.FC<LivingProps> = (props) => {
	const ref = useRef<HTMLCanvasElement>(null);
	const st = useRef<GLState | null>(null);
	const latest = useRef(props);
	latest.current = props;
	const [handle] = useState(() => delayRender(`living ${props.img}`));

	useEffect(() => {
		const canvas = ref.current!;
		const gl = canvas.getContext('webgl', {preserveDrawingBuffer: true, premultipliedAlpha: true, antialias: false})!;
		let alive = true;
		const prog = gl.createProgram()!;
		gl.attachShader(prog, compile(gl, gl.VERTEX_SHADER, VS));
		gl.attachShader(prog, compile(gl, gl.FRAGMENT_SHADER, FS));
		gl.linkProgram(prog);
		gl.useProgram(prog);
		const buf = gl.createBuffer();
		gl.bindBuffer(gl.ARRAY_BUFFER, buf);
		gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
		const ap = gl.getAttribLocation(prog, 'p');
		gl.enableVertexAttribArray(ap);
		gl.vertexAttribPointer(ap, 2, gl.FLOAT, false, 0, 0);
		const cache = new Map<string, WebGLUniformLocation | null>();
		const loc = (n: string) => (cache.has(n) ? cache.get(n)! : (cache.set(n, gl.getUniformLocation(prog, n)), cache.get(n)!));
		const {dir, img} = props;
		Promise.all([`${dir}/img/${img}.png`, `${dir}/maps/${img}.png`, `${dir}/glow/${img}.png`].map((f) => loadImage(staticFile(f))))
			.then((ims) => {
				if (!alive) return;
				ims.forEach((im, i) => {
					gl.activeTexture(gl.TEXTURE0 + i);
					const tex = gl.createTexture();
					gl.bindTexture(gl.TEXTURE_2D, tex);
					gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL, gl.NONE);
					gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, im);
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
				});
				gl.uniform1i(loc('uImg'), 0);
				gl.uniform1i(loc('uMaps'), 1);
				gl.uniform1i(loc('uGlow'), 2);
				st.current = {gl, prog, loc};
				draw(st.current, latest.current);
				continueRender(handle);
			})
			.catch((e) => {
				console.error(e);
				throw e;
			});
		return () => {
			alive = false;
			st.current = null;
			gl.getExtension('WEBGL_lose_context')?.loseContext();
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	useLayoutEffect(() => {
		if (st.current) draw(st.current, props);
	});

	return <canvas ref={ref} width={1080} height={1920} style={{position: 'absolute', inset: 0, width: 1080, height: 1920, ...props.style}} />;
};
