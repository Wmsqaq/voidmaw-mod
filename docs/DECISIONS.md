# 设计决策存档

记录 2026-10-04 立项时与作者的问答确认，供后续开发对照。

## 命名（作者委托提案）

- 文件夹：`voidmaw-mod`（沿用工作区 `<name>-mod` 惯例）
- modid：`voidmaw`，Mod 名 "Void Maw 虚空之喉"（黑洞即"虚空之口"），包名 `com.novapupil.voidmaw`

## 玩法问答结论

| 问题 | 作者选择 | 落地 |
|---|---|---|
| 触发方式 | 道具 + 指令 | 奇点核心右键开/潜行+右键关；`/voidmaw start\|stop\|status`（OP2） |
| 吞噬额外收益 | 仅"结束时能量爆炸"（未选进背包、未选经验） | 闭合时按质量爆发，威力上限 6 级，本体免疫 |
| 视觉风格 | 标准 | 渐大黑球渲染 + 服务端吸积盘粒子 + 音效；隐藏宿主模型 |
| 玩家状态 | 缓慢漂浮 | 缓降 + 缓慢效果，免摔伤，可移动 |

## 技术基线（全部取自工作区已验证模板）

- MC `1.21.10` · yarn `1.21.10+build.3` · loader `0.19.3` · loom `1.17.20` · Fabric API `0.138.4+1.21.10` · Java 21 · Gradle wrapper 9.5.1（`org.gradle.user.home=D:/.gradle`）
- 镜像：settings.gradle 插件仓库 AliYun → Fabric → mavenCentral → gradlePluginPortal（照抄 killstreak-mod）
- 发布：tag 推送 → GitHub Actions 构建 + Release；`tools/release-gitee.sh` 本地镜像 Gitee（Gitee 屏蔽云机房 IP）
- git 远程：origin = GitHub（Wmsqaq/voidmaw-mod）+ 双 pushurl（GitHub + Gitee novapupil/voidmaw-mod），一次 push 双仓库

## 实现要点备忘（踩坑记录）

- 1.21.10 的 `Item.Settings()` 必须先 `.registryKey(...)`，否则运行时 "Item id not set" 崩溃。
- yarn 1.21.10：`Entity` 无 `getPos()`（用 `getX/getY/getZ`）；`World.isClient` 是私有字段（用 `isClient()`）；`GameRenderer.camera` / `MatrixStack.Entry.positionMatrix` / `BlockSoundGroup.placeSound` 同理需走 getter；`SoundEvents.ENTITY_GENERIC_EXPLODE` 是 `RegistryEntry`（要 `.value()`）。
- 服务端改实体速度后必须 `entity.velocityModified = true`。
- 方块吞噬必须用"柱状扫描"而非随机 3D 采样：开放地形下随机点几乎全是空气，玩家感知为"吸不动"；由下至上扫描每根随机柱子里离核心最近的方块，开阔地会吃出不断加深的漏斗坑。

## 2026-10-06 部署约定（作者长期指示）

- **以后每次更新**：构建后必须运行 `bash tools/deploy-local.sh`，把新版 jar 部署到
  `J:\LauncherX\.minecraft\versions\团播-21.10-API0.19.3\mods`，并删除该目录下旧版 voidmaw jar（脚本只动 `voidmaw-*.jar`，不碰其他 mod）。
- 该实例 = 团播 1.21.10 + Fabric Loader 0.19.3，作者实际游玩环境，反馈都来自这里。

## 2026-10-05 Hole.io 式重构（作者追加需求）

- 黑洞本体=玩家脚下的地坑，**只吸收位于坑底之上的东西**（作者原话："黑洞只吸收它上方的东西，黑洞位置在玩家脚下"）
- 方块以 `FallingBlockEntity` 翻滚坠入坑中心后消化（`dropItem=false` 防止落地回放方块；坑底 2.25 距离内或 40 tick 后强制消化）
- **吸收等级 Lv1-5**（质量 0/15/40/80/150）决定：坑半径、可吞方块硬度、可吞生物包围盒、自爆威力（1.5/2.5/3.5/4.5/6）
- **黑洞仓库**：按玩家 UUID 分库；持久化为 `<world>/data/voidmaw/warehouses/<uuid>.json`（`ItemStack.CODEC` + Gson，作者明确要求**不用 NBT**）；`/voidmaw warehouse` 开 54 格箱子界面；溢出转质量
- 核心交互（作者原话："右键引力核心开启/关闭，只有shift右键自爆"）：右键=开/安静关；Shift+右键=自爆（唯一引爆；超时/死亡均安静闭合，`/voidmaw detonate` 例外）
- 方块与生物掉落走原版掉落表（注入钻石镐作 TOOL，石类保持掉落），全部入库
- 配方 JSON（1.21.2+）：配料是纯字符串（`"O": "minecraft:obsidian"`），结果为 `{"id": ..., "count": ...}`。
- 客户端 HUD 用新 API `HudElement` + `HudElementRegistry`（1.21.6+），文字颜色必须带 alpha（如 `0xFFDDB0FF`）。
- 世界渲染走 `WorldRenderEvents.END_MAIN`（新包 `...rendering.v1.world`），球体用 `RenderLayer.getDebugQuads()`（禁 cull 的 position-color 层）。
- 全程零 Mixin。
