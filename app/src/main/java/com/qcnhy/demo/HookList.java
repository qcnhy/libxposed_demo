package com.qcnhy.demo;
//hook类
import static com.qcnhy.demo.MainModule.context;
import static com.qcnhy.demo.OutLog.outlog;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class HookList extends XposedModule {
    /**
     * Instantiates a new Xposed module.<br/>
     * When the module is loaded into the target process, the constructor will be called.
     *
     * @param base  The implementation interface provided by the framework, should not be used by the module
     * @param param Information about the process in which the module is loaded
     */
    public HookList(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base, param);
    }

    public Class<?> findClassOrLog(String className, ClassLoader classLoader) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            outlog("✅ Found class: " + className);
            return clazz;
        } catch (ClassNotFoundException e) {
            outlog("❌ Class not found: " + className + " -> " + e);
            return null;
        }
    }
//Hook 入口类示例 查找类名和方法进行Hook
    public void hookNoUtilsAssembleRequest(XposedModuleInterface.PackageLoadedParam param) {
        try {
            final String className = "com.infothinker.gzmetro.util.nohttp.NoUtils";
            ClassLoader classLoader = param.getClassLoader(); //优先使用
//            ClassLoader classLoader = context.getClassLoader();
            Class<?> clazz = findClassOrLog(className, classLoader);
            if (clazz == null) return;

            final String methodName = "assembleRequest";
            Method method = clazz.getDeclaredMethod(
                    methodName,
                    Object.class,      // 第一个参数
                    boolean.class,     // 第二个参数
                    String.class       // 第三个参数
            );
            outlog("✅ Found method: " + method);

            hook(method, assembleRequestHooker.class);
            outlog("🎯 Hooking assembleRequest completed");
        } catch (Exception e) {
            outlog("❗ Error hooking assembleRequest: " + e);
        }
    }
}
