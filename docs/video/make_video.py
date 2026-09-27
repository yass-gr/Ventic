"""Renders the Ventic v1.0.0 release video (1920x1080, 30 fps, ~24 s) with a synthesized soundtrack.

Usage: python3 make_video.py <phone_frames_dir> <out.mp4>
Requires: numpy, Pillow, ffmpeg on PATH. Fonts are read from ../../design/fonts.
"""
import math
import os
import random
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS, DUR = 1920, 1080, 30, 24.0
SR = 44100
BEAT = 0.5  # 120 BPM
PHONE_DIR, OUT = sys.argv[1], sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "..", "design", "fonts")
TMP = os.path.dirname(os.path.abspath(OUT))

RED = (229, 84, 75)
BLUE = (39, 180, 230)
AMBER = (240, 163, 48)
GREEN = (124, 201, 87)
TEXT = (223, 226, 229)
SUB = (163, 169, 175)

# Scene boundaries (seconds)
S2, S3, S4, S5 = 3.5, 6.5, 15.5, 19.5
CUTS = [S2, S3, S4, S5]


# ----------------------------------------------------------------------------- audio
def synth():
    n = int(DUR * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    rng = np.random.default_rng(3)

    def place(sig, start, gain=1.0):
        i = int(start * SR)
        j = min(n, i + len(sig))
        if i < n:
            out[i:j] += sig[: j - i] * gain

    def tone(freq, dur, harmonics=1, detune=0.0):
        tt = np.arange(int(dur * SR)) / SR
        s = np.zeros_like(tt)
        for h in range(1, harmonics + 1):
            for d in ((-detune, detune) if detune else (0.0,)):
                s += np.sin(2 * np.pi * freq * (1 + d) * h * tt) / h
        return s / (2 if detune else 1)

    def env(length, a, r):
        e = np.ones(int(length * SR))
        na, nr = int(a * SR), int(r * SR)
        if na:
            e[:na] = np.linspace(0, 1, na)
        if nr:
            e[-nr:] *= np.linspace(1, 0, nr)
        return e

    # CRT power-on zap + hum
    tt = np.arange(int(0.5 * SR)) / SR
    zap = np.sin(2 * np.pi * np.cumsum(np.linspace(2400, 90, len(tt))) / SR) * np.exp(-tt * 7)
    place(zap, 0.15, 0.5)
    place(rng.standard_normal(int(0.08 * SR)) * np.exp(-np.arange(int(0.08 * SR)) / 900), 0.15, 0.4)
    hum = (np.sin(2 * np.pi * 55 * t) + 0.4 * np.sin(2 * np.pi * 110 * t)) * 0.05
    hum *= np.clip(t / 0.4, 0, 1) * np.clip((S2 - t) / 0.4, 0, 1)
    out += hum

    # Chord progression Am - F - C - G, one bar (4 beats = 2 s) each, from S2
    chords = [(110.0, [220.0, 261.63, 329.63]), (87.31, [174.61, 220.0, 261.63]),
              (130.81, [261.63, 329.63, 392.0]), (98.0, [196.0, 246.94, 293.66])]
    kick_env = np.zeros(n)
    bar = 0
    tb = S2
    while tb < DUR - 1.2:
        root, triad = chords[bar % 4]
        # pad (slow attack, detuned, soft harmonics)
        pad = sum(tone(f, 2.0, harmonics=3, detune=0.004) for f in triad) * env(2.0, 0.35, 0.4)
        place(pad, tb, 0.055)
        # bass: eighth-note pulses once the beat drops
        if tb >= S3 - 0.01:
            for k in range(8):
                b = tone(root, 0.24, harmonics=6) * env(0.24, 0.005, 0.12)
                place(b, tb + k * 0.25, 0.16)
        else:
            place(tone(root, 2.0, harmonics=4) * env(2.0, 0.2, 0.5), tb, 0.09)
        bar += 1
        tb += 2.0

    # drums from the drop
    kick_len = int(0.35 * SR)
    kt = np.arange(kick_len) / SR
    kick = np.sin(2 * np.pi * np.cumsum(45 + 110 * np.exp(-kt * 30)) / SR) * np.exp(-kt * 9)
    hat_len = int(0.05 * SR)
    beat_t = S3
    while beat_t < S5 + 3.0:
        place(kick, beat_t, 0.9)
        i = int(beat_t * SR)
        kick_env[i:i + kick_len] = np.maximum(kick_env[i:i + kick_len], np.exp(-kt[: n - i] * 8)[: len(kick_env[i:i + kick_len])])
        hat = np.diff(rng.standard_normal(hat_len + 1)) * np.exp(-np.arange(hat_len) / 300)
        place(hat, beat_t + BEAT / 2, 0.12)
        beat_t += BEAT
    # sidechain pump on everything tonal
    out *= 1 - 0.55 * kick_env

    # typing clicks in scene 2 (synced to the LCD typing below)
    for ct in typing_click_times():
        c = rng.standard_normal(int(0.012 * SR)) * np.exp(-np.arange(int(0.012 * SR)) / 90)
        place(c, ct, 0.25)

    # risers into each cut + impact on the cut
    for cut in CUTS:
        rl = int(1.2 * SR)
        rt = np.arange(rl) / SR
        noise = rng.standard_normal(rl)
        # crude brightening: blend noise with its first difference as it rises
        riser = (noise * (1 - rt / 1.2) + np.diff(np.r_[0, noise]) * (rt / 1.2)) * (rt / 1.2) ** 2
        riser += np.sin(2 * np.pi * np.cumsum(np.linspace(200, 1400, rl)) / SR) * (rt / 1.2) ** 3 * 0.5
        place(riser, cut - 1.2, 0.14)
        il = int(0.6 * SR)
        impact = rng.standard_normal(il) * np.exp(-np.arange(il) / (0.12 * SR))
        impact += np.sin(2 * np.pi * 50 * np.arange(il) / SR) * np.exp(-np.arange(il) / (0.2 * SR)) * 2
        place(impact, cut, 0.22)

    # power-off at the end
    off_t = DUR - 0.8
    ol = int(0.6 * SR)
    ot = np.arange(ol) / SR
    place(np.sin(2 * np.pi * np.cumsum(np.linspace(900, 30, ol)) / SR) * np.exp(-ot * 4), off_t, 0.45)
    fade = np.clip((DUR - t) / 0.9, 0, 1)
    out *= np.where(t > off_t, fade, 1.0)

    out = np.tanh(out * 1.3)
    out /= np.max(np.abs(out)) + 1e-9
    return (out * 0.89).astype(np.float32)


TYPE_LINES = ["NO STREAMING.", "NO INTERNET.", "JUST YOUR MUSIC."]
TYPE_START = S2 + 0.25
TYPE_CPS = 22.0
TYPE_LINE_GAP = 0.35


def typing_schedule():
    """[(line_index, char_count_fn)] -> returns list of (start_time, text)."""
    sched, t0 = [], TYPE_START
    for line in TYPE_LINES:
        sched.append((t0, line))
        t0 += len(line) / TYPE_CPS + TYPE_LINE_GAP
    return sched


def typing_click_times():
    times = []
    for start, line in typing_schedule():
        for i, ch in enumerate(line):
            if ch != " ":
                times.append(start + i / TYPE_CPS)
    return times


def band_levels(audio, t, bands=28):
    i = int(t * SR)
    win = 2048
    seg = audio[max(0, i - win):i]
    if len(seg) < win:
        seg = np.pad(seg, (win - len(seg), 0))
    spec = np.abs(np.fft.rfft(seg * np.hanning(win)))
    freqs = np.fft.rfftfreq(win, 1 / SR)
    edges = np.geomspace(50, 12000, bands + 1)
    lv = np.zeros(bands)
    for b in range(bands):
        m = (freqs >= edges[b]) & (freqs < edges[b + 1])
        mag = spec[m].mean() if m.any() else 0.0
        # +2 dB per band tilt (pink-noise weighting) so the upper bands read on screen
        lv[b] = np.clip((20 * np.log10(mag * 4 / win + 1e-9) + 44 + 2.0 * b) / 34, 0, 1)
    lv = np.convolve(np.r_[lv[0], lv, lv[-1]], [0.25, 0.5, 0.25], mode="valid")
    return np.maximum(lv, 0.06)


# ----------------------------------------------------------------------------- graphics helpers
def font(name, size, wght=None):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    if wght is not None:
        try:
            f.set_variation_by_axes([wght])
        except Exception:
            pass
    return f


ORB_XL = font("Orbitron%5Bwght%5D.ttf", 230, 800)
ORB_L = font("Orbitron%5Bwght%5D.ttf", 120, 800)
ORB_M = font("Orbitron%5Bwght%5D.ttf", 76, 800)
MONO_L = font("ShareTechMono-Regular.ttf", 64)
MONO_M = font("ShareTechMono-Regular.ttf", 34)
MONO_S = font("ShareTechMono-Regular.ttf", 26)
BAR_B = font("BarlowSemiCondensed-Bold.ttf", 40)
BAR_M = font("BarlowSemiCondensed-SemiBold.ttf", 30)
BAR_S = font("BarlowSemiCondensed-Bold.ttf", 24)


def ease_out(x):
    x = min(max(x, 0.0), 1.0)
    return 1 - (1 - x) ** 3


def ease_in_out(x):
    x = min(max(x, 0.0), 1.0)
    return 3 * x * x - 2 * x * x * x


def chassis():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        k = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * k) for a, b in zip((52, 55, 59), (26, 28, 31))))
    random.seed(4)
    hair = Image.new("L", (W, H), 0)
    hd = ImageDraw.Draw(hair)
    for x in range(0, W, 3):
        hd.line([(x, 0), (x, H)], fill=random.randint(0, 12))
    img = Image.composite(Image.new("RGB", (W, H), (255, 255, 255)), img, hair)
    return img


def vignette_mask():
    v = Image.new("L", (W, H), 0)
    ImageDraw.Draw(v).ellipse((-420, -320, W + 420, H + 380), fill=255)
    return np.asarray(v.filter(ImageFilter.GaussianBlur(220)), dtype=np.float32)[..., None] / 255.0


def scanlines():
    a = np.ones((H, 1, 1), dtype=np.float32)
    a[::3] = 0.82
    return a


BG = np.asarray(chassis(), dtype=np.float32)
BLACK = np.zeros((H, W, 3), dtype=np.float32)
VIG = vignette_mask()
SCAN = scanlines()


def glow_layer(size, draw_fn, color, blur, strength=2.0):
    """Returns RGBA image: blurred glow (x strength) + sharp content."""
    sharp = Image.new("RGBA", size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(sharp), color)
    g = sharp.filter(ImageFilter.GaussianBlur(blur))
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    for _ in range(int(strength)):
        out.alpha_composite(g)
    out.alpha_composite(sharp)
    return out


def text_center(d, cx, y, text, f, fill):
    w = d.textlength(text, font=f)
    d.text((cx - w / 2, y), text, font=f, fill=fill)


def paste_alpha(base, layer, xy=(0, 0), alpha=1.0):
    if alpha <= 0:
        return
    if alpha < 1:
        layer = layer.copy()
        layer.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
    base.alpha_composite(layer, xy)


def phone_frame(frame_img):
    w, h = frame_img.size
    pad, r = 16, 52
    fr = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(fr)
    d.rounded_rectangle((0, 0, w + 2 * pad - 1, h + 2 * pad - 1), r + pad, fill=(12, 13, 15, 255),
                        outline=(92, 96, 102, 255), width=3)
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    fr.paste(frame_img, (pad, pad), m)
    return fr


PHONE_FRAMES = sorted(os.listdir(PHONE_DIR))
PHONE_MASK_CACHE = {}


def load_phone(idx):
    idx = max(0, min(len(PHONE_FRAMES) - 1, idx))
    return Image.open(os.path.join(PHONE_DIR, PHONE_FRAMES[idx])).convert("RGB")


# ----------------------------------------------------------------------------- scenes
LOGO = glow_layer((W, 320), lambda d, c: text_center(d, W / 2, 20, "VENTIC", ORB_XL, c + (255,)), RED, 26, 3)
LOGO_SMALL = glow_layer((W, 180), lambda d, c: text_center(d, W / 2, 10, "VENTIC", ORB_L, c + (255,)), RED, 18, 2)


def scene_intro(t, canvas):
    # CRT power-on: a bright line opens into the frame
    if t < 0.15:
        return "black"
    if t < 0.55:
        k = ease_out((t - 0.15) / 0.4)
        h = max(2, int(H * k))
        d = ImageDraw.Draw(canvas)
        d.rectangle((0, H / 2 - h / 2, W, H / 2 + h / 2), fill=(235, 240, 245, int(255 * (1 - k) ** 0.6)))
        return "crt"
    # flicker on
    flick = [0.62, 0.66, 0.74, 0.8, 0.86]
    on = t > 0.9 or any(abs(t - f) < 0.025 for f in flick)
    if on:
        a = 1.0 if t > 0.9 else 0.55
        scale = 1 + 0.25 * (1 - ease_out((t - 0.55) / 0.6))
        logo = LOGO if scale == 1 else LOGO.resize((int(W * scale), int(320 * scale)))
        paste_alpha(canvas, logo, (int(W / 2 - logo.width / 2), int(360 - logo.height / 2 + 140)), a)
    # subtitle typed
    sub = "VINTAGE HI-FI  ·  LOCAL AUDIO  ·  ANDROID"
    n = int(max(0, (t - 1.4)) * 30)
    if n > 0:
        d = ImageDraw.Draw(canvas)
        shown = sub[: min(n, len(sub))]
        cursor = "_" if (int(t * 4) % 2 == 0 and n < len(sub) + 8) else " "
        text_center(d, W / 2, 690, shown + cursor, MONO_M, SUB)
    # LEDs
    for i, c in enumerate([BLUE, AMBER, RED, GREEN]):
        on_t = 2.2 + i * 0.12
        if t > on_t:
            cx, cy = W / 2 - 60 + i * 40, 780
            led = glow_layer((60, 60), lambda d, col: d.ellipse((22, 22, 38, 38), fill=col + (255,)), c, 7, 2)
            canvas.alpha_composite(led, (int(cx - 30), int(cy - 30)))
    return "bg"


def lcd_panel(size, radius=26):
    w, h = size
    p = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(p)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius + 12, fill=(20, 21, 24, 255))
    inner = (14, 14, w - 15, h - 15)
    for y in range(inner[1], inner[3]):
        k = (y - inner[1]) / (inner[3] - inner[1])
        c = tuple(int(a + (b - a) * k) for a, b in zip((13, 24, 27), (8, 17, 19)))
        d.line([(inner[0], y), (inner[2], y)], fill=c + (255,))
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(inner, radius, fill=255)
    bez = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(bez).rounded_rectangle((0, 0, w - 1, h - 1), radius + 12, fill=(20, 21, 24, 255))
    bez.paste(p, (0, 0), m)
    return bez


LCD2 = lcd_panel((1240, 620))


def scene_type(t, canvas, audio):
    lt = t - S2
    k = ease_out(lt / 0.5)
    x = int(W / 2 - 620)
    y = int(230 + (1 - k) * 60)
    paste_alpha(canvas, LCD2, (x, y), k)
    layer = Image.new("RGBA", (1240, 620), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((56, 44), "TRK 01   SHUF   REP", font=MONO_S, fill=RED + (110,))
    d.text((1060, 44), "PLAY", font=MONO_S, fill=RED + (255,))
    for i, (start, line) in enumerate(typing_schedule()):
        n = int(max(0, t - start) * TYPE_CPS)
        if n <= 0:
            continue
        shown = line[: min(n, len(line))]
        cur = "_" if n <= len(line) and int(t * 6) % 2 == 0 else ""
        d.text((56, 110 + i * 92), shown + cur, font=MONO_L, fill=RED + (255,))
    # spectrum from the soundtrack
    lv = band_levels(audio, t)
    bx, by, bw, gap, bh = 56, 560, 30, 10, 150
    for b in range(28):
        hgt = int(bh * max(0.05, lv[b]))
        x0 = bx + b * (bw + gap)
        for yy in range(by, by - bh, -9):
            lit = (by - yy) <= hgt
            d.rectangle((x0, yy - 5, x0 + bw, yy), fill=RED + (255 if lit else 38,))
    g = layer.filter(ImageFilter.GaussianBlur(9))
    paste_alpha(canvas, g, (x, y), k)
    paste_alpha(canvas, layer, (x, y), k)


CALLOUTS = [
    ("LIVE FFT SPECTRUM", "Computed from the audio itself"),
    ("REAL VU METER", "The needle rides the signal"),
    ("ROTARY VOLUME", "Synced with the volume keys"),
    ("BACKGROUND PLAY", "Lock-screen & notification controls"),
]


def scene_phone(t, canvas):
    lt = t - S3
    fr = load_phone(int(lt * FPS) + 30)
    ph = phone_frame(fr)
    push = 1.0 + 0.05 * ease_in_out(lt / (S4 - S3))
    ph = ph.resize((int(ph.width * push), int(ph.height * push)), Image.BILINEAR)
    slide = ease_out(lt / 0.8)
    px = int(1180 + (1 - slide) * 900 - (push - 1) * 200)
    py = int(H / 2 - ph.height / 2)
    # accent glow behind phone
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((px - 60, py + 120, px + ph.width + 60, py + ph.height - 80), fill=RED + (80,))
    paste_alpha(canvas, glow.filter(ImageFilter.GaussianBlur(110)), (0, 0), slide)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    a = ph.getchannel("A").point(lambda v: 180 if v else 0)
    shadow.paste((0, 0, 0, 255), (px + 10, py + 30), a)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(34)))
    canvas.alpha_composite(ph, (px, py))

    d = ImageDraw.Draw(canvas)
    head_k = ease_out((lt - 0.3) / 0.6)
    if head_k > 0:
        paste_alpha(canvas, LOGO_SMALL, (int(-W / 2 + 470 - (1 - head_k) * 80), 90), head_k)
    for i, (title, sub) in enumerate(CALLOUTS):
        ct = 1.0 + i * 1.8
        k = ease_out((lt - ct) / 0.45)
        if k <= 0:
            continue
        y = 330 + i * 150
        x = int(170 - (1 - k) * 60)
        col = RED
        led = glow_layer((60, 60), lambda dd, c: dd.ellipse((22, 22, 38, 38), fill=c + (255,)), col, 7, 2)
        paste_alpha(canvas, led, (x - 60, y - 4), k)
        tl = glow_layer((900, 70), lambda dd, c: dd.text((0, 0), title, font=BAR_B, fill=c + (255,)), TEXT, 8, 1)
        paste_alpha(canvas, tl, (x, y - 8), k)
        d.text((x, y + 46), sub, font=BAR_M, fill=SUB + (int(255 * k),))
        # underline sweep
        uw = int(460 * ease_out((lt - ct - 0.15) / 0.5))
        if uw > 0:
            d.rectangle((x, y + 92, x + uw, y + 94), fill=RED + (int(160 * k),))


STATS = [("1,940", "TRACKS, INSTANTLY", 1940, "{:,.0f}"), ("0.4 S", "COLD START", 0.4, "{:.1f} S"),
         ("3.8 MB", "APK SIZE", 3.8, "{:.1f} MB"), ("0", "NETWORK PERMISSIONS", 0, "{:.0f}")]


def scene_stats(t, canvas):
    lt = t - S4
    d = ImageDraw.Draw(canvas)
    head_k = ease_out(lt / 0.5)
    tl = glow_layer((W, 80), lambda dd, c: text_center(dd, W / 2, 0, "BUILT TO BE FAST", BAR_B, c + (255,)), SUB, 6, 1)
    paste_alpha(canvas, tl, (0, 150), head_k)
    for i, (_, label, target, fmt) in enumerate(STATS):
        col, row = i % 2, i // 2
        k = ease_out((lt - 0.25 - i * 0.3) / 0.5)
        if k <= 0:
            continue
        cx = W / 2 + (-430 if col == 0 else 430)
        cy = 380 + row * 300
        panel = lcd_panel((740, 240), 20)
        paste_alpha(canvas, panel, (int(cx - 370), int(cy - 110 + (1 - k) * 40)), k)
        count = ease_out((lt - 0.25 - i * 0.3) / 1.4)
        val = fmt.format(target * count) if target else "0"
        accent = [RED, AMBER, BLUE, GREEN][i]
        num = glow_layer((740, 120), lambda dd, c: text_center(dd, 370, 0, val, ORB_M, c + (255,)), accent, 14, 2)
        paste_alpha(canvas, num, (int(cx - 370), int(cy - 80 + (1 - k) * 40)), k)
        text_center(d, cx, cy + 50 + (1 - k) * 40, label, MONO_S, accent + (int(220 * k),))


END_LOGO = glow_layer((W, 320), lambda d, c: text_center(d, W / 2, 20, "VENTIC", ORB_XL, c + (255,)), RED, 30, 3)


def scene_end(t, canvas):
    lt = t - S5
    k = ease_out(lt / 0.7)
    paste_alpha(canvas, END_LOGO, (0, int(200 + (1 - k) * 50)), k)
    d = ImageDraw.Draw(canvas)
    k2 = ease_out((lt - 0.5) / 0.5)
    if k2 > 0:
        pill = lcd_panel((520, 110), 16)
        paste_alpha(canvas, pill, (W // 2 - 260, 540), k2)
        v = glow_layer((520, 80), lambda dd, c: text_center(dd, 260, 0, "v1.0.0  OUT NOW", MONO_M, c + (255,)), RED, 8, 2)
        paste_alpha(canvas, v, (W // 2 - 260, 575), k2)
    k3 = ease_out((lt - 1.0) / 0.5)
    if k3 > 0:
        text_center(d, W / 2, 700, "ANDROID 10+   ·   3.8 MB   ·   NO INTERNET", BAR_M, SUB + (int(255 * k3),))
        text_center(d, W / 2, 770, "github.com/yass-gr/Ventic", MONO_M, TEXT + (int(255 * k3),))
    # cycling accent LEDs (the 4 themes)
    for i, c in enumerate([BLUE, AMBER, RED, GREEN]):
        on = int((lt * 4)) % 4 == i or lt > 3.0
        if lt > 1.2:
            cx, cy = W / 2 - 60 + i * 40, 880
            if on:
                led = glow_layer((60, 60), lambda dd, col: dd.ellipse((22, 22, 38, 38), fill=col + (255,)), c, 7, 2)
                canvas.alpha_composite(led, (int(cx - 30), int(cy - 30)))
            else:
                d.ellipse((cx - 8, cy - 8, cx + 8, cy + 8), fill=(20, 22, 24, 255))


# ----------------------------------------------------------------------------- compositor
def glitch(arr, strength, rng):
    """RGB split + horizontal slice displacement."""
    s = int(18 * strength)
    out = arr.copy()
    if s:
        out[..., 0] = np.roll(arr[..., 0], s, axis=1)
        out[..., 2] = np.roll(arr[..., 2], -s, axis=1)
    for _ in range(int(10 * strength)):
        y = rng.integers(0, H - 40)
        h = rng.integers(6, 40)
        out[y:y + h] = np.roll(out[y:y + h], rng.integers(-120, 120), axis=1)
    return out


def render(audio):
    rng = np.random.default_rng(11)
    # 6 pre-baked grain plates cycled: keeps the texture while staying compressible
    global GRAIN
    GRAIN = [rng.normal(0, 1.6, (H, W, 1)).astype(np.float32) for _ in range(6)]
    n = int(DUR * FPS)
    ff = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
         "-i", "-", "-i", os.path.join(TMP, "soundtrack.wav"), "-c:v", "libx264", "-preset", "slow", "-crf", "21", "-tune", "film",
         "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", OUT],
        stdin=subprocess.PIPE)
    for f in range(n):
        t = f / FPS
        canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        base = BG
        if t < S2:
            mode = scene_intro(t, canvas)
            if mode in ("black", "crt"):
                base = BLACK
        elif t < S3:
            scene_type(t, canvas, audio)
        elif t < S4:
            scene_phone(t, canvas)
        elif t < S5:
            scene_stats(t, canvas)
        else:
            scene_end(t, canvas)
        rgba = np.asarray(canvas, dtype=np.float32)
        a = rgba[..., 3:4] / 255.0
        frame = base * (1 - a) + rgba[..., :3] * a
        if base is BG:
            frame = frame * VIG + frame * (1 - VIG) * 0.35
        # beat bloom after the drop
        if S3 <= t < S5 + 3:
            ph = ((t - S3) % BEAT) / BEAT
            frame *= 1 + 0.06 * math.exp(-ph * 6)
        frame *= SCAN
        frame += GRAIN[f % len(GRAIN)]
        # glitch on cuts
        for c in CUTS:
            dt = abs(t - c)
            if dt < 0.12:
                frame = glitch(frame, 1 - dt / 0.12, rng)
                if dt < 0.04:
                    frame = frame * 0.6 + 255 * 0.4
        # CRT power-off at the end
        if t > DUR - 0.8:
            k = ease_in_out((t - (DUR - 0.8)) / 0.6)
            hh = max(2, int(H * (1 - k)))
            mask = np.zeros((H, 1, 1), dtype=np.float32)
            mask[H // 2 - hh // 2:H // 2 + hh // 2 + 1] = 1
            frame = frame * mask + (1 - mask) * 0
            if k >= 1:
                frame[:] = 0
        ff.stdin.write(np.clip(frame, 0, 255).astype(np.uint8).tobytes())
        if f % 60 == 0:
            print(f"frame {f}/{n}", flush=True)
    ff.stdin.close()
    ff.wait()


def main():
    audio = synth()
    with wave.open(os.path.join(TMP, "soundtrack.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((audio * 32767).astype(np.int16).tobytes())
    render(audio)


if __name__ == "__main__":
    main()
