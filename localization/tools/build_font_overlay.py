"""Generates the zh_CN font overlay pack next to the catalog.

The official pack centres and right-aligns HUD text with two invisible "space" fonts
(hypixel_ravengard:-half / -full) whose advances are minus half / minus the full width of
each character. Those fonts only list ASCII, so every character the translations use gets
an advance here; CJK glyphs in the default font are 8px wide plus 1px spacing.
"""
import glob
import json
import os
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'zh_CN')
PACK = os.path.join(CATALOG, 'resourcepack')
FONT_DIR = os.path.join(PACK, 'assets', 'hypixel_ravengard', 'font')
OFFICIAL_MCMETA = os.path.join(os.path.dirname(ROOT), 'configuration', 'resourcepacks', 'ravengard', 'pack.mcmeta')

CJK_ADVANCE = 9.0


def used_characters():
    chars = set()
    for file in glob.glob(os.path.join(CATALOG, '*.yml')):
        data = yaml.safe_load(open(file, encoding='utf-8'))
        for value in data.get('entries', {}).values():
            for char in str(value):
                if ord(char) > 0x7F and not char.isspace():
                    chars.add(char)
    return sorted(chars)


def main():
    chars = used_characters()
    os.makedirs(FONT_DIR, exist_ok=True)
    for name, factor in (('-half', 0.5), ('-full', 1.0)):
        advances = {char: -CJK_ADVANCE * factor for char in chars}
        with open(os.path.join(FONT_DIR, name + '.json'), 'w', encoding='utf-8') as out:
            json.dump({'providers': [{'type': 'space', 'advances': advances}]}, out, ensure_ascii=False, indent=1)
    official = json.load(open(OFFICIAL_MCMETA, encoding='utf-8'))['pack']
    mcmeta = {'pack': {
        'pack_format': official['pack_format'],
        'min_format': official.get('min_format', official['pack_format']),
        'max_format': official.get('max_format', official['pack_format']),
        'description': '§6§lHypixel §d§lRavengard §f简体中文字体补充',
    }}
    with open(os.path.join(PACK, 'pack.mcmeta'), 'w', encoding='utf-8') as out:
        json.dump(mcmeta, out, ensure_ascii=False, indent=1)
    print(f'{len(chars)} characters -> {FONT_DIR}')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
