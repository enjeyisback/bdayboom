"""Builds the launcher icon and site images from the logo.

    python scripts/make_icons.py art/logo.webp

art/logo.webp is the full-colour logo on a transparent background. Writes:
  app/src/main/res/mipmap-*/ic_launcher_foreground.png   adaptive-icon foreground, logo inside the safe zone
  docs/img/icon-512.png, docs/img/favicon.png              the composed icon (store / site)
  docs/img/logo.webp                                       the logo, trimmed, 600 px wide
Needs Pillow.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LOGO_DP = 64  # of 108: the wordmark is wide, so it must sit inside the 66 dp safe circle
# Keep in sync with res/drawable/ic_launcher_background.xml
TOP, MID, BOTTOM = (0x6B, 0x2B, 0xF0), (0x2A, 0x0F, 0xB8), (0x12, 0x03, 0x7A)


def fit(logo, size):
    out = Image.new("RGBA", (size, size))
    l = logo.copy()
    l.thumbnail((size, size), Image.LANCZOS)
    out.paste(l, ((size - l.width) // 2, (size - l.height) // 2), l)
    return out


def background(size):
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        a, b, k = (TOP, MID, t * 2) if t < 0.5 else (MID, BOTTOM, t * 2 - 1)
        d.line([(0, y), (size, y)], fill=tuple(round(a[i] + (b[i] - a[i]) * k) for i in range(3)))
    return img.convert("RGBA")


def main(src):
    logo = Image.open(src).convert("RGBA")
    logo = logo.crop(logo.getbbox())

    for name, scale in DENSITIES.items():
        canvas, inner = round(108 * scale), round(LOGO_DP * scale)
        fg = Image.new("RGBA", (canvas, canvas))
        part = fit(logo, inner)
        fg.paste(part, ((canvas - inner) // 2,) * 2, part)
        d = ROOT / f"app/src/main/res/mipmap-{name}"
        d.mkdir(exist_ok=True)
        fg.save(d / "ic_launcher_foreground.png", optimize=True)

    # Composed square icon: background + logo at the same proportion, rounded like a launcher would.
    big = 1024
    icon = background(big)
    part = fit(logo, round(big * 0.78))
    icon.alpha_composite(part, ((big - part.width) // 2, (big - part.height) // 2))
    mask = Image.new("L", (big, big))
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, big - 1, big - 1], radius=big // 5, fill=255)
    icon.putalpha(mask)
    img = ROOT / "docs/img"
    icon.resize((512, 512), Image.LANCZOS).save(img / "icon-512.png", optimize=True)
    icon.resize((64, 64), Image.LANCZOS).save(img / "favicon.png", optimize=True)
    l = logo.copy()
    l.thumbnail((600, 600), Image.LANCZOS)
    l.save(img / "logo.webp", quality=90)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ROOT / "art/logo.webp")
