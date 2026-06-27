# FastLoginPlus 登录流程

## 概述

玩家连入服务器时，FastLoginPlus 按以下顺序判断：

1. **按名字查库** → 区分老玩家（有记录）和新玩家（无记录）
2. **老玩家** → 按上次登录方式（正版/离线）直接处理
3. **新玩家** → 查 Mojang API 判断是否正版，再按配置决定处理方式
4. **Mojang 握手通过后** → ForceLogin 管理器负责 auth 插件的注册/登录和写库

## 流程图

```mermaid
flowchart TD
    A([玩家连入]) --> B{Bedrock 连接?}
    B -->|是| BR[Bedrock 处理流程]
    B -->|否| D["profile = loadProfile(username)<br/>按名字查 premium 表"]

    D --> E{profile == null?}
    E -->|是| ERR([数据库连接异常，忽略])
    E -->|否| G{"Floodgate 已迁移?<br/>(数据库 Floodgate 列是否有值)"}

    G -->|未迁移| J["设 Floodgate = FALSE<br/>(标记为 Java 用户)"]
    J --> K
    G -->|已迁移| H{Floodgate == TRUE?}
    H -->|是| SKIP([跳过: 库中标记为 Bedrock<br/>但本次不是 Bedrock 连接<br/>交给其他插件处理])
    H -->|否| K

    K["触发 PreLogin 事件<br/>设 lastIp = 当前 IP"] --> L{"isExistingPlayer?<br/>即 rowId ≥ 0?"}

    %% ===== 老玩家 =====
    L -->|是: 老玩家| M{"isOnlinemodePreferred?<br/>即 Premium 字段?"}
    M -->|true: 上次正版| N["requestPremiumLogin<br/>(profile, registered=true)"]
    M -->|false: 上次离线| O{Bedrock 前缀检测}
    O -->|非法| P([踢出])
    O -->|合法| Q["startCrackedSession()"]

    %% ===== 新玩家 =====
    L -->|否: 新玩家| R{"secondAttemptCracked 开启?<br/>且上次正版验证失败?"}
    R -->|是| Q
    R -->|否| S{"nameChangeCheck 或<br/>autoRegister 或<br/>offline-whitelist 开启?"}

    S -->|否| W{"premiumUUID = 空"}
    S -->|是| U["premiumUUID = 查 Mojang API(username)"]
    U --> W

    W -->|空: 不是正版| W1{"offline-whitelist 开启?"}
    W1 -->|是| KICK([踢出: 新离线玩家不被允许加入])
    W1 -->|否| Q

    %% ===== 正版处理 =====
    W -->|有值: 是正版| Z{"nameChangeCheck 开启?"}
    Z -->|是| Z1["按 UUID 查库:<br/>loadProfile(premiumUUID)"]
    Z1 --> Z2{旧记录存在?}
    Z2 -->|是| Z3["更新旧记录名字 = username"]
    Z3 --> Z4["requestPremiumLogin<br/>(旧记录, registered=false)"]
    Z2 -->|否: UUID 也没在库里| AA
    Z -->|否| AA

    AA{"autoRegister 开启?<br/>且 auth 插件中未注册?"}
    AA -->|是| AB["requestPremiumLogin<br/>(当前profile, registered=false)"]
    AA -->|否| AC{"offline-whitelist 开启?"}
    AC -->|是| AD["requestPremiumLogin<br/>(当前profile, registered=true)"]
    AC -->|否| W1

    %% ===== requestPremiumLogin =====
    N --> FL
    Z4 --> FL
    AB --> FL
    AD --> FL

    subgraph FL ["requestPremiumLogin"]
        direction TB
        F1["启用在线模式 (Mojang 握手)"]
        F1 --> F2["创建 LoginSession<br/>携带 profile 和 registered 标志"]
    end

    FL --> FLM

    %% ===== 离线登录 =====
    Q --> QLM

    subgraph QLM ["离线登录"]
        direction TB
        Q1["创建 LoginSession<br/>(registered=false)"]
        Q1 --> Q2["玩家进入服务器"]
        Q2 --> FLM
    end

    %% ===== ForceLoginManagement.run() =====
    subgraph FLM ["ForceLoginManagement.run()"]
        direction TB
        G1{玩家在线?}
        G1 -->|否| G2([退出])
        G1 -->|是| G3{session.isOnlineMode?}

        G3 -->|正版| G4{有 auth 插件?}
        G4 -->|无| G5["直接放行"]
        G4 -->|有| G6{autoLogin 开启?}
        G6 -->|否| G7["不做操作"]
        G6 -->|是| G8{"needsRegistration?<br/>即 registered==false?"}

        G8 -->|是| G9["forceRegister()<br/>生成随机密码 → auth 插件注册<br/>发消息告知玩家密码"]
        G8 -->|否| G10{"FastLoginAutoLoginEvent<br/>被取消?"}
        G10 -->|是| G7
        G10 -->|否| G11["forceLogin()<br/>auth 插件自动登录"]

        G3 -->|离线| G12["设 UUID=null, Premium=false"]
    end

    %% ===== 写库 =====
    G5 --> SAVE
    G7 --> SAVE
    G9 --> SAVE
    G11 --> SAVE
    G12 --> SAVE

    subgraph SAVE ["storage.save() 写库"]
        direction TB
        S1{"rowId ≥ 0?<br/>即是否已在库中"}
        S1 -->|是| S2["UPDATE premium SET<br/>UUID, Name, Premium,<br/>LastIp, LastLogin<br/>WHERE UserID = ?"]
        S1 -->|否 rowId=-1| S3["INSERT INTO premium<br/>(UUID, Name, Premium,<br/>Floodgate, LastIp)<br/>→ 拿到自增 ID 回填 rowId"]
    end

    %% ===== 样式 =====
    style KICK fill:#ff6666,stroke:#cc0000,color:#fff
    style P fill:#ff6666,stroke:#cc0000,color:#fff
    style ERR fill:#999,stroke:#666,color:#fff
    style SKIP fill:#999,stroke:#666,color:#fff
    style G2 fill:#999,stroke:#666,color:#fff
```

## 关键配置项与职责

| 配置项 | 职责 | 默认值 |
|---|---|---|
| `nameChangeCheck` | 查 Mojang API，通过 UUID 识别改名玩家并更新数据库旧记录 | false |
| `autoRegister` | 正版新玩家自动注册到 auth 插件（forceRegister） | false |
| `offline-whitelist` | 访问控制：放行正版玩家，踢出新离线玩家，老离线玩家正常进入 | false |
| `premiumUuid` | 正版玩家使用正版 UUID（而非离线 UUID） | false |
| `forwardSkin` | 转发正版皮肤给玩家 | true |
| `secondAttemptCracked` | 正版验证失败后记住该 IP+用户名，下次连接直接走离线 | false |
| `autoLogin` | 正版玩家自动登录 auth 插件（forceLogin/forceRegister） | true |

## requestPremiumLogin 的三次调用

`requestPremiumLogin` 在三个不同场景被调用，参数不同：

| 触发配置 | profile 参数 | registered | 效果 |
|---|---|---|---|
| `nameChangeCheck` | 按 UUID 查出的**旧记录**（保留历史数据） | false | 更新名字 + 注册到 auth 插件 |
| `autoRegister` | 按名字查出的**当前 profile**（可能是空壳） | false | 注册到 auth 插件 |
| `offline-whitelist` | 按名字查出的**当前 profile**（可能是空壳） | true | 只放行，不注册 |

`registered` 标志决定了 `ForceLoginManagement` 的行为：
- `registered=false` → `needsRegistration()=true` → `forceRegister()`（自动注册）
- `registered=true` → `needsRegistration()=false` → `forceLogin()`（自动登录）

## 数据库写入逻辑

`storage.save()` 根据 `rowId` 决定 SQL 操作：

| 场景 | rowId | SQL 操作 | UUID | Premium |
|---|---|---|---|---|
| 老正版玩家再登录 | ≥ 0 | UPDATE | 正版 UUID | true |
| 老离线玩家再登录 | ≥ 0 | UPDATE | null | false |
| 新正版玩家首次加入 | -1 | INSERT | 正版 UUID | true |
| 新离线玩家首次加入 | -1 | INSERT | null | false |
