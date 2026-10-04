"""Generates zh_CN versions of the words the official pack bakes into textures.

Every replacement is drawn with SimSun's 12px bitmap glyphs, the smallest CJK size that stays
legible, and keeps the official artwork, palette and file size wherever the client or the
shaders depend on it:

- Tooltip tags (hypixel_ravengard:font/tags): rarity, item type and class pills, 7px tall with
  5px letters. Each pill is redrawn at twice the resolution (14px, glyph scale 0.5) so the
  12px glyphs fit with the same padding, and the overlay font maps the official characters to
  the new bitmaps with the official height and ascent, so tooltip lines keep their layout.
- Menu text buttons (ui/menu/button and ui/menu/hover): the menu shaders place these by their
  size in texels, so they keep their size; the face grows from 10 to 12 rows by folding the
  inner border into it.
- The inventory title plaque (minecraft:gui/container/inventory.png): the vanilla title is
  blanked by the official language files, the plaque text is part of the texture.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, 'zh_CN', 'resourcepack')
OFFICIAL = os.path.join(os.path.dirname(ROOT), 'configuration', 'resourcepacks', 'ravengard')
NAMESPACE = os.path.join('assets', 'hypixel_ravengard')
OFFICIAL_TAGS = os.path.join(OFFICIAL, NAMESPACE, 'textures', 'font', 'tags')
TAGS = os.path.join(PACK, NAMESPACE, 'textures', 'font', 'tags_zh')
TAGS_RESOURCE = 'hypixel_ravengard:font/tags_zh/'
OFFICIAL_FONT = os.path.join(OFFICIAL, NAMESPACE, 'font', 'ravengard.json')
FONT_OUT = os.path.join(PACK, NAMESPACE, 'font', 'ravengard.json')
MENU = os.path.join(NAMESPACE, 'textures', 'ui', 'menu')
INVENTORY = os.path.join('assets', 'minecraft', 'textures', 'gui', 'container', 'inventory.png')
FONT = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', 'simsun.ttc')

GLYPH_SIZE = 12
GLYPH_GAP = 1

TAG_TEXT = {
    'common': '普通', 'uncommon': '优秀', 'rare': '稀有', 'epic': '史诗', 'legendary': '传说',
    'knight': '骑士', 'warrior': '战士', 'hunter': '猎人', 'assassin': '刺客', 'sorcerer': '术士',
    'helmet': '头盔', 'chestplate': '胸甲', 'leggings': '护腿', 'boots': '靴子',
    'sword': '剑', 'shield': '盾牌', 'bow': '弓', 'crossbow': '弩', 'dagger': '匕首',
    'greataxe': '巨斧', 'greatsword': '巨剑', 'halberd': '长戟', 'hammer': '战锤', 'mace': '钉锤',
    'knife': '小刀', 'claw': '爪', 'magic_sword': '魔剑', 'staff': '法杖', 'orb': '宝珠',
    'necklace': '项链', 'earring': '耳环', 'belt': '腰带', 'ring': '戒指', 'trinket': '饰品',
    'consumable': '消耗品', 'misc': '杂物', 'sceptre': '杂物', 'spellbook': '杂物',
}
CLASS_TAGS = {'knight', 'warrior', 'hunter', 'assassin', 'sorcerer'}

BUTTON_TEXT = {
    'text_cancel': '取消', 'text_confirm': '确认', 'text_enter': '进入', 'text_expand': '扩展',
    'text_fight': '战斗', 'text_leave': '离开', 'text_sell': '确认',
}
INVENTORY_TEXT = '背包'


class Glyphs:
    def __init__(self):
        self.font = ImageFont.truetype(FONT, GLYPH_SIZE)

    def char(self, char):
        mask = Image.new('1', (GLYPH_SIZE * 2, GLYPH_SIZE * 2), 0)
        draw = ImageDraw.Draw(mask)
        draw.fontmode = '1'
        draw.text((0, 0), char, font=self.font, fill=1)
        left, _, right, _ = mask.getbbox()
        pixels = mask.load()
        return {(x - left, y - 1) for y in range(1, GLYPH_SIZE + 1) for x in range(left, right) if pixels[x, y]}, right - left

    def word(self, text):
        pixels, x = set(), 0
        for char in text:
            glyph, width = self.char(char)
            pixels |= {(gx + x, gy) for gx, gy in glyph}
            x += width + GLYPH_GAP
        return pixels, x - GLYPH_GAP


def tag_colors(official):
    pixels = official.load()
    counts = {}
    for y in range(official.height):
        for x in range(official.width):
            if pixels[x, y][3]:
                counts[pixels[x, y]] = counts.get(pixels[x, y], 0) + 1
    ranked = sorted(counts, key=counts.get, reverse=True)
    background, ink = ranked[0], ranked[1]
    shade = ranked[2] if len(ranked) > 2 else None
    return background, ink, shade


def first_text_column(official, background):
    pixels = official.load()
    icon_end = 1
    for x in range(1, official.width):
        if all(pixels[x, y] == background for y in range(1, official.height - 1)):
            if x > 2:
                return x
            continue
        icon_end = x
    return icon_end + 1


def render_tag(glyphs, name, text):
    official = Image.open(os.path.join(OFFICIAL_TAGS, name + '.png')).convert('RGBA')
    background, ink, shade = tag_colors(official)
    lead = first_text_column(official, background) + 1 if name in CLASS_TAGS else 2
    left = official.crop((0, 0, lead, official.height)).resize((lead * 2, official.height * 2), Image.NEAREST)
    right = official.crop((official.width - 2, 0, official.width, official.height)).resize((4, official.height * 2), Image.NEAREST)
    word, width = glyphs.word(text)

    height = official.height * 2
    tag = Image.new('RGBA', (left.width + width + right.width, height), background)
    tag.paste(left, (0, 0))
    tag.paste(right, (tag.width - right.width, 0))
    pixels = tag.load()
    origin = left.width
    if name not in CLASS_TAGS and shade is not None:
        for x, y in word:
            if (x, y + 1) not in word:
                pixels[origin + x, 1 + y + 1] = shade
    for x, y in word:
        pixels[origin + x, 1 + y] = ink
    return tag


def tag_providers(names):
    official = json.load(open(OFFICIAL_FONT, encoding='utf-8'))
    providers = []
    for provider in official['providers']:
        file = provider.get('file', '')
        prefix = 'hypixel_ravengard:font/tags/'
        if provider.get('type') != 'bitmap' or not file.startswith(prefix):
            continue
        name = file[len(prefix):-len('.png')]
        if name not in names:
            continue
        providers.append({'type': 'bitmap', 'file': TAGS_RESOURCE + name + '.png',
                          'ascent': provider['ascent'], 'height': provider['height'], 'chars': provider['chars']})
    return providers


def build_tags(glyphs):
    os.makedirs(TAGS, exist_ok=True)
    for name, text in TAG_TEXT.items():
        render_tag(glyphs, name, text).save(os.path.join(TAGS, name + '.png'))
    providers = tag_providers(TAG_TEXT)
    os.makedirs(os.path.dirname(FONT_OUT), exist_ok=True)
    with open(FONT_OUT, 'w', encoding='utf-8', newline='\n') as out:
        json.dump({'providers': providers}, out, ensure_ascii=True, indent=1)
    return len(TAG_TEXT), len(providers)


def button_face(image):
    """The face is the run of rows, below the frame, whose pixel at column 5 matches row 6's."""
    pixels = image.load()
    for top in range(3, 8):
        color = pixels[5, top]
        if color[3] and pixels[6, top] == color and pixels[5, top + 1] == color and pixels[5, top + 9] == color:
            bottom = top
            while pixels[5, bottom + 1] == color:
                bottom += 1
            right = 5
            while pixels[right + 1, top] == color:
                right += 1
            return top, bottom, 5, right, color
    raise SystemExit('button face not found')


def render_button(glyphs, path, text):
    image = Image.open(path).convert('RGBA')
    top, bottom, left, right, face = button_face(image)
    pixels = image.load()
    ink = None
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            if pixels[x, y] != face:
                ink = pixels[x, y]
                pixels[x, y] = face
    if ink is None:
        return None
    if bottom - top + 1 < GLYPH_SIZE:
        width = right + 6
        rows = {y: [pixels[x, y] for x in range(width)] for y in range(top - 2, bottom + 3)}
        for y, source in ((top - 2, top - 1), (top - 1, top), (bottom + 1, bottom), (bottom + 2, bottom + 1)):
            for x in range(width):
                pixels[x, y] = rows[source][x]
        top, bottom = top - 1, bottom + 1
    word, width = glyphs.word(text)
    origin_x = left + (right - left + 1 - width) // 2
    origin_y = top + (bottom - top + 1 - GLYPH_SIZE) // 2
    for x, y in word:
        pixels[origin_x + x, origin_y + y] = ink
    return image


def build_buttons(glyphs):
    count = 0
    for state in ('button', 'hover', 'press'):
        for name, text in BUTTON_TEXT.items():
            relative = os.path.join(MENU, state, name + '.png')
            image = render_button(glyphs, os.path.join(OFFICIAL, relative), text)
            if image is None:
                continue
            target = os.path.join(PACK, relative)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            image.save(target)
            count += 1
    return count


PLAQUE = (95, 47, 160, 59)
PLAQUE_TEXT = ((180, 170, 165, 255), (32, 27, 32, 255))


def build_inventory(glyphs):
    image = Image.open(os.path.join(OFFICIAL, INVENTORY)).convert('RGBA')
    pixels = image.load()
    left, top, right, bottom = PLAQUE
    face = pixels[left, top]
    light, shadow = PLAQUE_TEXT
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            if pixels[x, y] in PLAQUE_TEXT:
                pixels[x, y] = face
    word, width = glyphs.word(INVENTORY_TEXT)
    origin_x = left + (right - left + 1 - width) // 2
    for x, y in word:
        if (x, y + 1) not in word:
            pixels[origin_x + x, top + y + 1] = shadow
    for x, y in word:
        pixels[origin_x + x, top + y] = light
    target = os.path.join(PACK, INVENTORY)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)
    return target


def main():
    glyphs = Glyphs()
    tags, providers = build_tags(glyphs)
    buttons = build_buttons(glyphs)
    inventory = build_inventory(glyphs)
    print(f'{tags} tags ({providers} font providers) -> {TAGS}; {buttons} menu buttons; inventory title -> {inventory}')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
