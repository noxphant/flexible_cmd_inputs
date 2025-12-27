package top.wyatt.flexible_cmd_inputs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.lwjgl.glfw.GLFW;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Environment(EnvType.CLIENT)
public class FlexibleCommandInputs implements ClientModInitializer {
    // 全局日志（解决引用冲突）
    private static final Logger LOGGER = LogManager.getLogger(FlexibleCommandInputs.class);
    // 全局端口号
    public static int PORT = 5806;
    // 线程池
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    // F6按键绑定
    private static KeyBinding commandWindowKey;
    // TCP服务器实例
    private static TCPServer tcpServer;

    @Override
    public void onInitializeClient() {
        // 1. 注册F6按键
        registerKeyBinding();
        // 2. 注册游戏内指令
        registerClientCommands();
        // 3. 初始化日志监听
        LogListener.init();
        // 4. 启动TCP服务器
        restartTCPServer();
        // 5. 注册按键事件
        registerKeyPressHandler();
    }

    // 注册F6按键
    private void registerKeyBinding() {
        commandWindowKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.flexible_cmd_inputs.open_window",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.flexible_cmd_inputs.main"
        ));
    }

    // 注册客户端指令
    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("fci")
                    .then(ClientCommandManager.literal("setport")
                            .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
                                    .executes(context -> {
                                        int newPort = IntegerArgumentType.getInteger(context, "port");
                                        PORT = newPort;
                                        restartTCPServer();
                                        context.getSource().sendFeedback(Text.literal("[灵活指令输入] 端口已修改为: " + newPort));
                                        return 1;
                                    }))));
        });
    }

    // 按键监听（F6弹出窗口）
    private void registerKeyPressHandler() {
        MinecraftClient.getInstance().execute(() -> {
            new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(50);
                        if (commandWindowKey.wasPressed()) {
                            javax.swing.SwingUtilities.invokeLater(CommandInputWindow::new);
                        }
                    } catch (InterruptedException e) {
                        LOGGER.warn("按键监听线程中断", e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        LOGGER.error("打开指令窗口失败", e);
                    }
                }
            }, "FCI-KeyListener").start();
        });
    }

    // 重启TCP服务器
    public static void restartTCPServer() {
        if (tcpServer != null) tcpServer.stop();
        EXECUTOR.submit(() -> {
            try {
                tcpServer = new TCPServer(PORT);
                tcpServer.start();
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("[灵活指令输入] TCP服务器启动成功，端口: " + PORT), false);
                }
            } catch (IOException e) {
                LOGGER.error("TCP服务器启动失败", e);
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("[灵活指令输入] TCP服务器启动失败: " + e.getMessage()), false);
                }
            }
        });
    }
    // 指令执行
    public static boolean executeCommandAndFeedback(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        // 校验玩家和网络连接是否正常、指令是否为空
        if (client.player == null || client.getNetworkHandler() == null || command.isBlank()) {
            return false;
        }

        String finalCommand = command.startsWith("/") ? command.substring(1) : command;
        try {
            // 1.20.1 客户端执行指令的官方标准方式
            // 通过网络处理器发送指令，兼容所有1.20.x版本
            client.getNetworkHandler().sendChatCommand(finalCommand);

            // 反馈执行成功
            client.player.sendMessage(Text.literal("[灵活指令输入] 已执行指令: " + command), false);
            return true;
        } catch (Exception e) {
            LOGGER.error("执行指令失败: " + command, e);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[灵活指令输入] 指令执行失败: " + e.getMessage()), false);
            }
            return false;
        }
    }

    // 供TCP服务器调用的指令执行方法
    public static void executeCommand(String command) {
        executeCommandAndFeedback(command);
    }
}