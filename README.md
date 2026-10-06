# Void Maw 虚空之喉

Minecraft 1.21.10 Fabric 功能模组：**黑洞大作战**玩法——使用**奇点核心**，黑洞在玩家脚下张开，只吞噬圆盘范围内位于它上方的方块和生物。方块直接移除，生物被拖向中心；吞噬物与掉落物进入个人**黑洞仓库**。吞得越多等级越高（无玩法等级上限），圆盘越大、可吞噬对象越大。右键安静关闭，Shift+右键按等级引爆。

- **Mod ID**：`voidmaw`
- **作者**：novapupil
- **License**：MIT
- **仓库**：GitHub `Wmsqaq/voidmaw-mod` · Gitee `novapupil/voidmaw-mod`（一次 push 双仓库）

## 玩法

| 机制 | 行为 |
|---|---|
| 触发 | 奇点核心**右键**开启/安静关闭；**Shift+右键 自爆**（唯一引爆方式，威力按等级） |
| 黑洞本体 | **平面黑洞**：贴在玩家脚底地面上的湮灭圆盘（黑色事件视界+紫色吸积环，原版 ItemDisplay 实体渲染，全亮度显示，原版/Sodium/Iris 均可见），圆盘=脚底所在高度，随行走平滑拖动；玩家模型保持可见 |
| 吞噬范围 | **只消灭圆盘范围内、高于脚底平面的东西**；平面以下的地形（包括脚下和周围的地面）**永不破坏** |
| 方块 | 圆盘范围内从脚底平面直到世界最高层的可破坏方块都可吞噬，无等级硬度门槛；循环扫描逐步清空、掉落物入库；保留保护标签和不可破坏方块，不加载未加载区块 |
| 生物 | 圆盘范围内的生物被拖向中心吞掉；**等级不够吞不下的生物完全不受到影响**；掉落物进仓库 |
| 等级 | 质量阈值 Lv1≥0 / Lv2≥100 / Lv3≥400 / Lv4≥1200 / Lv5≥3000 / Lv6≥5700，后续按二次曲线继续增长；关闭、坍缩、自爆、死亡、重登均保留累计质量和等级 |
| 自爆 | 以普通苦力怕威力 3 为起点，按 `3 + 0.75×(等级−1)^0.75` 平缓增长；Lv1=3、Lv5≈5.12、Lv10≈6.90，自身短暂免疫，累计质量不消耗 |

### 吸收等级表

| 等级 | 半径 | 可吞方块 | 自爆威力 |
|---|---|---|---|
| Lv1 | 2.5 格 | 范围内全部可破坏方块 | 3 |
| Lv2 | 3.5 格 | 同上 | 3.75 |
| Lv3 | 4.5 格 | 同上 | 约 4.26 |
| Lv4 | 5.5 格 | 同上 | 约 4.71 |
| Lv5 | 7 格 | 同上 | 约 5.12 |
| Lv6+ | `7 + 2 ln(等级 - 4)` 格 | 同上 | `3 + 0.75×(等级−1)^0.75` |

Lv6+ 质量阈值为 `3000 + 1800n + 900n²`（n=等级−5）。等级由累计质量计算，存入 `<世界>/data/voidmaw/progress/<玩家UUID>.json`，不使用 NBT；每两秒及闭合时保存。每次开启重新获得基础 60 秒，吞噬仍可加时至 300 秒。

全高度扫描每 tick 最多检查 4096 个位置、吞噬 8 个方块。保持在同一处可循环覆盖整根柱体，不会一帧移除数万方块。只针对已加载区块；液体及保护块保持原规则不吞。

### 黑洞仓库

- `/voidmaw warehouse` 打开分页仓库界面：**9 页 × 45 格 = 405 格**，每页底行为导航栏（◀ 上一页 / 整理 / 页码 / 下一页 ▶）
- 热键 **G**（可改键）直接打开仓库；点导航栏的**箱子图标 = 整理**（合并同类堆叠、按物品排序、跨页压缩）
- 每个玩家独立一库（按 UUID 区分）；吞掉的物品、生物掉落物、被吞方块的全部掉落都会入库（石制方块按原版掉落表，注入钻石镐作为工具）
- 持久化：`<世界>/data/voidmaw/warehouses/<UUID>.json`（ItemStack JSON 编码，**不使用 NBT**，兼容旧版单页存档自动迁移到第 1 页）
- 全部页满时溢出转化为质量；每 2 秒增量落盘

### 其他

- **奇点核心锁死第九格**：核心固定在快捷栏最右格，无法点选/Shift 移动/拖拽/数字键换走/丢进容器；按 Q/Ctrl+Q 不丢出，按 F 不进副手；展示框、饰纹陶罐、书架、盔甲架、悦灵等世界交互拿不走核心；创造模式改槽/丢核数据包被服务端拒绝；**死亡不掉落**，重生自动回位；多余核心自动清除
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

### 指令

`start`、`stop`、`detonate` 需要 OP 等级 2；`status` 和个人 `warehouse` 普通玩家也可使用。

- `/voidmaw start` — 张开黑洞
- `/voidmaw stop` — 安静闭合
- `/voidmaw detonate` — 自爆（等级威力）
- `/voidmaw status` — 等级 / 质量 / 半径 / 剩余时间
- `/voidmaw warehouse` — 打开黑洞仓库

## 进服版权声明

每次玩家进服，聊天栏广播 4 行硬编码的版权信息（`VoidMawBroadcast`，与 killstreak 的进服广播同款）：

1. 渐变标语 `Void Maw All Rights Reserved.`
2. `当前版本：x.y.z（虚空之喉）`
3. 渐变 `This mod was created by novapupil`
4. `玩家名 ，你好！`

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
- 依赖：[Fabric API](https://modrinth.com/mod/fabric-api)。吞噬/渲染走 Fabric API 事件；仅核心防丢失等少数库存保护使用少量服务端 Mixin（见 `voidmaw.mixins.json`）
