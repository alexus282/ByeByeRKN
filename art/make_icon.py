"""Builds the launcher icon and the splash screen icon.

Launcher icon, from art/icon-source.webp, a full-bleed square picture. Launchers show at most the
central 72dp of an adaptive icon's 108dp canvas, under a mask of their own, so the picture is scaled
to put WINDOW, the part that has to stay visible, in that area. Around the picture the
ic_launcher_background colour continues its dark corners.

Splash icon, from art/splash-source.webp, a ring inside a dark square. The ring is cut out and centred
on a transparent canvas. Android 12+ draws the canvas at 288dp and shows its central 192dp circle.

Run from the project root: python art/make_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path("app/src/main/res")

ICON_SOURCE = Path("art/icon-source.webp")
# Head, both hands and the handcuffs, in source pixels.
WINDOW = (110, 70, 1150, 1110)
CANVAS_DP = 108
VISIBLE_DP = 72
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

SPLASH_SOURCE = Path("art/splash-source.webp")
# The ring's bounding box in the source, measured from its cyan pixels.
RING_BOX = (20, 21, 1224, 1223)
SPLASH_CANVAS_PX = 1152  # 288dp at xxxhdpi
SPLASH_RING_PX = 640  # 160dp at xxxhdpi
ANTIALIAS = 4


def launcher_layer(art: Image.Image, scale: float) -> Image.Image:
    canvas_px = round(CANVAS_DP * scale)
    visible_px = round(VISIBLE_DP * scale)
    factor = visible_px / (WINDOW[2] - WINDOW[0])
    margin = (canvas_px - visible_px) / 2
    resized = art.resize((round(art.width * factor), round(art.height * factor)), Image.LANCZOS)
    layer = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    layer.paste(resized, (round(margin - WINDOW[0] * factor), round(margin - WINDOW[1] * factor)))
    return layer


def circle_cutout(art: Image.Image) -> Image.Image:
    size = art.width * ANTIALIAS
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    cutout = art.convert("RGBA")
    cutout.putalpha(mask.resize(art.size, Image.LANCZOS))
    return cutout


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, quality=92, method=6)
    print(path, image.size, path.stat().st_size)


def main() -> None:
    icon = Image.open(ICON_SOURCE).convert("RGB")
    for name, scale in DENSITIES.items():
        save(launcher_layer(icon, scale), RES / f"mipmap-{name}" / "ic_launcher_foreground.webp")

    ring = circle_cutout(Image.open(SPLASH_SOURCE).convert("RGB").crop(RING_BOX))
    canvas = Image.new("RGBA", (SPLASH_CANVAS_PX, SPLASH_CANVAS_PX), (0, 0, 0, 0))
    offset = (SPLASH_CANVAS_PX - SPLASH_RING_PX) // 2
    canvas.paste(ring.resize((SPLASH_RING_PX, SPLASH_RING_PX), Image.LANCZOS), (offset, offset))
    save(canvas, RES / "drawable-nodpi" / "splash_icon.webp")


if __name__ == "__main__":
    main()
