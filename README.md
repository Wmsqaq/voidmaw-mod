# Void Maw 虚空之喉

Minecraft 1.21.10 Fabric 功能模组：**黑洞大作战**玩法——使用**奇点核心**，黑洞在玩家脚下张开，只吞噬位于它上方的东西：方块被撕离地面翻滚着坠入坑中，生物被拖过坑沿落入深渊。吞得越多等级越高（Lv1-5），坑越大、能吞的猎物越大；吞噬物与生物掉落物全部进入**黑洞仓库**。Shift+右键**自爆**，按等级释放全部质量。

- **Mod ID**：`voidmaw`
- **作者**：novapupil
- **License**：MIT
- **仓库**：GitHub `Wmsqaq/voidmaw-mod` · Gitee `novapupil/voidmaw-mod`（一次 push 双仓库）

## 玩法

| 机制 | 行为 |
|---|---|
| 触发 | 奇点核心**右键**开启/安静关闭；**Shift+右键 自爆**（唯一引爆方式，威力按等级） |
| 黑洞本体 | **平面黑洞**：贴在玩家脚底地面上的湮灭圆盘（黑色+紫色暗环，客户端平滑插值），坑口=脚底所在高度，随行走拖动 |
| 吞噬范围 | **只消灭圆盘范围内、高于脚底平面的东西**；平面以下的地形（包括脚下和周围的地面）**永不破坏** |
| 方块 | 高于脚底平面的方块（建筑、墙、树上等）自底向上被撕离，翻滚向圆盘中心、落地即消化，掉落物进仓库 |
| 生物 | 圆盘范围内的生物被拖向中心吞掉；**等级不够吞不下的生物完全不受到影响**；掉落物进仓库 |
| 等级 | 质量阈值 Lv1≥0 / Lv2≥100 / Lv3≥400 / Lv4≥1200 / Lv5≥3000，升级有提示与音效 |
| 自爆 | 爆炸威力按等级：1.5 / 2.5 / 3.5 / 4.5 / 6（TNT 为 4），黑洞本体免疫 |

### 吸收等级表

| 等级 | 坑半径 | 可吞方块硬度上限 | 可吞生物（近似包围盒） | 自爆威力 |
|---|---|---|---|---|
| Lv1 | 2.5 格 | 1（泥土/沙） | 兔子、鸡、掉落物 | 1.5 |
| Lv2 | 3.5 格 | 3（木头/矿石） | 牛羊猪、僵尸骷髅苦力怕 | 2.5 |
| Lv3 | 4.5 格 | 6（石头/铁块） | 马、铁傀儡 | 3.5 |
| Lv4 | 5.5 格 | 15 | 劫掠兽、凋灵 | 4.5 |
| Lv5 | 7 格 | 无限制（黑曜石/远古残骸） | 一切（含末影龙） | 6 |

### 黑洞仓库

- `/voidmaw warehouse` 打开分页仓库界面：**9 页 × 45 格 = 405 格**，每页底行为翻页栏（◀ 上一页 / 页码 / 下一页 ▶，点击切换）
- 每个玩家独立一库（按 UUID 区分）；吞掉的物品、生物掉落物、被吞方块的全部掉落都会入库（石制方块按原版掉落表，注入钻石镐作为工具）
- 持久化：`<世界>/data/voidmaw/warehouses/<UUID>.json`（ItemStack JSON 编码，**不使用 NBT**，兼容旧版单页存档自动迁移到第 1 页）
- 全部页满时溢出转化为质量；每 2 秒增量落盘

### 其他

- 基岩、屏障、强化深板岩、传送门方块等不可吞噬（数据包标签 `#voidmaw:unswallowable`）
- 不吞任何玩家（含自己）
- 进服礼包：每位玩家首次进入世界自动获得 1 个奇点核心（`Balance.GIVE_CORE_ON_FIRST_JOIN` 可关闭）
- 所有数值集中在 `Balance.java` 与 `HoleLevel.java`

### 合成

```
黑曜石  末影珍珠  黑曜石
末影珍珠  末影之眼  末影珍珠
黑曜石  末影珍珠  黑曜石
```

### 指令（OP 等级 2）

- `/voidmaw start` — 张开黑洞
- `/voidmaw stop` — 安静闭合
- `/voidmaw detonate` — 自爆（等级威力）
- `/voidmaw status` — 等级 / 质量 / 半径 / 剩余时间
- `/voidmaw warehouse` — 打开黑洞仓库

## 构建

依赖 JDK 21+ 与 Gradle（项目自带 wrapper 9.5.1，Gradle 缓存走 `D:/.gradle`，插件仓库已配置阿里云镜像优先）：

```bash
gradlew build        # 产物：build/libs/voidmaw-<版本>.jar
gradlew runClient    # 开发环境进游戏实测
```

贴图由脚本生成（纯 Python 标准库，无第三方依赖）：`python tools/gen_textures.py`

## 发布流程

1. 推送 tag（`v1.0.0-beta.1` 格式，tag 含 beta/alpha 会自动标记为预发布）→ GitHub Actions 自动构建并创建 GitHub Release（`.github/workflows/release.yml`）。
2. 本地运行 `bash tools/release-gitee.sh v1.0.0-beta.1` 把 Release 镜像到 Gitee 并附上 jar（token 放在仓库根的 `.gitee-token`，已被 gitignore；Gitee 屏蔽云机房 IP，因此该步骤必须在本地跑）。
3. **每次更新必须本地部署**：`bash tools/deploy-local.sh` — 把最新 jar 复制到测试实例 `J:\LauncherX\.minecraft\versions\团播-21.10-API0.19.3\mods`，并自动删除该目录下的旧版 voidmaw jar。

## 致谢 / 参考

- 玩法参考 Voodoo 的 Hole.io（黑洞大作战）：等级解锁猎物、边缘翻滚吞噬、限时成长循环
- 工程模板与发布流程借鉴同作者工作区的 `killstreak-mod` / `radkeyboard`
- 社区参考：CurseForge [Blackhole](https://www.curseforge.com/minecraft/mc-mods/blackhole)（引力吸方块并变大）的机制思路；全部代码为本仓库原创实现
- 依赖：[Fabric API](https://modrinth.com/mod/fabric-api)。零 Mixin，全部通过 Fabric API 事件实现
