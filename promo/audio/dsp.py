"""Small DSP toolkit for the Ventic soundtrack (numba-accelerated where recursion is needed)."""
import math

import numba as nb
import numpy as np

SR = 48000


def mtof(m):
    return 440.0 * 2.0 ** ((m - 69.0) / 12.0)


# ----------------------------------------------------------------------------- oscillators
@nb.njit(cache=True)
def _blep(t, dt):
    if t < dt:
        t = t / dt
        return t + t - t * t - 1.0
    if t > 1.0 - dt:
        t = (t - 1.0) / dt
        return t * t + t + t + 1.0
    return 0.0


@nb.njit(cache=True)
def saw(freq, phase0):
    """Band-limited (PolyBLEP) sawtooth with per-sample frequency."""
    n = freq.shape[0]
    out = np.empty(n)
    ph = phase0
    for i in range(n):
        dt = freq[i] / SR
        out[i] = 2.0 * ph - 1.0 - _blep(ph, dt)
        ph += dt
        if ph >= 1.0:
            ph -= 1.0
    return out


@nb.njit(cache=True)
def square(freq, phase0, pw):
    n = freq.shape[0]
    out = np.empty(n)
    ph = phase0
    for i in range(n):
        dt = freq[i] / SR
        v = 1.0 if ph < pw else -1.0
        v += _blep(ph, dt)
        t2 = ph - pw
        if t2 < 0.0:
            t2 += 1.0
        v -= _blep(t2, dt)
        out[i] = v
        ph += dt
        if ph >= 1.0:
            ph -= 1.0
    return out


@nb.njit(cache=True)
def sine(freq, phase0):
    n = freq.shape[0]
    out = np.empty(n)
    ph = phase0
    for i in range(n):
        out[i] = math.sin(2.0 * math.pi * ph)
        ph += freq[i] / SR
        if ph >= 1.0:
            ph -= 1.0
    return out


# ----------------------------------------------------------------------------- filters
@nb.njit(cache=True)
def svf(x, cutoff, q, mode):
    """Zavalishin TPT state-variable filter. mode 0=LP 1=BP 2=HP. cutoff is per-sample."""
    n = x.shape[0]
    out = np.empty(n)
    ic1 = 0.0
    ic2 = 0.0
    k = 1.0 / q
    for i in range(n):
        fc = cutoff[i]
        if fc > SR * 0.45:
            fc = SR * 0.45
        if fc < 10.0:
            fc = 10.0
        g = math.tan(math.pi * fc / SR)
        a1 = 1.0 / (1.0 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
        v3 = x[i] - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        if mode == 0:
            out[i] = v2
        elif mode == 1:
            out[i] = v1
        else:
            out[i] = x[i] - k * v1 - v2
    return out


def lp(x, fc, q=0.707):
    return svf(x, np.full(x.shape[0], float(fc)), q, 0)


def hp(x, fc, q=0.707):
    return svf(x, np.full(x.shape[0], float(fc)), q, 2)


def bp(x, fc, q=1.0):
    return svf(x, np.full(x.shape[0], float(fc)), q, 1)


@nb.njit(cache=True)
def onepole(x, a):
    out = np.empty(x.shape[0])
    s = 0.0
    for i in range(x.shape[0]):
        s = a * s + (1.0 - a) * x[i]
        out[i] = s
    return out


# ----------------------------------------------------------------------------- physical models
@nb.njit(cache=True)
def ks_string(freq, n_total, n_on, excite, bright, t60, t60_off):
    """Karplus-Strong string. excite: excitation signal (placed at t=0). n_on: samples until note-off."""
    a = bright  # loop low-pass coefficient (0 = bright, ->1 dark)
    comp = a / (1.0 - a)
    L = SR / freq - 0.5 - comp
    if L < 2.0:
        L = 2.0
    N = int(L)
    frac = L - N
    M = N + 4
    hist = np.zeros(M)
    out = np.zeros(n_total)
    rho_on = 10.0 ** (-3.0 / (t60 * freq))
    rho_off = 10.0 ** (-3.0 / (t60_off * freq))
    s = 0.0
    w = 0
    ne = excite.shape[0]
    for i in range(n_total):
        r1 = w - N
        if r1 < 0:
            r1 += M
        r2 = r1 - 1
        if r2 < 0:
            r2 += M
        v = (1.0 - frac) * hist[r1] + frac * hist[r2]
        s = (1.0 - a) * v + a * s
        rho = rho_on if i < n_on else rho_off
        e = excite[i] if i < ne else 0.0
        y = e + rho * s
        hist[w] = y
        out[i] = y
        w += 1
        if w >= M:
            w = 0
    return out


@nb.njit(cache=True)
def var_delay(x, delay_samples):
    """Fractional variable delay (linear interpolation). delay_samples per sample (>=1)."""
    n = x.shape[0]
    out = np.zeros(n)
    for i in range(n):
        d = delay_samples[i]
        pos = i - d
        if pos < 0.0:
            continue
        j = int(pos)
        f = pos - j
        if j + 1 < n:
            out[i] = (1.0 - f) * x[j] + f * x[j + 1]
        else:
            out[i] = x[j]
    return out


@nb.njit(cache=True)
def resample_path(x, pos):
    """Read x at fractional sample positions pos (varispeed / scratch). Out-of-range -> 0."""
    n = pos.shape[0]
    out = np.zeros(n)
    m = x.shape[0]
    for i in range(n):
        p = pos[i]
        if p < 0.0 or p >= m - 1:
            continue
        j = int(p)
        f = p - j
        out[i] = (1.0 - f) * x[j] + f * x[j + 1]
    return out


@nb.njit(cache=True)
def pingpong(xl, xr, d_samples, fb, damp):
    """Stereo ping-pong delay with low-passed feedback. Returns wet L, R."""
    n = xl.shape[0]
    line_l = np.zeros(n)
    line_r = np.zeros(n)
    out_l = np.zeros(n)
    out_r = np.zeros(n)
    sl = 0.0
    sr_ = 0.0
    for i in range(n):
        j = i - d_samples
        dl = line_l[j] if j >= 0 else 0.0
        dr = line_r[j] if j >= 0 else 0.0
        sl = damp * sl + (1.0 - damp) * dl
        sr_ = damp * sr_ + (1.0 - damp) * dr
        # mono-summed input feeds the left line; the lines cross-feed -> echoes alternate L/R
        line_l[i] = 0.5 * (xl[i] + xr[i]) + fb * sr_
        line_r[i] = fb * sl
        out_l[i] = sl
        out_r[i] = sr_
    return out_l, out_r


# ----------------------------------------------------------------------------- helpers
def env_adsr(n, a, d, s, r, n_on):
    """ADSR envelope, times in seconds, n_on = samples until release starts."""
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-6), s + (1 - s) * np.exp(-(t - a) / max(d, 1e-6)))
    t_off = n_on / SR
    lvl_off = np.interp(t_off, t, e) if n_on < n else e[-1]
    rel = lvl_off * np.exp(-(t - t_off) / max(r, 1e-6))
    return np.where(t < t_off, e, rel)


def pan_gains(p):
    """Constant-power pan, p in [-1, 1]."""
    ang = (p + 1) * math.pi / 4
    return math.cos(ang), math.sin(ang)


def db(x):
    return 20 * np.log10(np.maximum(x, 1e-12))


def undb(d):
    return 10 ** (d / 20)
