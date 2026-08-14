package com.bridge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;

@Mod(modid = BridgeMod.MODID, name = BridgeMod.NAME, version = BridgeMod.VERSION)
public class BridgeMod {
    public static final String MODID = "astrbot_bridge";
    public static final String NAME = "AstrBot Bridge v2";
    public static final String VERSION = "0.0.2";

    public static Logger logger;
    public static WebSocketServer wsServer;
    public static ApiServer httpServer;
    public static ConsoleCapture consoleCapture;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ModConfig.load(event.getSuggestedConfigurationFile());
        logger.info("AstrBot Bridge v2 配置已加载");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ChatListener());
        logger.info("AstrBot Bridge 聊天监听已注册");
    }

    @EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        wsServer = new WebSocketServer();
        wsServer.start();

        httpServer = new ApiServer();
        httpServer.start();

        if (ModConfig.consoleCapture) {
            consoleCapture = new ConsoleCapture();
            consoleCapture.start();
        }

        // 注册/bridge指令
        event.registerServerCommand(new BridgeCommand());
        
        logger.info("AstrBot Bridge v0.0.2 \u5df2\u542f\u52a8! WebSocket:{} HTTP:{}",
            ModConfig.wsPort, ModConfig.httpPort);
    }

    @EventHandler
    public void serverStop(FMLServerStoppingEvent event) {
        if (wsServer != null) wsServer.stop();
        if (httpServer != null) httpServer.stop();
        if (consoleCapture != null) consoleCapture.stop();
        logger.info("AstrBot Bridge \u5df2\u505c\u6b62");
    }

    public static void broadcastToWs(String type, String data) {
        if (wsServer != null) {
            wsServer.broadcast("{\"type\":\"" + type + "\",\"data\":" + data + "}");
        }
    }
}
