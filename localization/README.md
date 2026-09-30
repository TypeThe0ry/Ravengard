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
- `docker-compose.zh_CN.yml` —— 可选的 compose 覆盖文件，把目录挂进容器并设置
  `RAVENGARD_LOCALE=zh_CN`、`RAVENGARD_LOCALE_DIR=/app/localization`。

## 机制

`net.swofty.type.ravengardgeneric.i18n.RavengardLocalization` 在服务端启动时读取目录，并包装
Minestom 的 `MinestomAdventure.COMPONENT_TRANSLATOR`：每个携带文本组件的出站数据包（聊天、物品名与描述、
界面标题、侧边栏、Tab、标题、Boss 栏、实体名字）都会按目录重写。先尝试整句模板匹配，再逐段精确匹配；
译文保留原样式，原字体没有中文字形（`illageralt`、资源包私有字体）时改用默认字体。

## 启用

```bash
docker compose -f docker-compose.yml -f localization/docker-compose.zh_CN.yml up -d --force-recreate --no-deps ravengard_lobby ravengard_dungeon
```

不带覆盖文件启动即回到英文。
