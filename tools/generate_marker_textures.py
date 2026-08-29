"""Generate the six 16x16 Dedicated Dungeons editor-marker textures.

The output is intentionally deterministic: no random state, scaling, filtering, or
external source images are involved. The image-generation concept sheet supplied
during development was used only to choose the graphite/purple palette and the six
distinct symbols.
"""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/dedicated_dungeons/textures/block"

BACKGROUND = (20, 17, 29, 255)
FRAME = (64, 48, 83, 255)
SHADOW = (8, 7, 12, 255)

MARKERS = {
    "player_spawn_marker": ((55, 225, 238, 255), "player"),
    "room_connector_marker": ((80, 137, 246, 255), "connector"),
    "spawner_marker": ((239, 72, 88, 255), "spawner"),
    "loot_marker": ((250, 194, 62, 255), "loot"),
    "boss_spawn_marker": ((226, 70, 211, 255), "boss"),
    "exit_portal_marker": ((151, 91, 236, 255), "portal"),
}


def base(accent: tuple[int, int, int, int]) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (16, 16), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), fill=SHADOW)
    draw.rectangle((1, 1, 14, 14), fill=BACKGROUND, outline=FRAME)
    draw.point((2, 2), fill=accent)
    draw.point((13, 2), fill=accent)
    draw.point((2, 13), fill=accent)
    draw.point((13, 13), fill=accent)
    return image, draw


def draw_symbol(draw: ImageDraw.ImageDraw, accent, symbol: str) -> None:
    bright = tuple(min(255, value + 42) for value in accent[:3]) + (255,)
    dark = tuple(max(0, value - 82) for value in accent[:3]) + (255,)
    if symbol == "player":
        draw.rectangle((6, 3, 9, 6), fill=bright)
        draw.rectangle((5, 7, 10, 10), fill=accent)
        draw.rectangle((4, 8, 5, 11), fill=accent)
        draw.rectangle((10, 8, 11, 11), fill=accent)
        draw.rectangle((5, 11, 7, 13), fill=dark)
        draw.rectangle((8, 11, 10, 13), fill=dark)
    elif symbol == "connector":
        draw.polygon(((3, 8), (7, 4), (7, 7), (12, 7), (12, 9), (7, 9), (7, 12)), fill=accent)
        draw.line((4, 8, 10, 8), fill=bright)
    elif symbol == "spawner":
        draw.rectangle((4, 4, 11, 11), outline=accent)
        draw.line((4, 4, 11, 11), fill=accent)
        draw.line((11, 4, 4, 11), fill=accent)
        draw.rectangle((7, 7, 8, 8), fill=bright)
    elif symbol == "loot":
        draw.rectangle((3, 6, 12, 12), fill=dark, outline=accent)
        draw.rectangle((4, 4, 11, 6), fill=accent)
        draw.line((3, 7, 12, 7), fill=bright)
        draw.rectangle((7, 7, 8, 9), fill=bright)
    elif symbol == "boss":
        draw.polygon(((3, 5), (5, 7), (6, 4), (8, 7), (10, 4), (12, 6), (11, 11), (4, 11)), fill=accent)
        draw.rectangle((5, 10, 10, 12), fill=dark)
        draw.point((6, 8), fill=bright)
        draw.point((9, 8), fill=bright)
    elif symbol == "portal":
        draw.rectangle((4, 3, 11, 12), fill=dark, outline=accent)
        draw.rectangle((6, 5, 9, 10), fill=(51, 217, 226, 255))
        draw.point((7, 4), fill=bright)
        draw.point((10, 7), fill=bright)
        draw.point((5, 10), fill=bright)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, (accent, symbol) in MARKERS.items():
        image, draw = base(accent)
        draw_symbol(draw, accent, symbol)
        image.save(OUTPUT / f"{name}.png", format="PNG", optimize=False)
    print(f"Generated {len(MARKERS)} marker textures in {OUTPUT}")


if __name__ == "__main__":
    main()
