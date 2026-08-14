# 🎮 AstrBot MC Bridge v2

**MC服务器 + AstrBot 联合桥接系统**
**Mod + 插件 双端联动 | 聊天同步 | 远程管理 | AI回复**

---

## 🏗️ 项目总览

这是一个 **MC服务器 Forge Mod** 和 **AstrBot插件** 的联合项目，让QQ群和MC服务器实现双向互通。

```
┌─────────────────────────────────────┐
│          MC 服务器 (Forge)            │
│  ┌─────────────────────────────────┐ │
│  │  astrbot-bridge-2.0.0.jar      │ │
│  │  ├─ WebSocket :19199 ←→ 实时推送 │ │
│  │  ├─ HTTP API  :19200 ←→ 指令控制 │ │
│  │  ├─ 聊天监听 → 双向同步           │ │
│  │  ├─ 控制台捕获 → 远程查看         │ │
│  │  └─ /bridge 指令 → 交互菜单      │ │
│  └─────────────────────────────────┘ │
└──────────────┬──────────────────────┘
               │ WebSocket + HTTP
               ▼
┌─────────────────────────────────────┐
│          AstrBot (QQ Bot)            │
│  ┌─────────────────────────────────┐ │
│  │  astrbot_plugin_mc_bridge_v2   │ │
│  │  ├─ mcstatus → 看服务器状态     │ │
│  │  ├─ mcplayers → 看在线的玩家    │ │
│  │  ├─ mccmd → 远程执行指令       │ │
│  │  ├─ mcsay → 发消息到游戏       │ │
│  │  └─ 聊天同步 + AI自动回复       │ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

---

## 📂 文件结构

```
mc-bridge/                          ← 项目根目录
├── README.md                       ← 本文件
├── build.gradle                    ← Gradle构建配置
├── gradlew / gradlew.bat          ← Gradle Wrapper
│
├── src/main/java/com/bridge/      ← Mod Java源码
│   ├── BridgeMod.java             ← @Mod主类（生命周期管理）
│   ├── ModConfig.java             ← 配置文件（聊天同步设置）
│   ├── BridgeCommand.java         ← /bridge 指令+交互菜单
│   ├── ChatListener.java          ← 玩家聊天监听
│   ├── WebSocketServer.java       ← WebSocket服务（实时推送）
│   ├── ApiServer.java             ← HTTP REST API服务
│   ├── ConsoleCapture.java        ← 控制台日志捕获
│   └── CommandExecutor.java       ← 指令执行工具
│
├── src/main/resources/
│   ├── mcmod.info                 ← Mod元信息
│   └── pack.mcmeta                ← 资源包描述
│
├── build/libs/
│   └── astrbot-bridge-2.0.0.jar   ← ← 编译好的Mod！（用这个）
│
└── astrbot-plugin/                ← AstrBot插件源码
    ├── README.md                  ← 插件使用说明
    ├── metadata.yaml              ← 插件元数据
    ├── _conf_schema.json          ← 配置面板定义
    └── main.py                    ← 插件主代码
```

---

## 🔧 环境要求

| 工具 | 版本 | 用途 |
|:----|:-----|:-----|
| Java JDK 8 | 1.8.0_442+ | 编译Forge 1.12.2 Mod |
| Gradle | 4.9（Wrapper自动管理） | 构建Mod |
| Python 3 | 3.11+ | AstrBot插件运行 |
| Forge | 1.12.2-14.23.5.2860 | MC Mod框架 |

---

## 🚀 开发指南

### 编译Mod
```bash
cd mc-bridge/
JAVA_HOME=/usr/lib/jvm/jdk8-full ./gradlew build
# 编译产物在 build/libs/astrbot-bridge-2.0.0.jar
```

### 部署到服务器
```bash
# 复制Mod到mods目录
cp build/libs/astrbot-bridge-2.0.0.jar /path/to/mcserver/mods/

# 重启服务器
# 配置自动生成在 config/AstrBotBridge.cfg
```

### 插件热更
修改 `main.py` 后重启AstrBot即可生效。

---

## 📋 API文档

### HTTP API（端口19200）

#### `GET /api/health` — 健康检查
```json
{"status": "ok", "mod": "astrbot_bridge", "version": "2.0.0"}
```

#### `GET /api/status` — 服务器状态
```json
{"tps": 20.0, "players": 0, "maxPlayers": 5, 
 "memUsed": 610, "memMax": 2048, "version": "1.12.2"}
```

#### `GET /api/players` — 在线玩家列表
```json
{"players": [{"name": "Steve", "uuid": "...", 
  "world": "OVERWORLD", "x": 100, "y": 64, "z": 200, "health": 20}]}
```

#### `POST /api/command` — 执行MC指令
```json
// 请求: {"command": "give @a diamond 1"}
// 响应: {"status": "ok", "command": "give @a diamond 1"}
```

#### `POST /api/message` — 发送消息到游戏
```json
// 请求: {"message": "大家好!", "broadcast": true}
// 响应: {"status": "ok"}
```

#### `GET /api/console?lines=50` — 获取控制台日志
```json
{"lines": ["[14:55:05] [INFO] ..."], "total": 160}
```

### WebSocket（端口19199）

Mod端主动推送事件：

| 事件类型 | 说明 |
|:---------|:-----|
| `chat` | 玩家聊天消息 |
| `console` | 控制台日志输出 |
| `player_join` | 玩家加入游戏 |
| `player_leave` | 玩家离开游戏 |

---

## 🐱 关于

Made with ❤️ by 白糖(Su1ger)
