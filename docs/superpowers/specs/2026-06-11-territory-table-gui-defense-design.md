# 领地台 GUI 扩写 + 领地防御功能 设计规格

## 概述

为领地模组（NeoForge 1.21）添加两大核心功能：
1. **领地台 GUI 管理** — 已绑定的领地台右键打开 Container Menu GUI，提供可视化的领地管理界面
2. **领地防御系统** — 三层事件拦截器，覆盖原版 + 模组（机械动力/航空学）的全面防护

## 背景与动机

模组服务器中 Bukkit 插件无法识别模组产生的破坏行为（机械动力齿轮/滑轮/部署器/移动装置等），因此需要模组版本的原生领地防护。领地台作为模组的优势，可以提供 GUI 管理界面取代传统指令操作。

## 设计决策汇总

| # | 决策项 | 选择 | 理由 |
|---|--------|------|------|
| 1 | 防护范围 | 全面防护（参考 Residence 90+ 标志位） | 模组服务器需要完整覆盖 |
| 2 | 传送点 | 不在领地台，属于领地之书 | 已有 TerritoryBookItem 实现 |
| 3 | 标志位设计 | 两层：外层分类聚合 + 内层精细可调 | 平衡简洁与灵活 |
| 4 | 成员管理 | 角色模板（owner/admin/member/visitor）+ 可选个人覆盖 | 90% 场景用角色，10% 特殊用覆盖 |
| 5 | GUI 方案 | 原生 Container GUI（MenuType） | MC 原生风格，自动 C/S 同步 |
| 6 | 领地台交互 | 状态自动判断：未绑定→创建 / 已绑定→管理 GUI | 简洁直观 |
| 7 | 拆除规则 | 仅创建者可破坏 → 删除领地数据 | 安全保障 |
| 8 | 外人访问 GUI | 只读信息（名称、拥有者、面积） | 透明但不暴露细节 |
| 9 | GUI 权限显示 | 角色分级显示不同功能层级 | owner 全部 / admin 部分 / member 查看 / 外人只读 |
| 10 | 拦截提示 | Actionbar 提示 | 不阻塞游戏体验但明确告知 |
| 11 | GUI 菜单 | 6 个入口：信息/成员/权限/设置/日志/管理员 | 覆盖所有管理需求 |
| 12 | 模组兼容 | L1 通用 + L2 FakePlayer + L3 Create Mixin（含航空学） | 完整覆盖所有模组破坏 |
| 13 | 模组方块分类 | 模组方块注册表（自动扫描 + 手动配置） | 精确匹配非原版交互方块 |

---

## 1. 标志位与角色系统

### 1.1 标志位枚举（FlagType + FlagCategory）

6 大分类，每类下含子标志位：

**BUILD（建筑）**
- `build` — 建造/破坏（宏标志，覆盖 destroy + place）
- `destroy` — 仅破坏
- `place` — 仅放置
- `piston` — 活塞推动

**CONTAINER（容器）**
- `container` — 所有容器访问（箱子/熔炉/漏斗/模组容器，通过注册表匹配）

**INTERACT（交互）**
- `interact` — 交互宏标志（覆盖以下全部 + 注册表中的模组交互方块）
- `button` — 按钮
- `lever` — 拉杆
- `door` — 门/活板门
- `pressure` — 压力板
- `redstone` — 红石中继器/比较器
- `craft` — 工作台/附魔台/酿造台/铁砧
- `bed` — 床

**ENVIRONMENT（爆炸与环境）**
- `explosion` — 通用爆炸（宏标志）
- `tnt` — TNT
- `creeper` — 苦力怕
- `fire` — 点火
- `firespread` — 火焰蔓延（⚠️ NeoForge 1.21 无专用事件，需通过 BlockEvent.FluidPlaceBlockEvent 或 BlockEntity tick 间接处理）
- `flow` — 液体流动（宏标志，覆盖 waterflow + lavaflow）
- `waterflow` — 水流（通过 BlockEvent.FluidPlaceBlockEvent 拦截）
- `lavaflow` — 岩浆流（通过 BlockEvent.FluidPlaceBlockEvent 拦截）
- `trample` — 作物踩踏
- `decay` — 树叶腐烂

**ENTITY（实体与生物）**
- `damage` — 实体伤害（宏标志，通过 LivingEvent/LivingHurtEvent 拦截）
- `animal` — 动物生成（通过 EntityJoinLevelEvent 拦截，过滤动物类型）
- `monster` — 怪物生成（通过 EntityJoinLevelEvent 拦截，过滤怪物类型）
- `animalkilling` — 杀死动物
- `mobkilling` — 杀死怪物
- `riding` — 骑乘

**SPECIAL（特殊）**
- `move` — 移动
- `pvp` — PVP
- `enderpearl` — 末影珍珠
- `vehicledestroy` — 破坏载具
- `itemdrop` — 丢弃物品
- `itempickup` — 拾取物品

### 1.2 角色模板（TerritoryRole）

| 角色 | 建筑权限 | 容器 | 交互 | 爆炸环境 | 实体 | 特殊 |
|------|---------|------|------|---------|------|------|
| OWNER | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| ADMIN | ✅ | ✅ | ✅ | ❌ | ✅ | 部分 |
| MEMBER | ✅ | ❌ | ✅ | ❌ | ❌ | 部分 |
| VISITOR | ❌ | ❌ | ❌ | ❌ | ❌ | 仅 move |

**权限检查优先级**: 个人覆盖 > 角色模板 > 领地全局 flag > 默认值

### 1.3 模组方块注册表（ModBlockRegistry）

```
自动扫描（启动时）:
  instanceof BaseRailBlock → RAIL
  instanceof ButtonBlock → BUTTON
  instanceof LeverBlock → LEVER
  instanceof DoorBlock → DOOR
  instanceof Container → CONTAINER

手动配置（territory-modblocks.toml）:
  create:controller_lever = "lever"
  create_aeronautics:throttle_lever = "lever"
  create:speed_controller = "interact"
  create:mechanical_drill = "build"
  ...

查询接口: getBlockCategory(ResourceLocation) → FlagCategory
```

---

## 2. 领地台 GUI 系统

### 2.1 交互逻辑

```
领地台右键点击:
  ├── 未绑定（territoryUuid == null）
  │   └── 检查选区 → 创建领地 → 绑定 UUID（现有行为不变）
  └── 已绑定（territoryUuid != null）
      └── 打开 Container Menu GUI

领地台破坏:
  ├── 未绑定 → 掉落物品
  └── 已绑定 → 仅创建者可破坏 → 删除领地 + 掉落物品
               └── 非创建者 → 阻止破坏
```

### 2.2 GUI 主菜单结构

```
┌─────────────────────────────────────┐
│  ⬡ 领地名称                          │
│  世界 · 面积 · 成员数                  │
├─────────┬─────────┬─────────────────┤
│  📋      │  👥      │  🛡️             │
│ 领地信息 │ 成员管理 │ 权限配置         │
├─────────┼─────────┼─────────────────┤
│  🔧      │  📜      │  ⚙️             │
│ 领地设置 │ 日志记录 │ 管理员设置       │
└─────────┴─────────┴─────────────────┘
```

### 2.3 页面导航

```
主菜单
├── 📋 领地信息页 — 名称/坐标/面积/创建时间（拥有者可改名）
├── 👥 成员管理页
│   ├── 添加成员（输入玩家名）
│   ├── 移除成员
│   ├── 角色分配（下拉选择）
│   └── 个人覆盖（点击 ⚙️ 展开细调）
├── 🛡️ 权限配置页
│   ├── 第一层：6 分类卡片（点击切换允许/拒绝）
│   └── 第二层：展开分类 → 子标志位列表（单独控制）
├── 🔧 领地设置页 — 重命名等杂项
├── 📜 日志记录页 — 最近操作日志
└── ⚙️ 管理员设置页（仅 owner/admin 可见）
    ├── 删除领地
    └── 转让拥有者
```

### 2.4 GUI 权限矩阵

| 身份 | 打开 GUI | 可见内容 | 修改操作 |
|------|---------|---------|---------|
| OWNER | ✅ | 全部功能 | 全部 |
| ADMIN | ✅ | 成员管理 + 权限配置 + 信息 + 日志 | 部分 |
| MEMBER | ✅ | 信息 + 自身权限查看 | 仅查看 |
| 外人 | ✅ | 只读：名称、拥有者、面积 | ❌ |

### 2.5 网络包

- **C→S**: `TerritoryGuiActionPayload` — GUI 操作请求（territoryUuid, actionType, targetData）
- **S→C**: `TerritoryGuiSyncPayload` — GUI 数据同步（territoryUuid, pageType, pageData）

---

## 3. 防御系统（三层拦截器）

> 以下所有事件类名已通过 NeoForge 1.21 (26.1.x) 和 Create (mc1.21.1/dev) GitHub 源码验证。

### 3.1 L1 通用拦截 — NeoForge 标准事件（✅ 已验证）

| 事件类 | 完整包路径 | 可取消 | 对应 flag | 验证来源 |
|--------|-----------|--------|-----------|---------|
| `BlockEvent.BreakEvent` | `net.neoforged.neoforge.event.level.BlockEvent` | ✅ (`ICancellableEvent`) | build/destroy | NeoForge BlockEvent.java |
| `BlockEvent.EntityPlaceEvent` | `net.neoforged.neoforge.event.level.BlockEvent` | ✅ (`ICancellableEvent`) | build/place | NeoForge BlockEvent.java |
| `BlockEvent.FluidPlaceBlockEvent` | `net.neoforged.neoforge.event.level.BlockEvent` | ✅ (`ICancellableEvent`) | flow/waterflow/lavaflow | NeoForge BlockEvent.java |
| `PlayerInteractEvent.RightClickBlock` | `net.neoforged.neoforge.event.entity.player.PlayerInteractEvent` | ✅ (`ICancellableEvent`) | 查注册表匹配 | NeoForge PlayerInteractEvent.java |
| `PlayerInteractEvent.LeftClickBlock` | 同上 | ✅ (`ICancellableEvent`) | build/destroy | 同上 |
| `PlayerInteractEvent.EntityInteract` | 同上 | ✅ (`ICancellableEvent`) | damage/riding | 同上 |
| `ExplosionEvent.Start` | `net.neoforged.neoforge.event.level.ExplosionEvent` | ✅ (`ICancellableEvent`) | explosion | NeoForge ExplosionEvent.java |
| `ExplosionEvent.Detonate` | 同上 | 修改 affectedBlocks | explosion（过滤领地方块） | 同上 |
| `EntityJoinLevelEvent` | `net.neoforged.neoforge.event.entity` | ✅ (`ICancellableEvent`) | animal/monster（过滤实体类型） | NeoForge EntityJoinLevelEvent.java |

**⚠️ 不存在的事件（已验证不存在于 NeoForge 1.21）：**
- ❌ `LivingAttackEvent` — 不存在。实体伤害需通过 `LivingHurtEvent` 或类似事件处理，实施时需进一步确认具体类名
- ❌ `FireSpreadEvent` — 不存在。火焰蔓延需通过替代方案处理（如监听 BlockEvent 或配置方块免疫）

### 3.2 L2 FakePlayer 检测（✅ 已验证）

- **FakePlayer 类**: `net.neoforged.neoforge.common.util.FakePlayer` extends `ServerPlayer`
- **检测方法**: `entity instanceof FakePlayer` 或 `player.isFake()` 返回 `true`
- **Create 专项**: `instanceof DeployerFakePlayer`（见下方）
- **DeployerFakePlayer**: `com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer`
  - extends `FakePlayer`
  - 有 `owner` (UUID) 字段，可追踪放置者
  - 有 `onMinecartContraption` (boolean) 字段，标识是否在矿车装置上

**处理逻辑**:
- Deployer 在领地内操作领地外 → 检查领地边界
- Deployer 在领地外操作领地内 → 拦截
- Deployer 跨领地操作 → 检查双方权限

### 3.3 L3 Create Mixin 适配（✅ 已验证）

**Mixin 目标类（已在 Create mc1.21.1/dev 分支源码中确认）**:

| 类 | 完整包路径 | 说明 |
|----|-----------|------|
| `BlockBreakingMovementBehaviour` | `com.simibubi.create.content.kinetics.base` | 移动装置破坏基类（⚠️ 英式拼写 Behaviour） |
| `DrillMovementBehaviour` | `com.simibubi.create.content.kinetics.drill` | 机械钻 |
| `SawMovementBehaviour` | `com.simibubi.create.content.kinetics.saw` | 机械锯 |
| `PloughMovementBehaviour` | `com.simibubi.create.content.contraptions.actors.plough` | 机械犁 |
| `RollerMovementBehaviour` | `com.simibubi.create.content.contraptions.actors.roller` | 压路机 |
| `ContraptionCollider` | `com.simibubi.create.content.contraptions` | 碰撞检测，调用 canBreak() |
| `DeployerFakePlayer` | `com.simibubi.create.content.kinetics.deployer` | 有 owner UUID 字段 |
| `MovementBehaviour` (接口) | `com.simibubi.create.api.behaviour.movement` | 移动行为接口 |

**Mixin 策略**: 
- 注入 `BlockBreakingMovementBehaviour` 的 `visitNewPosition` 或 `canBreak` 方法
- 在破坏前检查目标位置的领地权限
- 通过 `MovementContext.blockEntityData.getUUID("Owner")` 追踪装置所有者

### 3.4 权限检查核心流程

```
操作事件触发
  → TerritoryDataManager.findTerritoryAt(worldKey, x, y, z)
  → 找到领地？
      ├── 否 → 放行
      └── 是 → 判断操作者身份
          ├── OWNER → 放行
          ├── 成员 → 查角色模板 flag → 有个人覆盖？→ 个人覆盖优先
          └── 外人 → 查全局默认 flag
              → ALLOW → 放行
              → DENY  → cancel + Actionbar 提示
```

### 3.5 拦截提示消息（i18n）

| key | 消息 |
|-----|------|
| `territory.defend.block_break` | ⚠ 你没有权限在这里破坏方块 |
| `territory.defend.block_place` | ⚠ 你没有权限在这里放置方块 |
| `territory.defend.container` | ⚠ 你没有权限访问这里的容器 |
| `territory.defend.interact` | ⚠ 你没有权限与这里交互 |
| `territory.defend.explosion` | ⚠ 爆炸被领地防护拦截 |
| `territory.defend.pvp` | ⚠ 这里禁止 PVP |
| `territory.defend.damage` | ⚠ 你没有权限伤害这里的实体 |
| `territory.defend.flow` | ⚠ 液体流动被领地防护拦截 |

---

## 4. 数据模型扩展

### 4.1 TerritoryData 扩展

现有字段需扩展:
- `flags: Map<String, String>` → 改为 `Map<FlagType, Boolean>`，初始化时设置默认值
- `members: List<MemberEntry>` → 扩展 MemberEntry，增加 `personalFlags` 字段
- 新增 `logs: List<TerritoryLogEntry>` 字段（操作日志）

### 4.2 新增数据类

```
FlagType (enum) — 标志位类型，含 category 属性
FlagCategory (enum) — 6 大分类
TerritoryRole (enum) — 4 个角色模板
RoleTemplate — 角色对应的默认 flag 映射
ModBlockRegistry — 模组方块注册表
TerritoryLogEntry — 操作日志条目（playerUuid, action, timestamp, detail）
```

---

## 5. 需要新建的文件

### GUI 系统
- `TerritoryTableMenu.java` — Container Menu（服务端）
- `TerritoryTableScreen.java` — Container Screen（客户端渲染）
- `TerritoryGuiActionPayload.java` — C→S 网络包
- `TerritoryGuiSyncPayload.java` — S→C 网络包

### 防御系统
- `TerritoryDefenseHandler.java` — L1 通用事件拦截器
- `FakePlayerDefenseHandler.java` — L2 FakePlayer 检测
- `FlagType.java` — 标志位枚举
- `FlagCategory.java` — 分类枚举
- `TerritoryRole.java` — 角色枚举
- `RoleTemplate.java` — 角色模板默认值
- `ModBlockRegistry.java` — 模组方块注册表
- `TerritoryLogEntry.java` — 日志条目

### Create 兼容（Mixin）
- `mixin/BlockBreakingMovementBehaviourMixin.java` — 移动装置破坏拦截
- `mixin/ContraptionColliderMixin.java` — 碰撞拦截（航空学）
- `mixin/DeployerFakePlayerMixin.java` — 部署器拦截（如果 L2 不够用）

### 配置
- `territory-modblocks.toml` — 模组方块手动配置

## 6. 需要修改的文件

- `TerritoryData.java` — 扩展 flags 类型、MemberEntry、日志
- `TerritoryDataManager.java` — 新增权限检查方法、日志管理
- `TerritoryTableBlock.java` — 添加右键打开 GUI（MenuProvider）
- `TerritoryTableBlockEntity.java` — 可能需要扩展
- `TerritoryTableHandler.java` — 重写右键逻辑（状态判断）+ 拆除权限检查
- `Territory.java` — 注册 MenuType + 新网络包
- `TerritoryPayloads.java` — 注册新网络包

---

## 7. 范围边界

### IN
- 领地台 GUI 6 个管理页面
- 全面防御事件拦截器（L1 + L2 + L3）
- 两层标志位系统
- 角色模板 + 个人覆盖
- 模组方块注册表（自动扫描 + 手动配置）
- 领地操作日志
- Create + 航空学 Mixin 适配

### OUT
- 传送点管理（属领地之书）
- 子领地/嵌套领地
- 经济系统集成
- 地图渲染集成（DynMap/BlueMap）
- 领地间父子继承

---

## 8. 实施注意事项

### ⚠️ 需要实施时进一步确认的项目

1. **实体伤害事件** — `LivingAttackEvent` 在 NeoForge 1.21 中不存在，实施时需通过 `LivingHurtEvent` 或类似事件替代，需在开发时确认具体类名
2. **火焰蔓延** — NeoForge 1.21 无专用 `FireSpreadEvent`，需通过替代方案处理（可能需要 Mixin 到 fire block tick 逻辑，或使用配置方式设置方块免疫）
3. **Create Aeronautics 碰撞** — 航空学的物理碰撞代码需在实施时进一步分析 Simulated-Project 仓库
