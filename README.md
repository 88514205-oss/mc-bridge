# AstrBot MC Bridge v2 · Mod（服务端）

MC 服务器与 AstrBot 双向桥接的 Forge Mod。Forge 1.12.2。

AstrBot 端插件已拆分到独立仓库：https://github.com/88514205-oss/astrbot_plugin_mc_bridge_v2

---

## 下载

Release：https://github.com/88514205-oss/mc-bridge/releases/tag/v2.0.0

| 文件 | 组件 | 安装位置 |
|:----|:----|:----|
| `astrbot-bridge-2.0.0.jar` | Mod（服务端） | 服务端 `mods/` |

---

## 安装

1. 下载 `astrbot-bridge-2.0.0.jar`，放进服务端 `mods/` 目录
2. 启动一次服务器，生成配置 `config/AstrBotBridge.cfg`
3. 按需填写端口与 `password`，重启服务器

---

## 通信约定

- WebSocket `19199`：Mod 主动推送 chat / console / player_join / player_leave
- HTTP API `19200`：客户端（AstrBot 插件）请求调用
- 端口与密码必须与插件端配置一致；插件所在机器需能访问本机 19199、19200

---

## 仓库结构

```
mc-bridge/
├── src/main/java/com/bridge/        Mod Java 源码
│   ├── BridgeMod.java               @Mod 主类
│   ├── ModConfig.java               配置
│   ├── BridgeCommand.java           /bridge 指令与菜单
│   ├── ChatListener.java            聊天监听
│   ├── WebSocketServer.java         WebSocket 服务
│   ├── ApiServer.java               HTTP API
│   ├── ConsoleCapture.java          控制台捕获
│   └── CommandExecutor.java         指令执行
├── src/main/resources/mcmod.info    Mod 元信息
├── build.gradle / gradlew           Mod 构建
└── (无插件代码，插件在 astrbot_plugin_mc_bridge_v2 仓库)
```

编译产物不入库，成品 jar 见 Release。

---

## 环境要求

| 工具 | 版本 | 用途 |
|:----|:----|:----|
| JDK 8 | 1.8.0_442+ | 编译 Forge 1.12.2 Mod |
| Gradle | 4.9（Wrapper 自带） | 构建 Mod |
| Forge | 1.12.2-14.23.5.2860 | Mod 框架 |

---

## 编译

```bash
JAVA_HOME=/usr/lib/jvm/jdk8-full ./gradlew build
# 产物：build/libs/astrbot-bridge-2.0.0.jar
```

---

## HTTP API（端口 19200）

| 接口 | 方法 | 说明 |
|:----|:----|:----|
| `/api/health` | GET | 健康检查，返回 Mod 版本 2.0.0 |
| `/api/status` | GET | TPS、在线人数、内存、MC 版本 |
| `/api/players` | GET | 在线玩家：名字、UUID、世界、坐标、血量 |
| `/api/command` | POST | 执行 MC 指令，body `{"command": "..."}` |
| `/api/message` | POST | 向游戏发消息，body `{"message": "...", "broadcast": true}` |
| `/api/console?lines=50` | GET | 控制台日志，最多 100 行 |

## WebSocket（端口 19199）

| 事件 | 说明 |
|:----|:----|
| `chat` | 玩家聊天 |
| `console` | 控制台输出 |
| `player_join` | 玩家加入 |
| `player_leave` | 玩家离开 |

---

## 关于

- 版本：Mod 2.0.0（Forge 1.12.2-14.23.5.2860）
- 作者：白糖(Su1ger)，杨大师（辅助）
- 配套插件：https://github.com/88514205-oss/astrbot_plugin_mc_bridge_v2
- 许可：`LICENSE.txt` 等为 Minecraft Forge MDK 自带（LGPL 2.1），其余代码版权归作者
