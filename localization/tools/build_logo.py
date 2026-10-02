"""Generates the zh_CN version of the animated RAVENGARD logo at the top of the HUD.

The official logo (hypixel_ravengard:ui/ravengard.png) is a strip of 11 frames, 63px wide:
all yellow, then each letter turns orange in turn and white once passed, then all white.
The four corners of the strip hold the shader's data pixels and each frame keeps one empty
row above and below the glyphs, which the shader crops. Its 7px letters cannot carry CJK,
so the frames here are 15px tall with SimSun's 12px bitmap glyphs, and the overlay ships the
official shader config with the logo's frame size changed to match.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, 'zh_CN', 'resourcepack')
LOGO = os.path.join(PACK, 'assets', 'hypixel_ravengard', 'textures', 'ui', 'ravengard.png')
CONFIG_PATH = os.path.join('assets', 'minecraft', 'shaders', 'include', 'ravengard_config.glsl')
OFFICIAL_CONFIG = os.path.join(os.path.dirname(ROOT), 'configuration', 'resourcepacks', 'ravengard', CONFIG_PATH)
FONT = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', 'simsun.ttc')

TEXT = '天穹余烬'
WIDTH = 63
FRAMES = 11
YELLOW = (255, 255, 85, 255)
ORANGE = (255, 170, 0, 255)
WHITE = (255, 255, 255, 255)
SHADOW = (0, 0, 0, 128)
DATA_PIXEL = (254, 253, 252, 255)


def glyph_pixels(font, char):
    mask = Image.new('1', (16, 16), 0)
    draw = ImageDraw.Draw(mask)
    draw.fontmode = '1'
    draw.text((0, 0), char, font=font, fill=1)
    left, top, right, bottom = mask.getbbox()
    pixels = mask.load()
    return {(x - left, y - top) for y in range(top, bottom) for x in range(left, right) if pixels[x, y]}


def render():
    font = ImageFont.truetype(FONT, 12)
    glyphs = [glyph_pixels(font, char) for char in TEXT]
    glyph_height = max(y for glyph in glyphs for _, y in glyph) + 1
    widths = [max(x for x, _ in glyph) + 1 for glyph in glyphs]
    frame_height = glyph_height + 3
    gap = (WIDTH - 3 - sum(widths)) // (len(TEXT) + 1)
    left = (WIDTH - (sum(widths) + gap * (len(TEXT) - 1) + 1)) // 2

    strip = Image.new('RGBA', (WIDTH, frame_height * FRAMES), (0, 0, 0, 0))
    pixels = strip.load()
    for frame in range(FRAMES):
        if frame == 0:
            colors = [YELLOW] * len(TEXT)
        elif frame == FRAMES - 1:
            colors = [WHITE] * len(TEXT)
        else:
            lit = (frame - 1) * len(TEXT) // (FRAMES - 2)
            colors = [WHITE if i < lit else ORANGE if i == lit else YELLOW for i in range(len(TEXT))]
        top = frame * frame_height + 1
        filled = {}
        x = left
        for glyph, width, color in zip(glyphs, widths, colors):
            for gx, gy in glyph:
                filled[(x + gx, top + gy)] = color
            x += width + gap
        for px, py in filled:
            if (px + 1, py + 1) not in filled:
                pixels[px + 1, py + 1] = SHADOW
        for position, color in filled.items():
            pixels[position] = color
    for corner in ((0, 0), (WIDTH - 1, 0), (0, strip.height - 1), (WIDTH - 1, strip.height - 1)):
        pixels[corner] = DATA_PIXEL
    return strip, frame_height


def patch_config(frame_height, strip_height):
    config = open(OFFICIAL_CONFIG, encoding='utf-8').read()
    replacements = {
        'const vec2 RAVENGARD_FULL_SIZE = vec2(63, 121);': f'const vec2 RAVENGARD_FULL_SIZE = vec2({WIDTH}, {strip_height});',
        'const vec2 RAVENGARD_FRAME_SIZE = vec2(63, 11);': f'const vec2 RAVENGARD_FRAME_SIZE = vec2({WIDTH}, {frame_height});',
    }
    for official, localized in replacements.items():
        if config.count(official) != 1:
            raise SystemExit(f'official shader config changed, expected exactly one "{official}"')
        config = config.replace(official, localized)
    target = os.path.join(PACK, CONFIG_PATH)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, 'w', encoding='utf-8', newline='\n') as out:
        out.write(config)
    return target


def main():
    strip, frame_height = render()
    os.makedirs(os.path.dirname(LOGO), exist_ok=True)
    strip.save(LOGO)
    config = patch_config(frame_height, strip.height)
    print(f'{TEXT}: {FRAMES} frames of {WIDTH}x{frame_height} -> {LOGO}; frame size in {config}')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
