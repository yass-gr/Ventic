import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {A, ACCENT, at, barF, clamp, easeIn, easeOut, noise1, pulse, rgba, since} from './lib';
import {Accents, Drop, EndCard, Features, Intro, Stats, Words} from './scenes';
import {Grain, Scanlines, Vignette} from './ui';

const sceneAt = (f: number) => {
  if (f < barF(3)) return <Intro f={f} />;
  if (f < barF(4)) return <Drop f={f} />;
  if (f < barF(5)) return <Words f={f} />;
  if (f < barF(9)) return <Features f={f} />;
  if (f < barF(11)) return <Accents f={f} />;
  if (f < barF(13)) return <Stats f={f} />;
  return <EndCard f={f} />;
};

export const Main: React.FC = () => {
  const f = useCurrentFrame();
  const groove = (f >= barF(3) && f < barF(11)) || (f >= barF(13) && f < A.stop.start);
  const imp = pulse(A.events.impact, f, 9);
  const shake = 18 * imp + (groove ? 3.5 * pulse(A.events.snare, f, 5) : 0);
  const sx = noise1(f * 0.8) * shake;
  const sy = noise1(f * 0.8 + 50) * shake;
  const flash = Math.max(pulse(A.events.impact, f, 3.2), 0.45 * pulse(A.events.whoosh, f, 2.2));
  const ds = since(A.events.impact, f);
  const speed = at(A.speed, f);
  // CRT power-off: snap to a bright horizontal line, shrink it to a dot, fade out
  const off0 = A.stop.end + 5;
  const vy = f < off0 ? 1 : Math.max(0.003, 1 - easeIn((f - off0) / 5));
  const vx = f < off0 + 6 ? 1 : Math.max(0.002, 1 - easeIn((f - off0 - 6) / 7));
  const lineGlow = f < off0 ? 0 : clamp((f - off0) / 4) * (1 - clamp((f - off0 - 13) / 6));
  return (
    <AbsoluteFill style={{background: '#000'}}>
      <AbsoluteFill style={{transform: `scaleX(${vx}) scaleY(${vy})`,
        filter: speed < 0.999 ? `saturate(${0.15 + 0.85 * speed}) brightness(${0.55 + 0.45 * speed})` : undefined}}>
        <AbsoluteFill style={{transform: `translate(${sx}px, ${sy}px)`}}>{sceneAt(f)}</AbsoluteFill>
        <Grain f={f} />
        <Scanlines />
        <Vignette />
        {ds < 34 ? (
          <div style={{position: 'absolute', left: 960, top: 520, width: 0, height: 0}}>
            <div style={{position: 'absolute', left: -600, top: -600, width: 1200, height: 1200, borderRadius: '50%',
              border: `${10 * (1 - ds / 34)}px solid ${rgba('#ffffff', 0.75 * (1 - ds / 34))}`,
              boxShadow: `0 0 60px ${rgba(ACCENT.red, 0.7 * (1 - ds / 34))}`,
              transform: `scale(${0.05 + 1.9 * easeOut(ds / 34)})`}} />
          </div>
        ) : null}
        <AbsoluteFill style={{background: '#fff', opacity: 0.85 * flash, pointerEvents: 'none'}} />
        <AbsoluteFill style={{background: '#fff', opacity: 0.9 * lineGlow, pointerEvents: 'none'}} />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
