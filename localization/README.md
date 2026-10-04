# Ravengard 中文

玩家能看到的文字直接以中文写在源码与配置里（`type.ravengard*`、`type.generic`、`commons`、`proxy.api`、
`velocity.extension`、`service.party`、`type.game`、`configuration/ravengard`、`configuration/i18n/en_US`），
服务端不做任何运行时翻译。本目录只放资源包层与生成它的工具。

## 目录

- `zh_CN/resourcepack/` —— 资源包层，构建时叠在官方包文件之上合成一个资源包发送，官方包目录本身不改动：
  - 同路径文件以本层为准；字体文件按包叠放的规则合并（本层的 providers 接在官方之后，后者优先；
    本层的 `reference` 放在官方之前，只给官方字形里没有的字符兜底），语言文件按键覆盖。
  - 包名与图标：`pack.mcmeta`/`pack.png`（Ellan艾尔岚服务器搜打撤材质包）。
  - 字体进距：官方包用两个不可见的“间距字体”（`hypixel_ravengard:-half` / `-full`）给 HUD 文字居中、右对齐，
    它们只登记了 ASCII 字符；本层为源码与配置里出现的每个中日韩字符补上进距。
  - 像素字体兜底：`hypixel_ravengard:ravengard` 引用 `minecraft:default`，中文在像素字体里没有字形时用默认字体显示。
  - 客户端文字：官方包在每个语言文件里改写了几条原版界面文字（暂停菜单按钮、声音滑块名），
    `zh_CN/client_lang.json` 给出中文，生成脚本把它写进官方包覆盖过的每一个语言文件。
  - 贴图文字：官方包画在贴图里的英文（物品描述里的稀有度/类型/职业徽章、菜单按钮上的 Fight、Confirm、Cancel、
    Expand 等、背包界面的标题、顶部的 RAVENGARD 标志）用宋体 12px 点阵字重画，沿用官方配色与尺寸；
    徽章另存为 `font/tags_zh/`，由本层的 `hypixel_ravengard:ravengard` 把官方字符映射到新图。
- `docker-compose.zh_CN.yml` —— compose 覆盖文件：把资源包层挂进两个游戏服与 pack server。
- `tools/build_font_overlay.py` —— 源码或配置里的中文、`client_lang.json` 改动后重新生成字体进距与客户端文字。
- `tools/build_texture_text.py` —— 重新生成贴图文字与徽章字体（需要 Windows 的 `simsun.ttc`）。
- `tools/build_logo.py` —— 重新生成顶部标志。

## 启用

```bash
docker compose -f docker-compose.yml -f localization/docker-compose.zh_CN.yml up -d --force-recreate --no-deps pack_server ravengard_lobby ravengard_dungeon
```
