import React from 'react';

export type Outfit = 'base' | 'leather' | 'fur' | 'sequin';
export type Person = 'me' | 'mom';

const SKIN: Record<Person, string> = {me: '#E9B898', mom: '#DDA585'};

// Stylized fashion-illustration figure, viewBox 400x720.
export const Figure: React.FC<{outfit: Outfit; person?: Person; id: string}> = ({
	outfit,
	person = 'me',
	id,
}) => {
	const skin = SKIN[person];
	const g = (n: string) => `${id}-${n}`;
	return (
		<svg viewBox="0 0 400 720" width="100%" height="100%">
			<defs>
				<linearGradient id={g('leather')} x1="0" x2="1">
					<stop offset="0" stopColor="#7E0B1E" />
					<stop offset="0.45" stopColor="#D2213F" />
					<stop offset="1" stopColor="#8E0F24" />
				</linearGradient>
				<linearGradient id={g('fur')} x1="0" x2="1">
					<stop offset="0" stopColor="#F38DB3" />
					<stop offset="0.5" stopColor="#FFC2D8" />
					<stop offset="1" stopColor="#EE7FA8" />
				</linearGradient>
				<linearGradient id={g('seq')} x1="0" y1="0" x2="1" y2="1">
					<stop offset="0" stopColor="#B8892E" />
					<stop offset="0.35" stopColor="#FFE39A" />
					<stop offset="0.6" stopColor="#D4A64A" />
					<stop offset="1" stopColor="#FFF0C2" />
				</linearGradient>
				<pattern id={g('dots')} width="10" height="10" patternUnits="userSpaceOnUse">
					<circle cx="5" cy="5" r="2.2" fill="#fff" opacity="0.45" />
				</pattern>
			</defs>

			{/* shadow */}
			<ellipse cx="200" cy="700" rx="110" ry="12" fill="#000" opacity="0.12" />

			{/* hair behind */}
			{person === 'me' ? (
				<path d="M150 110 C140 40 260 40 250 110 L262 255 C230 270 170 270 138 255 Z" fill="#3B2418" />
			) : (
				<path d="M150 112 C142 52 258 52 250 112 L256 172 C230 182 170 182 144 172 Z" fill="#B8B2AA" />
			)}

			{/* legs */}
			<g stroke={skin} strokeWidth="32" strokeLinecap="round">
				<line x1="182" y1="400" x2="176" y2="672" />
				<line x1="218" y1="400" x2="224" y2="672" />
			</g>
			{/* arms */}
			<g stroke={skin} strokeWidth="24" strokeLinecap="round" fill="none">
				<path d="M158 206 C138 260 132 330 136 404" />
				<path d="M242 206 C262 260 268 330 264 404" />
			</g>
			{/* neck + head */}
			<rect x="187" y="150" width="26" height="56" rx="10" fill={skin} />
			<ellipse cx="200" cy="112" rx="42" ry="52" fill={skin} />
			{/* face */}
			<ellipse cx="184" cy="112" rx="4" ry="5" fill="#2A1A14" />
			<ellipse cx="216" cy="112" rx="4" ry="5" fill="#2A1A14" />
			<ellipse cx="176" cy="130" rx="9" ry="5" fill="#F08A8A" opacity="0.35" />
			<ellipse cx="224" cy="130" rx="9" ry="5" fill="#F08A8A" opacity="0.35" />
			<path d="M191 142 Q200 149 209 142" stroke="#C2435A" strokeWidth="5" strokeLinecap="round" fill="none" />
			{/* hair front */}
			{person === 'me' ? (
				<path d="M158 98 C160 50 240 46 244 96 C226 76 196 70 158 98 Z" fill="#3B2418" />
			) : (
				<>
					<path d="M156 104 C158 54 242 52 246 104 C220 80 180 80 156 104 Z" fill="#B8B2AA" />
					<g stroke="#5A4A40" strokeWidth="3" fill="none">
						<circle cx="184" cy="112" r="13" />
						<circle cx="216" cy="112" r="13" />
						<line x1="197" y1="112" x2="203" y2="112" />
					</g>
				</>
			)}

			{outfit === 'base' && (
				<>
					<g stroke="#5D82B8" strokeWidth="40" strokeLinecap="butt">
						<line x1="182" y1="330" x2="177" y2="652" />
						<line x1="218" y1="330" x2="223" y2="652" />
					</g>
					<path d="M160 318 L240 318 L242 400 L158 400 Z" fill="#5D82B8" />
					<path d="M146 200 C170 186 230 186 254 200 L266 250 L246 256 L242 332 L158 332 L154 256 L134 250 Z" fill="#F7F4EF" />
					<ellipse cx="172" cy="676" rx="28" ry="14" fill="#fff" stroke="#ddd" strokeWidth="3" />
					<ellipse cx="228" cy="676" rx="28" ry="14" fill="#fff" stroke="#ddd" strokeWidth="3" />
				</>
			)}

			{outfit === 'leather' && (
				<>
					<g stroke="#141414" strokeWidth="40" strokeLinecap="round">
						<line x1="178" y1="520" x2="175" y2="676" />
						<line x1="222" y1="520" x2="225" y2="676" />
					</g>
					<path d="M164 200 L170 200 L176 236 L224 236 L230 200 L236 200 L244 260 C250 320 236 360 252 446 L148 446 C164 360 150 320 156 260 Z" fill={`url(#${g('leather')})`} />
					<path d="M176 250 C196 262 204 262 224 250" stroke="#fff" strokeOpacity="0.35" strokeWidth="4" fill="none" />
					<g fill="#111">
						<rect x="166" y="102" width="30" height="18" rx="8" />
						<rect x="204" y="102" width="30" height="18" rx="8" />
						<rect x="194" y="106" width="12" height="4" />
					</g>
				</>
			)}

			{outfit === 'fur' && (
				<>
					<path d="M156 380 L244 380 L250 470 L150 470 Z" fill="#161616" />
					<g stroke="#F3F1EE" strokeWidth="40" strokeLinecap="round">
						<line x1="178" y1="560" x2="175" y2="676" />
						<line x1="222" y1="560" x2="225" y2="676" />
					</g>
					<path d="M180 200 L220 200 L228 380 L172 380 Z" fill="#161616" />
					<path d="M140 196 C170 180 186 196 186 196 L180 520 L106 504 C108 400 112 260 140 196 Z" fill={`url(#${g('fur')})`} />
					<path d="M260 196 C230 180 214 196 214 196 L220 520 L294 504 C292 400 288 260 260 196 Z" fill={`url(#${g('fur')})`} />
					<g fill="#FFD3E2">
						{[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
							<React.Fragment key={i}>
								<circle cx={110 + i * 1.5} cy={250 + i * 34} r="16" />
								<circle cx={290 - i * 1.5} cy={250 + i * 34} r="16" />
								<circle cx="184" cy={220 + i * 38} r="12" />
								<circle cx="216" cy={220 + i * 38} r="12" />
							</React.Fragment>
						))}
					</g>
					<circle cx="134" cy="410" r="14" fill={skin} />
					<circle cx="266" cy="410" r="14" fill={skin} />
				</>
			)}

			{outfit === 'sequin' && (
				<>
					<path d="M156 202 C180 214 220 214 244 202 L250 300 C262 380 268 470 276 560 L124 560 C132 470 138 380 150 300 Z" fill={`url(#${g('seq')})`} />
					<path d="M156 202 C180 214 220 214 244 202 L250 300 C262 380 268 470 276 560 L124 560 C132 470 138 380 150 300 Z" fill={`url(#${g('dots')})`} />
					<g fill="#D4A64A">
						<path d="M160 650 L190 650 L188 686 L156 686 Z" />
						<path d="M210 650 L240 650 L244 686 L212 686 Z" />
					</g>
				</>
			)}
		</svg>
	);
};
