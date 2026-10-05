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
- 配方 JSON（1.21.2+）：配料是纯字符串（`"O": "minecraft:obsidian"`），结果为 `{"id": ..., "count": ...}`。
- 客户端 HUD 用新 API `HudElement` + `HudElementRegistry`（1.21.6+），文字颜色必须带 alpha（如 `0xFFDDB0FF`）。
- 世界渲染走 `WorldRenderEvents.END_MAIN`（新包 `...rendering.v1.world`），球体用 `RenderLayer.getDebugQuads()`（禁 cull 的 position-color 层）。
- 全程零 Mixin。
