package com.qcnhy.demo;
//示例

import static android.util.Log.getStackTraceString;
import static com.qcnhy.demo.MainModule.context;
import static com.qcnhy.demo.OutLog.outlog;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@XposedHooker
public class assembleRequestHooker implements XposedInterface.Hooker {

    /**
     * 反射调用静态方法
     *
     * @param className  完整类名，比如 "com.infothinker.gzmetro.util.JSONUtil"
     * @param methodName 方法名，比如 "toString"
     * @param paramTypes 参数类型数组，比如 new Class[]{Object.class}
     * @param args       方法参数，比如 new Object[]{obj}
     * @return 方法返回值，调用失败返回 null
     */
    public static Object invokeStaticMethod(String className, String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> clazz = classLoader.loadClass(className);

            // 推荐用 getDeclaredMethod，防止方法是 private/protected
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true); // 反射私有方法时要加这句
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            outlog("invokeStaticMethod error cause:" + cause + " " + getStackTraceString(e));
//                cause.printStackTrace();  // 打印目标方法内部异常的详细信息
            return null;
        } catch (Exception e) {
            outlog("invokeStaticMethod error:" + e);
            return null;
        }
    }

    public static Object getStaticField(String className, String fieldName) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> clazz = classLoader.loadClass(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            outlog("getStaticField error:" + e);
            return null;
        }
    }
    private static void dumpAllFields(Object obj) {// 递归获取父类的属性
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            StringBuilder sb = new StringBuilder("dumpAllFields对象字段信息: ");
            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    String valueStr = (value != null) ? value.toString() : "null";
                    sb.append("[字段名: ").append(field.getName())
                            .append("，类型: ").append(field.getType().getName())
                            .append("，值: ").append(valueStr).append("] ");
                } catch (Exception e) {
                    sb.append("[字段名: ").append(field.getName())
                            .append("，读取失败，异常: ").append(e.getClass().getSimpleName()).append("] ");
                }
            }
            outlog(sb.toString());

            clazz = clazz.getSuperclass(); // 继续向上查找父类字段
        }
    }
    @BeforeInvocation
    public static void before(@NonNull XposedInterface.BeforeHookCallback callback) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InstantiationException {

        outlog("assembleRequest before args: " + Arrays.toString(callback.getArgs()));
        ClassLoader classLoader = context.getClassLoader();// 获取当前进程的ClassLoader
        //开始重写
        Object obj = callback.getArgs()[0];
        boolean z = (boolean) callback.getArgs()[1];
        String str = (String) callback.getArgs()[2];

        String string;
        if (obj instanceof JSONObject || obj instanceof String) {
            string = obj.toString();
        } else {
            string = (String) invokeStaticMethod("com.infothinker.gzmetro.util.JSONUtil", "toString", new Class[]{Object.class}, new Object[]{obj});

        }

        TreeMap<String, String> treeMap = new TreeMap<>();
        String strRandomAESSecret = "";
        String strSign = "";

        if (z) {
            try {
                strRandomAESSecret = (String) invokeStaticMethod("net.bingo.secluded.encrypt.EncryptUtils", "randomAESSecret", null, null);


                try {
                    string = (String) invokeStaticMethod("com.infothinker.gzmetro.nps.CommonUtils", "aesEncrypt", new Class[]{String.class, String.class}, new Object[]{string, strRandomAESSecret});
// 反射调用 MetroApp.getAppInstance()
                    Object appInstance = invokeStaticMethod("com.infothinker.gzmetro.MetroApp", "getAppInstance", new Class[]{}, new Object[]{});

// 反射调用 MetroSo.getB2(appInstance)
                    assert appInstance != null;
                    Object b2 = invokeStaticMethod("net.bingo.secluded.MetroSo", "getB2", new Class[]{android.content.Context.class}, new Object[]{appInstance});


                    String strEncryptByPublicKey = (String) invokeStaticMethod("net.bingo.secluded.encrypt.RSAUtils", "encryptByPublicKey", new Class[]{String.class, String.class}, new Object[]{strRandomAESSecret, b2});


                    treeMap.put("encrydata", "YP");
                    treeMap.put("encrytype", "AES");
                    treeMap.put("encrykey", strEncryptByPublicKey);

                    HashMap<String, String> map = new HashMap<>();
                    map.put("clientip", "0.0.0.0");
                    map.put("deviceid", "b7e7b772eaaf4fd180ae345820bab5a5");

                    String mapJson = (String) invokeStaticMethod("com.infothinker.gzmetro.util.JSONUtil", "toString", new Class<?>[]{Object.class}, new Object[]{map});


                    String extendparamsEncrypted = (String) invokeStaticMethod("com.infothinker.gzmetro.nps.CommonUtils", "aesEncrypt", new Class<?>[]{String.class, String.class}, new Object[]{mapJson, strRandomAESSecret});

                    treeMap.put("extendparams", extendparamsEncrypted);

                } catch (Exception e) {
                    invokeStaticMethod("com.infothinker.gzmetro.util.MLog", "exception", new Class[]{Throwable.class}, new Object[]{e});


                    // 填充基本参数
                    treeMap.put("partnerid", (String) getStaticField("com.infothinker.gzmetro.define.Define", "PARTNER_ID"));
                    treeMap.put("appid", (String) getStaticField("com.infothinker.gzmetro.define.Define", "APP_ID_FOR_REQUEST"));
                    treeMap.put("signtype", "RSA");
                    treeMap.put((String) getStaticField("com.infothinker.gzmetro.define.Param", "TIMESTAMP"), (String) invokeStaticMethod("com.infothinker.gzmetro.util.TimeUtils", "getNowTimeInFormat", new Class<?>[0], new Object[0]));
                    treeMap.put("access_token", "");
                    treeMap.put("version", str);
                    treeMap.put("data", string);
                    treeMap.put("devicetype", "IOS");
                    treeMap.put("deviceid", "b7e7b772eaaf4fd180ae345820bab5a5");
                    treeMap.put("devicemodel", "iPhone 14 Pro Max_18.5");
                    treeMap.put("appver", (String) invokeStaticMethod("com.infothinker.gzmetro.util.VirtualNativeUtils", "getVersionName", new Class<?>[0], new Object[0]));

                    // 1. 调用 CommonUtils.getParamSortStr(treeMap, null)
                    Object paramSortResult = invokeStaticMethod("com.infothinker.gzmetro.nps.CommonUtils", "getParamSortStr", new Class<?>[]{Map.class, String.class}, new Object[]{treeMap, null});
                    String paramSortStr = null;
                    if (paramSortResult instanceof Object[]) {
                        Object[] arr = (Object[]) paramSortResult;
                        paramSortStr = (String) arr[1];
                    }

// 2. 调用 MetroSo.getA1(MetroApp.getAppInstance())
                    Object metroAppInstance = invokeStaticMethod("com.infothinker.gzmetro.MetroApp", "getAppInstance", new Class<?>[0], new Object[0]);
                    String a1Key = (String) invokeStaticMethod("net.bingo.secluded.MetroSo", "getA1", new Class<?>[]{android.content.Context.class}, new Object[]{metroAppInstance});

// 3. 调用 RSAUtils.sign(byte[], String)
                    byte[] dataBytes = paramSortStr != null ? paramSortStr.getBytes() : new byte[0];
                    strSign = (String) invokeStaticMethod("net.bingo.secluded.encrypt.RSAUtils", "sign", new Class<?>[]{byte[].class, String.class}, new Object[]{dataBytes, a1Key});

                    treeMap.put("sign", strSign);

                    Class<?> clazz = classLoader.loadClass("com.infothinker.gzmetro.nps.RequestData");
                    Object requestData = clazz.getDeclaredConstructor().newInstance();

                    Object json = invokeStaticMethod("com.infothinker.gzmetro.util.JSONUtil", "toJson", new Class[]{Object.class}, new Object[]{treeMap});

                    Method setDataMethod = clazz.getDeclaredMethod("setData", JSONObject.class);
                    setDataMethod.invoke(requestData, json);

                    Method setEncryptMethod = clazz.getDeclaredMethod("setEncrypt", boolean.class);
                    setEncryptMethod.invoke(requestData, z);

                    Method setSecretMethod = clazz.getDeclaredMethod("setSecret", String.class);
                    setSecretMethod.invoke(requestData, strRandomAESSecret);
                    callback.returnAndSkip(requestData);

                }
            } catch (Exception e) {
                strRandomAESSecret = "";
            }
        }

        // 这里是 z == false 或 AES加密成功的情况下，补全参数
        treeMap.put("partnerid", (String) getStaticField("com.infothinker.gzmetro.define.Define", "PARTNER_ID"));
        treeMap.put("appid", (String) getStaticField("com.infothinker.gzmetro.define.Define", "APP_ID_FOR_REQUEST"));
        treeMap.put("signtype", "RSA");
        treeMap.put((String) getStaticField("com.infothinker.gzmetro.define.Param", "TIMESTAMP"), (String) invokeStaticMethod("com.infothinker.gzmetro.util.TimeUtils", "getNowTimeInFormat", new Class<?>[0], new Object[0]));
        treeMap.put("access_token", "");
        treeMap.put("version", str);
        treeMap.put("data", string);
        treeMap.put("devicetype", "IOS");
        treeMap.put("deviceid", "b7e7b772eaaf4fd180ae345820bab5a5");
        treeMap.put("devicemodel", "iPhone 14 Pro Max_18.5");
        treeMap.put("appver", (String) invokeStaticMethod("com.infothinker.gzmetro.util.VirtualNativeUtils", "getVersionName", new Class<?>[0], new Object[0]));

        try {
            String[] paramSortStrArr = (String[]) invokeStaticMethod("com.infothinker.gzmetro.nps.CommonUtils", "getParamSortStr", new Class<?>[]{Map.class, String.class}, new Object[]{treeMap, null});
            Object appInstance = invokeStaticMethod("com.infothinker.gzmetro.MetroApp", "getAppInstance", new Class<?>[0], new Object[0]);
            String a1 = (String) invokeStaticMethod("net.bingo.secluded.MetroSo", "getA1", new Class<?>[]{android.content.Context.class}, new Object[]{appInstance});
            assert paramSortStrArr != null;
            strSign = (String) invokeStaticMethod("net.bingo.secluded.encrypt.RSAUtils", "sign", new Class<?>[]{byte[].class, String.class}, new Object[]{paramSortStrArr[1].getBytes(), a1});

        } catch (Exception e) {
            invokeStaticMethod("com.infothinker.gzmetro.util.MLog", "exception", new Class<?>[]{Throwable.class}, new Object[]{e});

        }
        treeMap.put("sign", strSign);

        // 创建 RequestData 实例
        Class<?> requestDataClass = classLoader.loadClass("com.infothinker.gzmetro.nps.RequestData");
        Object requestData = requestDataClass.getDeclaredConstructor().newInstance();

// 用 invokeStaticMethod 调用静态 JSONUtil.toJson 方法
        Object json = invokeStaticMethod("com.infothinker.gzmetro.util.JSONUtil", "toJson", new Class<?>[]{Map.class}, new Object[]{treeMap});

// 调用实例方法 setData
        Method setDataMethod = requestDataClass.getDeclaredMethod("setData", JSONObject.class);
        setDataMethod.setAccessible(true);
        setDataMethod.invoke(requestData, json);

// 调用实例方法 setEncrypt
        Method setEncryptMethod = requestDataClass.getDeclaredMethod("setEncrypt", boolean.class);
        setEncryptMethod.setAccessible(true);
        setEncryptMethod.invoke(requestData, z);

// 调用实例方法 setSecret
        Method setSecretMethod = requestDataClass.getDeclaredMethod("setSecret", String.class);
        setSecretMethod.setAccessible(true);
        setSecretMethod.invoke(requestData, strRandomAESSecret);
        outlog("assembleRequestbefore result: { data: " + json + ", isEncrypt: " + z + ", secret: " + strRandomAESSecret + " }");
        callback.returnAndSkip(requestData);
//            return new MyHooker(requestData);
//            return new MyHooker();
    }

    @AfterInvocation
    public static void after(@NonNull XposedInterface.AfterHookCallback callback) throws NoSuchFieldException, IllegalAccessException {

        Object result = callback.getResult();
        Class<?> clazz = result.getClass();
        // 反射获取字段值
        Field dataField = clazz.getDeclaredField("data");
        dataField.setAccessible(true);
        Object data = dataField.get(result);

        Field encryptField = clazz.getDeclaredField("isEncrypt");
        encryptField.setAccessible(true);
        Object isEncrypt = encryptField.get(result);

        Field secretField = clazz.getDeclaredField("secret");
        secretField.setAccessible(true);
        Object secret = secretField.get(result);

// 输出字段值
        outlog("assembleRequestafter result: { data: " + data + ", isEncrypt: " + isEncrypt + ", secret: " + secret + " }");
//        outlog("afterInvocation finished");
    }
}

