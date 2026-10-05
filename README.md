# Void Maw 虚空之喉

Minecraft 1.21.10 Fabric 功能模组：使用**奇点核心**化身成黑洞，不断吞噬上方的生物、掉落物与方块。吞得越多，黑洞越大、引力越强、持续时间越长；当虚空之喉闭合时，积累的质量化作一场规模成正比的能量爆发轰然释放。

- **Mod ID**：`voidmaw`
- **作者**：novapupil
- **License**：MIT
- **仓库**：GitHub `Wmsqaq/voidmaw-mod` · Gitee `novapupil/voidmaw-mod`（一次 push 双仓库）

## 玩法

| 阶段 | 行为 |
|---|---|
| 触发 | 手持**奇点核心**右键 → 化身黑洞；再次潜行 + 右键（或等时间耗尽）→ 闭合释放爆炸 |
| 引力 | 以上方为主的球形区域内，生物/掉落物/经验球被持续拉向核心，越近吸力越强 |
| 吞噬 | 靠近核心 1.4 格内的实体直接湮灭（无掉落）；上方方块按随机抽样逐步吞吃 |
| 成长 | 每点质量使引力半径增长（公式 `3 + √质量×1.2`，上限 16 格），并延长变身时间 |
| 闭合 | 爆炸威力 `2 + 质量×0.1`（上限 6 级，TNT 为 4 级），黑洞本体免疫 |
| 漂浮 | 变身期间缓慢移动、缓降免摔伤 |

- 基岩、屏障、强化深板岩、传送门方块、命令方块等不可吞噬（数据包标签 `#voidmaw:unswallowable`，可自行扩展）。
- **进服礼包**：每位玩家首次进入世界时自动获得 1 个奇点核心（按世界持久化记录仅发一次，重进不重复发；`Balance.GIVE_CORE_ON_FIRST_JOIN` 可关闭）。
- 黑曜石类高硬度方块需要质量 ≥ 30 才能咀嚼。
- 不会吞噬任何玩家（自己与他人）。
- 所有数值集中在 `Balance.java`，改一处即可重新调平。

### 合成

```
黑曜石  末影珍珠  黑曜石
末影珍珠  末影之眼  末影珍珠
黑曜石  末影珍珠  黑曜石
```

### 指令（OP 等级 2）

- `/voidmaw start` — 化身黑洞
- `/voidmaw stop` — 立即闭合并释放能量
- `/voidmaw status` — 查看质量 / 半径 / 剩余时间

## 构建

依赖 JDK 21+ 与 Gradle（项目自带 wrapper 9.5.1，Gradle 缓存走 `D:/.gradle`，插件仓库已配置阿里云镜像优先）：

```bash
gradlew build        # 产物：build/libs/voidmaw-<版本>.jar
gradlew runClient    # 开发环境进游戏实测
```

贴图由脚本生成（纯 Python 标准库，无第三方依赖）：`python tools/gen_textures.py`

## 发布流程

1. 推送 tag（`v1.0.0-beta.1` 格式，tag 含 beta/alpha 会自动标记为预发布）→ GitHub Actions 自动构建并创建 GitHub Release（`.github/workflows/release.yml`）。
2. 本地运行 `bash tools/release-gitee.sh v1.0.0` 把 Release 镜像到 Gitee 并附上 jar（token 放在仓库根的 `.gitee-token`，已被 gitignore；Gitee 屏蔽云机房 IP，因此该步骤必须在本地跑）。

## 致谢 / 参考

- 工程模板与发布流程借鉴同作者工作区的 `killstreak-mod` / `radkeyboard`。
- 引力吸取与"吞吃→成长"机制参考了吸取类（Vacuum Hopper / magnet 类）与黑洞生长类开源模组的思路；全部代码为本仓库原创实现。
- 依赖：[Fabric API](https://modrinth.com/mod/fabric-api)。零 Mixin，全部通过 Fabric API 事件实现，兼容性友好。
