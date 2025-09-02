package com.qcnhy.demo;

import static com.qcnhy.demo.MainModule.context;
import static com.qcnhy.demo.MainModule.mainModule;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class OutLog {

    private static NotificationManager notificationManager;
    private static final String CHANNEL_ID = "log_channel";
    private static void initNotificationManager() {
        if (context == null) {

            return;
        }
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            outlog("Failed to get NotificationManager");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "日志通知", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void logToNotification(String msg) {
        if (notificationManager == null) initNotificationManager();
        if (notificationManager == null || context == null) return;

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle("温馨提示").setContentText(msg).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }

    // 你的 log 方法示例，追加两种输出
    public static void outlog(String msg) {
        // 控制台输出
        System.out.println(msg);

        // 通知栏显示
        logToNotification(msg);

        // 写文件
        writeLogToFile(msg);

        mainModule.log(msg);
    }

    private static void writeLogToFile(String msg) {
        if (context == null) {

            return;
        }
        writeInternalStorage(msg);
        writeExternalStorage(msg);
    }

    private static void writeInternalStorage(String msg) {

        try {

            File file = new File(context.getFilesDir(), "tip.txt");
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write((msg + "\n").getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeExternalStorage(String msg) {
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalDir = context.getExternalFilesDir(null); // 应用私有外部目录
                if (externalDir == null) return; // 保护

                File file = new File(externalDir, "tip.txt");
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    fos.write((msg + "\n").getBytes());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
