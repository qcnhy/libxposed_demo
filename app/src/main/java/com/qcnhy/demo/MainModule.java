package com.qcnhy.demo;


import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;

//import org.apache.commons.lang3.ClassUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint("PrivateApi")
public class MainModule extends XposedModule {

    private static MainModule mainModule;
    private Context context;

    public MainModule(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base, param);
        log("MainModule at " + param.getProcessName());

//        initNotificationManager();
        mainModule = this;
    }

    private NotificationManager notificationManager;
    private static final String CHANNEL_ID = "log_channel";

    // 反射获取系统 Context
    public static Context getSystemContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getMethod("currentApplication");
            return (Context) currentApplication.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void initContext() {

        if (context == null) {
            log("Failed to get system context");
            context = getSystemContext();
        }
    }

    private void initNotificationManager() {
        if (context == null) {

            return;
        }
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            log("Failed to get NotificationManager");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "日志通知", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void logToNotification(String msg) {
        if (notificationManager == null) initNotificationManager();
        if (notificationManager == null || context == null) return;

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle("Xposed日志").setContentText(msg).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }

    // 你的 log 方法示例，追加两种输出
    public void outlog(String msg) {
        // 控制台输出
        System.out.println(msg);

        // 通知栏显示
        logToNotification(msg);

        // 写文件
        writeLogToFile(msg);
    }

    private void writeLogToFile(String msg) {
        if (context == null) {

            return;
        }
        writeInternalStorage(msg);
        writeExternalStorage(msg);
    }

    private void writeInternalStorage(String msg) {

        try {

            File file = new File(context.getFilesDir(), "xposed_log.txt");
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write((msg + "\n").getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeExternalStorage(String msg) {
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalDir = context.getExternalFilesDir(null); // 应用私有外部目录
                if (externalDir == null) return; // 保护

                File file = new File(externalDir, "xposed_log.txt");
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    fos.write((msg + "\n").getBytes());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {

        super.onPackageLoaded(param);
        initContext();
        outlog("success");
        log("onPackageLoaded: " + param.getPackageName());
        log("param classloader is " + param.getClassLoader());
        log("module apk path: " + this.getApplicationInfo().sourceDir);
        log("----------");
        if (param.isFirstPackage()) {
            log("First package ...)");
            try {
                log("Trying to find classes & methods to hook");
                final String className = param.getPackageName() + ".MainActivity";
                log(Arrays.toString(param.getClassLoader().getClass().getDeclaredFields()));
//                Class<?> clazz = ClassUtils.getClass(param.getClassLoader(), className, false);
//                if (clazz == null) {
//                    log("Failed to find the class :: " + className);
//                    return;
//                }
//                log("Found the class  to hook  :: " + clazz);
//                final String methodName = "J0";
//                Method method = clazz.getDeclaredMethod(methodName);
//                log("Found the method to be hooked :: " + method);
//                hook(method, MyHooker.class);
                log("hooking completed :)");
            } catch (Exception ex) {
                log("Error in finding class method & hooking :: " + ex);
            }
        } else {
            log("Not the first package...");
            // we can hook other package loaded by the Android app here if we need to do so.
        }
    }

    @XposedHooker
    static public class MyHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static MyHooker beforeInvocation(BeforeHookCallback callback) {
            mainModule.log("beforeInvocation values :: " + Arrays.toString(callback.getArgs()));
            return new MyHooker();
        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, MyHooker context) {
            mainModule.log("afterInvocation callback args: " + Arrays.toString(callback.getArgs()));
            mainModule.log("afterInvocation val: " + context.toString());
            HashMap<String, Object> map = new HashMap<>();
            map.put("noAd", true);
            callback.setResult(map);
        }
    }

}
