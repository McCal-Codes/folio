#!/usr/bin/env python3
"""Draws the icon for each of Folio's own packages.

One rounded tile per package, in its section's iOS color, with a white glyph that shows what the package does. The
SVG is the source; ImageMagick rasterises it to the PNG the source publishes. Re-run after editing a glyph:

    python3 docs/sdk/source/assets/icons/generate.py

Requires ImageMagick (`brew install imagemagick`). The PNGs are checked in, so a build never needs it.
"""

import pathlib
import subprocess

SIZE = 192
HERE = pathlib.Path(__file__).parent

# id -> (top color, bottom color, glyph)
# Colors are the iOS system set Folio uses everywhere else, one hue per package, darker at the bottom.
ICONS = {
    "cabinet": ("#3DA2FF", "#0A6FE0", """
        <rect x="66" y="34" width="60" height="60" rx="16" fill="#fff"/>
        <rect x="44" y="106" width="104" height="54" rx="14" fill="#fff" opacity=".92"/>
        <rect x="58" y="120" width="34" height="8" rx="4" fill="#0A6FE0"/>
        <rect x="58" y="136" width="62" height="8" rx="4" fill="#0A6FE0" opacity=".55"/>
    """),
    "harborline": ("#7D7BFF", "#4D4AD6", """
        <circle cx="42" cy="118" r="14" fill="#fff" opacity=".7"/>
        <circle cx="76" cy="108" r="20" fill="#fff" opacity=".85"/>
        <circle cx="120" cy="94" r="28" fill="#fff"/>
        <circle cx="164" cy="112" r="16" fill="#fff" opacity=".8"/>
        <rect x="24" y="150" width="144" height="8" rx="4" fill="#fff" opacity=".55"/>
    """),
    "roll-call": ("#FF5F56", "#D7302A", """
        <rect x="30" y="44" width="36" height="36" rx="10" fill="#fff"/>
        <rect x="78" y="44" width="36" height="36" rx="10" fill="#fff" opacity=".6"/>
        <rect x="126" y="44" width="36" height="36" rx="10" fill="#fff" opacity=".6"/>
        <rect x="30" y="98" width="132" height="26" rx="9" fill="#fff" opacity=".92"/>
        <rect x="30" y="134" width="132" height="26" rx="9" fill="#fff" opacity=".55"/>
    """),
    "palette": ("#FFB busy", "#E08600", ""),  # replaced below
    "colored-albums": ("#FF6A8A", "#E01E4E", """
        <rect x="36" y="36" width="88" height="88" rx="18" fill="#fff" opacity=".92"/>
        <circle cx="80" cy="80" r="16" fill="#E01E4E"/>
        <rect x="134" y="104" width="10" height="52" rx="5" fill="#fff"/>
        <rect x="150" y="84" width="10" height="72" rx="5" fill="#fff" opacity=".8"/>
        <rect x="118" y="120" width="10" height="36" rx="5" fill="#fff" opacity=".65"/>
    """),
    "theme-classic": ("#9BA0A8", "#5C626B", """
        <rect x="38" y="38" width="52" height="52" rx="14" fill="#fff"/>
        <rect x="102" y="38" width="52" height="52" rx="14" fill="#fff" opacity=".75"/>
        <rect x="38" y="102" width="52" height="52" rx="14" fill="#fff" opacity=".75"/>
        <rect x="102" y="102" width="52" height="52" rx="14" fill="#fff" opacity=".55"/>
    """),
    "theme-clear": ("#8FD3FF", "#3AA0E0", """
        <rect x="34" y="34" width="124" height="124" rx="34" fill="#fff" opacity=".28"/>
        <rect x="56" y="56" width="80" height="80" rx="24" fill="#fff" opacity=".85"/>
        <circle cx="132" cy="60" r="16" fill="#fff"/>
    """),
    "theme-dark": ("#3A3A3C", "#1C1C1E", """
        <path d="M120 40a56 56 0 1 0 40 96 64 64 0 0 1-40-96Z" fill="#fff"/>
        <circle cx="140" cy="62" r="5" fill="#fff" opacity=".8"/>
        <circle cx="158" cy="86" r="3.5" fill="#fff" opacity=".6"/>
    """),
    "theme-tinted": ("#C38BFF", "#7B3FD1", """
        <path d="M96 32c26 30 42 50 42 68a42 42 0 0 1-84 0c0-18 16-38 42-68Z" fill="#fff"/>
        <circle cx="96" cy="104" r="16" fill="#7B3FD1" opacity=".55"/>
    """),
}

ICONS["palette"] = ("#FFC15E", "#E08600", """
    <rect x="32" y="52" width="128" height="30" rx="11" fill="#fff"/>
    <rect x="32" y="92" width="128" height="30" rx="11" fill="#fff" opacity=".75"/>
    <rect x="32" y="132" width="128" height="30" rx="11" fill="#fff" opacity=".5"/>
    <circle cx="50" cy="67" r="7" fill="#E08600"/>
    <circle cx="50" cy="107" r="7" fill="#E08600" opacity=".7"/>
""")


def svg(top: str, bottom: str, glyph: str) -> str:
    """One flat color, like the colored squares in Folio Settings: at 44 dp a gradient is noise, and the
    rasteriser ignores SVG gradients anyway. `bottom` is kept for the glyph shapes that sit on the tile."""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 192 192">
  <rect width="192" height="192" rx="44" fill="{top}"/>
  {glyph.strip()}
</svg>
"""


def main() -> None:
    for name, (top, bottom, glyph) in ICONS.items():
        source = HERE / f"{name}.svg"
        source.write_text(svg(top, bottom, glyph))
        subprocess.run(
            ["magick", "-background", "none", str(source), "-resize", f"{SIZE}x{SIZE}", str(HERE / f"{name}.png")],
            check=True,
        )
        print("wrote", name + ".png")


if __name__ == "__main__":
    main()
