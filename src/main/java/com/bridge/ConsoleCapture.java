package com.bridge;

import java.util.LinkedList;
import java.util.List;

public class ConsoleCapture {

    private static final LinkedList<String> logBuffer = new LinkedList<>();
    private static org.apache.logging.log4j.core.appender.AbstractAppender appender;
    private static volatile boolean running = false;

    public void start() {
        if (running) return;
        running = true;

        appender = new org.apache.logging.log4j.core.appender.AbstractAppender(
            "AstrBotBridgeAppender", null,
            org.apache.logging.log4j.core.layout.PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss}] [%level] %msg")
                .build()
        ) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                if (!running) return;
                String msg = new String(getLayout().toByteArray(event));
                synchronized (logBuffer) {
                    logBuffer.addLast(msg);
                    while (logBuffer.size() > ModConfig.consoleMaxLines) {
                        logBuffer.removeFirst();
                    }
                }
                // 推送到 WebSocket
                String json = "{\"line\":\"" + escapeJson(msg) + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
                BridgeMod.broadcastToWs("console", json);
            }
        };
        appender.start();

        org.apache.logging.log4j.core.Logger rootLogger = 
            (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        rootLogger.addAppender(appender);
        
        BridgeMod.logger.info("AstrBot Bridge \u63a7\u5236\u53f0\u6355\u83b7\u5df2\u542f\u7528");
    }

    public void stop() {
        running = false;
        if (appender != null) {
            org.apache.logging.log4j.core.Logger rootLogger = 
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.removeAppender(appender);
            appender.stop();
        }
    }

    public static List<String> getRecentLines(int count) {
        synchronized (logBuffer) {
            if (count >= logBuffer.size()) {
                return new LinkedList<>(logBuffer);
            }
            return new LinkedList<>(logBuffer.subList(logBuffer.size() - count, logBuffer.size()));
        }
    }

    public static int getLineCount() {
        synchronized (logBuffer) {
            return logBuffer.size();
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
