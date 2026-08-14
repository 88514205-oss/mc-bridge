package com.bridge;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;

public class BridgeCommand extends CommandBase {

    @Override
    public String getName() {
        return "bridge";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bridge <status|config|reload|reconnect|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP权限
    }

    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add("astrbot");
        aliases.add("br");
        return aliases;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        // 权限检查：需要OP等级2（管理员/作弊模式）
        if (!checkPermission(sender)) {
            sender.sendMessage(new TextComponentString(
                TextFormatting.RED + "你没有权限执行此指令（需要管理员权限或开启作弊）"));
            return;
        }
        
        if (args.length == 0) {
            showMainMenu(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "status":
            case "s":
                showStatus(sender);
                break;
            case "config":
                if (args.length >= 3) {
                    setConfig(sender, args[1], args[2]);
                } else {
                    showConfig(sender);
                }
                break;
            case "reload":
                reloadConfig(sender);
                break;
            case "reconnect":
                reconnect(sender);
                break;
            case "help":
            case "h":
            case "?":
                showHelp(sender);
                break;
            default:
                sendMsg(sender, TextFormatting.RED + "未知指令。使用 /bridge help 查看帮助");
        }
    }

    // ========== 主菜单UI ==========
    private void showMainMenu(ICommandSender sender) {
        boolean connected = BridgeMod.wsServer != null;
        int clients = (connected && BridgeMod.wsServer.getClientCount() > 0) ? 1 : 0;

        sendMsg(sender, "");
        sendMsg(sender, TextFormatting.GOLD + "" + TextFormatting.STRIKETHROUGH + "=================================");
        sendMsg(sender, TextFormatting.LIGHT_PURPLE + "  ✦ AstrBot Bridge v2.0 ✦");
        sendMsg(sender, TextFormatting.GOLD + "" + TextFormatting.STRIKETHROUGH + "=================================");

        // 状态行
        String statusColor = clients > 0 ? TextFormatting.GREEN.toString() : TextFormatting.RED.toString();
        String statusIcon = clients > 0 ? "✅ 已连接" : "❌ 未连接";
        sendMsg(sender, TextFormatting.GRAY + "  状态: " + statusColor + statusIcon
            + TextFormatting.GRAY + " | AstrBot客户端: " + clients);

        sendMsg(sender, "");

        // 可点击的按钮行1
        sendClickable(sender,
            TextFormatting.GREEN + " [📊 服务器状态] ",
            "/bridge status",
            TextFormatting.GRAY + "查看TPS、内存、玩家等信息");

        // 行2
        sendClickable(sender,
            TextFormatting.AQUA + " [📋 在线玩家] ",
            "/mcplayers",
            TextFormatting.GRAY + "查看当前在线玩家详情");

        // 行3
        sendClickable(sender,
            TextFormatting.YELLOW + " [⚙️ 配置管理] ",
            "/bridge config",
            TextFormatting.GRAY + "查看和修改当前配置");

        // 行4
        sendClickable(sender,
            TextFormatting.RED + " [🔄 重载配置] ",
            "/bridge reload",
            TextFormatting.GRAY + "重新加载配置文件");

        sendMsg(sender, "");
        sendMsg(sender, TextFormatting.GOLD + "" + TextFormatting.STRIKETHROUGH + "=================================");
        sendFooter(sender, "输入 /bridge help 查看更多指令");
    }

    // ========== 状态页 ==========
    private void showStatus(ICommandSender sender) {
        net.minecraft.server.MinecraftServer mcServer = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (mcServer == null) {
            sendMsg(sender, TextFormatting.RED + "服务器未就绪");
            return;
        }

        sendMsg(sender, TextFormatting.GOLD + "---- AstrBot Bridge 状态 ----");

        // Mod信息
        sendMsg(sender, TextFormatting.GREEN + "Mod: " + TextFormatting.WHITE + "AstrBot Bridge v" + BridgeMod.VERSION);

        // WebSocket状态
        boolean wsAlive = BridgeMod.wsServer != null;
        int wsClients = wsAlive ? BridgeMod.wsServer.getClientCount() : 0;
        sendMsg(sender, TextFormatting.GREEN + "WebSocket: " + TextFormatting.WHITE + "端口" + ModConfig.wsPort
            + (wsAlive ? TextFormatting.GREEN + " ✅ 运行中" : TextFormatting.RED + " ❌ 未启动")
            + " | 客户端: " + wsClients);

        // HTTP API状态
        sendMsg(sender, TextFormatting.GREEN + "HTTP API: " + TextFormatting.WHITE + "端口" + ModConfig.httpPort
            + TextFormatting.GREEN + " ✅ 运行中");

        // 聊天同步
        String channels = String.join(", ", ModConfig.syncChannels);
        sendMsg(sender, TextFormatting.GREEN + "聊天同步: "
            + (ModConfig.syncToQQ ? TextFormatting.GREEN + "MC→QQ开启" : TextFormatting.RED + "MC→QQ关闭")
            + " | "
            + (ModConfig.syncToGame ? TextFormatting.GREEN + "QQ→MC开启" : TextFormatting.RED + "QQ→MC关闭")
            + TextFormatting.WHITE + " | 频道: " + channels);

        // 服务器信息
        int players = mcServer.getCurrentPlayerCount();
        int maxPlayers = mcServer.getMaxPlayers();
        long memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
        long memMax = Runtime.getRuntime().maxMemory() / 1048576;
        sendMsg(sender, TextFormatting.GREEN + "服务器: " + TextFormatting.WHITE
            + "玩家 " + players + "/" + maxPlayers
            + " | 内存 " + memUsed + "MB/" + memMax + "MB"
            + " | 版本 " + mcServer.getMinecraftVersion());

        sendMsg(sender, TextFormatting.GOLD + "--------------------------");
        sendFooter(sender, "点击下方按钮返回主菜单");
        sendClickable(sender,
            TextFormatting.LIGHT_PURPLE + " [⬅ 返回菜单] ",
            "/bridge",
            "返回主菜单");
    }

    // ========== 配置页 ==========
    private void showConfig(ICommandSender sender) {
        sendMsg(sender, TextFormatting.GOLD + "---- AstrBot Bridge 配置 ----");

        sendConfigLine(sender, "wsPort", String.valueOf(ModConfig.wsPort), "WebSocket端口");
        sendConfigLine(sender, "httpPort", String.valueOf(ModConfig.httpPort), "HTTP API端口");
        sendConfigLine(sender, "password", ModConfig.password.isEmpty() ? "(空)" : "******", "连接密码");
        sendConfigLine(sender, "syncChannels", String.join(",", ModConfig.syncChannels), "同步频道");
        sendConfigLine(sender, "syncToGame", String.valueOf(ModConfig.syncToGame), "QQ→游戏");
        sendConfigLine(sender, "syncToQQ", String.valueOf(ModConfig.syncToQQ), "游戏→QQ");
        sendConfigLine(sender, "filterCommands", String.valueOf(ModConfig.filterCommands), "过滤指令");
        sendConfigLine(sender, "chatPrefix", ModConfig.chatPrefix, "QQ消息前缀");
        sendConfigLine(sender, "consoleCapture", String.valueOf(ModConfig.consoleCapture), "控制台捕获");

        sendMsg(sender, TextFormatting.GOLD + "--------------------------");
        sendMsg(sender, TextFormatting.GRAY + "修改配置: /bridge config <键> <值>");
        sendFooter(sender, "例: /bridge config syncToGame false");
        sendClickable(sender,
            TextFormatting.LIGHT_PURPLE + " [⬅ 返回菜单] ",
            "/bridge",
            "返回主菜单");
    }

    private void setConfig(ICommandSender sender, String key, String value) {
        switch (key.toLowerCase()) {
            case "wsport":
                ModConfig.wsPort = parseIntSafe(value, ModConfig.wsPort);
                sendMsg(sender, TextFormatting.GREEN + "✓ wsPort 已设为 " + ModConfig.wsPort
                    + " (重启后生效)");
                break;
            case "httpport":
                ModConfig.httpPort = parseIntSafe(value, ModConfig.httpPort);
                sendMsg(sender, TextFormatting.GREEN + "✓ httpPort 已设为 " + ModConfig.httpPort
                    + " (重启后生效)");
                break;
            case "synctogame":
                ModConfig.syncToGame = parseBoolSafe(value, ModConfig.syncToGame);
                sendMsg(sender, TextFormatting.GREEN + "✓ syncToGame 已设为 " + ModConfig.syncToGame + " (立即生效)");
                break;
            case "synctoqq":
                ModConfig.syncToQQ = parseBoolSafe(value, ModConfig.syncToQQ);
                sendMsg(sender, TextFormatting.GREEN + "✓ syncToQQ 已设为 " + ModConfig.syncToQQ + " (立即生效)");
                break;
            case "filtercommands":
                ModConfig.filterCommands = parseBoolSafe(value, ModConfig.filterCommands);
                sendMsg(sender, TextFormatting.GREEN + "✓ filterCommands 已设为 " + ModConfig.filterCommands + " (立即生效)");
                break;
            case "chatprefix":
                ModConfig.chatPrefix = value;
                sendMsg(sender, TextFormatting.GREEN + "✓ chatPrefix 已设为 " + value + " (立即生效)");
                break;
            default:
                sendMsg(sender, TextFormatting.RED + "未知配置项: " + key
                    + "。可用: wsPort, httpPort, syncToGame, syncToQQ, filterCommands, chatPrefix");
        }
    }

    private void reloadConfig(ICommandSender sender) {
        ModConfig.load(new java.io.File("config/AstrBotBridge.cfg"));
        sendMsg(sender, TextFormatting.GREEN + "✓ 配置已重新加载");
        // 重启WebSocket如果端口变了...
        sendMsg(sender, TextFormatting.YELLOW + "提示: 端口变更需要重启服务器生效");
    }

    private void reconnect(ICommandSender sender) {
        // 模拟重连逻辑
        sendMsg(sender, TextFormatting.YELLOW + "正在重新初始化服务...");
        if (BridgeMod.wsServer != null) {
            BridgeMod.wsServer.stop();
        }
        BridgeMod.wsServer = new WebSocketServer();
        BridgeMod.wsServer.start();
        sendMsg(sender, TextFormatting.GREEN + "✓ WebSocket服务已重启");
    }

    private void showHelp(ICommandSender sender) {
        sendMsg(sender, TextFormatting.GOLD + "---- AstrBot Bridge 指令帮助 ----");
        sendHelpLine(sender, "/bridge", "打开交互式主菜单");
        sendHelpLine(sender, "/bridge status", "查看详细状态信息");
        sendHelpLine(sender, "/bridge config", "查看所有配置项");
        sendHelpLine(sender, "/bridge config <key> <val>", "修改配置（立即生效或重启）");
        sendHelpLine(sender, "/bridge reload", "重新加载配置文件");
        sendHelpLine(sender, "/bridge reconnect", "重启WebSocket服务");
        sendMsg(sender, TextFormatting.GOLD + "--------------------------");
        sendMsg(sender, TextFormatting.GRAY + "提示: 所有带 [ ] 的按钮都可以点击喵~");
    }

    // ========== 工具方法 ==========

    private void sendMsg(ICommandSender sender, String msg) {
        sender.sendMessage(new TextComponentString(msg));
    }

    private void sendClickable(ICommandSender sender, String display, String command, String hoverTip) {
        TextComponentString comp = new TextComponentString(display);
        comp.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        comp.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new TextComponentString(hoverTip)));
        sender.sendMessage(comp);
    }

    private void sendConfigLine(ICommandSender sender, String key, String value, String desc) {
        TextComponentString comp = new TextComponentString(
            TextFormatting.GREEN + "  " + key + TextFormatting.GRAY + " = " + TextFormatting.WHITE + value);
        comp.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new TextComponentString(TextFormatting.YELLOW + desc + "\n" + TextFormatting.GRAY + "点击复制: /bridge config " + key + " <值>")));
        comp.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/bridge config " + key + " "));
        sender.sendMessage(comp);
    }

    private void sendHelpLine(ICommandSender sender, String cmd, String desc) {
        TextComponentString comp = new TextComponentString(
            TextFormatting.AQUA + "  " + cmd + TextFormatting.GRAY + " - " + TextFormatting.WHITE + desc);
        comp.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd));
        sender.sendMessage(comp);
    }

    private void sendFooter(ICommandSender sender, String tip) {
        sender.sendMessage(new TextComponentString(TextFormatting.DARK_GRAY + "  " + tip));
    }

    private boolean checkPermission(ICommandSender sender) {
        // 控制台直接放行
        if (sender instanceof net.minecraft.command.ICommandManager) return true;
        if (!(sender instanceof EntityPlayerMP)) return true;

        EntityPlayerMP player = (EntityPlayerMP) sender;
        // OP等级2及以上（管理员）或者开启了作弊的局域网玩家
        return player.canUseCommand(2, getName());
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private boolean parseBoolSafe(String s, boolean def) {
        if ("true".equalsIgnoreCase(s) || "1".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)) return false;
        return def;
    }
}
