package top.wyatt.flexible_cmd_inputs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class LogStorage {
    // 线程安全的日志列表（最多存储1000条，避免内存溢出）
    private static final List<String> LOGS = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_LINES = 1000;
    // 日志变更监听器（供本地窗口实时刷新）
    private static Consumer<String> logUpdateListener;

    /**
     * 添加日志并触发监听器
     */
    public static void addLog(String log) {
        if (log == null || log.isBlank()) return;
        // 控制日志数量
        if (LOGS.size() >= MAX_LOG_LINES) {
            LOGS.remove(0);
        }
        LOGS.add(log);
        // 通知本地窗口刷新日志
        if (logUpdateListener != null) {
            logUpdateListener.accept(log);
        }
    }

    /**
     * 获取所有日志
     */
    public static List<String> getAllLogs() {
        return new ArrayList<>(LOGS);
    }

    /**
     * 清空日志
     */
    public static void clearLogs() {
        LOGS.clear();
        if (logUpdateListener != null) {
            logUpdateListener.accept("日志已清空");
        }
    }

    /**
     * 注册日志更新监听器（供本地窗口使用）
     */
    public static void setLogUpdateListener(Consumer<String> listener) {
        logUpdateListener = listener;
    }
}