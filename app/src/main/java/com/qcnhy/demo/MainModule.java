package com.qcnhy.demo;


import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import static com.qcnhy.demo.OutLog.outlog;

import androidx.annotation.NonNull;

//import org.apache.commons.lang3.ClassUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    static MainModule mainModule;
    static Context context;
    HookList hookList;

    public MainModule(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base, param);

        hookList = new HookList(base, param);
//        initNotificationManager();
        mainModule = this;
        log("MainModule at " + param.getProcessName());//类尚未完全初始化 不能调用outlog
    }

    public static String getStackTrace(Throwable e) {//打印堆栈字符串日志
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    // 反射获取系统 Context
    public static Context getSystemContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getMethod("currentApplication");
            return (Context) currentApplication.invoke(null);
        } catch (Exception e) {
            outlog(getStackTrace(e));//打印无法获取系统上下文堆栈日志
            return null;
        }
    }




    private void printClassLoaderInfo(PackageLoadedParam param) {
        try {
            ClassLoader cl = param.getClassLoader();
            outlog("[getClassLoader] => " + "class=" + cl.getClass().getName() + ", " + "toString=" + cl.toString() + ", " + "hash=" + cl.hashCode());
        } catch (Exception e) {
            outlog("[getClassLoader] ❌ error: " + e);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ClassLoader dcl = param.getDefaultClassLoader();
                outlog("[getDefaultClassLoader] => " + "class=" + dcl.getClass().getName() + ", " + "toString=" + dcl.toString() + ", " + "hash=" + dcl.hashCode());
            } catch (Exception e) {
                outlog("[getDefaultClassLoader] ❌ error: " + e);
            }
        } else {
            outlog("[getDefaultClassLoader] ⚠️ 不支持 (系统版本 < Q)");
        }
    }

    private String getCurrentProcessName(ClassLoader cl) {
        try {
            Class<?> activityThread = cl.loadClass("android.app.ActivityThread");
            Method currentProcessNameMethod = activityThread.getDeclaredMethod("currentProcessName");
            currentProcessNameMethod.setAccessible(true);
            return (String) currentProcessNameMethod.invoke(null);
        } catch (Exception e) {
            outlog("获取进程名失败: " + e.getMessage());
            return "";
        }
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {

        super.onPackageLoaded(param);
        outlog("welcome");
        String processName = getCurrentProcessName(param.getClassLoader());
        outlog("onPackageLoaded package: " + param.getPackageName() + ", process: " + processName);
        outlog("param classloader is " + param.getClassLoader());
        if (context == null) {
            context = getSystemContext();
        } else {
            outlog("context classloader is " + context.getClassLoader());
        }
        outlog("module apk path: " + this.getApplicationInfo().sourceDir);
        printClassLoaderInfo(param);
        outlog("----------");
        if (processName.equals("com.autonavi.minimap")) {
            if (param.isFirstPackage()) {
                outlog("First package ...)");
                try {
                    outlog("Trying to find classes & methods to hook");
//                    final String className = param.getPackageName() + ".MainActivity";
                    outlog(Arrays.toString(param.getClassLoader().getClass().getDeclaredFields()));//查看参数的所有属性
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
//                            hookList.hookNoUtilsAssembleRequest(param);
//                            hookList.hookGetQrCodeStr(param);
                        } catch (Exception e) {
                            outlog("Delayed hook failed: " + e);
                        }
                    }, 3000); // 延迟 3 秒
//                Class<?> clazz = ClassUtils.getClass(param.getClassLoader(), className, false);
//                if (clazz == null) {
//                    outlog("Failed to find the class :: " + className);
//                    return;
//                }
//                outlog("Found the class  to hook  :: " + clazz);
//                final String methodName = "J0";
//                Method method = clazz.getDeclaredMethod(methodName);
//                outlog("Found the method to be hooked :: " + method);
//                hook(method, MyHooker.class);
                    outlog("hooking completed :)");
                } catch (Exception ex) {
                    outlog("Error in finding class method & hooking :: " + ex);
                }
            } else {
                outlog("Not the first package...");
                // we can hook other package loaded by the Android app here if we need to do so.
            }
        } else if (processName.startsWith("com.autonavi.minimap:")) {
            outlog("子进程，执行子进程hook或者跳过");
        } else {
            outlog("未知进程，忽略");
        }

    }

    @XposedHooker
    static public class MyHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static MyHooker beforeInvocation(BeforeHookCallback callback) {
            outlog("beforeInvocation values :: " + Arrays.toString(callback.getArgs()));
            return new MyHooker();

        }

        @AfterInvocation
        public static void afterInvocation(AfterHookCallback callback, MyHooker context) {
            outlog("afterInvocation callback args: " + Arrays.toString(callback.getArgs()));
            outlog("afterInvocation val: " + context.toString());
//            HashMap<String, Object> map = new HashMap<>();
//            map.put("noAd", true);    `
//            callback.setResult(map);
        }
    }

}
