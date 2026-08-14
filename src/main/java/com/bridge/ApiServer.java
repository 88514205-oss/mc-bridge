package com.bridge;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ApiServer {

    private com.sun.net.httpserver.HttpServer server;

    public void start() {
        try {
            server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(ModConfig.httpPort), 0);

            server.createContext("/api/command", new CommandHandler());
            server.createContext("/api/message", new MessageHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/console", new ConsoleHandler());
            server.createContext("/api/health", exchange -> {
                String resp = "{\"status\":\"ok\",\"mod\":\"astrbot_bridge\",\"version\":\"2.0.0\"}";
                sendJson(exchange, 200, resp);
            });

            server.setExecutor(null);
            server.start();
            BridgeMod.logger.info("HTTP API \u670d\u52a1\u7aef\u5df2\u542f\u52a8\u5728\u7aef\u53e3 {}", ModConfig.httpPort);
        } catch (IOException e) {
            BridgeMod.logger.error("HTTP \u670d\u52a1\u7aef\u542f\u52a8\u5931\u8d25", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private boolean checkAuth(HttpExchange exchange) {
        if (!ModConfig.authEnabled) return true;
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        return ModConfig.password.equals(auth);
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    // ===== POST /api/command =====
    class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                String command = extractField(body, "command");
                if (command == null || command.isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"command is required\"}");
                    return;
                }
                CommandExecutor.execute(command);
                sendJson(exchange, 200, "{\"status\":\"ok\",\"command\":\"" + escapeJson(command) + "\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ===== POST /api/message =====
    class MessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                String player = extractField(body, "player");
                String message = extractField(body, "message");
                boolean broadcast = "true".equals(extractField(body, "broadcast"));

                if (message == null || message.isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"message is required\"}");
                    return;
                }

                String formatted = ModConfig.chatPrefix + message;
                if (broadcast || player == null || player.isEmpty() || player.equals("*")) {
                    ChatListener.broadcastMessage(formatted);
                } else {
                    ChatListener.sendMessageToPlayer(player, formatted);
                }

                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ===== GET /api/status =====
    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            try {
                net.minecraft.server.MinecraftServer server =
                    net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server == null) {
                    sendJson(exchange, 503, "{\"error\":\"server not ready\"}");
                    return;
                }

                double tps = 20.0;
                int players = server.getCurrentPlayerCount();
                int maxPlayers = server.getMaxPlayers();
                long memUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long memMax = Runtime.getRuntime().maxMemory();

                String json = String.format(
                    "{\"tps\":%.2f,\"players\":%d,\"maxPlayers\":%d,\"uptime\":%d," +
                    "\"memUsed\":%d,\"memMax\":%d,\"version\":\"%s\"}",
                    tps, players, maxPlayers,
                    System.currentTimeMillis() - server.getCurrentTime(),
                    memUsed / 1048576, memMax / 1048576,
                    server.getMinecraftVersion()
                );
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ===== GET /api/players =====
    class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            try {
                net.minecraft.server.MinecraftServer server =
                    net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server == null) {
                    sendJson(exchange, 503, "{\"error\":\"server not ready\"}");
                    return;
                }

                StringBuilder json = new StringBuilder("{\"players\":[");
                List<net.minecraft.entity.player.EntityPlayerMP> players = server.getPlayerList().getPlayers();
                boolean first = true;
                for (net.minecraft.entity.player.EntityPlayerMP p : players) {
                    if (!first) json.append(",");
                    json.append(String.format(
                        "{\"name\":\"%s\",\"uuid\":\"%s\",\"world\":\"%s\",\"x\":%.1f,\"y\":%.1f,\"z\":%.1f,\"health\":%.1f}",
                        escapeJson(p.getName()), p.getUniqueID().toString(),
                        p.world.provider.getDimensionType().getName(),
                        p.posX, p.posY, p.posZ, p.getHealth()
                    ));
                    first = false;
                }
                json.append("]}");
                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ===== GET /api/console =====
    class ConsoleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            try {
                int lines = 50;
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2 && kv[0].equals("lines")) {
                            lines = Integer.parseInt(kv[1]);
                        }
                    }
                }

                List<String> logs = ConsoleCapture.getRecentLines(lines);
                StringBuilder json = new StringBuilder("{\"lines\":[");
                boolean first = true;
                for (String log : logs) {
                    if (!first) json.append(",");
                    json.append("\"").append(escapeJson(log)).append("\"");
                    first = false;
                }
                json.append("],\"total\":" + ConsoleCapture.getLineCount() + "}");
                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = json.indexOf(":", idx + key.length());
        if (start < 0) return null;
        start++;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            start++;
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    sb.append(json.charAt(i + 1));
                    i++;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
