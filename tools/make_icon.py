#!/usr/bin/env python3
"""Generate EText launcher icons (legacy PNG + adaptive foreground/background).

JetBrains-style: a vivid diagonal gradient squircle with a bold geometric "E"
and a bright text-caret accent.
"""
import math
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
SS = 4  # supersample factor for antialiasing


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def diagonal_gradient(size, c0, c1):
    """Full-bleed diagonal (top-left -> bottom-right) gradient image."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    maxd = (size - 1) * 2
    for y in range(size):
        for x in range(size):
            t = (x + y) / maxd
            px[x, y] = lerp(c0, c1, t)
    return img


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def draw_glyph(draw, size):
    """Draw a bold 'E' and a caret, scaled to `size`."""
    s = size
    white = (245, 246, 250)
    caret = (255, 214, 10)  # bright yellow accent

    # Geometry of the E (occupies roughly the left-center).
    left = s * 0.30
    top = s * 0.26
    bottom = s * 0.74
    stem_w = s * 0.085
    arm_len = s * 0.30
    arm_h = s * 0.082
    r = stem_w * 0.45

    # Vertical stem.
    draw.rounded_rectangle([left, top, left + stem_w, bottom], radius=r, fill=white)
    # Top arm.
    draw.rounded_rectangle([left, top, left + arm_len, top + arm_h], radius=r, fill=white)
    # Middle arm (slightly shorter).
    mid_y = (top + bottom) / 2 - arm_h / 2
    draw.rounded_rectangle([left, mid_y, left + arm_len * 0.82, mid_y + arm_h], radius=r, fill=white)
    # Bottom arm.
    draw.rounded_rectangle([left, bottom - arm_h, left + arm_len, bottom], radius=r, fill=white)

    # Text caret to the right of the E.
    cx = s * 0.685
    draw.rounded_rectangle([cx, top, cx + s * 0.05, bottom], radius=s * 0.025, fill=caret)


def make_full(size, shape="rounded"):
    """Render a full-bleed icon (gradient + glyph), masked to shape."""
    big = size * SS
    grad = diagonal_gradient(big, (124, 77, 255), (255, 64, 157))  # purple -> pink
    draw = ImageDraw.Draw(grad, "RGBA")

    # Subtle darkening glow in the lower-right for depth.
    glow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([big * 0.45, big * 0.45, big * 1.25, big * 1.25], fill=(20, 6, 40, 70))
    grad = Image.alpha_composite(grad.convert("RGBA"), glow)

    draw = ImageDraw.Draw(grad)
    draw_glyph(draw, big)

    if shape == "circle":
        mask = circle_mask(big)
    elif shape == "square":
        mask = Image.new("L", (big, big), 255)
    else:
        mask = rounded_mask(big, int(big * 0.22))

    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def make_foreground(size):
    """Adaptive-icon foreground: glyph only, on transparent, with safe padding."""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Adaptive foreground content lives in the centered 2/3; shrink + center.
    inner = int(big * 0.66)
    tmp = Image.new("RGBA", (inner, inner), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(tmp)
    draw_glyph(d2, inner)
    off = (big - inner) // 2
    img.paste(tmp, (off, off), tmp)
    return img.resize((size, size), Image.LANCZOS)


def make_background(size):
    big = size * SS
    grad = diagonal_gradient(big, (124, 77, 255), (255, 64, 157)).convert("RGBA")
    glow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([big * 0.45, big * 0.45, big * 1.3, big * 1.3], fill=(20, 6, 40, 70))
    grad = Image.alpha_composite(grad, glow)
    return grad.resize((size, size), Image.LANCZOS)


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def main():
    for dens, sz in DENSITIES.items():
        d = os.path.join(OUT, f"mipmap-{dens}")
        os.makedirs(d, exist_ok=True)
        make_full(sz, "rounded").save(os.path.join(d, "ic_launcher.png"))
        make_full(sz, "circle").save(os.path.join(d, "ic_launcher_round.png"))
        # Adaptive layers (foreground/background) at the same density buckets.
        make_foreground(sz).save(os.path.join(d, "ic_launcher_foreground.png"))
        make_background(sz).save(os.path.join(d, "ic_launcher_background.png"))
        print(f"{dens}: {sz}px")

    # Play-store / hi-res preview.
    os.makedirs(os.path.join(OUT, "..", "..", "..", "..", "fastlane"), exist_ok=True)
    make_full(512, "rounded").save(os.path.join(os.path.dirname(__file__), "..", "icon-512.png"))
    print("done")


if __name__ == "__main__":
    main()
