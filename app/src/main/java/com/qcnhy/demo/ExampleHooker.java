package com.qcnhy.demo;

import static com.qcnhy.demo.MainModule.context;
import static com.qcnhy.demo.OutLog.outlog;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

/**
 * 通用的 Hook 类模板示例
 * 使用时复制此类并修改：
 * 1. 类名（如 MyMethodHooker）
 * 2. before/after 中的具体逻辑
 * 3. 如需传递上下文，before 可返回自定义对象
 */
@XposedHooker
public class ExampleHooker implements XposedInterface.Hooker {

    /**
     * 反射调用静态方法
     *
     * @param className  完整类名
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @param args       方法参数
     * @return 方法返回值，失败返回 null
     */
    public static Object invokeStaticMethod(String className, String methodName,
            Class<?>[] paramTypes, Object[] args) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> clazz = classLoader.loadClass(className);
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            outlog("invokeStaticMethod cause: " + e.getCause());
            return null;
        } catch (Exception e) {
            outlog("invokeStaticMethod error: " + e);
            return null;
        }
    }

    /**
     * 获取静态字段值
     *
     * @param className 完整类名
     * @param fieldName  字段名
     * @return 字段值，失败返回 null
     */
    public static Object getStaticField(String className, String fieldName) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> clazz = classLoader.loadClass(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            outlog("getStaticField error: " + e);
            return null;
        }
    }

    /**
     * 获取实例字段值
     *
     * @param obj       对象实例
     * @param fieldName 字段名
     * @return 字段值，失败返回 null
     */
    public static Object getField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            outlog("getField error: " + e);
            return null;
        }
    }

    /**
     * 设置实例字段值
     *
     * @param obj       对象实例
     * @param fieldName 字段名
     * @param value     新值
     */
    public static void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            outlog("setField error: " + e);
        }
    }

    /**
     * 打印对象所有字段（含父类）
     *
     * @param obj 对象实例
     */
    public static void dumpAllFields(Object obj) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            StringBuilder sb = new StringBuilder("dumpAllFields: ");
            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    sb.append("[").append(field.getName())
                            .append("=").append(value).append("] ");
                } catch (Exception e) {
                    sb.append("[").append(field.getName())
                            .append("=").append(e.getClass().getSimpleName()).append("] ");
                }
            }
            outlog(sb.toString());
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 方法执行前回调
     * 可用于：修改参数、跳过原方法、记录调用
     */
    @BeforeInvocation
    public static void before(@NonNull XposedInterface.BeforeHookCallback callback) {
        // 获取参数
        Object[] args = callback.getArgs();
        outlog("before args: " + Arrays.toString(args));

        // 获取 this 对象（静态方法为 null）
        Object thisObj = callback.getThisObject();
        if (thisObj != null) {
            outlog("thisObject: " + thisObj.getClass().getName());
        }

        // 示例：修改第一个参数
        // args[0] = "modified value";

        // 示例：跳过原方法并返回结果
        // callback.returnAndSkip("custom result");

        // 示例：抛出异常跳过原方法
        // callback.throwAndSkip(new RuntimeException("blocked"));
    }

    /**
     * 方法执行后回调
     * 可用于：修改返回值、记录结果、异常处理
     */
    @AfterInvocation
    public static void after(@NonNull XposedInterface.AfterHookCallback callback) {
        // 获取返回值
        Object result = callback.getResult();
        outlog("after result: " + result);

        // 获取异常（如有）
        Throwable throwable = callback.getThrowable();
        if (throwable != null) {
            outlog("after throwable: " + throwable);
        }

        // 是否被 before 跳过
        if (callback.isSkipped()) {
            outlog("method was skipped");
        }

        // 示例：修改返回值
        // callback.setResult("modified result");
    }
}