package com.bridge;

import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class ChatListener {

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        String playerName = event.getPlayer().getName();
        String message = event.getMessage();
        String channel = "global";

        if (ModConfig.filterCommands && message.startsWith("/")) {
            return;
        }

        if (!ModConfig.shouldSyncChannel(channel)) {
            return;
        }

        if (!ModConfig.syncToQQ) {
            return;
        }

        String json = "{\"player\":\"" + escapeJson(playerName)
            + "\",\"message\":\"" + escapeJson(message)
            + "\",\"channel\":\"" + channel + "\"}";
        BridgeMod.broadcastToWs("chat", json);
    }

    public static void sendMessageToPlayer(String playerName, String message) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        if (playerName.equals("@a") || playerName.equals("*")) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                player.sendMessage(new TextComponentString(message));
            }
        } else {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(playerName);
            if (player != null) {
                player.sendMessage(new TextComponentString(message));
            }
        }
    }

    public static void broadcastMessage(String message) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        server.getPlayerList().sendMessage(new TextComponentString(message));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
