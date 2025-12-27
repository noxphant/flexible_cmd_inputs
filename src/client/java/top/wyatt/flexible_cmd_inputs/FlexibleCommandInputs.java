package top.wyatt.flexible_cmd_inputs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import static spark.Spark.*;

@Environment(EnvType.CLIENT)
public class FlexibleCommandInputs implements ClientModInitializer {
    // 全局日志对象
    private static final Logger LOGGER = LogManager.getLogger(FlexibleCommandInputs.class);
    // Web服务端口（可通过/fci setport修改）
    public static int WEB_PORT = 5806;
    // F6按键绑定对象
    private static KeyBinding commandWindowKey;

    @Override
    public void onInitializeClient() {
        // 1. 注册F6按键（打开WebUI）
        registerKeyBinding();
        // 2. 初始化日志监听器
        LogListener.init();
        // 3. 启动内置Web服务（核心）
        startWebServer();
        // 4. 注册游戏内指令（修改Web端口）
        registerClientCommands();

        // 启动日志
        LogStorage.addLog("FlexibleCommandInputs 已启动，WebUI地址：http://127.0.0.1:" + WEB_PORT);
    }

    // ========== 1. 注册F6按键 + 监听按键（唯一版本，无重复） ==========
    private void registerKeyBinding() {
        // 注册F6按键
        commandWindowKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.flexible_cmd_inputs.open_webui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.flexible_cmd_inputs.main"
        ));

        // 按键监听线程：按F6自动打开WebUI
        MinecraftClient.getInstance().execute(() -> {
            new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(50);
                        // 检测F6按键是否被按下
                        if (commandWindowKey.wasPressed()) {
                            openWebUIInBrowser(); // 调用打开浏览器方法
                        }
                    } catch (InterruptedException e) {
                        LOGGER.warn("按键监听线程中断", e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        LOGGER.error("打开WebUI失败", e);
                        LogStorage.addLog("打开WebUI失败：" + e.getMessage());
                    }
                }
            }, "FCI-KeyListener").start();
        });
    }

    // ========== 2. 启动内置Web服务（API + WebUI） ==========
    private void startWebServer() {
        // 停止原有端口（防止冲突）
        stop();
        // 设置Web服务端口
        port(WEB_PORT);
        // 解决跨域问题
        options("/*", (req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type");
            return "";
        });

        // 1. WebUI页面（核心可视化界面）
        get("/", (req, res) -> {
            res.type("text/html; charset=utf-8");
            return """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                    <head>
                        <meta charset="UTF-8">
                        <title>Minecraft 指令控制台</title>
                        <style>
                            body { font-family: Consolas, monospace; margin: 20px; background: #1e1e1e; color: #ffffff; }
                            .log-container { width: 100%; height: 600px; border: 1px solid #444; padding: 10px; overflow-y: auto; background: #000; margin-bottom: 10px; }
                            .log-line { margin: 2px 0; }
                            .input-area { display: flex; gap: 10px; }
                            #commandInput { flex: 1; padding: 8px; font-size: 16px; background: #333; color: #fff; border: 1px solid #666; }
                            #submitBtn { padding: 8px 20px; background: #0078d7; color: #fff; border: none; cursor: pointer; }
                            #submitBtn:hover { background: #005a9e; }
                            #clearBtn { padding: 8px 20px; background: #d74400; color: #fff; border: none; cursor: pointer; }
                        </style>
                    </head>
                    <body>
                        <h1>Minecraft 指令控制台</h1>
                        <div class="log-container" id="logContainer"></div>
                        <div class="input-area">
                            <input type="text" id="commandInput" placeholder="输入Minecraft指令（无需/，如：gamemode creative）">
                            <button id="submitBtn">执行指令</button>
                            <button id="clearBtn">清空日志</button>
                        </div>

                        <script>
                            function loadLogs() {
                                fetch('/api/logs')
                                    .then(res => res.json())
                                    .then(logs => {
                                        const container = document.getElementById('logContainer');
                                        container.innerHTML = logs.map(log => `<div class="log-line">${log}</div>`).join('');
                                        container.scrollTop = container.scrollHeight;
                                    });
                            }

                            function executeCommand() {
                                const input = document.getElementById('commandInput');
                                const cmd = input.value.trim();
                                if (!cmd) return;
                                
                                fetch('/api/command', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ command: cmd })
                                }).then(res => res.json())
                                  .then(data => {
                                      if (data.success) {
                                          input.value = '';
                                          loadLogs();
                                      } else {
                                          alert('执行失败：' + data.message);
                                      }
                                  });
                            }

                            function clearLogs() {
                                fetch('/api/logs/clear', { method: 'POST' }).then(() => loadLogs());
                            }

                            window.onload = () => {
                                loadLogs();
                                setInterval(loadLogs, 500);
                                document.getElementById('submitBtn').onclick = executeCommand;
                                document.getElementById('clearBtn').onclick = clearLogs;
                                document.getElementById('commandInput').onkeydown = (e) => {
                                    if (e.key === 'Enter') executeCommand();
                                };
                            };
                        </script>
                    </body>
                    </html>
                    """;
        });

        // 2. API：获取日志
        get("/api/logs", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return LogStorage.getAllLogs();
        });

        // 3. API：执行指令
        post("/api/command", (req, res) -> {
            res.type("application/json; charset=utf-8");
            try {
                String cmd = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(req.body())
                        .get("command")
                        .asText()
                        .trim();

                if (cmd.isEmpty()) {
                    return new Result(false, "指令不能为空");
                }

                boolean success = executeCommandAndFeedback(cmd);
                LogStorage.addLog("执行指令：" + cmd + " | 结果：" + (success ? "成功" : "失败"));

                return new Result(success, success ? "指令执行成功" : "指令执行失败（玩家未加载/指令无效）");
            } catch (Exception e) {
                LogStorage.addLog("指令执行异常：" + e.getMessage());
                return new Result(false, "执行异常：" + e.getMessage());
            }
        });

        // 4. API：清空日志
        post("/api/logs/clear", (req, res) -> {
            LogStorage.clearLogs();
            res.type("application/json; charset=utf-8");
            return new Result(true, "日志已清空");
        });

        // Web服务启动日志
        LogStorage.addLog("内置Web服务已启动，访问地址：http://127.0.0.1:" + WEB_PORT);
        LOGGER.info("内置Web服务已启动，端口：{}", WEB_PORT);
    }

    // ========== 3. 注册游戏内指令（修改Web端口） ==========
    private void registerClientCommands() {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fci")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("setport")
                            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("port", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 65535))
                                    .executes(context -> {
                                        int newPort = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "port");
                                        WEB_PORT = newPort;
                                        startWebServer(); // 重启Web服务
                                        context.getSource().sendFeedback(Text.literal("[灵活指令输入] Web端口已修改为: " + newPort));
                                        LogStorage.addLog("Web端口已修改为：" + newPort);
                                        return 1;
                                    }))));
        });
    }

    // ========== 4. 核心方法：执行Minecraft指令 ==========
    public static boolean executeCommandAndFeedback(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        // 校验玩家和网络连接
        if (client.player == null || client.getNetworkHandler() == null || command.isBlank()) {
            return false;
        }

        String finalCommand = command.startsWith("/") ? command.substring(1) : command;
        try {
            // 1.20.1 官方标准指令执行方式
            client.getNetworkHandler().sendChatCommand(finalCommand);
            return true;
        } catch (Exception e) {
            LOGGER.error("执行指令失败: " + command, e);
            return false;
        }
    }

    // ========== 5. 辅助方法：打开系统默认浏览器访问WebUI ==========
    private void openWebUIInBrowser() {
        try {
            String url = "http://127.0.0.1:" + WEB_PORT;
            // 跨平台打开浏览器
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } else {
                // Windows备用方案
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
            LogStorage.addLog("已打开WebUI：" + url);
        } catch (Exception e) {
            LogStorage.addLog("打开浏览器失败，请手动访问：http://127.0.0.1:" + WEB_PORT);
        }
    }

    // ========== 内部类：API返回结果 ==========
    private static class Result {
        public boolean success;
        public String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}