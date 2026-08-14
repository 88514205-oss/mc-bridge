import asyncio
import json
import httpx
import websockets
from typing import Optional
from astrbot.api.event import filter, AstrMessageEvent
from astrbot.api.star import Context, Star, register
from astrbot.api import logger

@register(
    "astrbot_plugin_mc_bridge_v2",
    "白糖(Su1ger)",
    "MC服务器桥接v2 - 聊天同步/远程指令/控制台/服务器管理",
    "2.0.0",
)
class McBridgePluginV2(Star):
    def __init__(self, context: Context, config: dict):
        super().__init__(context)
        self.config = config

        # MC服务器连接配置
        self.mc_host = config.get("mc_host", "localhost")
        self.ws_port = config.get("ws_port", 19199)
        self.http_port = config.get("http_port", 19200)
        self.password = config.get("password", "")

        # 聊天同步配置
        self.sync_to_qq = config.get("sync_to_qq", True)
        self.sync_to_game = config.get("sync_to_game", True)
        self.qq_group_id = config.get("qq_group_id", "")
        self.ai_reply = config.get("ai_reply", True)
        self.reply_prefix = config.get("reply_prefix", "§7[§bAI§7] §f")

        # WebSocket连接
        self.ws = None
        self.ws_task = None
        self.running = False
        self.http_client = httpx.AsyncClient(timeout=10)

    async def initialize(self):
        """插件初始化时自动连接MC服务器"""
        self.running = True
        self.ws_task = asyncio.create_task(self._ws_connect_loop())
        logger.info("MC Bridge v2 插件已初始化，正在连接MC服务器...")

    async def _ws_connect_loop(self):
        """WebSocket重连循环"""
        while self.running:
            try:
                uri = f"ws://{self.mc_host}:{self.ws_port}"
                async with websockets.connect(uri) as ws:
                    self.ws = ws
                    logger.info(f"已连接到MC服务器 WebSocket: {uri}")

                    # 如果设置了密码，先认证
                    if self.password:
                        await ws.send(self.password)
                        auth_resp = await ws.recv()
                        logger.info(f"WebSocket 认证响应: {auth_resp}")

                    # 监听消息
                    async for message in ws:
                        await self._handle_ws_message(message)

            except websockets.ConnectionClosed:
                logger.warning("WebSocket连接断开，5秒后重连...")
            except Exception as e:
                logger.error(f"WebSocket连接异常: {e}")
                logger.info("5秒后重连...")

            await asyncio.sleep(5)

    async def _handle_ws_message(self, message: str):
        """处理WebSocket推送的消息"""
        try:
            data = json.loads(message)
            msg_type = data.get("type", "")
            msg_data = data.get("data", {})

            if msg_type == "chat":
                await self._handle_chat(msg_data)
            elif msg_type == "console":
                # 控制台输出可以记录或转发
                pass
            elif msg_type == "player_join":
                await self._handle_player_event("join", msg_data)
            elif msg_type == "player_leave":
                await self._handle_player_event("leave", msg_data)

        except json.JSONDecodeError:
            logger.error(f"WebSocket消息解析失败: {message[:100]}")

    async def _handle_chat(self, data: dict):
        """处理游戏内聊天消息"""
        player = data.get("player", "?")
        message = data.get("message", "")

        if not message:
            return

        # 同步到QQ群
        if self.sync_to_qq and self.qq_group_id:
            group_msg = f"[MC] {player}: {message}"
            await self.context.send_group_message(self.qq_group_id, group_msg)

        # AI回复
        if self.ai_reply and message:
            try:
                # 使用AstrBot的AI能力回复
                reply = await self._get_ai_reply(player, message)
                if reply:
                    # 发送回游戏
                    await self._send_to_game(reply, player)
            except Exception as e:
                logger.error(f"AI回复失败: {e}")

    async def _handle_player_event(self, event_type: str, data: dict):
        """处理玩家进出事件"""
        player = data.get("player", "?")
        if self.sync_to_qq and self.qq_group_id:
            if event_type == "join":
                msg = f"[MC] {player} 加入了游戏"
            else:
                msg = f"[MC] {player} 离开了游戏"
            await self.context.send_group_message(self.qq_group_id, msg)

    async def _get_ai_reply(self, player: str, message: str) -> Optional[str]:
        """获取AI回复"""
        try:
            # 调用AstrBot的AI对话功能
            session_id = f"mc_player_{player}"
            reply = await self.context.get_using_provider().text_chat(
                prompt=f"你在MC服务器中，玩家{player}对你说: {message}\n请用简短自然的方式回复他",
                session_id=session_id,
                contexts=[],
                image_urls=[],
            )
            if reply:
                return reply
        except Exception as e:
            logger.error(f"AI对话失败: {e}")
        return None

    async def _send_to_game(self, message: str, player: str = None):
        """发送消息到游戏"""
        url = f"http://{self.mc_host}:{self.http_port}/api/message"
        headers = {}
        if self.password:
            headers["Authorization"] = self.password

        payload = {
            "message": f"{self.reply_prefix}{message}",
            "broadcast": True,
            "player": player or "*"
        }

        try:
            resp = await self.http_client.post(url, json=payload, headers=headers)
            if resp.status_code != 200:
                logger.error(f"发送消息到游戏失败: {resp.status_code}")
        except Exception as e:
            logger.error(f"发送消息到游戏异常: {e}")

    async def _execute_mc_command(self, command: str) -> dict:
        """执行MC指令"""
        url = f"http://{self.mc_host}:{self.http_port}/api/command"
        headers = {"Content-Type": "application/json"}
        if self.password:
            headers["Authorization"] = self.password

        try:
            resp = await self.http_client.post(url, json={"command": command}, headers=headers)
            return {"status": resp.status_code, "data": resp.json() if resp.status_code == 200 else resp.text}
        except Exception as e:
            return {"status": 0, "error": str(e)}

    # ===== QQ消息处理 =====

    @filter.on_message()
    async def on_qq_message(self, event: AstrMessageEvent):
        """处理QQ群消息"""
        if not self.sync_to_game:
            return

        # 检查是否是指定的同步群
        if self.qq_group_id and str(event.get_group_id()) != str(self.qq_group_id):
            return

        message = event.get_message_str()
        if not message:
            return

        sender_name = event.get_sender_name() or "QQ用户"

        # 同步到游戏
        game_msg = f"<{sender_name}> {message}"
        await self._send_to_game(game_msg)

    # ===== 指令处理 =====

    @filter.command("mcstatus")
    async def mc_status(self, event: AstrMessageEvent):
        """查看MC服务器状态"""
        url = f"http://{self.mc_host}:{self.http_port}/api/status"
        headers = {}
        if self.password:
            headers["Authorization"] = self.password

        try:
            resp = await self.http_client.get(url, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                msg = (
                    f"MC服务器状态:\n"
                    f"TPS: {data.get('tps', '?')}\n"
                    f"玩家: {data.get('players', 0)}/{data.get('maxPlayers', 0)}\n"
                    f"内存: {data.get('memUsed', 0)}MB/{data.get('memMax', 0)}MB\n"
                    f"版本: {data.get('version', '?')}"
                )
                yield event.plain_result(msg)
            else:
                yield event.plain_result(f"连接失败: HTTP {resp.status_code}")
        except Exception as e:
            yield event.plain_result(f"连接失败: {e}")

    @filter.command("mcplayers")
    async def mc_players(self, event: AstrMessageEvent):
        """查看在线玩家"""
        url = f"http://{self.mc_host}:{self.http_port}/api/players"
        headers = {}
        if self.password:
            headers["Authorization"] = self.password

        try:
            resp = await self.http_client.get(url, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                players = data.get("players", [])
                if not players:
                    yield event.plain_result("当前没有玩家在线喵~")
                else:
                    msg = "在线玩家:\n"
                    for p in players:
                        msg += f"- {p['name']} ({p['world']}) [{p['x']:.0f}, {p['y']:.0f}, {p['z']:.0f}] HP:{p['health']:.0f}\n"
                    yield event.plain_result(msg.strip())
            else:
                yield event.plain_result(f"获取失败: HTTP {resp.status_code}")
        except Exception as e:
            yield event.plain_result(f"连接失败: {e}")

    @filter.command("mccmd")
    async def mc_command(self, event: AstrMessageEvent, command: str):
        """执行MC指令 - mccmd <指令>"""
        result = await self._execute_mc_command(command)
        if result.get("status") == 200:
            yield event.plain_result(f"指令已执行: /{command}")
        else:
            yield event.plain_result(f"执行失败: {result.get('error', '未知错误')}")

    @filter.command("mcconsole")
    async def mc_console(self, event: AstrMessageEvent, lines: int = 20):
        """查看MC控制台日志 - mcconsole [行数]"""
        url = f"http://{self.mc_host}:{self.http_port}/api/console?lines={min(lines, 100)}"
        headers = {}
        if self.password:
            headers["Authorization"] = self.password

        try:
            resp = await self.http_client.get(url, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                logs = data.get("lines", [])
                total = data.get("total", 0)
                if not logs:
                    yield event.plain_result("控制台日志为空喵~")
                else:
                    msg = f"控制台日志(共{total}条, 显示最近{len(logs)}条):\n"
                    for line in logs:
                        msg += line + "\n"
                    yield event.plain_result(msg.strip()[:1500])  # 避免消息过长
            else:
                yield event.plain_result(f"获取失败: HTTP {resp.status_code}")
        except Exception as e:
            yield event.plain_result(f"连接失败: {e}")

    @filter.command("mcsay")
    async def mc_say(self, event: AstrMessageEvent, message: str):
        """向MC发送消息 - mcsay <消息>"""
        await self._send_to_game(message)
        yield event.plain_result(f"消息已发送到MC服务器喵~")

    async def terminate(self):
        """插件卸载时清理连接"""
        self.running = False
        if self.ws:
            await self.ws.close()
        if self.ws_task:
            self.ws_task.cancel()
        await self.http_client.aclose()
        logger.info("MC Bridge v2 插件已卸载")
