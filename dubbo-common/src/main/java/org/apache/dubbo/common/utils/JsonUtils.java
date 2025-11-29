/**
 * JSON工具类
 * JSON Utilities
 */
public class JsonUtils {

    /** 
     * JSON工具实例
     */
    private static volatile JsonUtil jsonUtil;

    /**
     * 获取JSON工具实例
     * 
     * @return JSON工具实例
     */
    public static JsonUtil getJson() {
        if (jsonUtil == null) {
            synchronized (JsonUtils.class) {
                if (jsonUtil == null) {
                    jsonUtil = createJsonUtil();
                }
            }
        }
        return jsonUtil;
    }

    /**
     * 创建JSON工具实例
     * 
     * @return JSON工具实例
     */
    private static JsonUtil createJsonUtil() {
        Map<String, JsonUtil> extensions = new HashMap<>();
        String preferName = SystemPropertyConfigUtils.getSystemProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME);

        ClassLoader classLoader = JsonUtil.class.getClassLoader();
        JsonUtil jsonUtil = loadExtensions(preferName, classLoader, extensions);
        if (jsonUtil != null) {
            return jsonUtil;
        }

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null && tccl != classLoader) {
            jsonUtil = loadExtensions(preferName, classLoader, extensions);
            if (jsonUtil != null) {
                return jsonUtil;
            }
        }

        TreeMap<Integer, JsonUtil> sortedExtensions = new TreeMap<>();
        for (JsonUtil extension : extensions.values()) {
            Activate activate = extension.getClass().getAnnotation(Activate.class);
            sortedExtensions.put(activate == null ? 0 : activate.order(), extension);
        }

        if (sortedExtensions.isEmpty()) {
            throw new IllegalStateException("Dubbo unable to find out any json framework (e.g. fastjson2, "
                    + "fastjson, gson, jackson) from jvm env. Please import at least one json framework.");
        }

        return sortedExtensions.firstEntry().getValue();
    }

    /**
     * 加载扩展
     * 
     * @param name 名称
     * @param classLoader 类加载器
     * @param extensions 扩展映射表
     * @return JSON工具实例
     */
    private static JsonUtil loadExtensions(String name, ClassLoader classLoader, Map<String, JsonUtil> extensions) {
        ServiceLoader<JsonUtil> loader = ServiceLoader.load(JsonUtil.class, classLoader);
        Iterator<JsonUtil> it = loader.iterator();
        // In JDK 21+, ServiceLoader.hasNext() may throw NoClassDefFoundError
        // when checking class dependencies, so we need to catch it here
        while (true) {
            try {
                if (!it.hasNext()) {
                    break;
                }
                JsonUtil extension = it.next();
                if (extension.isSupport()) {
                    if (name != null && name.equals(extension.getName())) {
                        return extension;
                    }
                    extensions.put(extension.getName(), extension);
                }
            } catch (Throwable ignored) {
                // Ignore loading failures (e.g., NoClassDefFoundError in JDK 25)
                // and continue with the next extension
            }
        }
        return null;
    }

    /**
     * @deprecated 仅用于单元测试
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    protected static void setJson(JsonUtil json) {
        jsonUtil = json;
    }

    /**
     * 将JSON字符串转换为Java对象
     * 
     * @param json JSON字符串
     * @param type 类型
     * @param <T> 对象类型
     * @return Java对象
     */
    public static <T> T toJavaObject(String json, Type type) {
        return getJson().toJavaObject(json, type);
    }

    /**
     * 将JSON字符串转换为Java列表
     * 
     * @param json JSON字符串
     * @param clazz 类
     * @param <T> 对象类型
     * @return Java列表
     */
    public static <T> List<T> toJavaList(String json, Class<T> clazz) {
        return getJson().toJavaList(json, clazz);
    }

    /**
     * 将对象转换为JSON字符串
     * 
     * @param obj 对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        return getJson().toJson(obj);
    }

    /**
     * 将对象转换为格式化的JSON字符串
     * 
     * @param obj 对象
     * @return 格式化的JSON字符串
     */
    public static String toPrettyJson(Object obj) {
        return getJson().toPrettyJson(obj);
    }

    /**
     * 从映射表中获取列表
     * 
     * @param obj 映射表
     * @param key 键
     * @return 列表
     */
    public static List<?> getList(Map<String, ?> obj, String key) {
        return getJson().getList(obj, key);
    }

    /**
     * 从映射表中获取对象列表
     * 
     * @param obj 映射表
     * @param key 键
     * @return 对象列表
     */
    public static List<Map<String, ?>> getListOfObjects(Map<String, ?> obj, String key) {
        return getJson().getListOfObjects(obj, key);
    }

    /**
     * 从映射表中获取字符串列表
     * 
     * @param obj 映射表
     * @param key 键
     * @return 字符串列表
     */
    public static List<String> getListOfStrings(Map<String, ?> obj, String key) {
        return getJson().getListOfStrings(obj, key);
    }

    /**
     * 从映射表中获取对象
     * 
     * @param obj 映射表
     * @param key 键
     * @return 对象
     */
    public static Map<String, ?> getObject(Map<String, ?> obj, String key) {
        return getJson().getObject(obj, key);
    }

    /**
     * 转换对象
     * 
     * @param obj 对象
     * @param targetType 目标类型
     * @return 转换后的对象
     */
    public static Object convertObject(Object obj, Type targetType) {
        return getJson().convertObject(obj, targetType);
    }

    /**
     * 转换对象
     * 
     * @param obj 对象
     * @param targetType 目标类型
     * @return 转换后的对象
     */
    public static Object convertObject(Object obj, Class<?> targetType) {
        return getJson().convertObject(obj, targetType);
    }

    /**
     * 从映射表中获取数字作为双精度浮点数
     * 
     * @param obj 映射表
     * @param key 键
     * @return 双精度浮点数
     */
    public static Double getNumberAsDouble(Map<String, ?> obj, String key) {
        return getJson().getNumberAsDouble(obj, key);
    }

    /**
     * 从映射表中获取数字作为整数
     * 
     * @param obj 映射表
     * @param key 键
     * @return 整数
     */
    public static Integer getNumberAsInteger(Map<String, ?> obj, String key) {
        return getJson().getNumberAsInteger(obj, key);
    }

    /**
     * 从映射表中获取数字作为长整数
     * 
     * @param obj 映射表
     * @param key 键
     * @return 长整数
     */
    public static Long getNumberAsLong(Map<String, ?> obj, String key) {
        return getJson().getNumberAsLong(obj, key);
    }

    /**
     * 从映射表中获取字符串
     * 
     * @param obj 映射表
     * @param key 键
     * @return 字符串
     */
    public static String getString(Map<String, ?> obj, String key) {
        return getJson().getString(obj, key);
    }

    /**
     * 检查对象列表
     * 
     * @param rawList 原始列表
     * @return 对象列表
     */
    public static List<Map<String, ?>> checkObjectList(List<?> rawList) {
        return getJson().checkObjectList(rawList);
    }

    /**
     * 检查字符串列表
     * 
     * @param rawList 原始列表
     * @return 字符串列表
     */
    public static List<String> checkStringList(List<?> rawList) {
        return getJson().checkStringList(rawList);
    }

    /**
     * 检查JSON字符串是否有效
     * 
     * @param json JSON字符串
     * @return 如果有效返回true，否则返回false
     */
    public static boolean checkJson(String json) {
        return getJson().isJson(json);
    }
}