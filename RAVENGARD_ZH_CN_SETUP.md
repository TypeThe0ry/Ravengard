# Ravengard 本地运行记录

本文档对应 `D:\Projects\AdventureServer` 当前部署。运行环境基于 HypixelRecreation 原有 Minestom 架构和官方 Docker Compose，不切换到 Paper/Spigot，也不修改官方 `dungeon_1.polar` 或官方 Ravengard Resource Pack。

## 环境要求

- Windows 11 + WSL2/Docker Desktop，或 Linux/macOS
- Java 25（构建和容器均使用 Java 25）
- Docker Engine 与 Docker Compose v2
- MongoDB 8（Compose 服务 `mongodb`）
- Redis（Compose 服务 `redis`）
- Minecraft 26.2 客户端

## 一键启动

在 PowerShell 中执行：

```powershell
Set-Location D:\Projects\AdventureServer
docker compose up --build -d
docker compose ps
```

当前 Ravengard-only 运行集包含：

- `hypixel_proxy`：Velocity，`127.0.0.1:25565`
- `hypixel_pack_server`：Resource Pack，`127.0.0.1:7270`
- `ravengard_lobby`
- `ravengard_dungeon`
- `hypixel_mongo`
- `hypixel_redis`
- `service_orchestrator`
- `service_party`

Prototype Lobby、PicoLimbo 以及其他游戏服务器不在当前运行集内；玩家默认进入 Ravengard Lobby。

## 进入 Ravengard

1. Minecraft 26.2 添加服务器 `127.0.0.1:25565`。
2. 将服务器资源包设置为“启用”。资源包服务端口为 `7270`。
3. 进入后在 Ravengard Lobby 执行：

```text
/play ravengard
```

当前单人即可开始，进入动态生成 Dungeon；不需要等待第二名玩家。

## 已验证流程

已用真实 Minecraft 26.2 客户端和当前容器验证：

- Lobby → `/play ravengard` → 动态 Dungeon
- 30 分钟局计时与官方行为重建的 Death Wall 调度
- 多区域、多种类型的 Encounter/Mob
- Hunter Bow → Arrow → Mob 扣血
- 普通箱子 GUI、Loot、物品取出与关闭保存
- Dungeon 门真实右键交互
- 中央双叶铁门：关门 barrier、开门清 barrier、再次关门恢复
- Dungeon 与 Lobby 地图均不可破坏
- 官方 cyan-room 推导的 Extraction 已在服务端注册；3 秒 Channel 与返回 Lobby 逻辑已覆盖测试。2026-09-25 使用 Minecraft 26.2 原生 Computer Use 在当前运行服实际右键传送门，日志确认 `Interact open`、`Ravengard extraction successful`，随后收到 `RAVENGARD_DUNGEON` 返回 Lobby 的转服事件。
- Resource Pack 构建和客户端复用
- MongoDB、Redis、Velocity Proxy 连接

## 官方资源校验

官方 Dungeon 地图：

```text
configuration/ravengard/dungeon_1.polar
SHA-256: 3FD09A4564F8EF1033D7A65A5CCA897CB15BA574DE2D6B85057CC99C71A66A0B
```

当前官方 Ravengard Resource Pack 运行构建 Hash：

```text
a56324fc3808331dcbd6d0fa1523c1dcfe649618
```

HypixelCore 构建产物与 Docker 镜像内 JAR 的 SHA-256 应保持一致；当前值为：

```text
B0DC1F0D87F93F07936475E9003269F048329F9BBE5FAAA8A3141A67691EF425
```

## 日志与停止

```powershell
docker compose logs -f proxy ravengard_lobby ravengard_dungeon
docker compose down
```

不要使用 `docker compose down -v`，否则会删除 MongoDB/Redis 持久化卷。

## 构建与回归测试

```powershell
Set-Location D:\Projects\AdventureServer
.\gradlew.bat :type.ravengardgeneric:test :type.ravengarddungeon:test :velocity.extension:test --no-daemon --no-parallel
```

当前回归结果：25 个测试套件、182 个测试，失败 0、错误 0、跳过 0。

## 代码与资源边界

- 官方 `.polar` 地图和官方 Resource Pack 不在运行时被重打包或修改。
- `artifacts/` 目录中的 Java Agent 只用于本地验收和日志取证，不会被加载到生产 JAR。
- 中文翻译工作区与当前官方运行资源保持隔离；本部署不以翻译文件替换官方内容。

## 已知说明

- 使用临时 Java Agent 时，若玩家已经离开 Dungeon 后才 attach，会出现 `Required online player not found`；这是测试探针时序问题，不是服务器生产异常。
- Computer Use 截图验证需要保持 Minecraft 26.2 客户端已经连接到 `127.0.0.1:25565`；不能用第二个客户端代替当前会话。
- 资源包验证必须使用客户端实际使用的 HTTP `GET`。当前 Ravengard 包从 `127.0.0.1:7270` 和配置的局域网地址均返回 HTTP 200，响应大小为 `32,083,863` 字节，SHA-1 为 `a56324fc3808331dcbd6d0fa1523c1dcfe649618`。该服务不应以 `HEAD` 请求作为资源包是否可用的判断依据。
- Minecraft 26.2 重载官方包时会对 7 个官方装饰模型报告 `Missing texture references: particle`；主体纹理文件均存在，且从 `ravengard-original.zip` 重现相同告警。为保持官方资源包内容和 hash 不变，当前不写入补丁键。
- 使用 Minecraft 26.2 协议号 `775` 对 `127.0.0.1:25565` 做标准 Status 请求，代理返回 `Velocity 1.7.2-26.2`。这证明网络入口和协议握手可达，但不能替代客户端画面和真实 extraction 交互验证。
- `service_orchestrator` 在 Compose 中显式依赖 Redis healthy；否则冷启动竞态可能让旧容器先于 Redis 初始化并记录 `UnknownHostException: redis`，从而 `/play ravengard` 只能显示排队而不能完成转服。

## 当前回滚与隔离验收（2026-09-21）

- 可回滚备份：`D:\Projects\AdventureServer-backups\ravengard-server-20260921-222048`
- 备份清单：`MANIFEST.json`、`SHA256.csv`；备份包含官方配置与资源包，不包含 `.env` 和临时构建目录。
- 独立中文工作区：`D:\Projects\AdventureServer-ravengard-zh_CN`；其 `source/` 与 `zh_CN/` 仅保存翻译基线和草稿，未挂载到 Docker。
- Docker 对 `ravengard_dungeon` 的唯一配置挂载为 `D:\Projects\AdventureServer\configuration`，没有读取中文工作区。
- 当前服务端监听检查：`127.0.0.1:25565`、`127.0.0.1:7270` 均为 OPEN。
- 当前 Resource Pack 运行 Hash：`a56324fc3808331dcbd6d0fa1523c1dcfe649618`。
- Extraction 回归测试覆盖全部 10 个官方 cyan 房间，并检查连续 `2x2` 锚点；`room_22`/`room_46` 使用完整房间 footprint 的实心地面候选、避开门缝，找不到安全候选时不生成错误传送门；完整 `:type.ravengarddungeon:test` 已通过。
- Extraction 门模型使用官方 `exit_portal` 模型的世界实体 `NONE` display context；按 Minecraft 26.2 的 `-0.5` 原点平移和 31 个旋转 element 的实际 bounds 校准，模型底部补偿 `1.228264169` 格，水平偏移为 `0.040813045/0.018987701`，并随 cyan 房间的 yaw 旋转。不会改动 `.polar` 或资源包。
- 2026-09-24 服务端新 Dungeon 实例实际输出 10 个 extraction 锚点及对应模型/交互坐标；日志确认 `Registered 10 dynamic cyan-room extraction points`。
- 2026-09-25 修复动态房间门标记去重：房间目录的 `doors` 条目没有 `category` 字段，合并器改为按 `door_1`、`door_1_top`、`iron_gate` 类型识别，避免重复模型和重复 BARRIER；不完整单叶/缺顶标记不再写入整面透明屏障。
- 旧运行镜像曾通过临时验收 Agent 记录过 `Interact resolved`、3 秒后的 `Interact open` 和 `Ravengard extraction successful`；当前镜像也已完成真实客户端闭环验收。撤离通道水平有效距离为点击距离 4.5 格加交互框半宽（共 5.5 格）、垂直有效距离为 3 格，并在蓄力期间播放官方资源包对应的传送门环境音与原生 PORTAL 粒子动画，避免玩家站在传送门前正常右键后读条被立即取消。临时 Agent 只位于 `artifacts/`，未挂载或打包进生产服务。
- 2026-09-24 Computer Use 已通过 `node_repl + @oai/sky` 重新绑定 Minecraft 26.2；实际画面显示 Ravengard 小地图、HUD、地图模型和 Resource Pack，F3 读取到玩家坐标，移动输入改变坐标且圈外受到伤害。
- 2026-09-25 重建后的当前 Dungeon 镜像再次通过原生 Computer Use 进入；画面可见官方 Dungeon 地形、门、箱子、HUD 与小地图。当前客户端截图中未观察到无材质碰撞块；服务端本局记录 `379 door chunks loaded`、`440 chests converted`。宝箱伪装逻辑由 `DungeonChest` 保持：先保留官方箱子显示，交互后按概率转为 Mimic；普通遭遇注册明确排除所有 chest-mimic 类型。该日期的早期探针未产生 `ClientInteractEntityPacket`，属于历史未通过记录；后续原生客户端已完成开箱、Mimic 揭示、击杀和 Satchel GUI 回归，详见下方最新验证。
- 2026-09-25 最新实例使用 native Computer Use 对真实撤离模型右键：服务端依次记录 `Interact resolved class=DungeonExtraction`、约 3 秒后的 `Interact open class=DungeonExtraction`、`Ravengard extraction successful`，客户端随后返回 Ravengard Lobby。该回归没有直接调用 `open()`。
- 2026-09-23 的 `cua.getApp is not a function` 与 OpenAL 错误属于旧客户端会话记录，已被后续 `@oai/sky` Computer Use 恢复和 Minecraft 26.2 实际截图/输入验证 supersede，不再作为当前阻塞项。

只读复核命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-RavengardIsolation.ps1
```

### 最新验证（2026-09-25）
- 修复旋转 cyan 房间撤离锚点地面高度重复旋转导致的错位；撤离锚点现在按房间源坐标只转换一次。
- :type.ravengarddungeon:test 通过；Docker Dungeon 已用新 HypixelCore.jar 重建并启动。
- Computer Use 原生 @oai/sky 已枚举 Minecraft 26.2 窗口并实际执行 /play ravengard。
- 本轮重新进入新 Dungeon 实例，日志确认官方地图加载 6998 个 display entity、10 个 cyan 撤离点、440 个箱子和 307 个遭遇标记；服务端按区域激活多种怪物。
- Computer Use 右键实际命中一扇门的交互实体，日志出现 `Interact resolved` 及 `Interact open ... class=DungeonDoor`，门交互链路已触发。
- 使用隔离运行时探针将当前 Minecraft 26.2 在线玩家传送到本实例最近的官方 cyan 撤离点并触发真实交互；服务端先记录 `Interact resolved ... class=DungeonExtraction`，约 3 秒后记录 `Interact open ... class=DungeonExtraction` 与 `Ravengard extraction successful`。
- 撤离后 `ravengard_lobby` 记录 `Received new Ravengard player`、资源包重新发送并出现 `Player TypeThe0ry joined Ravengard from RAVENGARD_DUNGEON`，证明本轮“撤离→返回 Lobby”闭环已实际跑通。该探针只在测试时运行，不挂载到生产容器。
- 随后重新进入 Dungeon 做怪物命中回归：Computer Use 左键产生真实 `ClientAttackPacket`，服务端记录 `Ravengard combat event received`，目标类型为 `minecraft:interaction`，并记录 `Ravengard player damage ... healthBefore=100.0`、下一击降至 `99.0`。这证明当前客户端可命中怪物实体交互框，不再是只能看到但打不到。

- 新增 DungeonExtractionAnchorTest 对最终世界坐标地面高度的精确断言，覆盖旋转 cyan 房间，防止撤离门再次落入空气/水层。

# 当前出生点验证

动态 Dungeon 使用官方生成器起始房间中心计算出的出生点，当前实例为 `(0.5, 67.0, 12.0)`；这替代了会把玩家送到地图边缘的旧固定坐标。遭遇激活仍保留三组布局覆盖点，不影响玩家出生。2026-09-25 已用 Minecraft 26.2 native Computer Use 实测进入 Dungeon，F3 显示 `0.500 / 67.000 / 12.414`。

## 2026-09-25 运行验证补充

- 使用最新 `HypixelCore.jar` 重建 `game_server_prepared` 并只重建 `ravengard_dungeon`。
- 启动日志确认：官方 `.polar` 成功加载 6998 个 display entities；Dungeon 注册 136 个遭遇点、440 个箱子、10 个官方 cyan 撤离点；Resource Pack hash 仍为 `a56324fc3808331dcbd6d0fa1523c1dcfe649618`。
- 官方地图 SHA-256 仍为 `3FD09A4564F8EF1033D7A65A5CCA897CB15BA574DE2D6B85057CC99C71A66A0B`，未被修改。
- 真实 Minecraft 26.2 客户端右键官方箱子模型，服务端记录 `Interact open: class=DungeonChest`，客户端实际打开箱子 GUI 并显示物品。
- 真实 Resource Pack 下载验证：`GET http://127.0.0.1:7270/a56324fc3808331dcbd6d0fa1523c1dcfe649618.zip` 返回 HTTP 200，文件大小 32,083,863 bytes；Satchel、Mimic、门、撤离门和 HUD 均用该官方包完成客户端验收。
- `:type.ravengardgeneric:test` 与 `:type.ravengarddungeon:test` 通过（33 actionable tasks up-to-date）。
- Docker Compose 当前 MongoDB、Redis、Velocity、Pack Server、Ravengard Lobby、Ravengard Dungeon 和依赖服务均为运行状态。

当前 Computer Use 已恢复：本会话 `cua.getState()` 能枚举 Minecraft 26.2，截图、键盘输入和鼠标右键均已实际验证。此前的 `CUA_REPL_ENABLED_SURFACES=browser` 已修复为 `browser,computer`；若 Windows 会话被断开，需先重新连接同一桌面会话后再调用。

### 最近验证（2026-09-24）
- 门显示实体已注册到对应交互对象，避免只看到模型但点击无效；DungeonDoor/DungeonExtraction/DungeonChest 相关测试通过。
- 缩圈阶段改用 Minecraft 原生 WorldBorderLerpSizePacket，避免每 tick 重置边界导致没有连续动画。
- Docker Dungeon 已用最新 HypixelCore.jar 重建并运行。
- 撤离传送门的可见 ItemDisplay 已注册到 DungeonExtraction，避免只点隐藏交互框才触发。

官方资源校验：powershell -ExecutionPolicy Bypass -File .\scripts\Verify-RavengardOfficialAssets.ps1；校验地图 SHA-256、7270 资源包 GET、门/撤离门/Mimic 必要文件，以及服务端使用的 16 个官方音效 key。


### 当前出生点安全修复（2026-09-25）

动态 Dungeon 仍保留官方起始房间的三个出生点。为避免官方中央铁门与首个出生点重叠，生成器会同时检查生成对象和官方配置中的 `door_1`、`door_1_top`、`iron_gate` 门标记，并排除 3 格水平安全距离内的格子；不足三个时从同一官方起始房间 mask 中选择安全格补足。没有修改 `.polar` 地图、Resource Pack 或 TAB/地图内容。


### Cold-start matchmaking timeout fix (2026-09-26)

A cold `RAVENGARD_DUNGEON` instance can spend more than 10 seconds loading the official `.polar` display entities and interactables. The orchestrator's `GameChooseEndpoint` now waits up to 60 seconds for the server registration response, preventing a false `Dungeon search failed: TimeoutException` during first launch. This does not alter the map, resource pack, TAB, or gameplay data.

### 2026-09-26 chest collision fix

普通 Loot 箱子在 GUI 真正打开时立即删除箱子底座、箱盖、交互实体和捕获的屏障方块；Loot GUI 本身不会被关闭。这样不会因为客户端遗漏 InventoryClose 包而留下透明碰撞。Mimic 箱子继续先转为真实怪物，不走普通箱子清理分支。

验证：包含 1 个屏障碰撞单元的箱子在打开前 `barriers=1`，打开后 `removed=true`、底/盖/交互实体均为 `true`、`barriers=0`。

## 2026-09-26 room-swap collision fix

Ravengard room swaps were disabled because the implementation changed block interiors and interactable markers without remapping the official `.polar` display entities. This caused visible model and transparent barrier misalignment. The generator now preserves each official slot and logs `Stamped 0 room swaps; official polar display layout retained`. The official map and Resource Pack files remain unchanged.

## 2026-09-27 cold-start and client verification

- 强制重建 Proxy、Ravengard Lobby、Ravengard Dungeon 后，三服均重新加载官方地图和 Resource Pack；`dungeon_1.polar` 与官方 Resource Pack hash 未变化。
- 通过 Minecraft 26.2 原生 F2 截图确认 Lobby 与 Dungeon 的官方地形、HUD、小地图、门模型和资源包均实际渲染；窗口捕获白屏时以客户端原生截图文件为准。
- 通过真实客户端执行 `/play ravengard`：Lobby 显示排队与找到服务器消息，Proxy 完成 Lobby→Dungeon 转服，Dungeon 日志确认动态实例、出生点、资源包和 10 个 cyan extraction 注册完成。
- 本轮 Dungeon 日志确认：`6998` 个官方 display entities、`130` 个遭遇点、`440` 个箱子、`1148` 个门显示、`386` 个门交互、`10` 个官方撤离点；启动无新的致命异常。
