# 天穹余烬（Ravengard）

基于 Minestom 的 Minecraft 搜打撤（搜刮、战斗、撤离）地牢服务端：玩家在大厅整备，进入动态生成的地牢，搜刮战利品、对抗怪物和不断收缩的死亡之墙，并在时限内抵达撤离点带出收获。

本仓库是 [Swofty-Developments/HypixelRecreation](https://github.com/Swofty-Developments/HypixelRecreation) 的衍生作品，只保留 Ravengard 运行与构建所需的部分，其余玩法（SkyBlock、BedWars、SkyWars、Murder Mystery 等）、文档站和客户端模组均已移除。许可证为 AGPL-3.0，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

## 功能

- 大厅：职业与技能、装备商店、NPC、传送与组队入口（`/play ravengard`、`/dungeon`）。
- 地牢：按房间目录动态拼装的地图，怪物遭遇、宝箱与战利品、门与机关、死亡之墙、30 分钟一局的计时。
- 撤离：撤离点引导与返回大厅的转服流程，战利品与经验在撤离后结算。
- 队伍：单人或小队（`SOLO` 12 人、组队 9 人）共用一个地牢实例。
- 中文化：玩家可见文字直接写在源码和配置里，资源包的中文覆盖层位于 [localization](localization/README.md)，不改动官方资源包本体。

## 目录结构

| 路径 | 作用 |
| --- | --- |
| `loader` | 游戏服入口（`HypixelCore.jar`），按启动参数 `RAVENGARD_LOBBY` / `RAVENGARD_DUNGEON` 加载对应模块 |
| `type.ravengardgeneric` | 大厅与地牢共用的数据、物品、职业、商店、队列 |
| `type.ravengardlobby` / `type.ravengarddungeon` | 大厅与地牢服务器的玩法逻辑 |
| `type.generic` / `type.game` / `commons` / `dungeons` | 通用玩家、命令、GUI、文本、Redis 协议与地牢生成 |
| `velocity.extension` / `proxy.api` | Velocity 代理插件（`SkyBlockProxy.jar`）与服务器到代理的 Redis 接口 |
| `service.orchestrator` / `service.party` | 地牢实例调度服务、组队服务 |
| `packer` | 资源包服务（`net.swofty.packer.HypixelPackServer.jar`） |
| `configuration` | 运行配置、Ravengard 数据与资源包、翻译 |
| `deploy/hangzhou` | 挂在已有 Velocity 之后的服务器部署方案 |
| `docs` | 设计文档与计划 |

## 构建

需要 JDK 25。

```bash
./gradlew build
./gradlew :loader:shadowJar :velocity.extension:shadowJar :packer:shadowJar :service.orchestrator:shadowJar :service.party:shadowJar
```

产物：

- `loader/build/libs/HypixelCore.jar`
- `velocity.extension/build/libs/SkyBlockProxy.jar`
- `packer/build/libs/net.swofty.packer.HypixelPackServer.jar`
- `service.orchestrator/build/libs/ServiceOrchestrator.jar`
- `service.party/build/libs/ServiceParty.jar`

部分测试和运行依赖不随仓库分发的素材（已被 `.gitignore` 排除）：

- `configuration/ravengard/dungeon_1.polar`
- `configuration/world/hypixel_ravengard_lobby.polar`、`configuration/world/hypixel_ravengard_tutorial.polar`
- `configuration/resourcepacks/ravengard/`

请自行准备这些素材并放到上述位置。

## 本地 Docker 运行

```bash
cp .env.example .env
# 把 .env 里的 FORWARDING_SECRET 改成随机值
cp loader/build/libs/HypixelCore.jar \
   velocity.extension/build/libs/SkyBlockProxy.jar \
   packer/build/libs/net.swofty.packer.HypixelPackServer.jar \
   service.orchestrator/build/libs/ServiceOrchestrator.jar \
   service.party/build/libs/ServiceParty.jar \
   DockerFiles/jars/
docker compose up --build -d
```

Compose 会启动 MongoDB、Redis、Velocity 代理（`25565`）、资源包服务（`7270`）、`ravengard_lobby`、`ravengard_dungeon`、`service_orchestrator` 和 `service_party`。用与 `gradle/libs.versions.toml` 中 Minestom 版本匹配的 Minecraft 客户端连接 `127.0.0.1:25565`，启用服务器资源包，在大厅执行 `/play ravengard` 进入地牢。

启用中文资源包覆盖层与更细的运行说明见 [localization/README.md](localization/README.md) 和 [RAVENGARD_ZH_CN_SETUP.md](RAVENGARD_ZH_CN_SETUP.md)。

## 部署概述

`deploy/hangzhou` 是挂在已有 Velocity 之后的部署方式：`deploy.sh` 在本机构建上面的 jar，与服务器上的文件逐一比对后只上传变化部分，玩家在线时只上传不重启，随后在服务器上用 `docker-compose.yml` 起栈。该栈只含 MongoDB、Redis、代理、两个游戏服、资源包服务、调度服务与组队服务，所有容器的内存与 CPU 都有上限。`deploy.sh` 由维护者手动执行，它会改动远程服务器，不要在没有评估的情况下运行。

## 源码提供（AGPL-3.0 第 13 条）

本项目以 AGPL-3.0 授权。如果你修改了本项目并让用户通过网络与之交互（例如运营一个公开的服务器），必须向这些用户提供你修改后版本的完整对应源码。运营本项目的服务器时，请在服务器的公开页面或游戏内提供指向其源码仓库的链接。

## 许可与致谢

- 许可证：[GNU Affero General Public License v3.0](LICENSE)，不得改用其他许可证。
- 上游来源、主要作者与本仓库的修改范围：[NOTICE](NOTICE)。
- 本项目与 Mojang、Microsoft、Hypixel 没有隶属或授权关系。
