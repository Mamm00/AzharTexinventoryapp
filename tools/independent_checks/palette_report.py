#!/usr/bin/env python3
"""Bootstrap/verification twin of PaletteResolver.kt (v1.1, delta §10.1/D20/D21).

Reads seed-alazhartex.json (committed seed artifact) and emits palette-report.txt with
one line per colour: colour_id, hex, on_colour_hex, wcag_contrast_ratio (4dp, half-up).
stdlib only, zero shared code with the Kotlin app. CI re-runs the Kotlin generator and
fails on any byte difference vs the committed palette-report.txt.
"""
import json
import math
import sys
import os

FAMILY_ANCHORS = {
    "cotton": 0, "linen": 20, "wool": 45, "silk": 90, "polyester": 150,
    "viscose": 190, "denim": 220, "velvet": 270, "lycra": 300, "oxford": 330, "poplin": 15,
}
GRADE_L = {"A": 72.0, "B": 62.0, "C": 52.0, "D": 42.0}
FNV_OFFSET, FNV_PRIME = 2166136261, 16777619


def fnv1a(s: str) -> int:
    h = FNV_OFFSET
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * FNV_PRIME) & 0xFFFFFFFF
    return h


def lch_to_rgb(l_star: float, chroma: float, hue_deg: float):
    h = math.radians(hue_deg)
    a, b = chroma * math.cos(h), chroma * math.sin(h)
    fy = (l_star + 16.0) / 116.0
    fx, fz = fy + a / 500.0, fy - b / 200.0
    eps, kappa = 216.0 / 24389.0, 24389.0 / 27.0

    def inv_f(t):
        t3 = t * t * t
        return t3 if t3 > eps else (116.0 * t - 16.0) / kappa

    x = 0.95047 * inv_f(fx)
    z = 1.08883 * inv_f(fz)
    y = ((l_star + 16.0) / 116.0) ** 3 if l_star > kappa * eps else l_star / kappa

    def gamma(c):
        c = max(0.0, min(1.0, c))
        return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1.0 / 2.4)) - 0.055

    r = gamma(3.2404542 * x - 1.5371385 * y - 0.4985314 * z)
    g = gamma(-0.9692660 * x + 1.8760108 * y + 0.0415560 * z)
    b_ = gamma(0.0556434 * x - 0.2040259 * y + 1.0572252 * z)
    byt = lambda v: max(0, min(255, math.floor(v * 255.0 + 0.5)))
    return byt(r), byt(g), byt(b_)


def channel_lum(c8: int) -> float:
    c = c8 / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def rel_lum(r, g, b) -> float:
    return 0.2126 * channel_lum(r) + 0.7152 * channel_lum(g) + 0.0722 * channel_lum(b)


def contrast(l1, l2) -> float:
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


def resolve(family: str, grade: str, code: str):
    anchor = FAMILY_ANCHORS[family]
    l_star = GRADE_L[grade]
    h = fnv1a(code)
    jitter = (h % 17) - 8
    chroma = 0.42 + ((h >> 8) % 11) / 100.0
    hue = (anchor + jitter + 360) % 360
    r, g, b = lch_to_rgb(l_star, chroma * 100.0, float(hue))
    lum = rel_lum(r, g, b)
    on_black = contrast(lum, 0.0)
    on_white = contrast(lum, 1.0)
    if on_black >= on_white:
        return r, g, b, (0, 0, 0), on_black
    return r, g, b, (255, 255, 255), on_white


def main() -> int:
    src = sys.argv[1] if len(sys.argv) > 1 else "seed-alazhartex.json"
    out = sys.argv[2] if len(sys.argv) > 2 else "palette-report.txt"
    with open(src, "r", encoding="utf-8") as f:
        data = json.load(f)
    colours = data["colours"]
    lines = ["alazhartex-palette-report v1", "generator=alazhartex-generator/1.0.0", f"colours={len(colours)}"]
    for c in colours:
        r, g, b, on, ratio = resolve(c["family"], c["grade"], c["code"])
        scaled = math.floor(ratio * 10000.0 + 0.5) / 10000.0
        lines.append(f"{c['id']},#{r:02X}{g:02X}{b:02X},#{on[0]:02X}{on[1]:02X}{on[2]:02X},{scaled:.4f}")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {out} ({len(colours)} colours)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
