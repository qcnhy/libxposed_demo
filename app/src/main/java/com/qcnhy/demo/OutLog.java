package com.qcnhy.demo;

import static com.qcnhy.demo.MainModule.context;
import static com.qcnhy.demo.MainModule.mainModule;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 轻量日志系统 v2（性能优先）。
 *
 * 旧版问题：每条日志 2 次文件开关 + 1 次系统通知 IPC + 1 次全量堆栈捕获，
 * 高频 hook（RPC/JSON 响应）下严重拖慢页面加载，日志文件无限膨胀（实测 111MB+）。
 *
 * v2 方案：
 * - 分级 debug/info/warn/error，默认 INFO；files/log_config.json 可调（60s 热加载）：
 *   {"logLevel":"debug","maxFileSizeMb":20}
 * - 内存缓冲 + 单后台线程批量刷盘（2s 或 64KB 一批），hook 路径零同步文件 IO
 * - 只写内部存储 files/tip.txt；超限自动轮转 tip.1.txt / tip.2.txt
 * - 通知与调用堆栈只在 ERROR 级触发（通知 3s 限频）
 * - outlog(String) 兼容旧调用，等价 info()
 */
public class OutLog {

    public static final int DEBUG = 0, INFO = 1, WARN = 2, ERROR = 3;

    /** 模块加载时是否清空旧日志 */
    public static boolean CLEAR_LOG_ON_START = true;

    private static final String CHANNEL_ID = "log_channel";
    private static final String CONFIG_NAME = "log_config.json";

    private static final Object LOCK = new Object();
    private static final StringBuilder BUFFER = new StringBuilder(8 * 1024);
    private static final long FLUSH_INTERVAL_MS = 2000;
    private static final int FLUSH_THRESHOLD_BYTES = 64 * 1024;
    private static final int MAX_ROTATED = 2;

    private static volatile int logLevel = INFO;
    private static volatile long maxFileSize = 20L * 1024 * 1024;

    private static Thread flusherThread;
    private static long lastFlushAt = 0L;
    private static long lastCfgCheckAt = 0L;
    private static long lastNotifyAt = 0L;
    private static NotificationManager notificationManager;

    // ---------- 对外 API ----------

    /** 兼容旧调用：INFO 级 */
    public static void outlog(String msg) { log(INFO, msg, false); }

    /** 高频调试日志（JSON 响应/RPC 明细等，默认不落盘） */
    public static void debug(String msg) { log(DEBUG, msg, false); }

    public static void info(String msg) { log(INFO, msg, false); }

    public static void warn(String msg) { log(WARN, msg, false); }

    /** 错误：附带调用堆栈 + 通知栏提醒（限频） */
    public static void error(String msg) { log(ERROR, msg, true); }

    public static void error(String msg, Throwable t) {
        StringBuilder sb = new StringBuilder(msg == null ? "" : msg);
        if (t != null) {
            sb.append("\n").append(t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 30; i++) sb.append("\n  at ").append(st[i]);
        }
        log(ERROR, sb.toString(), true);
    }

    // ---------- 初始化 ----------

    public static void initLog() {
        if (CLEAR_LOG_ON_START) clearLogFiles();
        loadConfig();
        startFlusher();
    }

    /** 清空日志文件（仅内部存储 tip.txt；外部双写已在 v2 移除） */
    public static void clearLogFiles() {
        if (context == null) return;
        File f = new File(context.getFilesDir(), "tip.txt");
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ---------- 核心路径（只做内存 append） ----------

    private static void log(int level, String msg, boolean withStack) {
        try {
            if (level < logLevel) return;
            String line = timestamp() + " " + levelName(level) + " " + msg
                    + (withStack ? "\n" + currentStack() : "");
            synchronized (LOCK) {
                BUFFER.append(line).append('\n');
            }
            if (level >= INFO) {
                // logcat + LSPosed 模块日志；DEBUG 不走，避免高频 binder 调用
                System.out.println(line);
                if (mainModule != null) mainModule.log(line);
            }
            if (level >= ERROR) notifyError(msg);
        } catch (Throwable ignored) {
        }
    }

    private static String levelName(int level) {
        switch (level) {
            case DEBUG: return "D";
            case WARN: return "W";
            case ERROR: return "E";
            default: return "I";
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(new Date());
    }

    // ---------- 后台刷盘线程 ----------

    private static void startFlusher() {
        if (flusherThread != null) return;
        synchronized (OutLog.class) {
            if (flusherThread != null) return;
            Thread t = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        return;
                    }
                    reloadConfigIfStale();
                    flushIfNeeded();
                }
            }, "OutLog-Flusher");
            t.setDaemon(true);
            t.start();
            flusherThread = t;
        }
    }

    private static void flushIfNeeded() {
        String data;
        synchronized (LOCK) {
            if (BUFFER.length() == 0) return;
            long now = System.currentTimeMillis();
            if (BUFFER.length() < FLUSH_THRESHOLD_BYTES && now - lastFlushAt < FLUSH_INTERVAL_MS) return;
            data = BUFFER.toString();
            BUFFER.setLength(0);
            lastFlushAt = now;
        }
        writeToFile(data);
    }

    private static void writeToFile(String data) {
        try {
            if (context == null) return;
            File f = new File(context.getFilesDir(), "tip.txt");
            if (f.length() > maxFileSize) rotate(f);
            try (FileOutputStream fos = new FileOutputStream(f, true)) {
                fos.write(data.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 超限轮转：tip.txt -> tip.1.txt -> tip.2.txt（删除最老，共保留约 maxFileSize x 3） */
    private static void rotate(File f) {
        try {
            File dir = f.getParentFile();
            //noinspection ResultOfMethodCallIgnored
            new File(dir, "tip." + MAX_ROTATED + ".txt").delete();
            for (int i = MAX_ROTATED - 1; i >= 1; i--) {
                File src = new File(dir, "tip." + i + ".txt");
                if (src.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    src.renameTo(new File(dir, "tip." + (i + 1) + ".txt"));
                }
            }
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(new File(dir, "tip.1.txt"));
        } catch (Throwable ignored) {
        }
    }

    // ---------- 配置热加载 ----------

    private static void reloadConfigIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastCfgCheckAt < 60000) return;
        lastCfgCheckAt = now;
        loadConfig();
    }

    private static void loadConfig() {
        try {
            if (context == null) return;
            File f = new File(context.getFilesDir(), CONFIG_NAME);
            if (!f.exists()) return;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            }
            JSONObject cfg = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            String lv = cfg.optString("logLevel", "");
            if ("debug".equals(lv)) logLevel = DEBUG;
            else if ("info".equals(lv)) logLevel = INFO;
            else if ("warn".equals(lv)) logLevel = WARN;
            else if ("error".equals(lv)) logLevel = ERROR;
            int mb = cfg.optInt("maxFileSizeMb", 0);
            if (mb > 0) maxFileSize = (long) mb * 1024 * 1024;
        } catch (Throwable ignored) {
        }
    }

    // ---------- 错误通知（ERROR 级 + 3s 限频） ----------

    private static void notifyError(String msg) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastNotifyAt < 3000) return;
            lastNotifyAt = now;
            if (context == null) return;
            if (notificationManager == null) {
                notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager == null) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager.createNotificationChannel(new NotificationChannel(
                            CHANNEL_ID, "模块错误", NotificationManager.IMPORTANCE_HIGH));
                }
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            String text = msg != null && msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
            b.setContentTitle("模块错误").setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true);
            notificationManager.notify(2, b.build());
        } catch (Throwable ignored) {
        }
    }

    // ---------- 堆栈（仅 ERROR 附带） ----------

    private static String currentStack() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder("--- Stack Trace ---");
        for (StackTraceElement e : st) {
            if (e.getClassName().endsWith(".OutLog")) continue;
            sb.append("\n  at ").append(e);
        }
        return sb.append("\n--- End Stack Trace ---").toString();
    }
}


