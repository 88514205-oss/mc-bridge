package com.bridge;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class CommandExecutor {

    public static void execute(String command) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            BridgeMod.logger.warn("\u65e0\u6cd5\u6267\u884c\u6307\u4ee4: \u670d\u52a1\u5668\u5c1a\u672a\u542f\u52a8");
            return;
        }

        BridgeMod.logger.info("[AstrBot Bridge] \u6267\u884c\u6307\u4ee4: /{}", command);

        // 使用控制台发送者执行指令
        ICommandSender sender = server;
        server.getCommandManager().executeCommand(sender, command);
    }

    public static void sendMessageToPlayer(String playerName, String message) {
        ChatListener.sendMessageToPlayer(playerName, message);
    }

    public static void broadcast(String message) {
        ChatListener.broadcastMessage(message);
    }
}
