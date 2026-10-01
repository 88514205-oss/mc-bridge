# astrbot_plugin_mc_bridge_v2 · AstrBot 侧插件

MC服务器与 AstrBot 的桥接插件（v2），配合 Mod 端 [`astrbot-bridge-2.0.0.jar`](../) 使用。

## 安装

1. 下载 Release 里的 `astrbot_plugin_mc_bridge_v2.zip`
2. 解压到 AstrBot 的 `data/plugins/` 目录下（文件夹名保持 `astrbot_plugin_mc_bridge_v2`）
3. 打开 AstrBot WebUI → 插件管理 → 启用「MC Bridge v2」
4. 进入插件配置页填写 MC 服务器信息（见下表）
5. 重载插件 / 重启 AstrBot

> 依赖：`httpx`、`websockets`（AstrBot 环境通常已内置）。插件会主动连接 Mod 端的 WebSocket，断线自动重连。

## 配置项

| 配置 | 类型 | 默认 | 说明 |
|:----|:----|:----|:----|
| `mc_host` | string | `localhost` | MC 服务器地址，如 `192.168.1.100` 或 `mc.example.com` |
| `ws_port` | number | `19199` | Mod 端 WebSocket 端口 |
| `http_port` | number | `19200` | Mod 端 HTTP API 端口 |
| `password` | string | 空 | 连接密码，需与 Mod 端 `AstrBotBridge.cfg` 里的 password 一致；留空则不鉴权 |
| `sync_to_qq` | bool | `true` | MC 聊天同步到 QQ 群 |
| `sync_to_game` | bool | `true` | QQ 群消息同步到游戏 |
| `qq_group_id` | string | 空 | 绑定的 QQ 群号，留空不同步到群 |
| `ai_reply` | bool | `true` | MC 玩家说话时用 AI 自动回复 |
| `reply_prefix` | string | `§7[§bAI§7] §f` | AI 回复在游戏内的前缀（支持 § 颜色代码） |

## 命令

| 命令 | 用法 | 说明 |
|:----|:----|:----|
| `/mcstatus` | `/mcstatus` | 查看服务器 TPS / 在线人数 / 内存 / 版本 |
| `/mcplayers` | `/mcplayers` | 查看在线玩家（名字、世界、坐标、血量） |
| `/mccmd` | `/mccmd <指令>` | 远程执行 MC 指令 |
| `/mcconsole` | `/mcconsole [行数]` | 拉取服务器控制台日志（最多 100 行） |
| `/mcsay` | `/mcsay <消息>` | 向游戏内广播消息 |

## 通信协议

```
Mod 端 WebSocket :19199  →  推送 chat / console / player_join / player_leave
Mod 端 HTTP      :19200  →  /api/health /api/status /api/players /api/command /api/message /api/console
```

> 注意：插件是「客户端」，由插件主动连 Mod；请确保 AstrBot 所在机器能访问 MC 服务器的 19199/19200 端口（跨机需放行防火墙 / 端口转发）。

---
作者：白糖(Su1ger)
