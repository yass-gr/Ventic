import React from 'react';
import {AbsoluteFill, Img, staticFile} from 'remotion';
import {A, ACCENT, at, clamp, Clip, CLIPS, hash, H, LIGHT_CLIPS, rgba, W} from './lib';

export const FONT = {
  logo: 'Orbitron, sans-serif',
  ui: 'Barlow, sans-serif',
  mono: 'ShareTech, monospace',
};

// ----------------------------------------------------------------------------- footage & phone
export const Footage: React.FC<{clip: Clip; t: number}> = ({clip, t}) => {
  const idx = Math.max(1, Math.min(CLIPS[clip], Math.floor(t) + 1));
  return (
    <Img
      src={staticFile(`footage/${clip}/${String(idx).padStart(4, '0')}.jpg`)}
      style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover'}}
    />
  );
};

const CLIP_TOP_DARK = '#3c3c41';
const CLIP_TOP_LIGHT = '#ece9e4';

const StatusBar: React.FC<{w: number; h: number; light: boolean}> = ({w, h, light}) => {
  const ink = light ? '#2a2824' : '#e9eaec';
  const s = h / 110;
  return (
    <div
      style={{
        position: 'absolute', left: 0, top: 0, width: w, height: h,
        background: light ? CLIP_TOP_LIGHT : CLIP_TOP_DARK,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: `0 ${46 * s}px`, boxSizing: 'border-box', color: ink,
        fontFamily: FONT.ui, fontWeight: 600, fontSize: 40 * s,
      }}
    >
      <span>9:41</span>
      <div style={{position: 'absolute', left: w / 2 - 17 * s, top: h / 2 - 17 * s, width: 34 * s, height: 34 * s,
        borderRadius: '50%', background: '#050506', boxShadow: `inset 0 0 ${6 * s}px #222`}} />
      <svg width={150 * s} height={36 * s} viewBox="0 0 150 36">
        {[0, 1, 2, 3].map((i) => (
          <rect key={i} x={i * 11} y={26 - i * 7} width={7} height={10 + i * 7} rx={1.5} fill={ink} />
        ))}
        <path d="M58 14 a22 22 0 0 1 30 0 M63 20 a14 14 0 0 1 20 0 M68 26 a6 6 0 0 1 10 0" stroke={ink}
          strokeWidth={4} fill="none" strokeLinecap="round" />
        <rect x={98} y={6} width={44} height={24} rx={6} stroke={ink} strokeWidth={3} fill="none" />
        <rect x={102} y={10} width={34} height={16} rx={3} fill={ink} />
        <rect x={143} y={13} width={4} height={10} rx={2} fill={ink} />
      </svg>
    </div>
  );
};

export const PHONE_ASPECT = 1080 / 2400;

/** A clean phone mockup. `h` is the screen height in px; footage is placed under a redrawn status bar. */
export const Phone: React.FC<{
  clip: Clip;
  t: number;
  h: number;
  glare?: number;
  style?: React.CSSProperties;
  overlay?: React.ReactNode;
}> = ({clip, t, h, glare = 1, style, overlay}) => {
  const w = h * PHONE_ASPECT;
  const bez = h * 0.013;
  const r = h * 0.05;
  const topH = (h * 110) / 2400;
  return (
    <div
      style={{
        position: 'relative', width: w + 2 * bez, height: h + 2 * bez, borderRadius: r + bez, padding: bez,
        boxSizing: 'border-box',
        background: 'linear-gradient(145deg, #4a4e55 0%, #17181b 35%, #0b0c0e 60%, #2a2c30 100%)',
        boxShadow: `inset 0 0 0 ${Math.max(1, h * 0.0016)}px #6b7078, 0 ${h * 0.035}px ${h * 0.08}px rgba(0,0,0,0.6)`,
        ...style,
      }}
    >
      <div style={{position: 'relative', width: w, height: h, borderRadius: r, overflow: 'hidden', background: '#000'}}>
        <div style={{position: 'absolute', left: 0, top: topH, width: w, height: h - topH}}>
          <Footage clip={clip} t={t} />
        </div>
        <StatusBar w={w} h={topH} light={LIGHT_CLIPS.has(clip)} />
        {glare > 0 ? (
          <div style={{position: 'absolute', inset: 0, opacity: glare, pointerEvents: 'none',
            background: 'linear-gradient(112deg, rgba(255,255,255,0.16) 0%, rgba(255,255,255,0.04) 30%, rgba(255,255,255,0) 44%)'}} />
        ) : null}
        {overlay}
      </div>
    </div>
  );
};

/** Mirror image of a node fading into the floor. */
export const Reflection: React.FC<{children: React.ReactNode; height: number; opacity?: number}> = ({children, height, opacity = 0.22}) => (
  <div style={{position: 'absolute', left: 0, top: height, width: '100%', height,
    transform: 'scaleY(-1)', transformOrigin: 'top', opacity,
    WebkitMaskImage: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0) 38%)',
    maskImage: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0) 38%)'}}>
    {children}
  </div>
);

// ----------------------------------------------------------------------------- vinyl & tonearm
export const Vinyl: React.FC<{size: number; angle: number; accent?: string; glow?: number; plain?: boolean}> = ({
  size, angle, accent = ACCENT.red, glow = 0, plain = false,
}) => {
  const label = size * 0.36;
  return (
    <div style={{position: 'relative', width: size, height: size, borderRadius: '50%',
      boxShadow: `0 ${size * 0.03}px ${size * 0.08}px rgba(0,0,0,0.75), 0 0 ${size * 0.12 * glow}px ${rgba(accent, 0.35 * glow)}`}}>
      <div style={{position: 'absolute', inset: 0, borderRadius: '50%',
        background: `radial-gradient(circle, #0a0a0b 0 17.6%, transparent 18%),
          repeating-radial-gradient(circle at 50% 50%, #0d0d0f 0px, #0d0d0f 1.6px, #19191c 2.4px, #0f0f11 3.4px),
          radial-gradient(circle, #111 0%, #060607 100%)`}} />
      {/* rotating layer: label + dust so the spin reads */}
      <div style={{position: 'absolute', inset: 0, transform: `rotate(${angle}deg)`}}>
        {[...Array(26)].map((_, i) => {
          const rr = 0.2 + hash(i + 7) * 0.28;
          const a = hash(i + 51) * Math.PI * 2;
          return <div key={i} style={{position: 'absolute', left: size / 2 + Math.cos(a) * rr * size - 1.5,
            top: size / 2 + Math.sin(a) * rr * size - 1.5, width: 2 + hash(i) * 2, height: 2 + hash(i) * 2,
            borderRadius: '50%', background: 'rgba(255,255,255,0.18)'}} />;
        })}
        <div style={{position: 'absolute', left: (size - label) / 2, top: (size - label) / 2, width: label, height: label,
          borderRadius: '50%', background: plain
            ? `radial-gradient(circle at 40% 35%, #3a3c41 0%, #1c1d20 70%, #111214 100%)`
            : `radial-gradient(circle at 40% 35%, ${accent} 0%, ${rgba(accent, 0.92)} 55%, #7a2520 100%)`,
          boxShadow: 'inset 0 0 0 2px rgba(0,0,0,0.35)', display: 'flex', flexDirection: 'column', alignItems: 'center',
          justifyContent: 'center', color: '#fff4ec'}}>
          {plain ? null : <div style={{fontFamily: FONT.mono, fontSize: label * 0.075, letterSpacing: label * 0.012, opacity: 0.85}}>SIDE A</div>}
          <div style={{fontFamily: FONT.logo, fontWeight: 800, fontSize: label * 0.17, letterSpacing: label * 0.006,
            marginTop: label * 0.02, opacity: plain ? 0 : 1}}>VENTIC</div>
          <div style={{width: label * 0.06, height: label * 0.06, borderRadius: '50%', background: '#0b0b0c',
            margin: `${label * 0.05}px 0`, boxShadow: 'inset 0 1px 2px #000'}} />
          <div style={{fontFamily: FONT.mono, fontSize: label * 0.06, letterSpacing: label * 0.008, opacity: plain ? 0 : 0.85}}>
            33⅓ RPM · v1.0.0
          </div>
        </div>
      </div>
      {/* static sheen: light reflecting off the grooves */}
      <div style={{position: 'absolute', inset: 0, borderRadius: '50%', mixBlendMode: 'screen',
        background: `conic-gradient(from 25deg, rgba(255,255,255,0) 0deg, rgba(255,255,255,0.13) 16deg, rgba(255,255,255,0) 42deg,
          rgba(255,255,255,0) 175deg, rgba(255,255,255,0.09) 198deg, rgba(255,255,255,0) 226deg, rgba(255,255,255,0) 360deg)`,
        WebkitMaskImage: 'radial-gradient(circle, transparent 0 18.5%, #000 19%, #000 69.5%, transparent 70%)',
        maskImage: 'radial-gradient(circle, transparent 0 18.5%, #000 19%, #000 69.5%, transparent 70%)'}} />
    </div>
  );
};

/** Tonearm drawn around its pivot (0,0), arm pointing down (+y) and rotated by `angle`. */
export const Tonearm: React.FC<{x: number; y: number; length: number; angle: number; lift: number}> = ({x, y, length, angle, lift}) => {
  const L = length;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: 0, height: 0}}>
      <svg width={260} height={L + 240} viewBox={`-130 -140 260 ${L + 240}`}
        style={{position: 'absolute', left: -130, top: -140, overflow: 'visible', transformOrigin: '130px 140px',
          transform: `rotate(${angle}deg)`,
          filter: `drop-shadow(${10 + lift * 26}px ${14 + lift * 30}px ${8 + lift * 14}px rgba(0,0,0,0.6))`}}>
        <defs>
          <linearGradient id="metal" x1="0" x2="1">
            <stop offset="0" stopColor="#8d9299" /><stop offset="0.45" stopColor="#e6e8eb" />
            <stop offset="0.55" stopColor="#b9bdc3" /><stop offset="1" stopColor="#6c7178" />
          </linearGradient>
          <radialGradient id="base"><stop offset="0" stopColor="#5a5e65" /><stop offset="1" stopColor="#1b1c1f" /></radialGradient>
        </defs>
        <rect x={-22} y={-128} width={44} height={70} rx={10} fill="url(#metal)" />
        <circle cx={0} cy={0} r={48} fill="url(#base)" stroke="#0c0c0d" strokeWidth={3} />
        <circle cx={0} cy={0} r={20} fill="url(#metal)" />
        <path d={`M -6 0 L -6 ${L * 0.78} Q -6 ${L * 0.86} -22 ${L * 0.92} L -14 ${L * 0.95} Q 6 ${L * 0.88} 6 ${L * 0.78} L 6 0 Z`} fill="url(#metal)" />
        <g transform={`translate(-26 ${L * 0.9}) rotate(20)`}>
          <rect x={-22} y={0} width={46} height={78} rx={6} fill="#26282c" stroke="#0b0b0c" strokeWidth={2} />
          <rect x={-12} y={46} width={26} height={24} rx={3} fill={ACCENT.red} />
        </g>
      </svg>
    </div>
  );
};

// ----------------------------------------------------------------------------- audio-reactive
export const SpectrumRing: React.FC<{f: number; radius: number; color: string; opacity?: number; count?: number; gain?: number}> = ({
  f, radius, color, opacity = 1, count = 84, gain = 1,
}) => {
  const bands = at(A.bands as unknown as number[], f) as unknown as number[];
  const size = (radius + 200) * 2;
  return (
    <svg width={size} height={size} viewBox={`${-size / 2} ${-size / 2} ${size} ${size}`}
      style={{position: 'absolute', left: -size / 2, top: -size / 2, opacity, overflow: 'visible',
        filter: `drop-shadow(0 0 10px ${rgba(color, 0.9)})`}}>
      {[...Array(count)].map((_, i) => {
        const j = i < count / 2 ? i : count - 1 - i;
        const v = bands[Math.min(31, Math.floor((j / (count / 2)) * 32))] ?? 0;
        const len = 8 + 150 * Math.pow(v, 1.6) * gain;
        const a = (i / count) * 360;
        return <rect key={i} x={-3} y={-(radius + len)} width={6} height={len} rx={3} fill={color}
          transform={`rotate(${a})`} opacity={0.55 + 0.45 * v} />;
      })}
    </svg>
  );
};

export const SpectrumStrip: React.FC<{f: number; color: string; height: number; opacity?: number; decay?: number}> = ({
  f, color, height, opacity = 1, decay = 1,
}) => {
  const bands = A.bands[Math.max(0, Math.min(A.bands.length - 1, Math.floor(f)))];
  const n = 64;
  const bw = W / n;
  return (
    <svg width={W} height={height} style={{position: 'absolute', left: 0, bottom: 0, opacity}}>
      {[...Array(n)].map((_, i) => {
        const j = i < n / 2 ? n / 2 - 1 - i : i - n / 2;
        const v = bands[Math.min(31, Math.floor((j / (n / 2)) * 32))] * decay;
        const hh = Math.max(3, height * Math.pow(v, 1.4));
        return <rect key={i} x={i * bw + 2} y={height - hh} width={bw - 4} height={hh} fill={color} opacity={0.35 + 0.5 * v} />;
      })}
    </svg>
  );
};

export const Led: React.FC<{on: number; color: string; size?: number}> = ({on, color, size = 14}) => (
  <div style={{width: size, height: size, borderRadius: '50%',
    background: on > 0.02 ? color : '#141518',
    opacity: 0.35 + 0.65 * clamp(on),
    boxShadow: on > 0.02 ? `0 0 ${size * 1.2 * on}px ${rgba(color, 0.95)}, 0 0 ${size * 0.3}px ${color}` : 'inset 0 1px 2px rgba(0,0,0,0.9)'}} />
);

export const LcdPanel: React.FC<{w: number; h: number; children?: React.ReactNode; style?: React.CSSProperties}> = ({w, h, children, style}) => (
  <div style={{position: 'relative', width: w, height: h, borderRadius: 22, padding: 12, boxSizing: 'border-box',
    background: 'linear-gradient(180deg, #17181a, #222427)',
    boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.8), 0 1px 0 rgba(255,255,255,0.08), 0 20px 40px rgba(0,0,0,0.45)', ...style}}>
    <div style={{position: 'relative', width: '100%', height: '100%', borderRadius: 14, overflow: 'hidden',
      background: 'linear-gradient(180deg, #0d181b 0%, #081113 100%)', boxShadow: 'inset 0 2px 10px rgba(0,0,0,0.6)'}}>
      {children}
      <div style={{position: 'absolute', inset: 0, pointerEvents: 'none',
        background: 'repeating-linear-gradient(0deg, rgba(0,0,0,0.16) 0 1px, rgba(0,0,0,0) 1px 3px)'}} />
      <div style={{position: 'absolute', left: 0, right: 0, top: 0, height: '45%', pointerEvents: 'none',
        background: 'linear-gradient(180deg, rgba(255,255,255,0.08), rgba(255,255,255,0))'}} />
    </div>
  </div>
);

export const glowText = (color: string, k = 1) =>
  `0 0 ${10 * k}px ${rgba(color, 0.9)}, 0 0 ${30 * k}px ${rgba(color, 0.55)}, 0 0 ${70 * k}px ${rgba(color, 0.3)}`;

export const rgbSplit = (px: number) =>
  px > 0.05 ? `${px}px 0 0 rgba(0,240,255,0.55), ${-px}px 0 0 rgba(255,30,90,0.55)` : '0 0 0 transparent';

// ----------------------------------------------------------------------------- atmosphere
export const Chassis: React.FC<{tint?: string; tintAmt?: number}> = ({tint = ACCENT.red, tintAmt = 0.18}) => (
  <AbsoluteFill style={{background: `radial-gradient(ellipse at 50% 42%, ${rgba(tint, tintAmt)} 0%, rgba(0,0,0,0) 55%),
    repeating-linear-gradient(90deg, rgba(255,255,255,0.018) 0 1px, rgba(0,0,0,0.02) 1px 3px),
    linear-gradient(180deg, #25272b 0%, #17181b 60%, #0f1012 100%)`}} />
);

export const Particles: React.FC<{time: number; color?: string; count?: number; boost?: number; opacity?: number}> = ({
  time, color = '#ffd9b0', count = 90, boost = 0, opacity = 1,
}) => (
  <AbsoluteFill style={{opacity, pointerEvents: 'none'}}>
    {[...Array(count)].map((_, i) => {
      const depth = 0.3 + hash(i + 300) * 0.7;
      const sp = (18 + hash(i + 200) * 50) * depth;
      const x = hash(i) * W + Math.sin(time * 0.6 + i) * 18 * depth;
      const y = ((hash(i + 100) * (H + 80) - sp * time) % (H + 80) + (H + 80)) % (H + 80) - 40;
      const s = (1.5 + hash(i + 400) * 4) * depth;
      const a = (0.12 + 0.5 * hash(i + 500)) * depth * (0.7 + 0.8 * boost);
      return <div key={i} style={{position: 'absolute', left: x, top: y, width: s, height: s, borderRadius: '50%',
        background: color, opacity: clamp(a), boxShadow: `0 0 ${s * 3}px ${color}`}} />;
    })}
  </AbsoluteFill>
);

export const LightLeaks: React.FC<{time: number; opacity: number}> = ({time, opacity}) => (
  <AbsoluteFill style={{mixBlendMode: 'screen', opacity, pointerEvents: 'none'}}>
    <div style={{position: 'absolute', width: 1300, height: 1300, left: -520 + Math.sin(time * 0.23) * 160,
      top: -600 + Math.cos(time * 0.17) * 120, borderRadius: '50%',
      background: 'radial-gradient(circle, rgba(255,138,61,0.55) 0%, rgba(255,90,60,0.2) 40%, rgba(0,0,0,0) 70%)'}} />
    <div style={{position: 'absolute', width: 1100, height: 1100, right: -480 + Math.cos(time * 0.19) * 140,
      bottom: -560 + Math.sin(time * 0.21) * 100, borderRadius: '50%',
      background: 'radial-gradient(circle, rgba(255,70,120,0.4) 0%, rgba(255,120,60,0.14) 45%, rgba(0,0,0,0) 70%)'}} />
  </AbsoluteFill>
);

export const Grain: React.FC<{f: number; opacity?: number}> = ({f, opacity = 0.13}) => (
  <AbsoluteFill style={{pointerEvents: 'none', mixBlendMode: 'overlay', opacity,
    backgroundImage: `url(${staticFile(`grain/${f % 6}.png`)})`, backgroundSize: '256px 256px',
    backgroundPosition: `${Math.floor(hash(f) * 256)}px ${Math.floor(hash(f + 9) * 256)}px`}} />
);

export const Vignette: React.FC<{amount?: number}> = ({amount = 0.7}) => (
  <AbsoluteFill style={{pointerEvents: 'none',
    background: `radial-gradient(ellipse at 50% 48%, rgba(0,0,0,0) 52%, rgba(0,0,0,${amount}) 100%)`}} />
);

export const Scanlines: React.FC<{opacity?: number}> = ({opacity = 0.06}) => (
  <AbsoluteFill style={{pointerEvents: 'none', opacity,
    background: 'repeating-linear-gradient(0deg, rgba(0,0,0,0.9) 0 1px, rgba(0,0,0,0) 1px 4px)'}} />
);
