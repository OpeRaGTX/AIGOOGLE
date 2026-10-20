import React from 'react';
import {
	AbsoluteFill,
	Audio,
	Sequence,
	interpolate,
	spring,
	staticFile,
	useCurrentFrame,
	useVideoConfig,
} from 'remotion';
import {Figure, Outfit, Person} from './Figure';

const FONT = "Inter, -apple-system, 'Noto Color Emoji', sans-serif";
const BLUE = '#0A84FF';
const GRAY = '#E9E9EB';
export const REVEAL = 345;

type Msg =
	| {at: number; from: 'me' | 'mom'; text: string; lines: number}
	| {at: number; from: 'me'; photo: Outfit};

const MSGS: Msg[] = [
	{at: 0, from: 'me', photo: 'leather'},
	{at: 0, from: 'me', text: 'wearing this to church sunday 🙏', lines: 2},
	{at: 45, from: 'mom', text: 'EXCUSE ME??', lines: 1},
	{at: 72, from: 'mom', text: 'where did u even get that', lines: 1},
	{at: 108, from: 'me', photo: 'fur'},
	{at: 120, from: 'me', text: 'ok fine what about this', lines: 1},
	{at: 160, from: 'mom', text: 'who is paying for that coat', lines: 2},
	{at: 198, from: 'me', text: "nobody 😌 i don't own any of it", lines: 2},
	{at: 228, from: 'mom', text: '??????', lines: 1},
	{at: 260, from: 'me', text: "it's AI 😭 i put the outfits on my pic with Stylit", lines: 2},
	{at: 312, from: 'mom', text: 'ok… do me next 👀', lines: 1},
];
const TYPING = [
	[18, 45],
	[134, 160],
	[284, 312],
];

const LINE = 62;
const PHOTO_H = 820;
const heightOf = (m: Msg) => ('photo' in m ? PHOTO_H : 44 + m.lines * LINE);

const useSpring = (at: number, damping = 14) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	return spring({frame: frame - at, fps, config: {damping, stiffness: 220}});
};

export const Logo: React.FC<{size: number; dark?: boolean}> = ({size, dark}) => (
	<div style={{display: 'flex', alignItems: 'center', gap: size * 0.28}}>
		<div
			style={{
				width: size,
				height: size,
				borderRadius: size * 0.26,
				background: 'linear-gradient(135deg,#FF2E7E,#FF5A3C)',
				display: 'flex',
				alignItems: 'center',
				justifyContent: 'center',
				color: '#fff',
				fontWeight: 900,
				fontSize: size * 0.72,
				fontStyle: 'italic',
				boxShadow: `0 ${size * 0.08}px ${size * 0.25}px rgba(255,46,126,.35)`,
			}}
		>
			S
		</div>
		<span style={{fontWeight: 900, fontSize: size * 0.8, color: dark ? '#fff' : '#1A1A1A', letterSpacing: -size * 0.02}}>
			Stylit
		</span>
	</div>
);

const Photo: React.FC<{outfit: Outfit; person?: Person; id: string; w: number; h: number}> = ({outfit, person, id, w, h}) => (
	<div
		style={{
			width: w,
			height: h,
			borderRadius: 40,
			overflow: 'hidden',
			position: 'relative',
			background: 'linear-gradient(180deg,#F4E9E1 0%,#EADBD0 70%,#D9C6B8 70%,#CDB8A8 100%)',
		}}
	>
		<div style={{position: 'absolute', left: '50%', top: '6%', width: '62%', height: '92%', transform: 'translateX(-50%)'}}>
			<Figure outfit={outfit} person={person} id={id} />
		</div>
		<div style={{position: 'absolute', left: 22, top: 22, background: 'rgba(255,255,255,.9)', borderRadius: 999, padding: '10px 18px'}}>
			<Logo size={34} />
		</div>
	</div>
);

const Bubble: React.FC<{m: Msg; i: number; last: boolean}> = ({m, i, last}) => {
	const s = useSpring(m.at);
	const me = m.from === 'me';
	const h = heightOf(m);
	const grow = m.at === 0 ? 1 : interpolate(s, [0, 1], [0, 1], {extrapolateRight: 'clamp'});
	return (
		<div style={{height: (h + 14) * grow, display: 'flex', justifyContent: me ? 'flex-end' : 'flex-start', alignItems: 'flex-end'}}>
			<div
				style={{
					transform: `scale(${m.at === 0 ? 1 : 0.5 + 0.5 * s})`,
					transformOrigin: me ? '100% 100%' : '0% 100%',
					opacity: m.at === 0 ? 1 : Math.min(1, s * 2),
				}}
			>
				{'photo' in m ? (
					<Photo outfit={m.photo} id={`p${i}`} w={600} h={PHOTO_H} />
				) : (
					<div
						style={{
							maxWidth: 740,
							padding: '22px 36px',
							borderRadius: 46,
							borderBottomRightRadius: me && last ? 12 : 46,
							borderBottomLeftRadius: !me && last ? 12 : 46,
							background: me ? BLUE : GRAY,
							color: me ? '#fff' : '#111',
							fontSize: 50,
							lineHeight: `${LINE}px`,
							fontWeight: 500,
						}}
					>
						{m.text.split(/(Stylit)/).map((t, k) =>
							t === 'Stylit' ? (
								<b key={k} style={{fontWeight: 900, textDecoration: 'underline'}}>
									Stylit
								</b>
							) : (
								t
							),
						)}
					</div>
				)}
			</div>
		</div>
	);
};

const Typing: React.FC<{from: number; to: number}> = ({from, to}) => {
	const frame = useCurrentFrame();
	const s = useSpring(from);
	if (frame < from || frame >= to) return null;
	return (
		<div style={{height: 120 * Math.min(1, s), display: 'flex', alignItems: 'flex-end'}}>
			<div style={{background: GRAY, borderRadius: 46, padding: '30px 34px', display: 'flex', gap: 12, transform: `scale(${s})`, transformOrigin: '0 100%'}}>
				{[0, 1, 2].map((d) => (
					<div
						key={d}
						style={{
							width: 22,
							height: 22,
							borderRadius: 11,
							background: '#8E8E93',
							opacity: 0.4 + 0.6 * Math.max(0, Math.sin((frame - d * 5) / 4)),
						}}
					/>
				))}
			</div>
		</div>
	);
};

const Chat: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const visible = MSGS.filter((m) => m.at <= frame);
	// punch-in on mom's punchline, then whip out into the reveal
	const punch = spring({frame: frame - 312, fps, config: {damping: 12, stiffness: 200}});
	const whip = interpolate(frame, [REVEAL - 12, REVEAL], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const scale = 1 + 0.06 * punch + 0.6 * whip * whip;
	return (
		<AbsoluteFill style={{background: '#fff', fontFamily: FONT, transform: `scale(${scale})`, transformOrigin: '50% 82%', filter: `blur(${whip * 18}px)`}}>
			<div style={{position: 'absolute', left: 40, right: 40, bottom: 330, display: 'flex', flexDirection: 'column'}}>
				{visible.map((m, i) => (
					<Bubble key={i} m={m} i={i} last={MSGS[MSGS.indexOf(m) + 1]?.from !== m.from} />
				))}
				{TYPING.map(([a, b]) => (
					<Typing key={a} from={a} to={b} />
				))}
			</div>
			{/* header */}
			<div
				style={{
					position: 'absolute',
					top: 0,
					left: 0,
					right: 0,
					height: 330,
					background: 'rgba(247,247,247,.96)',
					borderBottom: '2px solid #E2E2E2',
					display: 'flex',
					flexDirection: 'column',
					alignItems: 'center',
					justifyContent: 'flex-end',
					paddingBottom: 24,
				}}
			>
				<div style={{position: 'absolute', left: 40, bottom: 120, color: BLUE, fontSize: 90, fontWeight: 300}}>‹</div>
				<div style={{width: 120, height: 120, borderRadius: 60, background: 'linear-gradient(180deg,#A8ACB6,#868A94)', color: '#fff', fontSize: 58, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
					M
				</div>
				<div style={{fontSize: 36, marginTop: 10, color: '#111'}}>Mom 🤍 ›</div>
			</div>
			{/* input bar */}
			<div style={{position: 'absolute', left: 40, right: 40, bottom: 200, height: 96, display: 'flex', alignItems: 'center', gap: 24}}>
				<div style={{width: 84, height: 84, borderRadius: 42, background: '#EDEDED', color: '#8E8E93', fontSize: 64, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>+</div>
				<div style={{flex: 1, height: 84, borderRadius: 42, border: '3px solid #D6D6D6', color: '#B0B0B5', fontSize: 40, display: 'flex', alignItems: 'center', paddingLeft: 32}}>iMessage</div>
			</div>
		</AbsoluteFill>
	);
};

const Hook: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const wiggle = spring({frame: frame - 260, fps, config: {damping: 8}});
	const text = frame < 260 ? ["sent my mom outfits I DON'T own 😭"] : ['her reply 💀💀'];
	return (
		<div style={{position: 'absolute', top: 400, left: 0, right: 0, display: 'flex', justifyContent: 'center', fontFamily: FONT, transform: `rotate(${frame < 260 ? 0 : (1 - wiggle) * -6}deg)`}}>
			{text.map((t) => (
				<div key={t} style={{background: '#fff', color: '#000', fontSize: 60, fontWeight: 800, padding: '18px 34px', borderRadius: 22, boxShadow: '0 10px 40px rgba(0,0,0,.18)', maxWidth: 900, textAlign: 'center', lineHeight: 1.2}}>
					{t}
				</div>
			))}
		</div>
	);
};

const LOOKS: Outfit[] = ['leather', 'fur', 'sequin'];

const Reveal: React.FC = () => {
	const frame = useCurrentFrame(); // local to sequence
	const {fps} = useVideoConfig();
	const logo = spring({frame: frame - 4, fps, config: {damping: 11}});
	const line = spring({frame: frame - 16, fps, config: {damping: 14}});
	const phone = spring({frame: frame - 26, fps, config: {damping: 15}});
	const cta = spring({frame: frame - 100, fps, config: {damping: 12}});
	const idx = Math.max(0, Math.min(LOOKS.length - 1, Math.floor((frame - 40) / 22)));
	const swap = frame >= 40 ? ((frame - 40) % 22) / 22 : 1;
	const flash = frame >= 40 && frame < 40 + 22 * LOOKS.length ? interpolate(swap, [0, 0.15, 0.4], [1, 0.6, 0], {extrapolateRight: 'clamp'}) : 0;
	const scan = interpolate(swap, [0, 0.3], [0, 100], {extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{fontFamily: FONT, background: 'radial-gradient(circle at 50% 30%,#FFE1EC 0%,#FF9DBF 45%,#FF4F7B 100%)', alignItems: 'center'}}>
			<div style={{position: 'absolute', top: 210, transform: `scale(${logo})`}}>
				<Logo size={150} />
			</div>
			<div style={{position: 'absolute', top: 420, fontSize: 64, fontWeight: 900, color: '#1A1A1A', opacity: line, transform: `translateY(${(1 - line) * 30}px)`, textAlign: 'center', lineHeight: 1.15}}>
				mom's makeover 😭
			</div>
			<div
				style={{
					position: 'absolute',
					top: 540,
					width: 560,
					height: 900,
					borderRadius: 70,
					background: '#111',
					padding: 18,
					transform: `translateY(${(1 - phone) * 900}px) rotate(${(1 - phone) * 8}deg)`,
					boxShadow: '0 40px 80px rgba(120,0,40,.35)',
				}}
			>
				<div style={{width: '100%', height: '100%', borderRadius: 54, background: '#fff', overflow: 'hidden', position: 'relative'}}>
					<div style={{height: 110, display: 'flex', alignItems: 'flex-end', justifyContent: 'center', paddingBottom: 14}}>
						<Logo size={44} />
					</div>
					<div style={{margin: '0 24px', position: 'relative'}}>
						<Photo outfit={frame < 40 ? 'base' : LOOKS[idx]} person="mom" id="mom" w={476} h={600} />
						<div style={{position: 'absolute', left: 0, right: 0, top: `${scan}%`, height: 8, background: 'linear-gradient(90deg,transparent,#FF2E7E,transparent)', opacity: frame >= 40 && swap < 0.35 ? 1 : 0}} />
						<div style={{position: 'absolute', inset: 0, borderRadius: 40, background: '#fff', opacity: flash}} />
						<div style={{position: 'absolute', right: 18, bottom: 18, background: 'linear-gradient(135deg,#FF2E7E,#FF5A3C)', color: '#fff', fontWeight: 800, fontSize: 28, padding: '10px 20px', borderRadius: 999}}>
							{frame < 40 ? 'your photo' : 'your version ✨'}
						</div>
					</div>
					<div style={{display: 'flex', gap: 14, margin: '22px 24px 0'}}>
						{['Dress', 'Coat', 'Boots'].map((t, k) => (
							<div key={t} style={{flex: 1, background: '#F6F0F3', borderRadius: 22, padding: '14px 0', textAlign: 'center', fontSize: 24, fontWeight: 700, color: '#333'}}>
								{t}
								<div style={{color: '#FF2E7E', fontSize: 22}}>shop ›</div>
							</div>
						))}
					</div>
				</div>
			</div>
			<div style={{position: 'absolute', top: 1470, transform: `scale(${cta})`, background: '#111', color: '#fff', fontSize: 50, fontWeight: 800, padding: '24px 46px', borderRadius: 999}}>
				try any outfit on YOU → Stylit
			</div>
		</AbsoluteFill>
	);
};

export const StylitTikTok: React.FC = () => {
	return (
		<AbsoluteFill style={{background: '#fff'}}>
			<Sequence durationInFrames={REVEAL}>
				<Chat />
				<Hook />
			</Sequence>
			<Sequence from={REVEAL}>
				<Reveal />
			</Sequence>
			{MSGS.filter((m) => m.at > 0).map((m, i) => (
				<Sequence key={i} from={m.at} durationInFrames={15}>
					<Audio src={staticFile(m.from === 'me' ? 'send.wav' : 'recv.wav')} volume={0.8} />
				</Sequence>
			))}
			<Sequence from={REVEAL - 12} durationInFrames={15}>
				<Audio src={staticFile('whoosh.wav')} />
			</Sequence>
			<Sequence from={REVEAL + 4} durationInFrames={30}>
				<Audio src={staticFile('chime.wav')} volume={0.7} />
			</Sequence>
		</AbsoluteFill>
	);
};
