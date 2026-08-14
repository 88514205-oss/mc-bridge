package com.bridge;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class ModConfig {
    
    public static int wsPort = 19199;
    public static int httpPort = 19200;
    public static String password = "";
    public static boolean authEnabled = false;
    
    // ===== 聊天同步配置 =====
    // 可选: global, local, team, all
    public static String[] syncChannels = {"all"};
    public static boolean syncToGame = true;
    public static boolean syncToQQ = true;
    public static boolean filterCommands = true;
    public static String chatPrefix = "§7[§bQQ§7] §f";
    public static String gameChatPrefix = "§7[§aMC§7] §f";
    
    // ===== 控制台捕获 =====
    public static boolean consoleCapture = true;
    public static int consoleMaxLines = 2000;
    
    public static Configuration config;
    
    public static void load(File configFile) {
        config = new Configuration(configFile);
        config.load();
        
        // ---- 服务器配置 ----
        wsPort = config.getInt("wsPort", "server", 19199, 1024, 65535,
                "WebSocket 服务端口（用于实时推送聊天/事件）");
        httpPort = config.getInt("httpPort", "server", 19200, 1024, 65535,
                "HTTP API 服务端口（用于执行指令/查询状态）");
        password = config.getString("password", "server", "",
                "连接密码，留空表示不启用认证");
        authEnabled = !password.isEmpty();
        
        // ---- 聊天同步配置 ----
        String[] defaultChannels = {"all"};
        String[] channels = config.getStringList("syncChannels", "chat", defaultChannels,
                "同步的聊天频道。可选: global(全局), local(本地), team(队伍), all(全部)");
        syncChannels = channels.length == 0 ? defaultChannels : channels;
        
        syncToGame = config.getBoolean("syncToGame", "chat", true,
                "是否将QQ消息同步到游戏内");
        syncToQQ = config.getBoolean("syncToQQ", "chat", true,
                "是否将游戏聊天同步到QQ");
        filterCommands = config.getBoolean("filterCommands", "chat", true,
                "是否过滤以 / 开头的指令消息（不同步到QQ）");
        chatPrefix = config.getString("chatPrefix", "chat", "§7[§bQQ§7] §f",
                "QQ消息发送到游戏时的前缀");
        gameChatPrefix = config.getString("gameChatPrefix", "chat", "§7[§aMC§7] §f",
                "游戏消息发送到QQ时附加的前缀");
        
        // ---- 控制台配置 ----
        consoleCapture = config.getBoolean("consoleCapture", "console", true,
                "是否捕获控制台输出并推送到WebSocket");
        consoleMaxLines = config.getInt("consoleMaxLines", "console", 2000, 100, 50000,
                "控制台缓存最大行数");
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public static Set<String> getSyncChannelSet() {
        Set<String> set = new HashSet<>();
        for (String ch : syncChannels) {
            set.add(ch.trim().toLowerCase());
        }
        return set;
    }
    
    public static boolean shouldSyncChannel(String channel) {
        Set<String> chs = getSyncChannelSet();
        return chs.contains("all") || chs.contains(channel.toLowerCase());
    }
    
    public static void save() {
        if (config != null) {
            config.save();
        }
    }
}
