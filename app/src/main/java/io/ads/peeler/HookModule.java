package io.ads.peeler;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 红果短剧 / 红果漫剧 —— 中插广告拦截（SDK 配置层）。
 *
 * <p>只做一件事：Gson 解析出广告设置对象后，把三个开关置 false：
 * <ul>
 *   <li>{@code shortVideoAdEnable}：普通中插（切下一集时）</li>
 *   <li>{@code landscapeInsertAdEnable}：横屏中插（手机横过来全屏看剧时）</li>
 *   <li>{@code enableMultiSeriesFlowAd}：短剧信息流总开关</li>
 * </ul>
 *
 * <p>不依赖 DexKit、不需要混淆类名、不 hook {@code checkAdAvailable}。
 * 识别方式是**字段存在性**，不是类名：只要 Gson 返回的对象里出现上述字段，
 * 就按字段路径逐级 get-or-create 后置 false。
 *
 * <p>代价：不处理首页推荐流 {@code recommend_video}、创作者 {@code creator_ad}、
 * 暂停广告、Banner、评论区广告。目标场景是“连续看剧、切下一集不出广告”。
 */
public class HookModule extends XposedModule {

    /** 构建标识：直接取 APK 的 versionName，避免与 build.gradle 漂移。 */
    private static final String MODULE_BUILD = "peeler-" + BuildConfig.VERSION_NAME;

    /** 命中审计：每条 hook 前 3 次全记，之后每 200 次记一行。 */
    private static final int AUDIT_MAX_LOGS = 3;
    private static final int AUDIT_EVERY = 200;

    private final Map<String, Integer> hitCounts = new ConcurrentHashMap<String, Integer>();
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<String, Field>();
    private final Map<Class<?>, Boolean> adConfigCache = new ConcurrentHashMap<Class<?>, Boolean>();
    private final Set<String> reportedFields = ConcurrentHashMap.newKeySet();
    private final List<String> installed = new ArrayList<String>();
    private final List<String> skipped = new ArrayList<String>();

    private volatile String processName = "<unknown>";
    private volatile boolean sdkHooked = false;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) {
            return;
        }
        String pkg = param.getPackageName();
        // 只处理主进程；:push / :downloader 等子进程不挂 hook。
        if (!pkg.equals(processName)) {
            return;
        }

        hookSdkSettings(param.getClassLoader());

        String msg = "build=" + MODULE_BUILD + ", pkg=" + pkg
                + ", installed=" + installed.size()
                + " skipped=" + skipped.size()
                + (skipped.isEmpty() ? "" : " lost=" + skipped)
                + ", hooks=sdk:" + (sdkHooked ? 1 : 0);
        log(skipped.isEmpty() ? Log.INFO : Log.WARN, null, msg);
    }

    // ----------------------------------------------------------- SDK 配置层

    /**
     * 同时挂 Gson 的两个重载：
     * {@code fromJson(String, Type)} 与 {@code fromJson(String, Class)}。
     * 宿主换解析方式时还有一条能用。
     */
    private void hookSdkSettings(ClassLoader cl) {
        try {
            Class<?> gson = Class.forName("com.google.gson.Gson", false, cl);
            hookGsonSettings(gson, "cfg:sdk",
                    String.class, java.lang.reflect.Type.class);
            hookGsonSettings(gson, "cfg:sdkClass", String.class, Class.class);
        } catch (Throwable t) {
            skipped.add("cfg:sdk");
            log(Log.WARN, null, "sdk hook failed: " + t);
        }
    }

    private void hookGsonSettings(final Class<?> gson, final String id,
                                  Class<?> jsonType, Class<?> targetType) {
        try {
            Method m = gson.getMethod("fromJson", jsonType, targetType);
            m.setAccessible(true);
            deopt(id, m);
            hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            Class<?> target = rawClass(chain.getArg(1));
                            Object out = chain.proceed();
                            if (out == null || target == null || !looksLikeAdConfig(target)) {
                                return out;
                            }
                            try {
                                int n = disableAdConfig(out);
                                if (n > 0) {
                                    hit(id + " " + target.getSimpleName());
                                    log(Log.INFO, null, "sdk settings (" + target.getSimpleName()
                                            + ") -> false (" + n + ")");
                                } else if (hasField(target, "adSdkConfig")) {
                                    // 只对主设置模型打「已是 false」，避免刷屏
                                    hit(id + " " + target.getSimpleName());
                                    log(Log.INFO, null, "sdk settings (" + target.getSimpleName()
                                            + "): already false");
                                }
                            } catch (Throwable t) {
                                log(Log.ERROR, null, "sdk mutate failed: " + t);
                            }
                            return out;
                        }
                    });
            installed.add(id);
            sdkHooked = true;
        } catch (Throwable t) {
            skipped.add(id);
            log(Log.WARN, null, "sdk hook failed: " + id + " " + t);
        }
    }

    /**
     * 不按类名判断：只要字段里出现下面任意一个，就当成广告配置处理。
     * 结果按 Class 缓存，避免每次 JSON 解析都反射。
     */
    private boolean looksLikeAdConfig(Class<?> c) {
        Boolean cached = adConfigCache.get(c);
        if (cached != null) {
            return cached;
        }
        boolean hit = hasField(c, "shortVideoAdEnable")
                || hasField(c, "shortSeriesAdConfig")
                || hasField(c, "adSdkConfig")
                || hasField(c, "enableMultiSeriesFlowAd")
                || hasField(c, "landscapeInsertAdEnable")
                || hasField(c, "shortSeriesLandscapeInsertAdConfig");
        adConfigCache.put(c, hit);
        return hit;
    }

    private static boolean hasField(Class<?> c, String name) {
        try {
            c.getField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * 按字段路径关开关。Gson 用 Unsafe 分配对象，嵌套配置可能是 null，
     * 所以逐级 get-or-create；决策类直接读这些字段，必须改对象。
     */
    private int disableAdConfig(Object model) {
        int n = 0;

        // SDK SettingsModel：adSdkConfig.commonAdConfig.*
        Object adSdk = getOrCreate(model, "adSdkConfig");
        if (adSdk != null) {
            Object common = getOrCreate(adSdk, "commonAdConfig");
            n += setFalse(getOrCreate(common, "shortSeriesAdConfig"), "shortVideoAdEnable");
            n += setFalse(getOrCreate(common, "shortSeriesLandscapeInsertAdConfig"),
                    "landscapeInsertAdEnable");
            n += setFalse(getOrCreate(adSdk, "seriesAdConfig"), "enableMultiSeriesFlowAd");
        }

        // App 侧 CommonAdConfig：shortSeriesAdConfig.shortSeriesLandscapeInsertAdConfig
        Object shortSeries = getOrCreate(model, "shortSeriesAdConfig");
        n += setFalse(shortSeries, "shortVideoAdEnable");
        n += setFalse(getOrCreate(shortSeries, "shortSeriesLandscapeInsertAdConfig"),
                "landscapeInsertAdEnable");

        // SDK CommonAdConfig 的横屏配置是直接字段
        n += setFalse(getOrCreate(model, "shortSeriesLandscapeInsertAdConfig"),
                "landscapeInsertAdEnable");

        // 直接解析到具体配置类时的兜底
        n += setFalse(model, "shortVideoAdEnable");
        n += setFalse(model, "landscapeInsertAdEnable");
        n += setFalse(model, "enableMultiSeriesFlowAd");
        return n;
    }

    private static Class<?> rawClass(Object type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.Type raw =
                    ((java.lang.reflect.ParameterizedType) type).getRawType();
            if (raw instanceof Class) {
                return (Class<?>) raw;
            }
        }
        return null;
    }

    private Field field(Class<?> c, String name) {
        String key = c.getName() + '#' + name;
        Field f = fieldCache.get(key);
        if (f != null) {
            return f;
        }
        try {
            f = c.getField(name);
            f.setAccessible(true);
            fieldCache.put(key, f);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private Object getOrCreate(Object parent, String name) {
        if (parent == null) {
            return null;
        }
        Field f = field(parent.getClass(), name);
        if (f == null) {
            return null;
        }
        try {
            Object value = f.get(parent);
            if (value == null) {
                value = f.getType().getDeclaredConstructor().newInstance();
                f.set(parent, value);
            }
            return value;
        } catch (Throwable t) {
            return null;
        }
    }

    private int setFalse(Object obj, String name) {
        if (obj == null) {
            return 0;
        }
        Field f = field(obj.getClass(), name);
        if (f == null || f.getType() != boolean.class) {
            return 0;
        }
        try {
            if (f.getBoolean(obj)) {
                f.setBoolean(obj, false);
                if (reportedFields.add(name)) {
                    log(Log.INFO, null, "sdk field " + name + " -> false");
                }
                return 1;
            }
        } catch (Throwable ignore) {
            // 单个字段失败不影响其他字段
        }
        return 0;
    }

    // ----------------------------------------------------------- 工具

    /** ART 会把高频方法内联进调用方；hook 前强制去优化。 */
    private void deopt(String id, Method m) {
        try {
            deoptimize(m);
        } catch (Throwable t) {
            log(Log.WARN, null, "deoptimize failed: " + id, t);
        }
    }

    /** 命中审计：证明 hook 真的在链路上。 */
    private void hit(String id) {
        Integer prev = hitCounts.get(id);
        int n = (prev == null ? 0 : prev) + 1;
        hitCounts.put(id, n);
        if (n <= AUDIT_MAX_LOGS || n % AUDIT_EVERY == 0) {
            log(Log.INFO, null, "hit[" + n + "]: " + id);
        }
    }
}
