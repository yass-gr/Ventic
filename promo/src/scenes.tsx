import React from 'react';
import {AbsoluteFill} from 'remotion';
import {
  A, ACCENT, at, barF, beatF, clamp, Clip, CUM_SPEED, easeIn, easeInOut, easeOut, easeOutBack, lerp, mixHex, noise1,
  pulse, rgba, springy,
} from './lib';
import {
  Chassis, FONT, glowText, Led, LcdPanel, LightLeaks, Particles, Phone, PHONE_ASPECT, Reflection, rgbSplit,
  SpectrumRing, SpectrumStrip, Tonearm, Vinyl,
} from './ui';

const recAngle = (f: number) => at(A.record, f) * 200; // 33⅓ rpm = 200°/s of groove time

// ============================================================================= 1. needle drop + build
export const Intro: React.FC<{f: number}> = ({f}) => {
  const fade = clamp(f / 20);
  const angle = recAngle(f);
  const arm = lerp(-9, 17.1, easeInOut((f - 6) / 34));
  const lift = 1 - easeOut((f - 36) / 9);
  const open = at(A.open, f);
  const build = clamp((f - barF(2)) / 108);
  const push = f < barF(2) ? lerp(1.0, 1.05, easeInOut(f / barF(2))) : lerp(1.05, 1.32, easeIn(build));
  const tilt = lerp(30, 10, easeInOut(f / 330));
  const scr = pulse(A.events.scratch, f, 6);
  const shakeX = noise1(f * 0.9) * 10 * scr;
  const shakeY = noise1(f * 0.9 + 40) * 10 * scr;
  const dA = (at(A.record, f) - at(A.record, f - 1)) * 200;
  const blurLayers = f >= beatF(2, 3) ? 4 : 0;
  const low = at(A.low, f);
  const typed = 'TURN IT UP';
  const nType = clamp(Math.floor((f - beatF(2, 2)) / 3), 0, typed.length);
  return (
    <AbsoluteFill style={{opacity: fade, background: '#0b0b0c'}}>
      <Chassis tint={ACCENT.amber} tintAmt={0.1 + 0.18 * open + 0.1 * low} />
      <AbsoluteFill style={{transform: `translate(${shakeX}px, ${shakeY}px)`}}>
        <div style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, transformOrigin: '960px 560px',
          transform: `perspective(1800px) rotateX(${tilt}deg) scale(${push}) rotate(${lerp(-5, 2, f / 360)}deg)`}}>
          <div style={{position: 'absolute', left: 960, top: 560, opacity: clamp(open * 1.2)}}>
            <SpectrumRing f={f} radius={468} color={ACCENT.red} gain={0.5 + open} />
          </div>
          <div style={{position: 'absolute', left: 510, top: 110}}>
            {[...Array(blurLayers)].map((_, k) => (
              <div key={k} style={{position: 'absolute', inset: 0, opacity: 0.22}}>
                <Vinyl size={900} angle={angle - (k + 1) * dA * 0.6} />
              </div>
            ))}
            <Vinyl size={900} angle={angle} glow={0.4 + open} />
          </div>
          <Tonearm x={1480} y={230} length={378} angle={arm} lift={lift} />
        </div>
      </AbsoluteFill>
      <div style={{position: 'absolute', left: 96, top: 84, display: 'flex', alignItems: 'center', gap: 16,
        opacity: clamp((f - 14) / 20)}}>
        <Led on={f > 45 ? 1 : 0.1} color={ACCENT.red} />
        <span style={{fontFamily: FONT.ui, fontWeight: 700, fontSize: 30, letterSpacing: 9, color: '#c9ccd1'}}>VENTIC · SIDE A</span>
      </div>
      <div style={{position: 'absolute', right: 96, top: 76, opacity: clamp((f - 20) / 20)}}>
        <LcdPanel w={230} h={66}>
          <div style={{fontFamily: FONT.mono, fontSize: 30, color: ACCENT.red, textShadow: glowText(ACCENT.red, 0.5),
            letterSpacing: 3, lineHeight: '42px', textAlign: 'center'}}>33⅓ RPM</div>
        </LcdPanel>
      </div>
      {nType > 0 ? (
        <div style={{position: 'absolute', left: 0, right: 0, bottom: 90, textAlign: 'center', fontFamily: FONT.mono,
          fontSize: 66, letterSpacing: 18, color: ACCENT.red, textShadow: glowText(ACCENT.red, 0.8 + scr),
          opacity: f % 6 < 5 ? 1 : 0.6}}>
          ▲ {typed.slice(0, nType)}
        </div>
      ) : null}
      <LightLeaks time={f / 60} opacity={0.18 + 0.2 * open} />
    </AbsoluteFill>
  );
};

// ============================================================================= 2. drop: logo slam
export const Drop: React.FC<{f: number}> = ({f}) => {
  const lf = f - barF(3);
  const k = pulse(A.events.kick, f, 6);
  const glow = 0.7 + 0.8 * at(A.rms, f) + 0.9 * k;
  const scale = lerp(2.7, 1, easeOutBack(lf / 15, 1.3)) * (1 + 0.025 * k);
  const blur = Math.max(0, 16 * (1 - lf / 9));
  const split = 20 * Math.exp(-lf / 7) + 4 * k;
  const sub = 'VINTAGE HI-FI MUSIC PLAYER';
  const nSub = clamp(Math.floor((lf - 20) / 1.5), 0, sub.length);
  return (
    <AbsoluteFill>
      <Chassis tint={ACCENT.red} tintAmt={0.3 + 0.2 * k} />
      <AbsoluteFill style={{mixBlendMode: 'screen', opacity: 0.2, transform: `rotate(${lf * 0.15}deg) scale(1.6)`,
        background: `repeating-conic-gradient(from 0deg at 50% 44%, ${rgba(ACCENT.red, 0.35)} 0deg 6deg, rgba(0,0,0,0) 6deg 18deg)`}} />
      <Particles time={f / 60 + 3} color="#ffc9a8" boost={k} count={110} />
      <div style={{position: 'absolute', left: 960, top: 470}}>
        <SpectrumRing f={f} radius={300} color={ACCENT.red} gain={1.3} count={96} />
        <div style={{position: 'absolute', left: -280, top: -280, opacity: 0.9}}>
          <Vinyl size={560} angle={recAngle(f)} glow={0.7 + k} plain />
        </div>
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: 360, textAlign: 'center', fontFamily: FONT.logo,
        fontWeight: 800, fontSize: 230, letterSpacing: 10, color: '#ff6a5e', opacity: clamp(lf / 3),
        transform: `scale(${scale})`, filter: blur > 0.3 ? `blur(${blur}px)` : undefined,
        textShadow: `${rgbSplit(split)}, ${glowText(ACCENT.red, glow)}`}}>VENTIC</div>
      <div style={{position: 'absolute', left: 0, right: 0, top: 690, textAlign: 'center', fontFamily: FONT.ui,
        fontWeight: 700, fontSize: 44, letterSpacing: 16, color: '#e8e9eb'}}>
        {sub.slice(0, nSub)}<span style={{opacity: nSub < sub.length && lf % 10 < 5 ? 1 : 0}}>_</span>
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: 790, display: 'flex', justifyContent: 'center', gap: 34}}>
        {[ACCENT.blue, ACCENT.amber, ACCENT.red, ACCENT.green].map((c, i) => (
          <Led key={i} color={c} size={18} on={clamp((lf - 30 - i * 26) / 4) * (0.75 + 0.25 * k)} />
        ))}
      </div>
    </AbsoluteFill>
  );
};

// ============================================================================= 3. kinetic words
const WORDS = [
  {k: 0, text: 'NO STREAMING.', color: ACCENT.red},
  {k: 1, text: 'NO ADS.', color: ACCENT.amber},
  {k: 2, text: 'NO INTERNET.', color: ACCENT.blue},
  {k: 3, text: 'JUST YOUR MUSIC.', color: ACCENT.red},
];

export const Words: React.FC<{f: number}> = ({f}) => {
  const cur = [...WORDS].reverse().find((w) => f >= beatF(4, w.k)) ?? WORDS[0];
  const lf = f - beatF(4, cur.k);
  const prev = WORDS[cur.k - 1];
  const last = cur.k === 3;
  const s = lerp(1.55, 1, easeOutBack(lf / 11, 1.6)) * (last ? lerp(1, 1.1, easeInOut(lf / 36)) : 1);
  const wipe = easeOut(lf / 8);
  const k = pulse(A.events.kick, f, 6);
  return (
    <AbsoluteFill style={{background: '#0d0d0f'}}>
      <AbsoluteFill style={{background: `linear-gradient(115deg, ${rgba(cur.color, 0.0)} 0%, ${rgba(cur.color, 0.55)} 50%, ${rgba(cur.color, 0.0)} 100%)`,
        clipPath: `polygon(${lerp(-40, 0, wipe)}% 0, ${lerp(0, 100, wipe)}% 0, ${lerp(-20, 120, wipe)}% 100%, ${lerp(-60, 0, wipe)}% 100%)`}} />
      <AbsoluteFill style={{background: 'repeating-linear-gradient(90deg, rgba(255,255,255,0.02) 0 1px, rgba(0,0,0,0) 1px 3px)'}} />
      {prev ? (
        <div style={{position: 'absolute', left: 0, right: 0, top: 440 - 90 * easeOut(lf / 8), textAlign: 'center',
          fontFamily: FONT.logo, fontWeight: 800, fontSize: 130, color: '#fff', opacity: 0.35 * (1 - clamp(lf / 8)),
          filter: `blur(${6 * clamp(lf / 8)}px)`, letterSpacing: 4}}>{prev.text}</div>
      ) : null}
      <div style={{position: 'absolute', left: 0, right: 0, top: last ? 360 : 430, textAlign: 'center', transform: `scale(${s}) rotate(${lerp(-4, 0, easeOut(lf / 10))}deg)`}}>
        {last ? (
          <>
            <div style={{fontFamily: FONT.ui, fontWeight: 700, fontSize: 70, letterSpacing: 30, color: '#f2f2f2'}}>JUST YOUR</div>
            <div style={{fontFamily: FONT.logo, fontWeight: 800, fontSize: 230, color: '#fff', letterSpacing: 8,
              textShadow: `${rgbSplit(14 * Math.exp(-lf / 6) + 3 * k)}, ${glowText(ACCENT.red, 1.2 + k)}`}}>MUSIC.</div>
          </>
        ) : (
          <div style={{fontFamily: FONT.logo, fontWeight: 800, fontSize: 150, color: '#fff', letterSpacing: lerp(40, 6, easeOut(lf / 10)),
            textShadow: `${rgbSplit(12 * Math.exp(-lf / 6))}, ${glowText(cur.color, 1 + k)}`}}>{cur.text}</div>
        )}
      </div>
    </AbsoluteFill>
  );
};

// ============================================================================= 4. features: phone + zoom-to-feature
type Cam = {s: number; x: number; y: number; ry: number; tx: number};
const PH = 860; // screen height (px) in the wide shot
const PK = PH / 2400;
const PC = {x: 1330, y: 545};
const focus = (sx: number, sy: number, s: number, ry: number, tx = 1300): Cam => ({s, x: (sx - 540) * PK, y: (sy - 1200) * PK, ry, tx});
const SHOTS: {f: number; cam: Cam; dur: number}[] = [
  {f: barF(5), cam: focus(540, 1200, 1, -14, PC.x), dur: 1},
  {f: beatF(5, 1), cam: focus(540, 690, 2.05, 0), dur: 26},
  {f: barF(6), cam: focus(330, 1850, 2.25, 0), dur: 22},
  {f: barF(7), cam: focus(840, 1850, 2.45, 0, 1500), dur: 22},
  {f: barF(8), cam: focus(540, 1200, 1, 12, PC.x), dur: 30},
];

const camAt = (f: number): Cam => {
  let c = SHOTS[0].cam;
  for (let i = 1; i < SHOTS.length; i++) {
    const sh = SHOTS[i];
    if (f < sh.f) break;
    const t = easeInOut((f - sh.f) / sh.dur);
    const p = c;
    c = {s: lerp(p.s, sh.cam.s, t), x: lerp(p.x, sh.cam.x, t), y: lerp(p.y, sh.cam.y, t), ry: lerp(p.ry, sh.cam.ry, t),
      tx: lerp(p.tx, sh.cam.tx, t)};
  }
  return c;
};

const featureClip = (f: number): {clip: Clip; t: number} => {
  if (f < barF(7)) return {clip: 'np_red', t: 200 + (f - barF(5))};
  if (f < barF(8)) return {clip: 'np_red_knob', t: 100 + 1.8 * (f - barF(7))};
  return {clip: 'library_scroll', t: 220 + 1.3 * (f - barF(8))};
};

const TITLES = [
  {f: beatF(5, 1), n: '01', title: 'LIVE SPECTRUM', sub: 'A real FFT of your music.\nNo microphone permission.'},
  {f: barF(6), n: '02', title: 'REAL VU METER', sub: 'The needle rides\nthe actual signal.'},
  {f: barF(7), n: '03', title: 'HARDWARE VOLUME', sub: 'Press your volume keys —\nthe knob turns with them.'},
];

const FeatureTitle: React.FC<{f: number; start: number; end: number; n: string; title: string; sub: string}> = ({f, start, end, n, title, sub}) => {
  const lf = f - start;
  const out = clamp((f - (end - 8)) / 8);
  const rev = easeOut(lf / 12);
  return (
    <div style={{position: 'absolute', left: 130, top: 330, width: 760, opacity: 1 - out, transform: `translateY(${-40 * out}px)`}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 18, marginBottom: 18, opacity: clamp(lf / 6)}}>
        <Led on={1} color={ACCENT.red} size={16} />
        <span style={{fontFamily: FONT.mono, fontSize: 30, color: ACCENT.red, letterSpacing: 6, textShadow: glowText(ACCENT.red, 0.5)}}>
          FEATURE {n} / 04
        </span>
      </div>
      <div style={{overflow: 'hidden', clipPath: `inset(0 ${100 - rev * 100}% 0 0)`}}>
        <div style={{fontFamily: FONT.ui, fontWeight: 700, fontSize: 104, lineHeight: 1.0, letterSpacing: 6, color: '#f4f4f5',
          textShadow: rgbSplit(10 * Math.exp(-lf / 6))}}>{title}</div>
      </div>
      <div style={{width: 520 * rev, height: 4, background: ACCENT.red, margin: '26px 0', boxShadow: `0 0 14px ${ACCENT.red}`}} />
      <div style={{fontFamily: FONT.ui, fontWeight: 500, fontSize: 40, lineHeight: 1.3, color: '#b5b9bf', whiteSpace: 'pre-line',
        opacity: clamp((lf - 6) / 10), transform: `translateY(${16 * (1 - clamp((lf - 6) / 10))}px)`}}>{sub}</div>
    </div>
  );
};

export const Features: React.FC<{f: number}> = ({f}) => {
  const lf = f - barF(5);
  const enter = easeOutBack(lf / 30, 1.2);
  const cam = camAt(f);
  const k = pulse(A.events.kick, f, 7);
  const sw = pulse(A.events.whoosh, f, 5);
  const {clip, t} = featureClip(f);
  const w = PH * PHONE_ASPECT;
  const dx = cam.tx - PC.x - cam.s * cam.x;
  const dy = 560 - PC.y - cam.s * cam.y;
  const zoomed = cam.s > 1.2;
  const x0 = lerp(760, 0, enter);
  const ry = cam.ry + lerp(40, 0, easeOut(lf / 30));
  const glitch = f >= barF(8) && f < barF(8) + 6;
  const count = Math.round(1940 * easeOut((f - barF(8) - 4) / 44));
  const phone = <Phone clip={clip} t={t} h={PH} glare={zoomed ? 0.4 : 1} />;
  return (
    <AbsoluteFill>
      <Chassis tint={ACCENT.red} tintAmt={0.14 + 0.08 * k} />
      <Particles time={f / 60} count={70} boost={k} opacity={0.8} />
      {/* floor */}
      <div style={{position: 'absolute', left: 0, right: 0, top: 980, bottom: 0,
        background: 'linear-gradient(180deg, rgba(0,0,0,0.0), rgba(0,0,0,0.45))'}} />
      <div style={{position: 'absolute', left: PC.x - w / 2 - 12, top: PC.y - PH / 2 - 12,
        transform: `translate(${dx + x0}px, ${dy}px) scale(${cam.s * (1 + 0.012 * k)})`, transformOrigin: `${w / 2 + 12}px ${PH / 2 + 12}px`}}>
        <div style={{transform: `perspective(2400px) rotateY(${ry}deg) rotateX(${zoomed ? 0 : 4}deg)`,
          filter: glitch ? 'saturate(1.6) contrast(1.2)' : undefined}}>
          {phone}
          {!zoomed ? <Reflection height={PH + 24}>{phone}</Reflection> : null}
        </div>
      </div>
      {glitch ? (
        <AbsoluteFill style={{background: `linear-gradient(0deg, rgba(0,0,0,0) 40%, ${rgba(ACCENT.red, 0.35)} 41%, rgba(0,0,0,0) 44%)`,
          mixBlendMode: 'screen'}} />
      ) : null}
      {/* left gradient keeps titles readable over zoomed footage */}
      <div style={{position: 'absolute', left: 0, top: 0, bottom: 0, width: 980,
        background: 'linear-gradient(90deg, rgba(14,14,16,0.92) 0%, rgba(14,14,16,0.78) 60%, rgba(14,14,16,0) 100%)',
        opacity: clamp((cam.s - 1) * 1.6)}} />
      {TITLES.map((tt, i) => {
        const end = i + 1 < TITLES.length ? TITLES[i + 1].f : barF(8);
        return f >= tt.f && f < end ? (
          <FeatureTitle key={tt.n} n={tt.n} title={tt.title} sub={tt.sub} start={tt.f} end={end} f={f} />
        ) : null;
      })}
      {f >= barF(8) ? (
        <div style={{position: 'absolute', left: 130, top: 300}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 18, marginBottom: 10}}>
            <Led on={1} color={ACCENT.red} size={16} />
            <span style={{fontFamily: FONT.mono, fontSize: 30, color: ACCENT.red, letterSpacing: 6}}>FEATURE 04 / 04</span>
          </div>
          <div style={{fontFamily: FONT.logo, fontWeight: 800, fontSize: 190, color: '#fff', letterSpacing: 4,
            textShadow: `${rgbSplit(8 * Math.exp(-(f - barF(8)) / 6))}, ${glowText(ACCENT.red, 0.9 + k)}`}}>
            {count.toLocaleString('en-US')}
          </div>
          <div style={{fontFamily: FONT.ui, fontWeight: 700, fontSize: 64, letterSpacing: 8, color: '#f4f4f5', marginTop: -8}}>
            TRACKS. INSTANTLY.
          </div>
          <div style={{fontFamily: FONT.ui, fontWeight: 500, fontSize: 38, color: '#b5b9bf', marginTop: 18,
            opacity: clamp((f - barF(8) - 14) / 12)}}>Your whole library, cached on-device.</div>
        </div>
      ) : null}
      <AbsoluteFill style={{background: '#fff', opacity: 0.35 * sw * (zoomed ? 1 : 0.4), mixBlendMode: 'overlay'}} />
    </AbsoluteFill>
  );
};

// ============================================================================= 5. accents + dark/light flip
const QUAD: {clip: Clip; color: string; name: string}[] = [
  {clip: 'np_blue', color: ACCENT.blue, name: 'BLUE'},
  {clip: 'np_amber', color: ACCENT.amber, name: 'AMBER'},
  {clip: 'np_red', color: ACCENT.red, name: 'RED'},
  {clip: 'np_green', color: ACCENT.green, name: 'GREEN'},
];

export const Accents: React.FC<{f: number}> = ({f}) => {
  const b9 = barF(9);
  const b10 = barF(10);
  const shown = QUAD.filter((_, i) => f >= beatF(9, i));
  const curColor = f < b10 ? (shown[shown.length - 1]?.color ?? ACCENT.blue) : ACCENT.red;
  const k = pulse(A.events.kick, f, 7);
  const brass = pulse(A.events.brass, f, 8);
  const collapse = easeInOut((f - b10) / 14);
  const flip = easeInOut((f - beatF(10, 2)) / 20);
  const light = clamp((f - beatF(10, 2) - 10) / 10);
  const title = f < b10 ? '4 ACCENT COLOURS' : f < beatF(10, 2) ? 'SWITCH ANYTIME' : 'DARK & LIGHT';
  const tStart = f < b10 ? b9 : f < beatF(10, 2) ? b10 : beatF(10, 2);
  const tl = f - tStart;
  return (
    <AbsoluteFill>
      <Chassis tint={curColor} tintAmt={0.22 + 0.2 * brass} />
      <AbsoluteFill style={{background: `radial-gradient(ellipse at 50% 55%, ${rgba('#f3e6cf', 0.28 * light)} 0%, rgba(0,0,0,0) 60%)`}} />
      <Particles time={f / 60 + 7} count={70} color={mixHex('#ffd9b0', curColor, 0.5)} boost={k} opacity={0.7} />
      <div style={{position: 'absolute', left: 0, right: 0, top: 70, textAlign: 'center', fontFamily: FONT.logo, fontWeight: 800,
        fontSize: 86, letterSpacing: 10, color: '#fff', transform: `scale(${lerp(1.3, 1, easeOutBack(tl / 10))})`,
        textShadow: `${rgbSplit(10 * Math.exp(-tl / 6))}, ${glowText(curColor, 0.8 + brass)}`}}>{title}</div>
      {/* the four phones */}
      {f < b10 + 16
        ? QUAD.map((q, i) => {
            const st = beatF(9, i);
            if (f < st) return null;
            const lf = f - st;
            const pop = springy(lf, 0.35, 6);
            const x = lerp(360 + i * 400, 960, collapse);
            const ry = lerp([18, 6, -6, -18][i], 0, collapse);
            const hh = 600;
            const w = hh * PHONE_ASPECT;
            return (
              <div key={q.name} style={{position: 'absolute', left: x - w / 2, top: 590 - hh / 2 + (1 - clamp(pop)) * 120,
                transform: `perspective(2000px) rotateY(${ry}deg) scale(${lerp(0.55, 1, clamp(pop, 0, 1.15)) * (1 - collapse * 0.3)})`,
                opacity: clamp(lf / 4) * (1 - collapse)}}>
                <div style={{position: 'absolute', left: -60, top: -40, right: -60, bottom: -40, borderRadius: '50%',
                  background: `radial-gradient(circle, ${rgba(q.color, 0.45 * Math.exp(-lf / 14) + 0.12)} 0%, rgba(0,0,0,0) 65%)`}} />
                <Phone clip={q.clip} t={40 + lf} h={hh} />
                <div style={{position: 'absolute', left: 0, right: 0, top: hh + 44, display: 'flex', justifyContent: 'center'}}>
                  <div style={{display: 'flex', alignItems: 'center', gap: 12, padding: '10px 20px', borderRadius: 10,
                    background: 'linear-gradient(180deg, #0d181b, #081113)', boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.7)'}}>
                    <Led on={1} color={q.color} size={12} />
                    <span style={{fontFamily: FONT.mono, fontSize: 28, letterSpacing: 5, color: q.color, textShadow: glowText(q.color, 0.4)}}>{q.name}</span>
                  </div>
                </div>
              </div>
            );
          })
        : null}
      {/* centre phone: live recolour, then flips to the light theme */}
      {f >= b10 ? (
        <div style={{position: 'absolute', left: 960 - (760 * PHONE_ASPECT) / 2, top: 610 - 380,
          transform: `perspective(2600px) scale(${lerp(0.7, 1, easeOutBack((f - b10) / 16))}) rotateY(${flip * 180}deg)`,
          transformStyle: 'preserve-3d', opacity: clamp((f - b10) / 6)}}>
          <div style={{backfaceVisibility: 'hidden'}}>
            <Phone clip="settings_live" t={50 + 3 * (f - b10)} h={760} />
          </div>
          <div style={{position: 'absolute', left: 0, top: 0, backfaceVisibility: 'hidden', transform: 'rotateY(180deg)'}}>
            <Phone clip="np_light_blue" t={60 + (f - beatF(10, 2))} h={760} />
          </div>
        </div>
      ) : null}
    </AbsoluteFill>
  );
};

// ============================================================================= 6. stats (breakdown)
const STATS = [
  {k: 0, from: 9.9, to: 0.4, dec: 1, unit: 's', label: 'COLD START', color: ACCENT.red},
  {k: 2, from: 99.9, to: 3.8, dec: 1, unit: 'MB', label: 'APK SIZE', color: ACCENT.amber},
  {k: 4, from: 9, to: 0, dec: 0, unit: '', label: 'NETWORK PERMISSIONS', color: ACCENT.blue},
  {k: 6, from: 9, to: 0, dec: 0, unit: '', label: 'ADS · ACCOUNTS · TRACKERS', color: ACCENT.green},
];

export const Stats: React.FC<{f: number}> = ({f}) => {
  const b11 = barF(11);
  const build = clamp((f - beatF(12, 2)) / 72);
  const zoom = 1 + 0.18 * easeIn(build);
  const snare = at(A.snare, f);
  const shake = 10 * build * snare;
  const white = clamp((f - (barF(13) - 10)) / 10);
  return (
    <AbsoluteFill style={{background: '#0c0c0e'}}>
      <Chassis tint={ACCENT.amber} tintAmt={0.12} />
      <div style={{position: 'absolute', left: -380, top: 380, opacity: 0.32, filter: 'blur(3px)'}}>
        <Vinyl size={1300} angle={recAngle(f) * 0.5} plain />
      </div>
      <LightLeaks time={f / 60} opacity={0.35} />
      <AbsoluteFill style={{transform: `scale(${zoom}) translate(${noise1(f * 0.7) * shake}px, ${noise1(f * 0.7 + 9) * shake}px)`}}>
        <div style={{position: 'absolute', left: 0, right: 0, top: 120, textAlign: 'center', fontFamily: FONT.ui, fontWeight: 700,
          fontSize: 50, letterSpacing: 18, color: '#d9dbde', opacity: clamp((f - b11) / 12)}}>BUILT TO BE FAST & PRIVATE</div>
        {STATS.map((s, i) => {
          const st = beatF(11, 0) + s.k * 36;
          if (f < st) return null;
          const lf = f - st;
          const roll = easeOut(lf / 32);
          const v = lerp(s.from, s.to, roll);
          const txt = s.dec ? v.toFixed(1) : String(Math.round(v));
          const col = i % 2;
          const row = Math.floor(i / 2);
          const pop = springy(lf, 0.4, 5);
          return (
            <div key={s.label} style={{position: 'absolute', left: col ? 1000 : 160, top: 250 + row * 330,
              transform: `scale(${lerp(0.8, 1, clamp(pop, 0, 1.1))})`, opacity: clamp(lf / 4)}}>
              <LcdPanel w={760} h={280}>
                <div style={{position: 'absolute', left: 40, top: 26, display: 'flex', alignItems: 'baseline', gap: 16}}>
                  <span style={{fontFamily: FONT.logo, fontWeight: 800, fontSize: 132, color: s.color,
                    textShadow: glowText(s.color, 0.9)}}>{txt}</span>
                  <span style={{fontFamily: FONT.logo, fontWeight: 600, fontSize: 56, color: s.color, opacity: 0.9}}>{s.unit}</span>
                </div>
                <div style={{position: 'absolute', left: 44, bottom: 30, fontFamily: FONT.mono, fontSize: 32, letterSpacing: 5,
                  color: s.color, opacity: 0.85}}>{s.label}</div>
                <div style={{position: 'absolute', right: 34, top: 34}}><Led on={1} color={s.color} /></div>
              </LcdPanel>
            </div>
          );
        })}
      </AbsoluteFill>
      <AbsoluteFill style={{background: '#fff', opacity: white * 0.9}} />
    </AbsoluteFill>
  );
};

// ============================================================================= 7. end card
const DownArrow: React.FC = () => (
  <svg width={30} height={30} viewBox="0 0 24 24" style={{verticalAlign: '-5px', marginRight: 8}}>
    <path d="M12 3v13M6 11l6 6 6-6M4 21h16" stroke="#e9eaec" strokeWidth={2.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const EndCard: React.FC<{f: number}> = ({f}) => {
  const b13 = barF(13);
  const lf = f - b13;
  const k = pulse(A.events.kick, f, 7);
  const brass = pulse(A.events.brass, f, 9);
  const speed = at(A.speed, f);
  const stopAmt = 1 - speed;
  const clipT = 420 + (CUM_SPEED[Math.min(CUM_SPEED.length - 1, f)] - CUM_SPEED[b13]);
  const hit = pulse([barF(15)], f, 10);
  const el = (i: number) => clamp((lf - i * 36) / 6);
  const slam = (i: number) => lerp(1.35, 1, easeOutBack((lf - i * 36) / 11, 1.5));
  const ledOn = (i: number) => (f >= barF(14) ? (Math.floor((f - barF(14)) / 36) % 4 === i ? 1 : 0.25) : el(3));
  return (
    <AbsoluteFill>
      <Chassis tint={ACCENT.red} tintAmt={0.22 + 0.15 * brass + 0.3 * hit} />
      <Particles time={CUM_SPEED[Math.min(CUM_SPEED.length - 1, f)] / 60 + 11} count={100} boost={k + hit} />
      <div style={{position: 'absolute', left: 560 - (860 * PHONE_ASPECT) / 2, top: 540 - 430,
        transform: `perspective(2400px) rotateY(${lerp(34, 16, easeOut(lf / 30))}deg) rotateX(4deg) translateY(${lerp(80, 0, easeOutBack(lf / 26))}px)`,
        opacity: clamp(lf / 6)}}>
        <Phone clip="np_red" t={clipT} h={860} />
        <Reflection height={860 + 24}><Phone clip="np_red" t={clipT} h={860} /></Reflection>
      </div>
      <div style={{position: 'absolute', left: 930, top: 250}}>
        <div style={{fontFamily: FONT.logo, fontWeight: 800, fontSize: 176, color: '#ff6a5e', letterSpacing: 6, opacity: el(0),
          transform: `scale(${slam(0) * (1 + 0.04 * hit)})`, transformOrigin: 'left center',
          textShadow: `${rgbSplit(12 * Math.exp(-lf / 6) + 6 * hit)}, ${glowText(ACCENT.red, 0.9 + 0.6 * k + 1.2 * hit)}`}}>VENTIC</div>
        <div style={{fontFamily: FONT.ui, fontWeight: 500, fontSize: 40, color: '#c9ccd1', marginTop: 4, opacity: clamp((lf - 14) / 12)}}>
          The vintage hi-fi player for your local music.
        </div>
        <div style={{marginTop: 38, opacity: el(1), transform: `scale(${slam(1)})`, transformOrigin: 'left center', width: 560}}>
          <LcdPanel w={560} h={100}>
            <div style={{fontFamily: FONT.mono, fontSize: 44, lineHeight: '76px', textAlign: 'center', letterSpacing: 6,
              color: ACCENT.red, textShadow: glowText(ACCENT.red, 0.7 + brass)}}>v1.0.0 · OUT NOW</div>
          </LcdPanel>
        </div>
        <div style={{marginTop: 34, fontFamily: FONT.ui, fontWeight: 700, fontSize: 36, letterSpacing: 7, color: '#e6e7e9',
          opacity: el(2), transform: `translateX(${lerp(40, 0, easeOut((lf - 72) / 10))}px)`}}>
          FREE · OPEN SOURCE · ANDROID 10+ · 3.8 MB
        </div>
        <div style={{marginTop: 40, display: 'flex', alignItems: 'center', gap: 22, opacity: el(3),
          transform: `scale(${slam(3)})`, transformOrigin: 'left center'}}>
          <div style={{padding: '18px 30px', borderRadius: 14, fontFamily: FONT.ui, fontWeight: 700, fontSize: 36, letterSpacing: 5,
            color: '#e9eaec', background: 'linear-gradient(180deg, #4c5055 0%, #33363a 100%)', border: '1px solid #141517',
            boxShadow: '0 4px 10px rgba(0,0,0,0.55), inset 0 1px 0 rgba(255,255,255,0.15)'}}><DownArrow /> GET THE APK</div>
          <div style={{fontFamily: FONT.mono, fontSize: 38, color: '#f1f1f2', letterSpacing: 2}}>github.com/yass-gr/Ventic</div>
        </div>
        <div style={{marginTop: 46, display: 'flex', gap: 30}}>
          {[ACCENT.blue, ACCENT.amber, ACCENT.red, ACCENT.green].map((c, i) => <Led key={i} color={c} size={18} on={ledOn(i)} />)}
        </div>
      </div>
      <SpectrumStrip f={f} color={ACCENT.red} height={110} opacity={0.5} decay={speed} />
      <AbsoluteFill style={{background: '#000', opacity: 0.55 * stopAmt}} />
    </AbsoluteFill>
  );
};
