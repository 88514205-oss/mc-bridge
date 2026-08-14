package com.bridge;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebSocketServer {

    private ServerSocket serverSocket;
    private Thread listenThread;
    private volatile boolean running = false;
    private final List<WebSocketClient> clients = new CopyOnWriteArrayList<>();

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int OP_TEXT = 0x1;
    private static final int OP_CLOSE = 0x8;
    private static final int OP_PING = 0x9;
    private static final int OP_PONG = 0xA;

    public void start() {
        running = true;
        listenThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(ModConfig.wsPort);
                BridgeMod.logger.info("WebSocket \u670d\u52a1\u7aef\u5df2\u542f\u52a8\u5728\u7aef\u53e3 {}", ModConfig.wsPort);

                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        new Thread(() -> handleConnection(socket)).start();
                    } catch (IOException e) {
                        if (running) {
                            BridgeMod.logger.error("WebSocket \u63a5\u53d7\u8fde\u63a5\u5931\u8d25", e);
                        }
                    }
                }
            } catch (IOException e) {
                BridgeMod.logger.error("WebSocket \u670d\u52a1\u7aef\u542f\u52a8\u5931\u8d25", e);
            }
        }, "WS-Server");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public int getClientCount() {
        return clients.size();
    }

    public void stop() {
        running = false;
        for (WebSocketClient client : clients) {
            client.close();
        }
        clients.clear();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            BridgeMod.logger.error("WebSocket \u670d\u52a1\u7aef\u5173\u95ed\u5931\u8d25", e);
        }
    }

    public void broadcast(String message) {
        for (WebSocketClient client : clients) {
            client.send(message);
        }
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            // 手动读取HTTP请求行和头部，避免BufferedReader吃掉后续WebSocket帧数据
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int prev = -1, b;
            int emptyLineCount = 0;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if (prev == 13 && b == 10) { // 

                    emptyLineCount++;
                    if (emptyLineCount == 2) break; // 空行 = 头部结束
                } else if (b != 10 && b != 13) {
                    emptyLineCount = 0;
                }
                prev = b;
            }
            
            if (headerBuf.size() == 0) return;
            
            String headerStr = new String(headerBuf.toByteArray(), StandardCharsets.UTF_8);
            BridgeMod.logger.info("WebSocket收到HTTP请求:\n{}", headerStr);
            String[] lines = headerStr.split("\\r?\\n");
            
            if (lines.length == 0) return;
            
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                int colon = line.indexOf(":");
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(),
                            line.substring(colon + 1).trim());
                }
            }

            String upgrade = headers.get("upgrade");
            if ("websocket".equalsIgnoreCase(upgrade)) {
                handleWebSocketUpgrade(socket, out, headers, in);
            }

        } catch (IOException e) {
            BridgeMod.logger.error("WebSocket \u8fde\u63a5\u5904\u7406\u5931\u8d25", e);
        }
    }

    private void handleWebSocketUpgrade(Socket socket, OutputStream out,
                                         Map<String, String> headers,
                                         InputStream in) throws IOException {
        String key = headers.get("sec-websocket-key");
        BridgeMod.logger.info("WebSocket握手 - 收到key: [{}]", key);
        if (key == null) {
            BridgeMod.logger.warn("WebSocket握手失败 - 未收到sec-websocket-key");
            BridgeMod.logger.info("收到的headers: {}", headers.toString());
            socket.close();
            return;
        }

        String accept;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            String toHash = key + WS_MAGIC;
            byte[] hash = md.digest(toHash.getBytes(StandardCharsets.UTF_8));
            accept = Base64.getEncoder().encodeToString(hash);
            BridgeMod.logger.info("WebSocket握手 - key=[{}] 计算accept=[{}]", key, accept);
        } catch (NoSuchAlgorithmException e) {
            socket.close();
            return;
        }

        // 直接用OutputStream写HTTP响应，避免PrintWriter的缓冲问题
        String httpResponse = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: " + accept + "\r\n"
            + "\r\n";
        byte[] respBytes = httpResponse.getBytes(StandardCharsets.US_ASCII);
        out.write(respBytes);
        out.flush();

        WebSocketClient client = new WebSocketClient(socket, in, out);
        clients.add(client);

        try {
            byte[] buffer = new byte[65536];
            while (running && !socket.isClosed()) {
                int read = in.read(buffer);
                if (read <= 0) break;

                int b0 = buffer[0] & 0xFF;
                int opcode = b0 & 0x0F;
                boolean fin = (b0 & 0x80) != 0;

                if (opcode == OP_CLOSE) {
                    break;
                }

                if (opcode == OP_PING) {
                    sendFrame(out, OP_PONG, new byte[0]);
                    continue;
                }

                if (opcode == OP_PONG) {
                    continue;
                }

                if (opcode == OP_TEXT) {
                    String text = decodeFrame(buffer, read);
                    if (text != null && !text.isEmpty()) {
                        handleMessage(client, text);
                    }
                }
            }
        } catch (IOException e) {
            // 客户端断开
        } finally {
            client.close();
            clients.remove(client);
        }
    }

    private String decodeFrame(byte[] buffer, int length) throws IOException {
        int b1 = buffer[1] & 0xFF;
        boolean masked = (b1 & 0x80) != 0;
        int payloadLen = b1 & 0x7F;
        int offset = 2;

        if (payloadLen == 126) {
            payloadLen = ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
            offset += 2;
        } else if (payloadLen == 127) {
            payloadLen = 0;
            for (int i = 0; i < 8; i++) {
                payloadLen = (payloadLen << 8) | (buffer[offset + i] & 0xFF);
            }
            offset += 8;
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            System.arraycopy(buffer, offset, maskKey, 0, 4);
            offset += 4;
        }

        byte[] payload = new byte[payloadLen];
        System.arraycopy(buffer, offset, payload, 0, payloadLen);

        if (masked && maskKey != null) {
            for (int i = 0; i < payloadLen; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int b0 = 0x80 | opcode;
        out.write(b0);

        if (payload.length < 126) {
            out.write(payload.length);
        } else if (payload.length < 65536) {
            out.write(126);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) {
                out.write((payload.length >> (i * 8)) & 0xFF);
            }
        }

        out.write(payload);
        out.flush();
    }

    private void handleMessage(WebSocketClient client, String message) {
        try {
            if (ModConfig.authEnabled) {
                if (!client.isAuthenticated()) {
                    if (message.trim().equals(ModConfig.password)) {
                        client.setAuthenticated(true);
                        client.send("{\"type\":\"auth\",\"data\":{\"status\":\"ok\"}}");
                    } else {
                        client.send("{\"type\":\"auth\",\"data\":{\"status\":\"fail\"}}");
                    }
                    return;
                }
            } else {
                client.setAuthenticated(true);
            }
        } catch (Exception e) {
            BridgeMod.logger.error("WebSocket \u6d88\u606f\u5904\u7406\u5931\u8d25", e);
        }
    }

    private static class WebSocketClient {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private volatile boolean authenticated = false;

        WebSocketClient(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        public void send(String message) {
            try {
                byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                int b0 = 0x80 | OP_TEXT;
                out.write(b0);
                if (payload.length < 126) {
                    out.write(payload.length);
                } else if (payload.length < 65536) {
                    out.write(126);
                    out.write((payload.length >> 8) & 0xFF);
                    out.write(payload.length & 0xFF);
                } else {
                    out.write(127);
                    for (int i = 7; i >= 0; i--) {
                        out.write((payload.length >> (i * 8)) & 0xFF);
                    }
                }
                out.write(payload);
                out.flush();
            } catch (IOException e) {
                BridgeMod.logger.error("WebSocket \u53d1\u9001\u6d88\u606f\u5931\u8d25", e);
            }
        }

        public void close() {
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }

        public boolean isAuthenticated() { return authenticated || !ModConfig.authEnabled; }
        public void setAuthenticated(boolean v) { this.authenticated = v; }
    }
}
