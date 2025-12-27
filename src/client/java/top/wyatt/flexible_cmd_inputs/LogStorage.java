package top.wyatt.flexible_cmd_inputs;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

// 线程安全的日志存储类（供WebUI展示）
public class LogStorage {
    // 日志队列（最多保存1000条，避免内存溢出）
    private static final ConcurrentLinkedQueue<String> LOG_QUEUE = new ConcurrentLinkedQueue<>();
    private static final int MAX_LOGS = 1000;
    private static final AtomicInteger LOG_COUNT = new AtomicInteger(0);

    // 添加日志（自动截断超出数量的旧日志）
    public static void addLog(String log) {
        String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logWithTime = "[" + timestamp + "] " + log;

        LOG_QUEUE.offer(logWithTime);
        LOG_COUNT.incrementAndGet();

        // 超出最大数量时删除旧日志
        while (LOG_QUEUE.size() > MAX_LOGS) {
            LOG_QUEUE.poll();
            LOG_COUNT.decrementAndGet();
        }
    }

    // 获取所有日志（转为数组供WebUI展示）
    public static String[] getAllLogs() {
        return LOG_QUEUE.toArray(new String[0]);
    }

    // 清空日志
    public static void clearLogs() {
        LOG_QUEUE.clear();
        LOG_COUNT.set(0);
    }
}