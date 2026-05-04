# Frontleaves Territory — 领地 Mod 设计文档

> NeoForge 1.21.1 双端 Mod（服务端 + 客户端强制安装）

## 一、架构总览

```
┌─ 客户端 Mod (玩家安装) ─────────────────────┐
│                                              │
│  自定义选区物品 (territory_wand)              │
│  ├── 注册新物品，有自定义模型和贴图           │
│  ├── 左键/右键选点，客户端 UI 反馈            │
│  └── 3D 渲染选区边界（自定义 Renderer）       │
│                                              │
│  不含业务逻辑                                │
│  不连数据库 / gRPC                           │
│                                              │
└──────────────────────────────────────────────┘
                    ↕ 网络包同步 ↕
┌─ 服务端 Mod (服务器安装) ───────────────────┐
│                                              │
│  领地核心逻辑                                │
│  ├── 选区合法性校验（包含、重叠、面积）       │
│  ├── 直角多边形几何算法                       │
│  ├── 父子领地层级管理                         │
│  └── 权限 Flag 系统                          │
│                                              │
│  事件拦截 (HIGHEST 优先级)                    │
│  ├── BlockEvent.BreakEvent                   │
│  ├── BlockEvent.PlaceEvent                   │
│  ├── EntityBlockPlaceEvent                   │
│  ├── PlayerInteractEvent.RightClickBlock     │
│  ├── ExplosionEvent.Start / Detonate         │
│  ├── EntityChangeBlockEvent                  │
│  └── BlockEvent.FluidPlaceBlockEvent         │
│                                              │
│  MySQL (HikariCP + 异步 ExecutorService)      │
│  ├── territories 表                          │
│  ├── territory_members 表                    │
│  ├── territory_flags 表                      │
│  └── territory_vertices 表                   │
│                                              │
│  gRPC client (grpc-netty-shaded) → Go 后端   │
│  ├── 领地变更事件上报（审计）                  │
│  └── Web 管理面板数据同步                     │
│                                              │
│  日志 + OP 通知                               │
│  ├── Console: LOGGER.info()                  │
│  └── OP: ServerPlayer.sendSystemMessage()    │
│                                              │
│  命令系统                                    │
│  ├── /territory create <name>                │
│  ├── /territory delete <name>                │
│  ├── /territory sub <parent> <name>          │
│  ├── /territory member add/remove <player>   │
│  ├── /territory flag set <flag> <value>      │
│  ├── /territory info [name]                  │
│  └── /territory list                         │
│                                              │
└──────────────────────────────────────────────┘
                    ↕ gRPC ↕
┌─ Go 后端 (辅助角色) ────────────────────────┐
│  gRPC TerritoryService                       │
│  ├── 接收领地变更事件（审计/通知）             │
│  └── RESTful API → Web 管理面板              │
│                                              │
│  PostgreSQL (Go 自用)                         │
│  └── 审计日志 / 用户系统                      │
└──────────────────────────────────────────────┘
```

## 二、技术选型

### 2.1 构建工具

| 项目 | 方案 |
|---|---|
| 构建系统 | Gradle + ModDevGradle 插件 |
| NeoForge 版本 | MC 1.21.1 → NeoForge 21.1.x |
| Java 版本 | 21 |
| 外部库打包 | `jarJar`（NeoForge 原生方案，不用 shadow） |
| 双端分发 | 同一个 jar，`mods.toml` 中 `side = "BOTH"`，客户端强制安装 |

### 2.2 数据库

| 项目 | 方案 |
|---|---|
| 数据库 | MySQL |
| 驱动 | `mysql-connector-j 9.x`（支持 Java 21） |
| 连接池 | HikariCP 7.0.2 |
| 连接池大小 | 10-20（领地查询为简单 SQL） |
| 线程模型 | 绝不在主线程执行 JDBC，使用 `CompletableFuture` + 专用 `ExecutorService` |

**HikariCP 关键配置：**
```java
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setConnectionTimeout(30000);
config.setIdleTimeout(600000);
config.setMaxLifetime(1800000);
config.setLeakDetectionThreshold(30000);
// MySQL 优化
config.addDataSourceProperty("cachePrepStmts", "true");
config.addDataSourceProperty("prepStmtCacheSize", "250");
config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
config.addDataSourceProperty("useServerPrepStmts", "true");
config.addDataSourceProperty("rewriteBatchedStatements", "true");
```

### 2.3 gRPC

| 项目 | 方案 |
|---|---|
| Transport | `grpc-netty-shaded`（**禁止**使用 `grpc-netty`） |
| 版本 | 1.62.2+ |
| 为什么用 shaded | Netty 类重定位到 `io.grpc.netty.shaded.*`，与 NeoForge 嵌入的 Netty 零冲突 |
| 备选方案 | `grpc-okhttp`（完全不依赖 Netty，更轻量，性能略低） |

### 2.4 性能关键路径

```
BlockEvent.BreakEvent (主线程)
  → 查内存缓存 (O(1) chunk → territory 映射)     ← 微秒级
  → 命中领地？查内存中的 flag 缓存               ← 微秒级
  → 全部缓存命中：不阻塞，直接 allow/deny
  → 缓存未命中：标记为 allow，异步查库更新缓存     ← 不阻塞主线程
```

**核心原则**：热数据全在内存，MySQL 只做持久化和冷启动加载。

---

## 三、领地形状 — 直角多边形

### 3.1 存储方式

顶点序列按顺时针排列，所有边平行于 X 或 Z 轴。

```
v4 ─────── v3
│          │
│   v7 ── v6       支持凹多边形（L 型、U 型等）
│   │      │
│   v8 ── v5
│
v1 ─────── v2
```

### 3.2 核心算法

| 算法 | 用途 | 复杂度 |
|---|---|---|
| 射线法 (Ray Casting) | 判断方块是否在领地内 | O(n)，n = 顶点数 |
| Shoelace 公式 | 面积计算 | O(n) |
| 包含检测 | 子领地校验：所有顶点必须在父领地内部 | O(n²) |
| 边界相交检测 | 同级领地重叠校验 | O(n²) |
| AABB 粗筛 | 先用包围盒过滤，再做精确多边形检测 | O(1) |

### 3.3 数据表设计

```sql
-- 领地表
CREATE TABLE territories (
    id              BIGINT PRIMARY KEY,          -- Snowflake ID
    name            VARCHAR(64) NOT NULL,        -- 领地名称（同级唯一）
    owner_uuid      CHAR(36) NOT NULL,           -- 所有者 UUID
    parent_id       BIGINT NULL,                 -- 父领地 ID (NULL = 顶级)
    world_name      VARCHAR(64) NOT NULL,        -- 所在世界
    min_y           SMALLINT NOT NULL DEFAULT -64,
    max_y           SMALLINT NOT NULL DEFAULT 320,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES territories(id)
);

-- 顶点表（顺时针排列）
CREATE TABLE territory_vertices (
    id              BIGINT PRIMARY KEY,
    territory_id    BIGINT NOT NULL,
    sort_order      SMALLINT NOT NULL,           -- 顶点顺序
    x               INT NOT NULL,
    z               INT NOT NULL,
    FOREIGN KEY (territory_id) REFERENCES territories(id) ON DELETE CASCADE
);

-- 成员表
CREATE TABLE territory_members (
    id              BIGINT PRIMARY KEY,
    territory_id    BIGINT NOT NULL,
    player_uuid     CHAR(36) NOT NULL,
    role            ENUM('owner','admin','member') NOT NULL DEFAULT 'member',
    FOREIGN KEY (territory_id) REFERENCES territories(id) ON DELETE CASCADE
);

-- 权限标记表
CREATE TABLE territory_flags (
    id              BIGINT PRIMARY KEY,
    territory_id    BIGINT NOT NULL,
    flag_name       VARCHAR(32) NOT NULL,        -- break / place / interact / ...
    target_type     ENUM('role','player','group') NOT NULL,
    target_id       VARCHAR(64) NOT NULL,        -- 角色名/UUID/组名
    value           ENUM('allow','deny','inherit') NOT NULL,
    FOREIGN KEY (territory_id) REFERENCES territories(id) ON DELETE CASCADE
);
```

---

## 四、父子领地规则

```
┌─ 主城 (Root) ─────────────────────┐
│                                    │
│  ┌─ 商业区 ────┐  ┌─ 住宅区 ───┐  │
│  │             │  │            │  │
│  │ ┌─ 玩家A ┐ │  │ ┌─ 玩家B ┐ │  │
│  │ │  的店  │ │  │ │  的家  │ │  │
│  │ └────────┘ │  │ └────────┘ │  │
│  └─────────────┘  └────────────┘  │
└────────────────────────────────────┘
```

| 规则 | 说明 |
|---|---|
| 子领地必须完全在父领地内 | 创建时校验所有顶点的包含关系 |
| 同级领地不能重叠 | 同一父领地下的子领地互不相交 |
| 深度限制 | 建议限制最大嵌套层级 3-4 层 |
| 权限继承 | 子领地默认继承父领地权限，可按需覆盖 |
| 继承优先级 | 自己的权限 > 父领地 > 祖父领地 > ... > 世界默认 |

---

## 五、权限 Flag 系统

### 5.1 Flag 清单

| 类别 | Flag | 默认 | 说明 |
|---|---|---|---|
| 方块 | `block.break` | deny | 破坏方块 |
| 方块 | `block.place` | deny | 放置方块 |
| 方块 | `block.interact` | deny | 交互（门、箱子等） |
| 实体 | `entity.damage` | allow | 受到伤害 |
| 实体 | `entity.spawn` | deny | 生物生成 |
| 移动 | `player.enter` | allow | 进入领地 |
| 移动 | `player.teleport` | deny | 传送进入 |
| 杂项 | `pvp` | deny | PVP |
| 杂项 | `explosion` | deny | 爆炸破坏 |
| 杂项 | `fire_spread` | deny | 火焰蔓延 |
| 杂项 | `bucket.use` | deny | 使用桶 |

### 5.2 权限解析优先级（从高到低）

```
玩家级 Flag → 角色级 Flag → 自己领地继承 → 父领地继承 → 世界默认
```

---

## 六、NeoForge 事件拦截

### 6.1 监听的事件

| 事件 | 用途 | 可取消 | 取消方式 |
|---|---|---|---|
| `BlockEvent.BreakEvent` | 玩家破坏 | ✅ | `setCanceled(true)` |
| `BlockEvent.PlaceEvent` | 玩家放置 | ✅ | `setCanceled(true)` |
| `EntityBlockPlaceEvent` | 铁傀儡等实体放置 | ✅ | `setCanceled(true)` |
| `PlayerInteractEvent.RightClickBlock` | 右键交互 | ✅ | `setCanceled(true)` + `setCancellationResult(FAIL)` |
| `ExplosionEvent.Start` | 爆炸取消 | ✅ | `setCanceled(true)` |
| `ExplosionEvent.Detonate` | 移除受保护方块 | ✅ | 从 `getAffectedBlocks()` 中 removeIf |
| `EntityChangeBlockEvent` | 末影人搬运/僵尸破门 | ✅ | `setCanceled(true)` |
| `BlockEvent.FluidPlaceBlockEvent` | 流体流动 | ✅ | `setCanceled(true)` |

### 6.2 事件优先级

- **领地拦截**：`EventPriority.HIGHEST` — 确保在破坏发生前检查
- **日志记录**：`EventPriority.MONITOR` — 不干扰事件处理链

---

## 七、客户端 Mod

### 7.1 选区物品

- 物品 ID：`territory_wand`
- 自定义模型和贴图（Blockbench 制作）
- 父级模型：`minecraft:item/handheld`
- 客户端必须安装才能进服

### 7.2 选区工具交互

| 操作 | 行为 |
|---|---|
| 左键方块 | 添加一个顶点 |
| 右键方块 | 闭合多边形（完成选区） |
| Shift + 右键 | 清除当前选区 |

### 7.3 渲染

- 客户端自定义 Renderer 渲染选区边界
- 不使用服务端粒子包
- 实时预览当前已选顶点构成的直角多边形

### 7.4 网络包

客户端选点后通过 NeoForge 网络包发送坐标给服务端校验，服务端返回领地信息给客户端渲染。

---

## 八、Gradle 依赖配置

```gradle
dependencies {
    // NeoForge
    implementation "net.neoforged:neoforge:${neo_version}"

    // MySQL (jarJar 打包)
    jarJar 'com.mysql:mysql-connector-j:9.0.0'
    jarJar 'com.zaxxer:HikariCP:7.0.2'

    // gRPC (使用 shaded 避免 Netty 冲突)
    jarJar 'io.grpc:grpc-netty-shaded:1.62.2'
    jarJar 'io.grpc:grpc-protobuf:1.62.2'
    jarJar 'io.grpc:grpc-stub:1.62.2'
    implementation 'io.grpc:grpc-api:1.62.2'
    implementation 'io.grpc:grpc-context:1.62.2'
    implementation 'com.google.protobuf:protobuf-java:4.26.1'
}
```

---

## 九、开发阶段

| 阶段 | 内容 | 状态 |
|---|---|---|
| **Phase 0** | Bukkit 插件端破坏监听（已完成，将被丢弃） | ✅ 已完成 |
| **Phase 1** | gRPC proto 定义 + Go 后端领地 CRUD | 待开始 |
| **Phase 2** | NeoForge Mod 项目搭建 + 选区物品 + 选区工具 | 待开始 |
| **Phase 3** | MySQL 数据层 + 直角多边形算法 | 待开始 |
| **Phase 4** | 事件拦截 + 权限检查 | 待开始 |
| **Phase 5** | 子领地 + 权限继承 | 待开始 |
| **Phase 6** | 客户端选区渲染 | 待开始 |
| **Phase 7** | Web 管理面板 + 地图可视化 | 待开始 |
| **Phase 8** | 高级功能（领地传送、租售、war） | 待开始 |

---

## 十、原版资源参考

原版木棍文件已导出至 `~/Downloads/mc-stick/`：

```
mc-stick/
├── stick_model.json       ← 父级 handheld，指向 stick 贴图
└── stick_texture.png      ← 16×16 原版木棍贴图
```

模型文件：
```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "minecraft:item/stick"
  }
}
```

选区工具贴图可在 Blockbench 中基于此文件改色加发光效果制作。

---

*文档版本 1.0.0 · 2026-05-05 · xiao_lfeng*
