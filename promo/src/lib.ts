import raw from './audio.json';

export type AudioData = {
  fps: number;
  frames: number;
  bars: number[];
  beats: number[];
  events: Record<'kick' | 'snare' | 'brass' | 'crash' | 'whoosh' | 'impact' | 'scratch', number[]>;
  stop: {start: number; end: number};
  rms: number[];
  low: number[];
  bands: number[][];
  kick: number[];
  snare: number[];
  brass: number[];
  lead: number[];
  record: number[];
  speed: number[];
  open: number[];
};

/** Per-frame analysis + musical events exported by audio/compose.py (60 fps). */
export const A = raw as unknown as AudioData;
export const W = 1920;
export const H = 1080;
export const FPS = 60;
export const TOTAL = A.frames;
export const BEAT = 36;
export const barF = (b: number) => A.bars[b - 1];
export const beatF = (b: number, k: number) => A.bars[b - 1] + k * BEAT;

export const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const easeOut = (t: number) => 1 - Math.pow(1 - clamp(t), 3);
export const easeIn = (t: number) => Math.pow(clamp(t), 3);
export const easeInOut = (t: number) => {
  const x = clamp(t);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const easeOutBack = (t: number, s = 1.70158) => {
  const x = clamp(t) - 1;
  return x * x * ((s + 1) * x + s) + 1;
};
/** Damped spring 0 -> 1 (overshoots), t in frames since start. */
export const springy = (t: number, freq = 0.32, decay = 7) =>
  t <= 0 ? 0 : 1 - Math.exp(-t / decay) * Math.cos(t * freq);

export const at = (arr: number[], f: number) => arr[Math.max(0, Math.min(arr.length - 1, Math.floor(f)))];
/** Frames since the most recent event at or before f (Infinity if none). */
export const since = (events: number[], f: number) => {
  let d = Infinity;
  for (const e of events) {
    if (e > f) break;
    d = f - e;
  }
  return d;
};
export const pulse = (events: number[], f: number, decay = 8) => {
  const d = since(events, f);
  return d === Infinity ? 0 : Math.exp(-d / decay);
};

export const ACCENT = {blue: '#27B4E6', amber: '#F0A330', red: '#E5544B', green: '#7CC957'};
const hex = (h: string) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
export const rgba = (h: string, a: number) => {
  const [r, g, b] = hex(h);
  return `rgba(${r},${g},${b},${a})`;
};
export const mixHex = (a: string, b: string, k: number) => {
  const x = hex(a);
  const y = hex(b);
  return '#' + x.map((v, i) => Math.round(v + (y[i] - v) * clamp(k)).toString(16).padStart(2, '0')).join('');
};

export const hash = (n: number) => {
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
};
export const noise1 = (x: number) => {
  const i = Math.floor(x);
  const f = x - i;
  const u = f * f * (3 - 2 * f);
  return lerp(hash(i), hash(i + 1), u) * 2 - 1;
};

export const CLIPS = {
  np_red: 837,
  np_red_knob: 479,
  np_blue: 359,
  np_amber: 341,
  np_green: 360,
  settings_live: 659,
  np_light_blue: 359,
  np_light_amber: 300,
  library_scroll: 547,
} as const;
export type Clip = keyof typeof CLIPS;
export const LIGHT_CLIPS: ReadonlySet<Clip> = new Set<Clip>(['np_light_blue', 'np_light_amber']);

/** Cumulative "playback speed" (1 per frame, slowing to 0 during the record stop). */
export const CUM_SPEED: number[] = (() => {
  const out: number[] = [];
  let acc = 0;
  for (let i = 0; i < A.speed.length; i++) {
    out.push(acc);
    acc += A.speed[i];
  }
  return out;
})();
