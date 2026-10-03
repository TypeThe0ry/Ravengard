"""Generates the zh_CN overlay pack next to the catalog: font advances and client strings.

The official pack centres and right-aligns HUD text with two invisible "space" fonts
(hypixel_ravengard:-half / -full) whose advances are minus half / minus the full width of
each character. Those fonts only list ASCII, so every character the translations use gets
an advance here; CJK glyphs in the default font are 8px wide plus 1px spacing.

The official pack also renames a few vanilla client strings (the pause-menu button, sound
sliders) in every language file. client_lang.json translates those; the overlay is applied on
top of the official pack, so it has to carry the same language files to win in each of them.
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
OFFICIAL_LANG = os.path.join(os.path.dirname(ROOT), 'configuration', 'resourcepacks', 'ravengard', 'assets', 'minecraft', 'lang')
LANG_DIR = os.path.join(PACK, 'assets', 'minecraft', 'lang')
CLIENT_LANG = os.path.join(CATALOG, 'client_lang.json')
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


def write_client_strings():
    strings = json.load(open(CLIENT_LANG, encoding='utf-8'))
    os.makedirs(LANG_DIR, exist_ok=True)
    count = 0
    for name in sorted(os.listdir(OFFICIAL_LANG)):
        official = json.load(open(os.path.join(OFFICIAL_LANG, name), encoding='utf-8'))
        translated = {key: value for key, value in strings.items() if key in official}
        with open(os.path.join(LANG_DIR, name), 'w', encoding='utf-8', newline='\n') as out:
            json.dump(translated, out, ensure_ascii=False)
        count += 1
    return count


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
        'description': {'color': 'gray', 'text': 'Ellan艾尔岚服务器搜打撤材质包'},
    }}
    with open(os.path.join(PACK, 'pack.mcmeta'), 'w', encoding='utf-8') as out:
        json.dump(mcmeta, out, ensure_ascii=False, indent=1)
    languages = write_client_strings()
    print(f'{len(chars)} characters -> {FONT_DIR}; client strings in {languages} language files')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
