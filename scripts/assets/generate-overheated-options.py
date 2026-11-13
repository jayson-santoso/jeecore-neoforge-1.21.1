"""Draw native-resolution status icon candidates and a nearest-neighbor preview."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUTPUT = Path(__file__).resolve().parents[2] / "artwork" / "overheated-options"
PALETTE = {
    ".": (0, 0, 0, 0),
    "#": "#482b28",  # Dark warm outline.
    "s": "#71544b",  # Stone shadow.
    "m": "#a77b64",  # Stone midtone.
    "l": "#d4ac87",  # Stone highlight.
    "r": "#b73d28",  # Hot red edge.
    "o": "#ef762d",  # Orange heat.
    "y": "#ffc65b",  # Hot center.
    "w": "#fff0b5",  # Brightest highlight.
}

ICONS = [
    ("1", "Cracked Core", "cracked_core", [
        "..................",
        ".....o......o.....",
        "....oy.....yo.....",
        ".....o......o.....",
        "..................",
        ".....########.....",
        "...##lllmoymm##...",
        "..#llmmmoyommmm#..",
        "..#lmmmooymmmms#..",
        "..#mmoyyyommmss#..",
        "..#mmoymmoymmss#..",
        "..#mmoymmmoymss#..",
        "..#msroyomoymss#..",
        "...#ssroyoysss#...",
        "....##sroros##....",
        "......######......",
        "..................",
        "..................",
    ]),
    ("2", "Heat Gauge", "heat_gauge", [
        "..................",
        ".......####.......",
        "......#lwwm#......",
        "......#w..m#......",
        "......#wyom#......",
        "......#wyom#......",
        "......#wyom#......",
        "......#wyom#......",
        "......#wyom#......",
        "......#wyom#......",
        ".....#lwyorm#.....",
        "....#lwyyoorm#....",
        "....#wyyyorrm#....",
        "....#myyoorrs#....",
        ".....#moorrs#.....",
        "......#mrrs#......",
        ".......####.......",
        "..................",
    ]),
    ("3", "Heat Haze", "heat_haze", [
        "..................",
        ".......##.........",
        "...##.#yo#...##...",
        "..#yo##yo#..#yo#..",
        "..#yo#.#yo#.#yo#..",
        "...#yo##yo#..#yo#.",
        "...#yo#.#yo#.#yo#.",
        "..#yo#..#yo##yo#..",
        "..#yo#.#yo#.#yo#..",
        "...##..#yo#..##...",
        "........##........",
        "..................",
        "...############...",
        "..#wyyyyyyyyyoo#..",
        "...#ooooooooor#...",
        "....##########....",
        "..................",
        "..................",
    ]),
]


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGB", (900, 400), "#202326")
    draw = ImageDraw.Draw(preview)
    font_path = "C:/Windows/Fonts/consola.ttf"
    heading = ImageFont.truetype(font_path, 22)
    small = ImageFont.truetype(font_path, 15)
    for index, (number, title, filename, rows) in enumerate(ICONS):
        assert len(rows) == 18 and all(len(row) == 18 for row in rows), filename
        icon = Image.new("RGBA", (18, 18))
        pixels = ImageDraw.Draw(icon)
        for y, row in enumerate(rows):
            for x, color in enumerate(row):
                pixels.point((x, y), fill=PALETTE[color])
        icon.save(OUTPUT / f"overheated_{filename}.png")
        left = index * 300
        draw.text((left + 150, 25), f"{number}. {title}", font=heading, anchor="mt", fill="#f3e5cf")
        # The dark backing is preview-only; every source icon is transparent.
        draw.rectangle((left + 34, 72, left + 265, 303), fill="#343434", outline="#666666", width=2)
        enlarged = icon.resize((216, 216), Image.NEAREST)
        preview.paste(enlarged, (left + 42, 80), enlarged)
        draw.text((left + 150, 322), "18 x 18 | enlarged 12x", font=small, anchor="mt", fill="#b8b8b8")
        preview.paste(icon, (left + 141, 359), icon)
    preview.save(OUTPUT / "overheated-options.png")
    print(f"Saved 3 transparent 18x18 icons and preview to {OUTPUT}")


if __name__ == "__main__":
    main()
