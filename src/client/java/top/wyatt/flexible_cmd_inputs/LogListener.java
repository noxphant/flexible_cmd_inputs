package top.wyatt.flexible_cmd_inputs;

import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.function.Consumer;

public class LogListener {
    private static final Logger LOGGER = LogManager.getLogger(LogListener.class);
    private static Consumer<String> logConsumer;
    private static boolean isInitialized = false;

    public static void init() {
        if (isInitialized) return;
        isInitialized = true;

        try {
            AbstractAppender appender = new AbstractAppender("FCI-LogAppender", null,
                    PatternLayout.newBuilder().withPattern("%d{HH:mm:ss} [%level] %msg%n").build(),
                    false, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    String log = getLayout().toSerializable(event).toString();
                    // 日志存入LogStorage（供WebUI展示）
                    LogStorage.addLog(log);

                    if (logConsumer != null) {
                        MinecraftClient.getInstance().execute(() -> logConsumer.accept(log));
                    }
                }
            };
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        } catch (Exception e) {
            LOGGER.error("初始化日志监听器失败", e);
            LogStorage.addLog("初始化日志监听器失败：" + e.getMessage());
        }
    }

    public static void setLogConsumer(Consumer<String> consumer) {
        logConsumer = consumer;
    }
}