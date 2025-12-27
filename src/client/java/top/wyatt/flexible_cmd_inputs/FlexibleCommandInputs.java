package top.wyatt.flexible_cmd_inputs;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FlexibleCommandInputs implements ClientModInitializer {
    public static final String MOD_ID = "flexible_cmd_inputs";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static int WEB_SERVER_PORT = 8080; // 非final，支持动态修改
    private static HttpServer httpServer = null; // 保存服务器实例，用于重启

    // 客户端状态统计相关变量
    private static final AtomicLong TICK_COUNT = new AtomicLong(0);
    private static long LAST_TICK_TIME = System.currentTimeMillis();
    private static double CLIENT_TPS = 20.0;
    private static double CLIENT_MTPS = 20.0;

    // ========== 模组初始化 ==========
    @Override
    public void onInitializeClient() {
        // 1. 初始化日志监听器
        try {
            LogListener.init();
        } catch (Exception e) {
            LOGGER.warn("日志监听器初始化失败，不影响WebUI功能", e);
        }

        // 2. 注册客户端指令（/fci setport / /fci getport）
        registerClientCommands();

        // 3. 启动WebUI服务
        startWebUIServer(WEB_SERVER_PORT);

        // 4. 初始化客户端TPS/MTPS统计（改用Fabric ClientTickEvents，修正核心）
        initClientTickStats();

        LOGGER.info("FlexibleCmdInputs 初始化完成! WebUI地址：http://localhost:{}", WEB_SERVER_PORT);
        LogStorage.addLog("模组加载完成：WebUI访问 http://localhost:" + WEB_SERVER_PORT);
    }

    // ========== 注册客户端指令 ==========
    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // 根指令 /fci
            dispatcher.register(ClientCommandManager.literal("fci")
                    // 子指令 /fci getport（查看当前端口）
                    .then(ClientCommandManager.literal("getport")
                            .executes(this::executeGetPort))
                    // 子指令 /fci setport <port>（修改端口）
                    .then(ClientCommandManager.literal("setport")
                            .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535)) // 合法端口范围1-65535
                                    .executes(this::executeSetPort)))
            );
        });
    }

    // 执行 /fci getport 指令
    private int executeGetPort(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        // 向玩家发送当前端口信息
        source.sendFeedback(Text.literal(String.format("[FCI] 当前WebUI端口：%d（访问地址：http://localhost:%d）",
                WEB_SERVER_PORT, WEB_SERVER_PORT)));
        LOGGER.info("[FCI] 玩家查询端口：{}", WEB_SERVER_PORT);
        return 1; // 指令执行成功返回1
    }

    // 执行 /fci setport <port> 指令
    private int executeSetPort(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        FabricClientCommandSource source = context.getSource();
        int newPort = IntegerArgumentType.getInteger(context, "port");

        if (newPort == WEB_SERVER_PORT) {
            source.sendFeedback(Text.literal("[FCI] 新端口与当前端口一致，无需修改！"));
            return 1;
        }

        // 停止旧服务器（若存在）
        stopOldWebServer();

        // 更新端口并启动新服务器
        WEB_SERVER_PORT = newPort;
        startWebUIServer(WEB_SERVER_PORT);

        // 向玩家发送修改成功提示
        source.sendFeedback(Text.literal(String.format("[FCI] 端口已修改为：%d，WebUI服务已重启（访问地址：http://localhost:%d）",
                WEB_SERVER_PORT, WEB_SERVER_PORT)));
        LogStorage.addLog(String.format("WebUI端口已修改为：%d，服务已重启", WEB_SERVER_PORT));
        LOGGER.info("[FCI] 玩家修改端口：{}", WEB_SERVER_PORT);
        return 1;
    }

    // ========== WebUI服务（支持动态端口重启） ==========
    private void startWebUIServer(int port) {
        try {
            // 创建新的HttpServer实例
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            LOGGER.info("正在初始化WebUI服务，绑定端口：{}", port);

            // 1. 根路径：返回独立WebUI页面
            httpServer.createContext("/", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                String webUIHtml = null;
                try {
                    webUIHtml = readResourceFile("/webui.html");
                } catch (Exception e) {
                    LOGGER.error("读取webui.html资源文件失败", e);
                }

                if (webUIHtml != null && !webUIHtml.isEmpty()) {
                    byte[] responseBytes = webUIHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                        os.flush();
                    }
                } else {
                    String errorMsg = "<h1>WebUI加载失败</h1><p>无法找到或读取webui.html资源文件</p>";
                    byte[] errorBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, errorBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorBytes);
                        os.flush();
                    }
                }
                exchange.close();
            });

            // 2. API：执行指令
            httpServer.createContext("/api/cmd", exchange -> {
            // 设置响应头（跨域+JSON）
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            try {
                // 补充：仅支持 POST 请求
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, new Result(false, "仅支持 POST 请求"));
                    return;
                }

                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryParams(query);
                String rawCmd = params.getOrDefault("command", "").trim();

                if (rawCmd.isBlank()) {
                    sendJsonResponse(exchange, 400, new Result(false, "指令不能为空"));
                    return;
                }

                String purifiedCmd = purifyCommand(rawCmd);
                boolean success = executeCommandAndFeedback(purifiedCmd);
                String logMsg = String.format("[WebAPI执行] %s | 结果：%s", purifiedCmd, success ? "成功" : "失败");
                LogStorage.addLog(logMsg);

                sendJsonResponse(exchange, 200, new Result(success,
                        success ? "指令执行成功" : "指令执行失败（玩家未加载/指令无效）"));
            } catch (Exception e) {
                LogStorage.addLog("[WebAPI异常] " + e.getMessage());
                sendJsonResponse(exchange, 500, new Result(false, "执行异常：" + e.getMessage()));
            } finally {
                exchange.close();
            }
        });

            // 3. 新增API：获取客户端状态（FPS、Ping、TPS/MTPS）
            httpServer.createContext("/api/status", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                try {
                    ClientStatus status = getCurrentClientStatus();
                    String json = String.format("{\"fps\":%.1f,\"ping\":%d,\"tps\":%.1f,\"mtps\":%.1f}",
                            status.fps, status.ping, status.tps, status.mtps);

                    byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, jsonBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(jsonBytes);
                        os.flush();
                    }
                } catch (Exception e) {
                    LOGGER.error("获取客户端状态失败", e);
                    sendJsonResponse(exchange, 500, new Result(false, "获取状态异常：" + e.getMessage()));
                } finally {
                    exchange.close();
                }
            });

            // 启动新服务器
            httpServer.setExecutor(null);
            httpServer.start();
            LOGGER.info("WebUI服务已成功启动：http://localhost:{}", port);
        } catch (IOException e) {
            LOGGER.error("启动WebUI服务失败（端口可能被占用）", e);
            LogStorage.addLog("WebUI服务启动失败：" + e.getMessage());
        }
    }

    // 停止旧的Web服务器
    private void stopOldWebServer() {
        if (httpServer != null) {
            LOGGER.info("正在停止旧的WebUI服务（端口：{}）", WEB_SERVER_PORT);
            httpServer.stop(1); // 1秒后停止，允许未完成的请求结束
            httpServer = null;
        }
    }

    // ========== 客户端状态统计（核心修正：改用Fabric ClientTickEvents） ==========
    private void initClientTickStats() {
        // 1. 注册客户端每帧Tick事件（递增Tick计数，主线程安全）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TICK_COUNT.incrementAndGet(); // 每帧执行一次，递增Tick数
        });

        // 2. 启动后台线程，每秒统计一次TPS/MTPS（保持原有逻辑，仅移除无效递归）
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // 每秒统计一次
                    long currentTime = System.currentTimeMillis();
                    long tickDelta = currentTime - LAST_TICK_TIME;
                    LAST_TICK_TIME = currentTime;

                    // 计算TPS（客户端每秒Tick数，理想20）
                    double tps = (double) TICK_COUNT.get() / (tickDelta / 1000.0);
                    CLIENT_TPS = Math.min(20.0, tps); // 限制最大值为20，避免异常值
                    CLIENT_MTPS = Math.min(20.0, CLIENT_TPS); // MTPS简化为与TPS一致（客户端单线程）

                    TICK_COUNT.set(0); // 重置Tick计数，准备下一秒统计
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("TPS统计线程被中断", e);
                }
            }
        }).start();
    }

    // 获取当前客户端状态
    private ClientStatus getCurrentClientStatus() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientStatus status = new ClientStatus();

        // 1. FPS（客户端当前帧率，MinecraftClient内置方法，1.20.1支持）
        status.fps = client.getCurrentFps();

        // 2. Ping（单人世界0，多人世界获取延迟）
        if (client.isInSingleplayer() || client.getNetworkHandler() == null || client.player == null) {
            status.ping = 0;
        } else {
            try {
                status.ping = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getLatency();
            } catch (Exception e) {
                status.ping = 0;
            }
        }

        // 3. TPS/MTPS（客户端统计的每秒Tick数）
        status.tps = CLIENT_TPS;
        status.mtps = CLIENT_MTPS;

        return status;
    }

    // 客户端状态实体类
    private static class ClientStatus {
        double fps;
        int ping;
        double tps;
        double mtps;
    }

    // ========== 原有工具方法（保持不变） ==========
    private String readResourceFile(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            if (inputStream == null) {
                LOGGER.error("资源文件不存在：{}", resourcePath);
                return null;
            }

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            return content.toString().trim();
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Result result) throws IOException {
        String safeMessage = result.message.replace("\"", "\\\"").replace("\n", "\\n");
        String json = String.format("{\"success\":%b,\"message\":\"%s\"}", result.success, safeMessage);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(statusCode, jsonBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonBytes);
            os.flush();
        }
    }

    public static String purifyCommand(String command) {
        if (command == null) return "";
        return command.replaceAll("[\"'`\\\\|;$<>]", "").trim();
    }

    public static boolean executeCommandAndFeedback(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null || command.isBlank()) {
            return false;
        }

        String finalCmd = command.startsWith("/") ? command.substring(1) : command;
        try {
            client.getNetworkHandler().sendChatCommand(finalCmd);
            return true;
        } catch (Exception e) {
            LOGGER.error("执行指令失败：{}", command, e);
            return false;
        }
    }

    // ========== 内部实体类 ==========
    public static class Result {
        public boolean success;
        public String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    // ========== 补充缺失的辅助类 ==========
    public static class LogStorage {
        private static final StringBuilder LOG = new StringBuilder();

        public static void addLog(String msg) {
            LOG.append(msg).append(System.lineSeparator());
            LOGGER.info(msg);
        }

        public static String getLogs() {
            return LOG.toString();
        }
    }

    public static class LogListener {
        public static void init() {
            LOGGER.info("日志监听器已初始化");
        }
    }
}