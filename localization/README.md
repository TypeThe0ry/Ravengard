# Ravengard 本地化

服务端与配置里的玩家可见文本全部以英文书写；本目录存放独立的翻译目录（catalog），运行服默认不加载。

## 目录

- `inventory_en.txt` —— 从源码与配置提取的英文文本清单（脚本生成，供对照）。
- `zh_CN/*.yml` —— 简体中文目录。每个文件形如：

  ```yaml
  entries:
    "<a>You used <f>{}<a>!": "<a>你使用了 <f>{}<a>！"
    "Med Kit": "医疗包"
  ```

  键是源码里的英文标记文本（或配置里的英文原文，可带 `§` 颜色码），值是同一标记语法的译文；`{}`/`{0}` 占位符按位置对应。多个文件在加载时合并。
- `zh_CN/resourcepack/` —— 字体覆盖资源包（由 `tools/build_font_overlay.py` 从译文生成）。官方包用两个
  不可见的“间距字体”（`hypixel_ravengard:-half` / `-full`）给 HUD 文字居中、右对齐，它们只登记了 ASCII 字符；
  覆盖包为译文用到的每个字符补上进距。它作为独立的第二个资源包发送，排在官方包下面，官方包本身不改动。
- `docker-compose.zh_CN.yml` —— 可选的 compose 覆盖文件：把目录挂进两个游戏服、代理与 pack server，设置
  `RAVENGARD_LOCALE=zh_CN`、`RAVENGARD_LOCALE_DIR=/app/localization`，并关闭 Minestom 的分组数据包
  （分组包对所有观众只序列化一次，绕过逐玩家翻译）。
- `tools/build_font_overlay.py` —— 改动译文后重新生成字体覆盖包。

## 机制

`net.swofty.type.ravengardgeneric.i18n.RavengardLocalization` 在服务端启动时读取目录，并包装
Minestom 的 `MinestomAdventure.COMPONENT_TRANSLATOR`：每个携带文本组件的出站数据包（聊天、物品名与描述、
界面标题、侧边栏、Tab、标题、Boss 栏、实体名字）都会按目录重写。先尝试整句模板匹配，再逐段精确匹配；
译文保留原样式；资源包的位图字体没有中文字形时改用默认字体，间距字体保持不变（由覆盖包补进距），
故意不可读的符文字体（`illageralt`）不翻译。技能描述在换行前就本地化。代理（Velocity）没有数据包边界，
改由 `Text.of` 按同一目录替换标记文本（`commons` 的 `MarkupLocalization`）。

不可翻译的部分：资源包里画成贴图的按钮文字（Fight、Confirm、Cancel、Expand）、背包界面的 Inventory 标题、
顶部的 RAVENGARD 标志、稀有度/类型/职业徽章，这些都是官方包的图片。服务器名、地牢 ID、玩家名等标识符保持原样。

## 查漏

覆盖文件里设置了 `RAVENGARD_LOCALE_REPORT=true`：目录没有覆盖到的可读英文文本，每条只在日志里记录一次。

```bash
docker compose logs ravengard_lobby ravengard_dungeon | grep "i18n-gap"
```

把报告出来的原文补进 `zh_CN/*.yml`，重新生成字体覆盖包，再重启相应容器即可。

## 启用

```bash
docker compose -f docker-compose.yml -f localization/docker-compose.zh_CN.yml up -d --force-recreate --no-deps proxy pack_server ravengard_lobby ravengard_dungeon
```

（代理重启会断开在线玩家；只改了译文时重启 `ravengard_lobby`、`ravengard_dungeon` 即可，改了字体覆盖包还需重启 `pack_server`。）
不带覆盖文件启动即回到英文。
