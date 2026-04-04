# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android Xposed module demo project using libxposed-api. The module demonstrates hooking techniques for Android applications, with example hooks targeting specific apps (e.g., `com.infothinker.gzmetro`).

## Build Commands

```bash
# Build the project
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run lint checks
./gradlew lint
```

## Architecture

### Decompiled Code
- [decompiled/](decompiled/) - Store decompiled APK source code for analysis (jadx/apktool output)

### Entry Point
- [MainModule.java](app/src/main/java/com/qcnhy/demo/MainModule.java) - Main entry point extending `XposedModule`
  - Constructor receives `XposedInterface` and `ModuleLoadedParam`
  - `onPackageLoaded()` handles hook logic when module is loaded into target process

### Hook Classes
- [HookList.java](app/src/main/java/com/qcnhy/demo/HookList.java) - Contains hook method definitions
- [ExampleHooker.java](app/src/main/java/com/qcnhy/demo/ExampleHooker.java) - Hooker template with common reflection utilities

### Utilities
- [OutLog.java](app/src/main/java/com/qcnhy/demo/OutLog.java) - Multi-output logging

### Log Locations
`OutLog.outlog()` outputs to (context is from hooked app, not module):
- Console: `System.out.println`
- Notification: System notification bar
- Internal storage: `/data/data/{hooked_app_package}/files/tip.txt`
- External storage: `/storage/emulated/0/Android/data/{hooked_app_package}/files/tip.txt`
- Xposed log: Framework log viewer

**Note:** `{hooked_app_package}` is the target app being hooked, not the module package.

### Log Clear Switch
In [OutLog.java](app/src/main/java/com/qcnhy/demo/OutLog.java), set `CLEAR_LOG_ON_START`:
- `true`: Clear log files on each module load (default)
- `false`: Keep historical logs (append mode)

### Stack Trace
`OutLog.outlog()` automatically prints stack trace with each log message. Use stack traces to:
- Identify the complete call path from entry point to target method
- Find upstream hook points that intercept multiple calls at once
- Understand the execution flow and class relationships
- Discover hidden methods that call your target method

**Tip:** When looking for hook points, trace from the bottom of the stack upward. Hooking earlier entry points (closer to the top) often provides better coverage and more control over the execution flow.

### Xposed Configuration
- [module.prop](app/src/main/resources/META-INF/xposed/module.prop) - Module metadata (API version)
- [scope.list](app/src/main/resources/META-INF/xposed/scope.list) - Target app package names
- [java_init.list](app/src/main/resources/META-INF/xposed/java_init.list) - Entry class name

## Dependencies

- `libxposed-api:api` - Core Xposed API (git submodule)
- `libxposed-api:checks` - Lint checks for Xposed development
- `libxposed-compat` - Compatibility layer

## Hook Class Template

Create a new hooker class implementing `XposedInterface.Hooker`:

```java
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.XposedHooker;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.AfterInvocation;

@XposedHooker
public class MyHooker implements XposedInterface.Hooker {

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        // Get args: callback.getArgs()
        // Get this object: callback.getThisObject() (null for static methods)
        // Skip and return: callback.returnAndSkip(result)
        // Throw exception: callback.throwAndSkip(throwable)
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        // Get result: callback.getResult()
        // Set result: callback.setResult(result)
        // Get throwable: callback.getThrowable()
        // Check if skipped: callback.isSkipped()
    }
}
```

**With context passing between before/after:**

```java
@XposedHooker
public class MyHookerWithContext implements XposedInterface.Hooker {

    @BeforeInvocation
    public static MyContext before(XposedInterface.BeforeHookCallback callback) {
        // Save state between before/after
        return new MyContext(callback.getArgs()[0]);
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback, MyContext ctx) {
        // Use ctx saved from before
    }
}
```

**Register hook in HookList or MainModule:**

```java
Method method = clazz.getDeclaredMethod("methodName", ParamType.class);
hook(method, MyHooker.class);  // XposedModule's hook method
```

**Common reflection utilities:**

```java
// Invoke static method
public static Object invokeStaticMethod(String className, String methodName,
    Class<?>[] paramTypes, Object[] args) {
    Class<?> clazz = classLoader.loadClass(className);
    Method method = clazz.getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    return method.invoke(null, args);
}

// Get static field
public static Object getStaticField(String className, String fieldName) {
    Class<?> clazz = classLoader.loadClass(className);
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(null);
}
```

## Target App Configuration

Modify `scope.list` to change target app package. Update `onPackageLoaded()` in MainModule.java to match the process name check.

## Creating New Xposed Module from This Demo

1. **build.gradle.kts** - Update namespace and applicationId:
   ```kotlin
   namespace = "com.qcnhy.新项目名"
   applicationId = "com.qcnhy.新项目名"
   ```

2. **Package structure** - Create new package and move files:
   ```bash
   mkdir -p app/src/main/java/com/qcnhy/新项目名
   mv app/src/main/java/com/qcnhy/demo/*.java app/src/main/java/com/qcnhy/新项目名/
   rm -rf app/src/main/java/com/qcnhy/demo
   ```

3. **Java files** - Update package declaration in all Java files:
   ```java
   // Change from:
   package com.qcnhy.demo;
   // To:
   package com.qcnhy.新项目名;
   ```

4. **strings.xml** - Update app name:
   ```xml
   <string name="app_name">新模块名称</string>
   ```

5. **scope.list** - Set target app package name (e.g., `com.cn21.ecloud`)

6. **java_init.list** - Update entry class with new package:
   ```
   com.qcnhy.新项目名.MainModule
   ```

7. **MainModule.java** - Update process name check in `onPackageLoaded()`:
   ```java
   if (processName.equals("目标应用包名")) {
       // Hook logic here
   }
   ```

## Troubleshooting

If module is not loaded at all, try in LSPosed:
1. Toggle module off → then back on
2. Open module scope settings, re-check target app
3. If still not working, reboot device

## Development Workflow

After each modification, increment version number in `app/build.gradle.kts`:
- `versionCode`: Increment by 1
- `versionName`: Follow x.y.z format
  - x: Major changes (architecture, incompatible changes)
  - y: Feature updates (new features, modules)
  - z: Bug fixes (fixes, small tweaks)

After each build and install:
1. Force stop the target app (`adb shell am force-stop {package}`)
2. Clear log files if needed
3. Do NOT auto-launch the app - user will open manually