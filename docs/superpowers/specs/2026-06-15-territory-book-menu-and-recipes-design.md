# 领地之书 Menu 化重构 + 合成表设计

**日期**: 2026-06-15
**状态**: 已批准（待实施）
**关联**: `2026-06-11-territory-table-gui-defense-design.md`（领地桌 GUI 与防御系统）

---

## 1. 目标

将领地之书从「纯客户端 `Screen` + 单次 payload 推送」重构为「`Menu`/`Container` 架构 + 双向同步」，与领地桌 GUI 视觉统一；同时更新合成表配方。

### 成功标准

1. 领地之书右键打开的 GUI 与领地桌同源（深色面板 + 标签 + 滚动列表视觉），底层走 `AbstractContainerMenu` + `TerritoryGuiSyncPayload`/`TerritoryGuiActionPayload` 双向同步。
2. 旧的单次推送管道（`TerritoryListResponsePayload`）被移除，无残留引用。
3. 合成表更新为：法杖=钻石-绿宝石-木棍纵向单列；领地台=讲台+法杖无序；领地之书=书+绿宝石无序。管理员物品仍不提供合成。
4. `./gradlew build`（或 `compileJava`）通过，无编译错误/LSP 报错。

---

## 2. 架构设计

### 2.1 方案选择

采用 **方案 A — 全新独立 Menu**：

- 新建 `TerritoryBookMenu extends AbstractContainerMenu`，职责单一（管理"我的/共享"领地列表），不污染领地桌 Menu。
- 新建 `TerritoryBookScreen extends AbstractContainerScreen<TerritoryBookMenu>`，视觉沿用领地桌范式。
- **复用**现有 `TerritoryGuiSyncPayload`（S→C）与 `TerritoryGuiActionPayload`（C→S），仅新增一个 `BOOK_LIST` pageType，几乎零新增网络代码。
- 传送操作复用领地桌已有的 `TELEPORT` action 分支（`handleGuiAction` 已有，领地之书直接走）。

**不选方案 B（塞进领地桌 Menu）**：领地桌 Menu 的角色权限/单领地数据模型与"列表展示所有领地"语义冲突，会让 Menu 职责模糊。
**不选方案 C（仅改视觉）**：不解决"统一为 Menu 架构"的核心诉求，数据同步仍是单次推送。

### 2.2 组件清单

**新增组件：**

| 组件 | 路径 | 职责 |
|---|---|---|
| `TerritoryBookMenu` | `gui/TerritoryBookMenu.java` | 服务端容器，无物品槽，持有玩家视角的 owned/shared 列表，提供 `syncPageData()`、`handleAction()` |
| `TerritoryBookMenuScreen` 工厂注册 | `client/TerritoryBookScreen.java`（重写） | 客户端渲染，`AbstractContainerScreen` 基类 |
| `TERRITORY_BOOK_MENU` MenuType | `Territory.java` | DeferredRegister 注册 |

**复用组件（零修改或极小修改）：**

| 组件 | 改动 |
|---|---|
| `TerritoryGuiSyncPayload` | 无改动；`pageType="BOOK_LIST"` 时 pageData 含 owned/shared 两个列表 |
| `TerritoryGuiActionPayload` | 无改动；传送 action `"TELEPORT"`（如不存在则复用领地桌的传送逻辑） |
| `handleGuiSync` (`TerritoryPayloads.java:543`) | 扩展：识别当前 screen 是 `TerritoryBookScreen` 时分发数据 |

**改造组件：**

| 组件 | 改动 |
|---|---|
| `TerritoryBookItem.use()` | 客户端发 `TerritoryBookOpenPayload`（不变）；服务端侧改为 `openMenu` |
| `handleBookOpen` (`TerritoryPayloads.java:162`) | 改为服务端 `serverPlayer.openMenu(new SimpleMenuProvider(...))` 打开 `TerritoryBookMenu`，构造时查询 owned/shared 并在打开后立即 push sync payload |
| `Territory.registerScreens()` (`Territory.java:173`) | 新增 `event.register(TERRITORY_BOOK_MENU.get(), TerritoryBookScreen::new)` |

**删除组件：**

| 组件 | 原因 |
|---|---|
| `TerritoryListResponsePayload`（含 `TerritoryEntry`） | 被 `TerritoryGuiSyncPayload(BOOK_LIST)` 取代 |
| `handleListResponse` (`TerritoryPayloads.java:191`) | 对应 handler 删除 |
| `TerritoryBookScreen.setTerritoryData()` | 旧的单次数据接收接口，改为 `receiveSyncData()` |

### 2.3 数据流

```
玩家右键领地之书
  → 客户端 TerritoryBookItem.use() 发 C→S TerritoryBookOpenPayload
  → 服务端 handleBookOpen:
      - 查询 owned/shared 列表（TerritoryDataManager）
      - serverPlayer.openMenu(SimpleMenuProvider((id, inv, p) ->
            new TerritoryBookMenu(id, inv, playerUuid), title))
      - openMenu 后立即 PacketDistributor.sendToPlayer(sync payload, BOOK_LIST, {...})
  → 客户端 TerritoryBookScreen 通过 handleGuiSync.receiveSyncData() 接收列表
  → 渲染深色面板 + 标签 + 滚动列表

玩家点击"传送"按钮
  → 客户端发 C→S TerritoryGuiActionPayload(TELEPORT, {territoryUuid})
  → 服务端 handleGuiAction → TELEPORT 分支执行传送 + 冷却 + 日志
```

### 2.4 数据编码（BOOK_LIST pageType）

`TerritoryGuiSyncPayload.pageData`（`Map<String, Object>`，值支持 String/Integer/Boolean）：

由于现有 sync payload 的 pageData 不直接支持嵌套列表，**领地之书采用单独的同步策略**：
- `TerritoryBookMenu` 在服务端构造时即持有 owned/shared 列表
- 通过 MenuProvider 的额外数据写入（`FriendlyByteBuf` 在 `writeClientData`/`openMenu` 时传递）传给客户端构造器
- 列表条目序列化为扁平字符串（`uuid|name|worldKey|area|hasSpawn`），客户端解析

> 备选：若 MenuProvider buffer 传列表过于复杂，则保留独立 `TerritoryBookListPayload`（取代 `TerritoryListResponsePayload`），仅服务端→客户端单向，由 `handleGuiSync` 路由分发。**实施时优先选 MenuProvider buffer 方式；若遇 StreamCodec 复杂度过高，回退到独立 payload。**（实施阶段决策点）

---

## 3. 视觉设计

### 3.1 布局规格（沿用领地桌常量）

```
imageWidth=260, imageHeight=200
面板背景 0xDD1A1A1A（深色半透明）
边框 0xFF555555（左/上高光）/ 0xFF333333（右/下阴影）
```

### 3.2 顶部标签栏（2 个）

| 标签 | 颜色 | 含义 |
|---|---|---|
| `OWNED` | `0xFF4A90D9`（蓝） | 我的领地 |
| `SHARED` | `0xFF50C878`（绿） | 共享领地 |

- 激活态：填色 + 底部白色指示线 `0xFFFFFFFF`
- 非激活：灰底 `0xFF3A3A3A`
- 标签宽 `TAB_WIDTH=40`，高 `TAB_HEIGHT=14`（同领地桌）

### 3.3 内容区

- **搜索框**：`EditBox`，宽 200，居中，标签下方
- **列表区**：每行高 25，最多 `MAX_VISIBLE_ROWS=12`，可滚动
- **每行结构**：
  - 左：领地名（白 `0xFFFFFF`）+ 第二行 `世界 | 面积 m²`（灰 `0xAAAAAA`）
  - 右：`[传送]` 绿色小按钮（hover `0xFF33AA33` / 常态 `0xFF228822`）
- **行 hover**：`0x44FFFFFF`（领地桌同款）
- **空列表**：居中显示 `gui.territory.book.empty.owned/shared/search` 提示

### 3.4 底部

左下角统计提示（灰 `0x888888`）：`共 N 个领地`（`gui.territory.book.stats`）。

### 3.5 与领地桌视觉一致性核对

| 元素 | 领地桌 | 领地之书（新） |
|---|---|---|
| 面板背景 | `0xDD1A1A1A` | 同 |
| 边框 | `0xFF555555`/`0xFF333333` | 同 |
| 标签激活态 | 填色 + 白底线 | 同 |
| 行 hover | `0x44FFFFFF` | 同 |
| 灰字辅助 | `0x888888` | 同 |
| 滚动 | `MAX_VISIBLE_ROWS=12` | 同 |

---

## 4. 合成表设计

全部通过 `ModRecipeProvider`（datagen）生成，修改 `data/ModRecipeProvider.java:25-45`。

### 4.1 领地工具（法杖）`TERRITORY_WAND` — 替换现有

```
|D| | |
|E| | |
|S| | |
```
- D = 钻石 `Items.DIAMOND`（上）
- E = 绿宝石 `Items.EMERALD`（中）
- S = 木棍 `Items.STICK`（下）
- 有序合成（`ShapedRecipeBuilder`），纵向单列
- 解锁条件：`has(DIAMOND)` 且 `has(EMERALD)`
- 类比钻石剑"两矿一棍"（钻石剑是 D-D-S 对角，此处 D-E-S 单列）

### 4.2 领地台 `TERRITORY_TABLE` — 替换现有

```
无序合成：讲台(LECTERN) + 领地工具(TERRITORY_WAND)
```
- `ShapelessRecipeBuilder`
- 解锁条件：`has(LECTERN)` 且 `has(TERRITORY_WAND)`
- 法杖作为材料被消耗

### 4.3 领地之书 `TERRITORY_BOOK` — 新增

```
无序合成：书(BOOK) + 绿宝石(EMERALD)
```
- `ShapelessRecipeBuilder`
- 解锁条件：`has(BOOK)` 且 `has(EMERALD)`

### 4.4 不变项

- `ADMIN_TERRITORY_WAND` / `ADMIN_TERRITORY_TABLE` **仍不提供合成**（管理员物品仅创造栏，保持现状）

### 4.5 合成链示意

```
玩家攒齐 钻石+绿宝石+木棍 → 合成领地工具(法杖)
        ├─ + 讲台 → 领地台
玩家单独：书+绿宝石 → 领地之书
```

---

## 5. 改动清单（落地映射）

| # | 改动 | 文件 | 类型 |
|---|---|---|---|
| 1 | 新建 `TerritoryBookMenu`（AbstractContainerMenu，无槽，owned/shared 列表 + syncPageData + handleAction） | `gui/TerritoryBookMenu.java` | 新增 |
| 2 | 重写 `TerritoryBookScreen`（AbstractContainerScreen，视觉对齐领地桌） | `client/TerritoryBookScreen.java` | 重写 |
| 3 | 注册 `TERRITORY_BOOK_MENU` MenuType + Screen 工厂 + `setMenuType` 注入 | `Territory.java` | 修改 |
| 4 | `TerritoryBookItem.use()` 改为发 C→S payload（客户端侧基本不变，确认即可） | `item/TerritoryBookItem.java` | 修改 |
| 5 | `handleBookOpen` 改为服务端 `openMenu` + 推送 BOOK_LIST sync | `network/TerritoryPayloads.java` | 修改 |
| 6 | `handleGuiSync` 扩展：识别 `TerritoryBookScreen` 时分发 | `network/TerritoryPayloads.java` | 修改 |
| 7 | 删除 `TerritoryListResponsePayload` 及 `handleListResponse`、注册 | `network/*` | 删除 |
| 8 | 更新配方 datagen（法杖/领地台/领地之书） | `data/ModRecipeProvider.java` | 修改 |
| 9 | 新增/更新语言键（`gui.territory.book.*`） | `lang/zh_cn.json` + `en_us.json` | 修改 |

---

## 6. 验证清单

- [ ] `./gradlew compileJava` 通过，无编译错误
- [ ] `./gradlew runData`（datagen）生成新配方 JSON，无报错
- [ ] 无对 `TerritoryListResponsePayload` 的残留引用（grep 验证）
- [ ] 领地之书右键打开的 GUI 视觉与领地桌一致（深色面板/标签/滚动）
- [ ] 我的领地/共享领地切换正常，搜索框过滤生效
- [ ] 传送按钮触发服务端传送（含冷却/权限校验）
- [ ] 三个配方在合成台可用：法杖(D/E/S)、领地台(讲台+法杖)、领地之书(书+绿宝石)
- [ ] 管理员法杖/管理员领地台仍无合成配方

---

## 7. 实施顺序（writing-plans 将细化）

1. **配方（独立、低风险）** — 改 `ModRecipeProvider`，datagen 验证
2. **网络层改造** — 删除旧 payload，扩展 sync 路由
3. **Menu + Screen** — 新建 Menu，重写 Screen
4. **主类注册** — MenuType + Screen 工厂
5. **编译验证** — `gradlew compileJava`，修复 LSP 报错
