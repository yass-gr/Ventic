"""Ventic release soundtrack — chill lo-fi funk, 100 BPM, A minor, composed and synthesized from scratch.

Writes:  ../out/music.wav (48 kHz / 24-bit stereo)  and  ../src/audio.json (per-frame analysis + events for the video).
Run:     ../.venv/bin/python compose.py
"""
import json
import math
import os

import numba as nb
import numpy as np
import pyloudnorm as pyln
import soundfile as sf
from pedalboard import (Chorus, Compressor, HighShelfFilter, LowpassFilter, PeakFilter, Pedalboard, Reverb)

from dsp import (SR, bp, db, env_adsr, hp, lp, mtof, onepole, pan_gains, pingpong, resample_path, saw, sine,
                 square, svf, undb, var_delay, ks_string)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_WAV = os.path.join(HERE, "..", "out", "music.wav")
OUT_JSON = os.path.join(HERE, "..", "src", "audio.json")

BPM = 100.0
BEAT = 60.0 / BPM          # 0.6 s
BAR = 4 * BEAT             # 2.4 s
T0 = 1.2                   # needle-drop pre-roll before bar 1
SWING = 0.56               # 16th-note swing
NBARS = 15
DUR = T0 + NBARS * BAR     # 37.2 s
N = int(round(DUR * SR))
FPS = 60
rng = np.random.default_rng(1977)


def bar_t(b):
    return T0 + (b - 1) * BAR


def st(b, step):
    """Swung time of 16th `step` (0..15) in bar b."""
    beat, sub = divmod(step, 4)
    eighth, odd = divmod(sub, 2)
    return bar_t(b) + beat * BEAT + eighth * BEAT / 2 + odd * (BEAT / 2) * SWING


def st_straight(b, step):
    return bar_t(b) + step * BEAT / 4


# ----------------------------------------------------------------------------- mixing buffers
STEMS = {}


def stem(name):
    if name not in STEMS:
        STEMS[name] = np.zeros((2, N))
    return STEMS[name]


def place(name, sig, t, pan=0.0, gain=1.0):
    buf = stem(name)
    i0 = int(round(t * SR))
    if sig.ndim == 1:
        gl, gr = pan_gains(pan)
        sig = np.vstack([sig * gl, sig * gr])
    if i0 < 0:
        sig = sig[:, -i0:]
        i0 = 0
    if i0 >= N:
        return
    i1 = min(N, i0 + sig.shape[1])
    buf[:, i0:i1] += sig[:, : i1 - i0] * gain


@nb.njit(cache=True)
def ar_smooth(x, a_att, a_rel):
    out = np.empty(x.shape[0])
    y = 0.0
    for i in range(x.shape[0]):
        c = a_att if x[i] > y else a_rel
        y = c * y + (1.0 - c) * x[i]
        out[i] = y
    return out


@nb.njit(cache=True)
def peak_limiter(xl, xr, ceiling, look, rel_coef):
    """Transparent look-ahead peak limiter: min-filter + moving-average gain, exponential release."""
    n = xl.shape[0]
    req = np.empty(n)
    for i in range(n):
        a = max(abs(xl[i]), abs(xr[i]))
        req[i] = ceiling / a if a > ceiling else 1.0
    gmin = np.empty(n)
    for i in range(n):
        m = 1.0
        for j in range(i, min(n, i + look)):
            if req[j] < m:
                m = req[j]
        gmin[i] = m
    g = np.empty(n)
    cur = 1.0
    for i in range(n):
        target = gmin[i]
        if target < cur:
            cur = target
        else:
            cur = rel_coef * cur + (1.0 - rel_coef) * target
        g[i] = cur
    # smooth the attack: moving average over the look-ahead window
    out_g = np.empty(n)
    acc = 0.0
    for i in range(n):
        acc += g[i]
        if i >= look:
            acc -= g[i - look]
        out_g[i] = acc / min(i + 1, look)
    yl = np.zeros(n)
    yr = np.zeros(n)
    for i in range(n):
        k = i - look
        if k >= 0:
            yl[i] = xl[k] * out_g[i]
            yr[i] = xr[k] * out_g[i]
    return yl, yr


def coef(seconds):
    return math.exp(-1.0 / (seconds * SR))


def tvec(n):
    return np.arange(n) / SR


# ----------------------------------------------------------------------------- drums
def kick(vel):
    n = int(0.5 * SR)
    t = tvec(n)
    f = 49 + 100 * np.exp(-t / 0.03)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.24) * np.minimum(1, t / 0.0015)
    click = hp(rng.standard_normal(n), 2500) * np.exp(-t / 0.004) * 0.3
    y = np.tanh(1.8 * (body + click)) / np.tanh(1.8)
    return lp(y, 7000) * vel


def snare(vel):
    n = int(0.45 * SR)
    t = tvec(n)
    f = 188 * (1 + 0.22 * np.exp(-t / 0.012))
    ph = np.cumsum(f) / SR
    tone = (0.7 * np.sin(2 * np.pi * ph) + 0.3 * np.sin(2 * np.pi * 1.72 * ph)) * np.exp(-t / 0.07)
    noise = rng.standard_normal(n)
    nz = (bp(noise, 3600, 0.6) * 1.3 + hp(noise, 7000) * 0.35) * np.exp(-t / (0.09 + 0.07 * vel))
    y = (0.55 * tone + 0.9 * nz) * np.minimum(1, t / 0.0008)
    return np.tanh(1.5 * y) * vel


def rim(vel):
    n = int(0.12 * SR)
    t = tvec(n)
    click = bp(rng.standard_normal(n), 1700, 3.0) * np.exp(-t / 0.012)
    tone = np.sin(2 * np.pi * 820 * t) * np.exp(-t / 0.02)
    return (click * 1.2 + tone * 0.5) * vel


HAT_F = [205.3, 304.4, 369.6, 522.7, 540.0, 800.0]


def hat(vel, open_=False):
    dur = 0.7 if open_ else 0.12
    n = int(dur * SR)
    t = tvec(n)
    metal = sum(square(np.full(n, f), rng.random(), 0.5) for f in HAT_F)
    y = hp(metal * 0.4 + rng.standard_normal(n) * 0.7, 7200, 0.8)
    decay = 0.24 if open_ else 0.026 + 0.018 * vel
    return y * np.exp(-t / decay) * np.minimum(1, t / 0.0006) * vel


def shaker(vel):
    n = int(0.11 * SR)
    t = tvec(n)
    return bp(rng.standard_normal(n), 6800, 1.3) * np.minimum(1, t / 0.007) * np.exp(-t / 0.04) * vel


def tambourine(vel):
    n = int(0.3 * SR)
    t = tvec(n)
    jingles = sum(square(np.full(n, f), rng.random(), 0.5) for f in (2140, 2910, 3720, 5230, 6120))
    y = hp(jingles * 0.3 + rng.standard_normal(n), 6500, 0.9)
    return y * np.exp(-t / 0.13) * np.minimum(1, t / 0.002) * vel


def crash(vel, dur=3.2):
    n = int(dur * SR)
    t = tvec(n)
    out = []
    for _ in range(2):  # decorrelated L/R
        metal = sum(square(np.full(n, f * rng.uniform(0.98, 1.02)), rng.random(), 0.5)
                    for f in (311, 447, 613, 787, 1031, 1277))
        y = hp(metal * 0.25 + rng.standard_normal(n), 4200, 0.7)
        out.append(lp(y, 11000) * np.exp(-t / 1.15) * np.minimum(1, t / 0.002) * vel)
    return np.vstack(out)


def reverse_crash(length=1.5):
    c = crash(1.0, dur=length + 0.3)[:, : int(length * SR)]
    r = c[:, ::-1] * np.linspace(0, 1, c.shape[1]) ** 2
    return r


# ----------------------------------------------------------------------------- tonal instruments
def rhodes_note(m, vel, dur):
    f = mtof(m)
    n = int((dur + 1.2) * SR)
    t = tvec(n)
    decay = float(np.interp(m, [40, 90], [3.0, 1.0]))
    amp = np.exp(-t / decay) * np.minimum(1, t / 0.002)
    rel = np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.09))
    i1 = (0.8 + 1.6 * vel) * np.exp(-t / 0.35) + 0.22
    y1 = np.sin(2 * np.pi * f * t + i1 * np.sin(2 * np.pi * f * t))
    i2 = (1.2 + 2.2 * vel) * np.exp(-t / 0.018)
    tine = np.sin(2 * np.pi * f * t + i2 * np.sin(2 * np.pi * 14 * f * t)) * np.exp(-t / 0.2)
    return (y1 + 0.22 * vel * tine) * amp * rel * vel


def bass_note(m, dur, vel, kind="finger"):
    f = mtof(m)
    n_on = int(dur * SR)
    n_tot = n_on + int(0.15 * SR)
    per = max(8, int(SR / f))
    noise = rng.standard_normal(per)
    if kind == "ghost":
        exc = lp(noise, 450) * np.hanning(per) * 0.8
        y = ks_string(f, int(0.09 * SR), int(0.03 * SR), exc, 0.7, 0.12, 0.02)
        return y / (np.max(np.abs(y)) + 1e-9) * vel * 0.8
    bright = 0.28 if kind == "pop" else 0.5
    exc = lp(noise, 3200 if kind == "pop" else 1100) * np.hanning(per)
    y = ks_string(f, n_tot, n_on, exc, bright, 2.4, 0.05)
    y = y / (np.max(np.abs(y)) + 1e-9)
    t = tvec(n_tot)
    sub_env = np.where(t < dur, np.exp(-t / 1.2), math.exp(-dur / 1.2) * np.exp(-(t - dur) / 0.03))
    sub = np.sin(2 * np.pi * f * t) * sub_env * 0.3
    tail = np.where(t < dur + 0.03, 1.0, np.exp(-(t - dur - 0.03) / 0.02))
    return (y + sub) * tail * vel


def guitar_chank(midis, vel, dead=False):
    parts = []
    for k, m in enumerate(midis):
        f = mtof(m)
        per = max(8, int(SR / f))
        exc = hp(rng.standard_normal(per), 400) * np.hanning(per)
        if dead:
            y = ks_string(f, int(0.06 * SR), int(0.012 * SR), exc * 0.6, 0.35, 0.05, 0.01)
        else:
            y = ks_string(f, int(0.2 * SR), int(0.075 * SR), exc, 0.12, 1.2, 0.025)
        y = y / (np.max(np.abs(y)) + 1e-9)
        pad = int(k * 0.006 * SR)  # down-strum
        parts.append(np.concatenate([np.zeros(pad), y]))
    L = max(len(p) for p in parts)
    return sum(np.pad(p, (0, L - len(p))) for p in parts) * vel / len(midis) ** 0.5


def brass_stab(midis, dur, vel, fall=False):
    n = int((dur + 0.35) * SR)
    t = tvec(n)
    out = np.zeros((2, n))
    for k, m in enumerate(midis):
        bend = -0.45 * np.exp(-t / 0.022)
        if fall:
            bend += -5.0 * np.clip((t - (dur - 0.3)) / 0.35, 0, 1) ** 2
        freq = mtof(m + bend)
        osc = sum(saw(freq * 2 ** (c / 1200), rng.random()) for c in (-9, 0, 9)) / 3
        fenv = np.where(t < 0.03, t / 0.03, 0.45 + 0.55 * np.exp(-(t - 0.03) / 0.16))
        if fall:
            fenv *= np.clip(1 - (t - dur + 0.3) / 0.5, 0.25, 1)
        y = svf(osc, 420 + 3600 * fenv * (0.7 + 0.3 * vel), 0.9, 0)
        y *= env_adsr(n, 0.012, 0.3, 0.8, 0.06, int(dur * SR))
        gl, gr = pan_gains((k / max(1, len(midis) - 1) - 0.5) * 0.6)
        out[0] += y * gl
        out[1] += y * gr
    return np.tanh(out * 1.6) * vel


def supersaw_chord(midis, dur):
    n = int((dur + 1.0) * SR)
    out = np.zeros((2, n))
    for m in midis:
        for k, c in enumerate((-14, -6, 0, 6, 14)):
            f = np.full(n, mtof(m) * 2 ** (c / 1200))
            y = saw(f, rng.random())
            gl, gr = pan_gains((k - 2) / 2 * 0.8)
            out[0] += y * gl
            out[1] += y * gr
    env = env_adsr(n, 0.45, 1.0, 1.0, 0.8, int(dur * SR))
    out = np.vstack([svf(out[c], np.full(n, 1700.0), 0.7, 0) for c in range(2)])
    return out * env / (len(midis) * 2.5)


def vowel_ahh(dur=1.2):
    n = int(dur * SR)
    t = tvec(n)
    f0 = 196 * (1 + 0.012 * np.sin(2 * np.pi * 5.5 * t))
    src = saw(f0, 0.0) + 0.15 * rng.standard_normal(n)
    y = (bp(src, 760, 6) + 0.55 * bp(src, 1150, 7) + 0.3 * bp(src, 2750, 9) + 0.15 * bp(src, 3450, 10))
    return y * np.minimum(1, t / 0.02) / (np.max(np.abs(y)) + 1e-9)


# ----------------------------------------------------------------------------- harmony & parts
CHORDS = {1: "F", 2: "E", 3: "Am", 4: "GC", 5: "F", 6: "E", 7: "Am", 8: "GC",
          9: "F", 10: "E", 11: "Am", 12: "GC", 13: "F", 14: "E", 15: "Am"}
VOICE = {"F": [57, 60, 64, 67], "E": [56, 59, 62, 67], "Am": [55, 59, 60, 64],
         "G": [53, 57, 58, 62], "C": [52, 55, 58, 62]}
GUIT = {"F": [69, 72, 76], "E": [68, 74, 79], "Am": [67, 72, 76], "G": [65, 70, 74], "C": [64, 70, 74]}
BASS = {
    "F": [(0, 29, 2, 1.0, "finger"), (3, 41, 1, .8, "pop"), (4, None, 0, .35, "ghost"), (6, 29, 1, .8, "finger"),
          (7, 36, 1, .7, "finger"), (8, 38, 2, .8, "finger"), (10, 41, 1, .85, "pop"), (11, None, 0, .3, "ghost"),
          (12, 36, 1, .75, "finger"), (13, 33, 1, .7, "finger"), (14, 31, 1, .7, "finger"), (15, 29, 1, .7, "finger")],
    "E": [(0, 28, 2, 1.0, "finger"), (3, 40, 1, .8, "pop"), (4, None, 0, .35, "ghost"), (6, 28, 1, .8, "finger"),
          (7, 35, 1, .7, "finger"), (8, 38, 2, .8, "finger"), (10, 40, 1, .85, "pop"), (11, None, 0, .3, "ghost"),
          (12, 32, 1, .75, "finger"), (13, 35, 1, .7, "finger"), (14, 33, 1, .7, "finger"), (15, 32, 1, .7, "finger")],
    "Am": [(0, 33, 2, 1.0, "finger"), (3, 45, 1, .8, "pop"), (4, None, 0, .35, "ghost"), (6, 33, 1, .8, "finger"),
           (7, 40, 1, .7, "finger"), (8, 43, 2, .8, "finger"), (10, 45, 1, .85, "pop"), (11, None, 0, .3, "ghost"),
           (12, 40, 1, .75, "finger"), (13, 36, 1, .7, "finger"), (14, 33, 1, .7, "finger"), (15, 32, 1, .7, "finger")],
    "GC": [(0, 31, 2, 1.0, "finger"), (3, 43, 1, .8, "pop"), (4, None, 0, .35, "ghost"), (6, 31, 1, .75, "finger"),
           (7, 38, 1, .7, "finger"), (8, 36, 2, .85, "finger"), (10, 48, 1, .8, "pop"), (11, None, 0, .3, "ghost"),
           (12, 34, 1, .75, "finger"), (13, 33, 1, .7, "finger"), (14, 31, 1, .7, "finger"), (15, 30, 1, .7, "finger")],
}
MELODY = {
    5: [(2, 76, 2), (4, 79, 2), (6, 81, 3), (10, 79, 1), (11, 76, 1), (12, 72, 4)],
    6: [(0, 71, 2), (2, 74, 2), (4, 76, 1), (5, 79, 1), (6, 80, 4), (10, 76, 1), (11, 74, 1), (12, 71, 3)],
    7: [(2, 76, 2), (4, 79, 2), (6, 81, 2), (8, 83, 2), (10, 81, 1), (11, 79, 1), (12, 76, 4)],
    8: [(0, 74, 2), (2, 77, 2), (4, 79, 3), (8, 76, 2), (10, 74, 2), (12, 72, 2), (14, 70, 2)],
    9: [(0, 81, 4), (4, 84, 4), (8, 81, 2), (10, 79, 2), (12, 76, 4)],
    10: [(0, 80, 3), (3, 83, 3), (6, 86, 2), (8, 83, 2), (10, 80, 2), (12, 76, 2), (14, 79, 1), (15, 80, 1)],
    11: [(0, 81, 8), (10, 79, 2), (12, 76, 4)],
    12: [(0, 74, 6), (8, 76, 4), (12, 79, 2), (14, 82, 2)],
    13: [(0, 81, 2), (2, 76, 2), (4, 79, 2), (6, 81, 3), (10, 79, 1), (11, 76, 1), (12, 72, 4)],
    14: [(0, 71, 2), (2, 74, 2), (4, 76, 1), (5, 79, 1), (6, 80, 4), (10, 76, 1), (11, 74, 1), (12, 71, 3)],
    15: [(0, 81, 10)],
}
BRASS = [  # (bar, step, midis, dur, fall)
    (3, 0, [69, 72, 76, 79], 0.30, False),
    (4, 0, [67, 70, 74, 77], 0.20, False), (4, 4, [67, 70, 74, 77], 0.20, False),
    (4, 8, [70, 74, 76, 79], 0.20, False), (4, 12, [70, 74, 76, 79], 0.50, True),
    (9, 0, [65, 69, 72, 76], 0.22, False), (9, 4, [69, 72, 76, 79], 0.22, False),
    (9, 8, [72, 76, 79, 81], 0.22, False), (9, 12, [76, 79, 81, 84], 0.36, False),
    (13, 0, [65, 69, 72, 76], 0.22, False), (13, 4, [69, 72, 76, 79], 0.22, False),
    (13, 8, [72, 76, 79, 81], 0.22, False), (13, 12, [76, 79, 81, 84], 0.36, False),
    (15, 0, [69, 72, 76, 79, 83], 0.80, True),
]

KICK_T = []
EVENTS = {"kick": [], "snare": [], "brass": [], "crash": [], "whoosh": [], "impact": [], "scratch": []}


def ev(kind, t):
    EVENTS[kind].append(int(round(t * FPS)))


def chord_at(b, step):
    c = CHORDS[b]
    if c == "GC":
        return "G" if step < 8 else "C"
    return c


def write_drums():
    for b in range(2, 16):
        odd = b % 2 == 1
        kicks, snares, ghosts = ([0, 7, 10], [4, 12], [14]) if odd else ([0, 3, 10], [4, 12], [6, 15])
        hats16, hat_vel, openh = True, 1.0, (not odd)
        if b == 2:
            kicks, snares, ghosts = [0, 7, 10], [4], []
        if b == 11:
            kicks, snares, ghosts, hats16, hat_vel, openh = [0], [], [], False, 0.7, False
            for s in (4, 12):
                place("rim", rim(0.8), st(b, s), pan=0.1)
        if b == 12:
            kicks, snares, ghosts, openh = [0, 7, 10], [], [], False
            place("rim", rim(0.8), st(b, 4), pan=0.1)
            for s in range(8, 16):  # build roll (straight 16ths)
                v = 0.3 + 0.7 * (s - 8) / 7
                place("snare", snare(v), st_straight(b, s), pan=0.05)
        if b == 15:
            kicks, snares, ghosts, hats16, openh = [0], [], [], None, False
        for s in kicks:
            if b == 2 and s >= 12:
                continue
            place("kick", kick(1.0 if s == 0 else 0.85), st(b, s))
            ev("kick", st(b, s))
            KICK_T.append(st(b, s))
        for s in snares:
            place("snare", snare(1.0), st(b, s), pan=0.05)
            ev("snare", st(b, s))
        for s in ghosts:
            place("snare", snare(0.22), st(b, s) + rng.normal(0, 0.003), pan=0.05)
        if b in (4, 8):  # fills
            for s, v in ((13, 0.45), (14, 0.65), (15, 0.9)):
                place("snare", snare(v), st(b, s), pan=0.05)
        if hats16 is None:
            continue
        for s in range(16):
            if b == 2 and s >= 12:
                break
            if not hats16 and s % 2:
                continue
            if openh and s == 14:
                place("hats", hat(0.7, open_=True), st(b, s), pan=0.2)
                continue
            if openh and s == 15:
                continue
            v = [0.9, 0.35, 0.65, 0.4][s % 4] * hat_vel * rng.uniform(0.85, 1.05)
            place("hats", hat(v), st(b, s) + rng.normal(0, 0.002), pan=0.2)
        if b in (5, 6, 7, 8, 9, 10, 13, 14):
            for s in range(16):
                place("perc", shaker(rng.uniform(0.5, 0.8) * (1.0 if s % 2 == 0 else 0.6)), st(b, s) + 0.004, pan=-0.3)
        if b in (9, 10, 13, 14):
            for s in (4, 12):
                place("perc", tambourine(0.8), st(b, s), pan=-0.45)
    for b in (3, 5, 9, 13, 15):
        place("crash", crash(1.0 if b != 5 else 0.6), bar_t(b))
        ev("crash", bar_t(b))
    for b in (3, 13):
        rc = reverse_crash(1.5)
        place("crash", rc * 0.8, bar_t(b) - rc.shape[1] / SR)


def write_bass():
    for b in range(1, 16):
        c = CHORDS[b]
        if b == 15:
            place("bass", bass_note(33, 1.6, 1.0), bar_t(b))
            continue
        if b == 11:
            pattern = [(0, 33, 12, .9, "finger"), (14, 32, 2, .6, "finger")]
        elif b == 12:
            pattern = [(0, 31, 7, .85, "finger"), (8, 36, 6, .85, "finger"), (15, 30, 1, .6, "finger")]
        else:
            pattern = BASS[c]
        last = pattern[0][1]
        for (s, m, ln, v, kind) in pattern:
            if b == 2 and s >= 12:
                break
            t = st(b, s)
            if kind == "ghost":
                place("bass", bass_note(last, 0.03, v, "ghost"), t)
                continue
            end_step = s + ln
            t_end = st(b, end_step) if end_step < 16 else bar_t(b + 1)
            place("bass", bass_note(m, (t_end - t) * 0.86, v * rng.uniform(0.95, 1.0), kind), t)
            last = m


def write_rhodes():
    for b in range(1, 16):
        c = CHORDS[b]
        if b == 1:
            hits = [(0, 14, 0.75)]
        elif b in (11,):
            hits = [(0, 16, 0.7)]
        elif b == 12:
            hits = [(0, 8, 0.7), (8, 8, 0.7)]
        elif b == 15:
            hits = [(0, 14, 0.85)]
        elif c == "GC":
            hits = [(0, 5, 0.8), (6, 2, 0.55), (8, 5, 0.8), (14, 2, 0.55)]
        else:
            hits = [(0, 5, 0.8), (7, 3, 0.55), (10, 5, 0.72)]
        for (s, ln, v) in hits:
            if b == 2 and s >= 12:
                continue
            chord = chord_at(b, s)
            t = st(b, s)
            t_end = st(b, s + ln) if s + ln < 16 else bar_t(b + 1)
            for k, m in enumerate(VOICE[chord]):
                vv = v * rng.uniform(0.9, 1.05)
                place("rhodes", rhodes_note(m, vv, (t_end - t) * 0.95), t + k * 0.004 + rng.normal(0, 0.002))


def write_guitar():
    for b in (5, 6, 7, 8, 9, 10, 13, 14):
        for s in (2, 6, 10, 14):
            chord = chord_at(b, s)
            place("guitar", guitar_chank(GUIT[chord], rng.uniform(0.8, 1.0)), st(b, s) + rng.normal(0, 0.003), pan=-0.35)
        for s in (3, 7, 11, 15):
            chord = chord_at(b, s)
            place("guitar", guitar_chank(GUIT[chord], 0.35, dead=True), st(b, s) + rng.normal(0, 0.003), pan=-0.35)


def write_brass():
    for (b, s, midis, dur, fall) in BRASS:
        t = st(b, s)
        place("brass", brass_stab(midis, dur, 1.0, fall), t)
        ev("brass", t)


def write_pad():
    for b in range(9, 16):
        c = CHORDS[b]
        if c == "GC":
            place("pad", supersaw_chord(VOICE["G"], BAR / 2), bar_t(b))
            place("pad", supersaw_chord(VOICE["C"], BAR / 2), bar_t(b) + BAR / 2)
        else:
            dur = BAR if b != 15 else 1.6
            place("pad", supersaw_chord(VOICE[c], dur), bar_t(b))


def write_lead():
    notes = []
    for b, phrase in MELODY.items():
        for (s, m, ln) in phrase:
            t = st(b, s)
            t_end = st(b, s + ln) if s + ln < 16 else bar_t(b + 1)
            notes.append((t, (t_end - t) * 0.92, m, 0.95 if ln >= 3 else 0.85))
    lead = render_lead(notes, 0)
    place("lead", lead, 0.0)
    dbl = render_lead([n for n in notes if bar_t(13) <= n[0] < bar_t(15)], -12)
    place("lead", dbl * 0.45, 0.0)


def render_lead(notes, shift):
    pitch = np.full(N, np.nan)
    gate = np.zeros(N)
    since = np.full(N, 10.0)
    for (t, d, m, v) in notes:
        i0, i1 = int(t * SR), min(N, int((t + d) * SR))
        pitch[i0:i1] = m + shift
        gate[i0:i1] = v
        since[i0:i1] = np.arange(i1 - i0) / SR
    # hold pitch through rests (so glides start from the previous note)
    idx = np.where(~np.isnan(pitch), np.arange(N), 0)
    np.maximum.accumulate(idx, out=idx)
    pitch = pitch[idx]
    pitch[np.isnan(pitch)] = 76
    pitch = onepole(pitch, coef(0.028))
    t = tvec(N)
    vib_amt = np.clip((since - 0.22) / 0.3, 0, 1) * 0.2
    freq = mtof(pitch + vib_amt * np.sin(2 * np.pi * 5.2 * t))
    osc = 0.6 * saw(freq, 0.0) + 0.4 * square(freq, 0.3, 0.45) + 0.25 * sine(freq / 2, 0.0)
    amp = ar_smooth(gate, coef(0.006), coef(0.09))
    cutoff = 1300 + 2600 * np.exp(-since / 0.25) * (gate > 0) + 400 * amp
    y = svf(osc, cutoff, 1.1, 0)
    return np.tanh(1.3 * y * amp)


def write_scratch():
    """Two baby scratches on an 'ahh' vowel in the last beat of bar 2 (music stops underneath)."""
    sample = vowel_ahh(1.2)
    ts = st(2, 12)
    n = int(0.6 * SR)
    tau = tvec(n)
    period = 0.3
    A = 0.14 * math.pi / period
    v = A * np.sin(2 * np.pi * (tau % period) / period)
    p = A * period / (2 * math.pi) * (1 - np.cos(2 * np.pi * (tau % period) / period))
    y = resample_path(sample, (0.05 + p) * SR)
    y *= np.clip(np.abs(v) / 0.5, 0, 1)
    fr = bp(rng.standard_normal(n), 2200, 1.0) * np.abs(v) * 0.12
    place("scratch", (y + fr) * 0.9, ts, pan=0.0)
    ev("scratch", ts)
    ev("scratch", ts + period)
    return ts, p


def write_sfx():
    # turntable motor rumble + needle drop at 0.75 s
    n = int(1.4 * SR)
    t = tvec(n)
    hum = (np.sin(2 * np.pi * 50 * t) + 0.5 * np.sin(2 * np.pi * 100 * t)) * np.minimum(1, t / 0.4) * 0.05
    place("sfx", hum * np.clip((1.4 - t) / 0.3, 0, 1), 0.0)
    nd = int(0.6 * SR)
    t = tvec(nd)
    thump = np.sin(2 * np.pi * np.cumsum(70 - 25 * t / 0.6) / SR) * np.exp(-t / 0.09)
    click = hp(rng.standard_normal(nd), 3000) * np.exp(-t / 0.003)
    scrape = bp(rng.standard_normal(nd), 1300, 1.2) * np.exp(-t / 0.05) * 0.4
    place("sfx", thump * 0.9 + click * 0.6 + scrape, 0.75)
    # whooshes into camera moves
    for tc in (bar_t(5), bar_t(9), bar_t(10) + 2 * BEAT, bar_t(11), bar_t(13)):
        place("sfx", whoosh(0.8), tc - 0.55)
        ev("whoosh", tc)
    for tc in (bar_t(6), bar_t(7), bar_t(8)):
        place("sfx", whoosh(0.45) * 0.6, tc - 0.3)
        ev("whoosh", tc)
    # impacts on the drops
    for tc in (bar_t(3), bar_t(13), bar_t(15)):
        place("sfx", impact(), tc)
        ev("impact", tc)
    # riser into the final chorus
    rn = int(BAR / 2 * SR)
    t = tvec(rn)
    k = t / (BAR / 2)
    riser = svf(rng.standard_normal(rn), 300 * (30 ** k), 1.5, 0) * k ** 2
    riser += np.sin(2 * np.pi * np.cumsum(250 * 4 ** k) / SR) * k ** 3 * 0.25
    place("sfx", riser * 0.5, bar_t(12) + BAR / 2)


def whoosh(dur):
    n = int(dur * SR)
    t = tvec(n)
    k = t / dur
    shape = np.sin(np.pi * np.clip(k, 0, 1)) ** 2 * (k < 0.75) + (k >= 0.75) * np.sin(np.pi * 0.75) ** 2 * np.exp(-(k - 0.75) / 0.08)
    y = svf(rng.standard_normal(n), 250 * (18 ** k), 1.8, 1) * shape
    gl = np.cos((k) * np.pi / 2)
    gr = np.sin((k) * np.pi / 2)
    return np.vstack([y * gl, y * gr]) * 0.9


def impact():
    n = int(1.6 * SR)
    t = tvec(n)
    f = 32 + 40 * np.exp(-t / 0.12)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.55)
    burst = lp(rng.standard_normal(n), 1600) * np.exp(-t / 0.18) * 0.5
    return np.tanh(1.4 * (sub + burst))


def vinyl_bed():
    t = tvec(N)
    level = np.interp(t, [0, bar_t(3) - 0.1, bar_t(3), bar_t(11), bar_t(11) + 0.2, bar_t(13), bar_t(13) + 0.1, DUR],
                      [1.0, 1.0, 0.45, 0.45, 0.85, 0.85, 0.5, 0.7])
    imp = np.zeros(N)
    count = int(28 * DUR)
    pos = rng.integers(0, N, count)
    imp[pos] = rng.exponential(1.0, count) ** 1.6 * rng.choice([-1, 1], count)
    big = rng.integers(0, N, int(2.2 * DUR))
    imp[big] += rng.choice([-3.0, 3.0], big.shape[0])
    crackle = bp(imp, 2600, 0.7) + 0.5 * hp(imp, 6000)
    chans = []
    for c in range(2):  # clicks are shared (mono-ish, like a real stylus), hiss is independent per channel
        hiss = lp(hp(rng.standard_normal(N), 1800), 8000) * 0.05
        chans.append((crackle * (1.0 if c == 0 else 0.92) + hiss) * level)
    return np.vstack(chans)


# ----------------------------------------------------------------------------- mix
def active_rms_db(x):
    mono = x.mean(axis=0)
    e = np.sqrt(ar_smooth(mono ** 2, coef(0.02), coef(0.05)) + 1e-12)
    thr = e.max() * 0.08
    act = e > thr
    return 10 * np.log10(np.mean(mono[act] ** 2) + 1e-12) if act.any() else -120.0


def set_level(x, target_db):
    cur = active_rms_db(x)
    return x * undb(target_db - cur)


def pb(effects, x):
    return Pedalboard(effects)(x.astype(np.float32), SR).astype(np.float64)


def duck_curve(depth):
    g = np.ones(N)
    for t0 in KICK_T:
        i0 = int(t0 * SR)
        m = int(0.35 * SR)
        tt = np.arange(m) / SR
        d = 1 - depth * np.minimum(1, tt / 0.004) * np.exp(-tt / 0.11)
        i1 = min(N, i0 + m)
        g[i0:i1] = np.minimum(g[i0:i1], d[: i1 - i0])
    return g


def main():
    write_drums()
    write_bass()
    write_rhodes()
    write_guitar()
    write_brass()
    write_pad()
    write_lead()
    scratch_t, scratch_p = write_scratch()
    write_sfx()
    STEMS["vinyl"] = vinyl_bed()

    t = tvec(N)
    # --- per-stem processing
    rh = STEMS["rhodes"]
    trem_depth = np.where((t >= bar_t(11)) & (t < bar_t(13)), 0.5, 0.28)
    trem = trem_depth * np.sin(2 * np.pi * 4.3 * t)
    rh = np.vstack([rh[0] * (1 + trem), rh[1] * (1 - trem)])
    wow = (3.0 + 1.0 * np.sin(2 * np.pi * 0.42 * t) + 0.25 * np.sin(2 * np.pi * 5.1 * t)) * SR / 1000
    rh = np.vstack([var_delay(rh[c], wow) for c in range(2)])
    rh = pb([PeakFilter(cutoff_frequency_hz=400, gain_db=-2.5, q=1.0), LowpassFilter(6500),
             Chorus(rate_hz=0.6, depth=0.15, mix=0.25)], rh)
    STEMS["rhodes"] = rh
    STEMS["bass"] = pb([PeakFilter(cutoff_frequency_hz=160, gain_db=3.0, q=0.9), LowpassFilter(3800),
                        Compressor(threshold_db=-22, ratio=4, attack_ms=6, release_ms=90)], STEMS["bass"])
    STEMS["bass"] = np.tanh(STEMS["bass"] * 1.4) / 1.4
    STEMS["guitar"] = pb([LowpassFilter(5200), Chorus(rate_hz=0.8, depth=0.2, mix=0.3)], STEMS["guitar"])
    STEMS["brass"] = pb([HighShelfFilter(cutoff_frequency_hz=3000, gain_db=1.5)], STEMS["brass"])
    STEMS["lead"] = pb([HighShelfFilter(cutoff_frequency_hz=3000, gain_db=1.5)], STEMS["lead"])
    lead = STEMS["lead"]
    wl, wr = pingpong(lead[0], lead[1], int(0.45 * SR), 0.38, 0.55)
    STEMS["lead"] = lead + np.vstack([wl, wr]) * 0.22

    # --- levels (active RMS, dBFS) — funk / lo-fi balance
    targets = {"kick": -15, "snare": -17, "rim": -24, "hats": -29, "perc": -33, "crash": -30, "bass": -16.5,
               "rhodes": -21, "guitar": -27, "brass": -17, "pad": -29, "lead": -18.5, "scratch": -19,
               "sfx": -24, "vinyl": -44}
    for k, v in targets.items():
        if k in STEMS:
            STEMS[k] = set_level(STEMS[k], v)

    # --- sidechain pump (subtle, lo-fi)
    for k, d in (("rhodes", 0.28), ("pad", 0.45), ("bass", 0.22), ("guitar", 0.2), ("lead", 0.12)):
        STEMS[k] = STEMS[k] * duck_curve(d)

    # --- sends
    hall_in = STEMS["rhodes"] * 0.25 + STEMS["lead"] * 0.28 + STEMS["brass"] * 0.3 + STEMS["pad"] * 0.6 + \
        STEMS["guitar"] * 0.15 + STEMS["scratch"] * 0.2 + STEMS["sfx"] * 0.3
    hall_in = np.roll(hall_in, int(0.025 * SR), axis=1)
    hall = pb([Reverb(room_size=0.82, damping=0.5, wet_level=1.0, dry_level=0.0, width=1.0), LowpassFilter(7000)], hall_in)
    room = pb([Reverb(room_size=0.32, damping=0.65, wet_level=1.0, dry_level=0.0, width=0.9)],
              STEMS["snare"] * 0.35 + STEMS["rim"] * 0.3 + STEMS["hats"] * 0.1)

    drums = sum(STEMS[k] for k in ("kick", "snare", "rim", "hats", "perc", "crash")) + room
    drums = pb([Compressor(threshold_db=-18, ratio=3, attack_ms=12, release_ms=110)], drums)
    drums = np.tanh(drums * 1.15) / 1.15

    music = drums + STEMS["bass"] + STEMS["rhodes"] + STEMS["guitar"] + STEMS["brass"] + STEMS["pad"] + \
        STEMS["lead"] + hall * 0.9

    # --- section dynamics: quiet muffled intro rising into the drop, dip for the breakdown, lift for the finale
    sec_db = np.interp(t, [0, bar_t(2), st(2, 12), bar_t(3) - 0.01, bar_t(3), bar_t(11), bar_t(11) + 0.3,
                           bar_t(12) + BAR / 2, bar_t(13) - 0.01, bar_t(13), DUR],
                       [-7, -7, -2, -2, 0, 0, -2.5, -2.5, 0, 0.5, 0.5])
    music = music * undb(sec_db)

    # --- intro: muffled ("next room") then the filter opens into the drop
    cut = np.where(t < bar_t(2), 520, np.exp(np.interp(t, [bar_t(2), st(2, 12)], [math.log(520), math.log(4800)])))
    muff = np.vstack([svf(music[c], cut, 1.25, 0) for c in range(2)])
    xf = np.clip((t - bar_t(3)) / 0.006, 0, 1)
    music = muff * (1 - xf) + music * xf

    mix = music + STEMS["scratch"] + STEMS["sfx"] + STEMS["vinyl"]

    # --- master
    mix = pb([Compressor(threshold_db=-16, ratio=2.0, attack_ms=20, release_ms=160),
              HighShelfFilter(cutoff_frequency_hz=9000, gain_db=-2.0), LowpassFilter(16500)], mix)
    mix = np.tanh(mix * 1.1) / 1.1

    # --- record stop (varispeed to zero) after the final hit
    t_stop, stop_len = bar_t(15) + 0.5, 0.95
    i_stop = int(t_stop * SR)
    tt = (np.arange(N - i_stop)) / SR
    u = np.clip(tt / stop_len, 0, 1)
    speed = (1 - u) ** 1.7
    pos = i_stop + np.cumsum(speed)
    tail = np.vstack([resample_path(mix[c], pos) for c in range(2)])
    tail *= np.where(u < 1, 1.0, 0.0)
    mix[:, i_stop:] = tail
    # CRT power-off blip
    pn = int(0.3 * SR)
    pt = tvec(pn)
    blip = np.sin(2 * np.pi * np.cumsum(900 * 0.07 ** (pt / 0.3)) / SR) * np.exp(-pt / 0.12) * 0.12
    i_b = int((t_stop + stop_len + 0.12) * SR)
    mix[:, i_b:i_b + pn] += blip

    # --- loudness: -14 LUFS integrated, limited to -1 dBFS peak
    meter = pyln.Meter(SR)
    look = int(0.004 * SR)
    for _ in range(3):
        lufs = meter.integrated_loudness(mix.T)
        mix *= undb(-14.0 - lufs)
        yl, yr = peak_limiter(mix[0], mix[1], undb(-1.5), look, coef(0.08))
        mix = np.vstack([np.roll(yl, -look), np.roll(yr, -look)])  # re-align the look-ahead delay
    lufs = meter.integrated_loudness(mix.T)
    from scipy.signal import resample_poly
    tp = db(np.max(np.abs(resample_poly(mix, 4, 1, axis=1))))
    print(f"true peak (4x): {tp:.2f} dBTP")
    peak = db(np.max(np.abs(mix)))
    print(f"master: {lufs:.2f} LUFS, peak {peak:.2f} dBFS, {DUR:.1f} s")
    for k in sorted(STEMS):
        print(f"  {k:8s} active RMS {active_rms_db(STEMS[k]):6.1f} dB")

    os.makedirs(os.path.dirname(OUT_WAV), exist_ok=True)
    sf.write(OUT_WAV, mix.T.astype(np.float32), SR, subtype="PCM_24")

    # ----------------------------------------------------------------------- analysis for the video
    nfr = int(round(DUR * FPS))
    mono = mix.mean(axis=0)
    win = 2048
    hann = np.hanning(win)
    freqs = np.fft.rfftfreq(win, 1 / SR)
    edges = np.geomspace(40, 16000, 33)
    bands, rms, low = [], [], []
    prev = np.zeros(32)
    for f in range(nfr):
        c = int((f + 0.5) / FPS * SR)
        seg = mono[max(0, c - win // 2): c + win // 2]
        seg = np.pad(seg, (0, win - len(seg)))
        mag = np.abs(np.fft.rfft(seg * hann)) / (win / 4)
        bd = np.zeros(32)
        for i in range(32):
            m = (freqs >= edges[i]) & (freqs < edges[i + 1])
            bd[i] = np.sqrt(np.mean(mag[m] ** 2)) if m.any() else 0
        v = np.clip((db(bd) + 62 + np.linspace(0, 14, 32)) / 50, 0, 1)
        prev = np.where(v > prev, v, prev * 0.82 + v * 0.18)
        bands.append([round(float(x), 3) for x in prev])
        r = np.sqrt(np.mean(seg[win // 2 - 400: win // 2 + 400] ** 2))
        rms.append(round(float(np.clip((db(r) + 36) / 30, 0, 1)), 3))
        lo = np.sqrt(np.mean(mag[(freqs > 30) & (freqs < 140)] ** 2))
        low.append(round(float(np.clip((db(lo) + 40) / 34, 0, 1)), 3))

    def stem_env(name):
        x = STEMS[name].mean(axis=0) if name in STEMS else np.zeros(N)
        hop = SR // FPS
        e = np.array([np.sqrt(np.mean(x[i * hop:(i + 1) * hop] ** 2)) for i in range(nfr)])
        e = e / (e.max() + 1e-9)
        out, y = [], 0.0
        for v in e:
            y = v if v > y else y * 0.8 + v * 0.2
            out.append(round(float(y), 3))
        return out

    # record platter position (seconds of groove) -> the video rotates the vinyl by 200 deg/s
    tf = np.arange(nfr) / FPS
    rec = np.copy(tf)
    in_scr = (tf >= scratch_t) & (tf < scratch_t + 0.6)
    rec[in_scr] = scratch_t + np.interp(tf[in_scr] - scratch_t, np.arange(len(scratch_p)) / SR, scratch_p)
    after = tf >= scratch_t + 0.6
    rec[after] = scratch_t + (tf[after] - (scratch_t + 0.6))
    ustop = np.clip((tf - t_stop) / stop_len, 0, 1)
    sp = np.where(tf < t_stop, 1.0, (1 - ustop) ** 1.7)
    rec_stop = rec.copy()
    base = np.interp(t_stop, tf, rec)
    idx = tf >= t_stop
    rec_stop[idx] = base + np.cumsum(sp[idx]) / FPS
    open_ = np.clip(np.interp(tf, [bar_t(2), st(2, 12)], [0, 1]), 0, 1)

    data = {
        "fps": FPS, "frames": nfr, "bpm": BPM, "t0": T0, "swing": SWING,
        "bars": [int(round(bar_t(b) * FPS)) for b in range(1, NBARS + 2)],
        "beats": [int(round((T0 + k * BEAT) * FPS)) for k in range(NBARS * 4)],
        "events": EVENTS,
        "stop": {"start": int(round(t_stop * FPS)), "end": int(round((t_stop + stop_len) * FPS))},
        "rms": rms, "low": low, "bands": bands,
        "kick": stem_env("kick"), "snare": stem_env("snare"), "brass": stem_env("brass"), "lead": stem_env("lead"),
        "record": [round(float(x), 4) for x in rec_stop],
        "speed": [round(float(x), 3) for x in sp],
        "open": [round(float(x), 3) for x in open_],
    }
    with open(OUT_JSON, "w") as fh:
        json.dump(data, fh, separators=(",", ":"))
    print("wrote", OUT_WAV, "and", OUT_JSON)


if __name__ == "__main__":
    main()
