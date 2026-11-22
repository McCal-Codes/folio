#!/usr/bin/env python3
"""Draws the icon for each of Folio's own packages.

One rounded tile per package, in its section's iOS color, shaded top to bottom, with a white glyph that shows what the
package does. The
SVG is the source; ImageMagick rasterises it to the PNG the source publishes. Re-run after editing a glyph:

    python3 docs/sdk/source/assets/icons/generate.py            # the icons
    python3 docs/sdk/source/assets/icons/generate.py --heroes   # the pictures at the top of each tweak's page

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
        <circle cx="48" cy="118" r="12" fill="#fff" opacity=".7"/>
        <circle cx="78" cy="108" r="17" fill="#fff" opacity=".85"/>
        <circle cx="116" cy="95" r="25" fill="#fff"/>
        <circle cx="152" cy="112" r="13" fill="#fff" opacity=".8"/>
        <rect x="34" y="148" width="124" height="8" rx="4" fill="#fff" opacity=".55"/>
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
    "flipbook": ("#30B0C7", "#1A8599", """
        <rect x="30" y="44" width="62" height="104" rx="12" fill="#fff" opacity=".6"/>
        <path d="M100 44 L154 32 Q162 30 162 38 L162 154 Q162 162 154 160 L100 148 Z" fill="#fff"/>
        <rect x="94" y="40" width="4" height="112" rx="2" fill="#fff" opacity=".85"/>
        <rect x="112" y="64" width="34" height="8" rx="4" fill="#1A8599" opacity=".55"/>
        <rect x="112" y="80" width="24" height="8" rx="4" fill="#1A8599" opacity=".4"/>
    """),
    "duet": ("#5E5CE6", "#3F3DB8", """
        <rect x="30" y="44" width="62" height="104" rx="12" fill="#fff"/>
        <path d="M104 52 L152 40 Q162 38 162 48 L162 144 Q162 154 152 152 L104 140 Z" fill="#fff" opacity=".6"/>
        <rect x="94" y="40" width="10" height="112" rx="5" fill="#fff" opacity=".9"/>
    """),
    "standby-2": ("#FF5F56", "#D7302A", """
        <rect x="26" y="52" width="140" height="88" rx="18" fill="#fff"/>
        <rect x="44" y="70" width="28" height="52" rx="10" fill="#D7302A"/>
        <circle cx="96" cy="88" r="6" fill="#D7302A"/><circle cx="96" cy="106" r="6" fill="#D7302A"/>
        <rect x="120" y="70" width="28" height="52" rx="10" fill="#D7302A" opacity=".55"/>
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


GLYPH_SCALE = 1.0


def svg(top: str, bottom: str, glyph: str, uid: str = "i") -> str:
    """The tile in its section's color, shaded from `top` to `bottom`, with a soft highlight along the top edge, a hairline
    around it, and the glyph lifted off it by a shadow. The glyph is scaled about the tile's center. Needs a renderer that
    draws gradients and filters: ImageMagick does when it has librsvg (`brew install imagemagick librsvg`)."""
    shift = 96 * (1 - GLYPH_SCALE)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 192 192">
  <defs>
    <linearGradient id="t-{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient>
    <linearGradient id="h-{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".38"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    <filter id="s-{uid}" x="-20%" y="-20%" width="140%" height="150%"><feDropShadow dx="0" dy="5" stdDeviation="5" flood-color="#000" flood-opacity=".28"/></filter>
    <clipPath id="c-{uid}"><rect width="192" height="192" rx="44"/></clipPath>
  </defs>
  <rect width="192" height="192" rx="44" fill="url(#t-{uid})"/>
  <g clip-path="url(#c-{uid})"><ellipse cx="96" cy="-30" rx="140" ry="92" fill="url(#h-{uid})"/></g>
  <g filter="url(#s-{uid})"><g transform="translate({shift:.1f} {shift:.1f}) scale({GLYPH_SCALE})">
    {glyph.strip()}
  </g></g>
  <rect x=".75" y=".75" width="190.5" height="190.5" rx="43.5" fill="none" stroke="#fff" stroke-opacity=".28" stroke-width="1.5"/>
</svg>
"""


HERO_W, HERO_H = 1024, 500
HERO_DIR = HERE.parent / "heroes"
# The tweaks get a picture on their page; themes and apps bring their own. Two looks: a screen showing the feature
# (SCENES), and a bold two-tone picture with the icon's glyph running off the edge (everything else).
HERO_FOR = ["cabinet", "colored-albums", "duet", "flipbook", "harborline", "palette", "roll-call", "standby-2"]


def shade(color: str, keep: float) -> str:
    """The color darkened toward black; keep=1 is unchanged."""
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    return "#%02X%02X%02X" % (round(r * keep), round(g * keep), round(b * keep))


def phone(screen: str) -> str:
    """A phone body that runs off the bottom of the picture, with the screen's own shapes inside it."""
    return f"""<rect x="312" y="48" width="400" height="520" rx="44" fill="#1c1c1e"/>
  <rect x="326" y="62" width="372" height="520" rx="34" fill="#000"/>
  {screen.strip()}"""


# Each scene is drawn on the phone's screen: x 350 to 674, from y 96 down. Flat shapes, no words, nothing that moves.
SCENES = {
    "roll-call": """
        <rect x="350" y="96" width="52" height="52" rx="14" fill="#5E5CE6"/>
        <rect x="414" y="96" width="52" height="52" rx="14" fill="#30B0C7"/>
        <rect x="478" y="96" width="52" height="52" rx="14" fill="#FF9F0A"/>
        <rect x="542" y="96" width="52" height="52" rx="14" fill="#34C759"/>
        <circle cx="394" cy="98" r="13" fill="#FF3B30"/><circle cx="394" cy="98" r="5" fill="#fff"/>
        <rect x="350" y="172" width="324" height="64" rx="16" fill="#fff" opacity=".14"/>
        <rect x="350" y="248" width="324" height="64" rx="16" fill="#fff" opacity=".14"/>
        <rect x="350" y="324" width="324" height="64" rx="16" fill="#fff" opacity=".14"/>
        <rect x="366" y="190" width="28" height="28" rx="8" fill="#5E5CE6"/>
        <rect x="366" y="266" width="28" height="28" rx="8" fill="#5E5CE6"/>
        <rect x="366" y="342" width="28" height="28" rx="8" fill="#30B0C7"/>
        <rect x="410" y="194" width="150" height="8" rx="4" fill="#fff" opacity=".8"/>
        <rect x="410" y="270" width="170" height="8" rx="4" fill="#fff" opacity=".8"/>
        <rect x="410" y="346" width="130" height="8" rx="4" fill="#fff" opacity=".8"/>
    """,
    "cabinet": """
        <rect x="350" y="96" width="52" height="52" rx="14" fill="#fff" opacity=".22"/>
        <rect x="414" y="96" width="52" height="52" rx="14" fill="#3DA2FF"/>
        <rect x="478" y="96" width="52" height="52" rx="14" fill="#fff" opacity=".22"/>
        <rect x="542" y="96" width="52" height="52" rx="14" fill="#fff" opacity=".22"/>
        <rect x="350" y="392" width="52" height="52" rx="14" fill="#fff" opacity=".22"/>
        <rect x="414" y="392" width="52" height="52" rx="14" fill="#fff" opacity=".22"/>
        <rect x="360" y="164" width="304" height="196" rx="22" fill="#2C2C2E"/>
        <rect x="378" y="182" width="44" height="44" rx="10" fill="#FF375F"/>
        <rect x="436" y="190" width="120" height="8" rx="4" fill="#fff" opacity=".9"/>
        <rect x="436" y="208" width="80" height="8" rx="4" fill="#fff" opacity=".5"/>
        <circle cx="610" cy="204" r="14" fill="#fff"/>
        <rect x="378" y="240" width="268" height="34" rx="10" fill="#fff" opacity=".12"/>
        <rect x="378" y="284" width="268" height="34" rx="10" fill="#fff" opacity=".12"/>
        <rect x="378" y="328" width="56" height="20" rx="10" fill="#0A84FF"/>
        <rect x="442" y="328" width="56" height="20" rx="10" fill="#0A84FF" opacity=".7"/>
        <rect x="506" y="328" width="56" height="20" rx="10" fill="#0A84FF" opacity=".5"/>
    """,
    "palette": """
        <rect x="350" y="96" width="324" height="76" rx="18" fill="#5E5CE6" opacity=".55"/>
        <rect x="350" y="184" width="324" height="76" rx="18" fill="#34C759" opacity=".5"/>
        <rect x="350" y="272" width="324" height="76" rx="18" fill="#FF375F" opacity=".5"/>
        <rect x="350" y="360" width="324" height="76" rx="18" fill="#30B0C7" opacity=".5"/>
        <rect x="368" y="114" width="40" height="40" rx="11" fill="#5E5CE6"/>
        <rect x="368" y="202" width="40" height="40" rx="11" fill="#34C759"/>
        <rect x="368" y="290" width="40" height="40" rx="11" fill="#FF375F"/>
        <rect x="368" y="378" width="40" height="40" rx="11" fill="#30B0C7"/>
        <rect x="424" y="118" width="170" height="9" rx="4" fill="#fff" opacity=".9"/><rect x="424" y="138" width="120" height="8" rx="4" fill="#fff" opacity=".55"/>
        <rect x="424" y="206" width="150" height="9" rx="4" fill="#fff" opacity=".9"/><rect x="424" y="226" width="110" height="8" rx="4" fill="#fff" opacity=".55"/>
        <rect x="424" y="294" width="180" height="9" rx="4" fill="#fff" opacity=".9"/><rect x="424" y="314" width="130" height="8" rx="4" fill="#fff" opacity=".55"/>
        <rect x="424" y="382" width="140" height="9" rx="4" fill="#fff" opacity=".9"/><rect x="424" y="402" width="100" height="8" rx="4" fill="#fff" opacity=".55"/>
    """,
    "standby-2": """
        <rect x="350" y="112" width="64" height="136" rx="20" fill="#FF5F56"/>
        <rect x="424" y="112" width="64" height="136" rx="20" fill="#FF5F56"/>
        <circle cx="512" cy="152" r="9" fill="#FF5F56"/><circle cx="512" cy="208" r="9" fill="#FF5F56"/>
        <rect x="536" y="112" width="64" height="136" rx="20" fill="#FF5F56" opacity=".8"/>
        <rect x="610" y="112" width="64" height="136" rx="20" fill="#FF5F56" opacity=".55"/>
        <rect x="350" y="288" width="324" height="64" rx="18" fill="#fff" opacity=".14"/>
        <rect x="368" y="306" width="28" height="28" rx="8" fill="#FF5F56"/>
        <rect x="410" y="310" width="150" height="8" rx="4" fill="#fff" opacity=".8"/>
        <rect x="410" y="326" width="100" height="8" rx="4" fill="#fff" opacity=".45"/>
        <circle cx="372" cy="396" r="8" fill="#FF5F56"/><circle cx="398" cy="396" r="8" fill="#5E5CE6"/><circle cx="424" cy="396" r="8" fill="#34C759"/>
    """,
    "colored-albums": """
        <rect x="350" y="96" width="324" height="250" rx="26" fill="#E01E4E" opacity=".75"/>
        <rect x="372" y="118" width="108" height="108" rx="18" fill="#FF6A8A"/>
        <circle cx="426" cy="172" r="26" fill="#E01E4E"/>
        <rect x="500" y="130" width="140" height="10" rx="5" fill="#fff" opacity=".95"/>
        <rect x="500" y="152" width="100" height="8" rx="4" fill="#fff" opacity=".6"/>
        <rect x="500" y="190" width="10" height="36" rx="5" fill="#FF9DB4"/>
        <rect x="518" y="170" width="10" height="56" rx="5" fill="#FF9DB4" opacity=".85"/>
        <rect x="536" y="198" width="10" height="28" rx="5" fill="#FF9DB4" opacity=".7"/>
        <rect x="554" y="180" width="10" height="46" rx="5" fill="#FF9DB4" opacity=".85"/>
        <rect x="372" y="254" width="280" height="8" rx="4" fill="#fff" opacity=".3"/>
        <rect x="372" y="254" width="150" height="8" rx="4" fill="#fff" opacity=".9"/>
        <circle cx="428" cy="304" r="14" fill="#fff" opacity=".9"/><circle cx="498" cy="304" r="20" fill="#fff"/><circle cx="568" cy="304" r="14" fill="#fff" opacity=".9"/>
    """,
}


def hero_svg(name: str, bottom: str, glyph: str) -> str:
    """A still, flat picture about twice as wide as tall, in the tweak's own color. No words in it (the page says those,
    in the reader's language) and nothing that moves."""
    head = f'<svg xmlns="http://www.w3.org/2000/svg" width="{HERO_W}" height="{HERO_H}" viewBox="0 0 {HERO_W} {HERO_H}">'
    if name in SCENES:
        return f"""{head}
  <rect width="{HERO_W}" height="{HERO_H}" fill="{bottom}"/>
  {phone(SCENES[name])}
</svg>
"""
    # Two tones, the glyph large and running off the right edge, and a few quiet bars where a title would sit.
    return f"""{head}
  <rect width="{HERO_W}" height="{HERO_H}" fill="{shade(bottom, .72)}"/>
  <rect x="440" width="{HERO_W - 440}" height="{HERO_H}" fill="{bottom}"/>
  <g transform="translate(520 -20) scale(3.4)">
    {glyph.strip()}
  </g>
  <g transform="translate(70 150)" fill="#fff">
    <rect width="300" height="22" rx="11" opacity=".9"/>
    <rect y="40" width="220" height="14" rx="7" opacity=".45"/>
    <rect y="66" width="170" height="14" rx="7" opacity=".3"/>
  </g>
</svg>
"""


def heroes() -> None:
    """Writes `assets/heroes/<name>.svg`; rasterise each to a 1024x500 `.webp` next to it with ImageMagick."""
    HERO_DIR.mkdir(exist_ok=True)
    for name in HERO_FOR:
        _top, bottom, glyph = ICONS[name]
        (HERO_DIR / f"{name}.svg").write_text(hero_svg(name, bottom, glyph))
        subprocess.run(
            ["magick", "-background", "none", str(HERO_DIR / f"{name}.svg"), "-quality", "88", str(HERO_DIR / f"{name}.webp")],
            check=True,
        )
        print("wrote", name + ".webp")


def main() -> None:
    for name, (top, bottom, glyph) in ICONS.items():
        source = HERE / f"{name}.svg"
        source.write_text(svg(top, bottom, glyph, name))
        subprocess.run(
            ["magick", "-background", "none", str(source), "-resize", f"{SIZE}x{SIZE}", str(HERE / f"{name}.png")],
            check=True,
        )
        print("wrote", name + ".png")


if __name__ == "__main__":
    import sys

    heroes() if "--heroes" in sys.argv else main()
