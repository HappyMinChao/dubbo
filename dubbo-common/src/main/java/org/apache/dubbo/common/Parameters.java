/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY_PREFIX;
import static org.apache.dubbo.common.constants.CommonConstants.HIDE_KEY_PREFIX;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION;

/**
 * Parameters for backward compatibility for version prior to 2.0.5
 *
 * @deprecated
 */
@Deprecated
public class Parameters {
    /** 
     * 日志记录器
     */
    protected static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(Parameters.class);
    /** 
     * 参数映射表
     */
    private final Map<String, String> parameters;

    /**
     * 构造函数，通过键值对数组创建Parameters对象
     * 
     * @param pairs 键值对数组
     */
    public Parameters(String... pairs) {
        this(toMap(pairs));
    }

    /**
     * 构造函数，通过参数映射表创建Parameters对象
     * 
     * @param parameters 参数映射表
     */
    public Parameters(Map<String, String> parameters) {
        this.parameters =
                Collections.unmodifiableMap(parameters != null ? new HashMap<>(parameters) : new HashMap<>(0));
    }

    /**
     * 将键值对数组转换为映射表
     * 
     * @param pairs 键值对数组
     * @return 映射表
     */
    private static Map<String, String> toMap(String... pairs) {
        return CollectionUtils.toStringMap(pairs);
    }

    /**
     * 解析查询字符串创建Parameters对象
     * 
     * @param query 查询字符串
     * @return Parameters对象
     */
    public static Parameters parseParameters(String query) {
        return new Parameters(StringUtils.parseQueryString(query));
    }

    /**
     * 获取参数映射表
     * 
     * @return 参数映射表
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * 获取指定类型的扩展实例
     * 
     * @param type 扩展类型
     * @param key 键
     * @param <T> 扩展类型泛型
     * @return 扩展实例
     * @deprecated will be removed in 3.3.0
     */
    @Deprecated
    public <T> T getExtension(Class<T> type, String key) {
        String name = getParameter(key);
        return ExtensionLoader.getExtensionLoader(type).getExtension(name);
    }

    /**
     * 获取指定类型的扩展实例，如果不存在则返回默认值
     * 
     * @param type 扩展类型
     * @param key 键
     * @param defaultValue 默认值
     * @param <T> 扩展类型泛型
     * @return 扩展实例
     * @deprecated will be removed in 3.3.0
     */
    @Deprecated
    public <T> T getExtension(Class<T> type, String key, String defaultValue) {
        String name = getParameter(key, defaultValue);
        return ExtensionLoader.getExtensionLoader(type).getExtension(name);
    }

    /**
     * 获取指定方法的扩展实例
     * 
     * @param type 扩展类型
     * @param method 方法名
     * @param key 键
     * @param <T> 扩展类型泛型
     * @return 扩展实例
     * @deprecated will be removed in 3.3.0
     */
    @Deprecated
    public <T> T getMethodExtension(Class<T> type, String method, String key) {
        String name = getMethodParameter(method, key);
        return ExtensionLoader.getExtensionLoader(type).getExtension(name);
    }

    /**
     * 获取指定方法的扩展实例，如果不存在则返回默认值
     * 
     * @param type 扩展类型
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @param <T> 扩展类型泛型
     * @return 扩展实例
     * @deprecated will be removed in 3.3.0
     */
    @Deprecated
    public <T> T getMethodExtension(Class<T> type, String method, String key, String defaultValue) {
        String name = getMethodParameter(method, key, defaultValue);
        return ExtensionLoader.getExtensionLoader(type).getExtension(name);
    }

    /**
     * 获取解码后的参数值
     * 
     * @param key 键
     * @return 解码后的参数值
     */
    public String getDecodedParameter(String key) {
        return getDecodedParameter(key, null);
    }

    /**
     * 获取解码后的参数值，如果不存在则返回默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 解码后的参数值
     */
    public String getDecodedParameter(String key, String defaultValue) {
        String value = getParameter(key, defaultValue);
        if (value != null && value.length() > 0) {
            try {
                value = URLDecoder.decode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                logger.error(COMMON_UNEXPECTED_EXCEPTION, "", "", e.getMessage(), e);
            }
        }
        return value;
    }

    /**
     * 获取参数值
     * 
     * @param key 键
     * @return 参数值
     */
    public String getParameter(String key) {
        String value = parameters.get(key);
        if (StringUtils.isEmpty(value)) {
            value = parameters.get(HIDE_KEY_PREFIX + key);
        }
        if (StringUtils.isEmpty(value)) {
            value = parameters.get(DEFAULT_KEY_PREFIX + key);
        }
        if (StringUtils.isEmpty(value)) {
            value = parameters.get(HIDE_KEY_PREFIX + DEFAULT_KEY_PREFIX + key);
        }
        return value;
    }

    /**
     * 获取参数值，如果不存在则返回默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 参数值
     */
    public String getParameter(String key, String defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return value;
    }

    /**
     * 获取整型参数值
     * 
     * @param key 键
     * @return 整型参数值，如果不存在或为空则返回0
     */
    public int getIntParameter(String key) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    /**
     * 获取整型参数值，如果不存在则返回默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 整型参数值
     */
    public int getIntParameter(String key, int defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * 获取正整数参数值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正整数参数值，如果不存在或小于等于0则返回默认值
     */
    public int getPositiveIntParameter(String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        int i = Integer.parseInt(value);
        if (i > 0) {
            return i;
        }
        return defaultValue;
    }

    /**
     * 获取布尔型参数值
     * 
     * @param key 键
     * @return 布尔型参数值，如果不存在或为空则返回false
     */
    public boolean getBooleanParameter(String key) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取布尔型参数值，如果不存在则返回默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 布尔型参数值
     */
    public boolean getBooleanParameter(String key, boolean defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 判断是否包含指定参数
     * 
     * @param key 键
     * @return 如果包含指定参数且不为空则返回true，否则返回false
     */
    public boolean hasParameter(String key) {
        String value = getParameter(key);
        return value != null && value.length() > 0;
    }

    /**
     * 获取指定方法的参数值
     * 
     * @param method 方法名
     * @param key 键
     * @return 参数值
     */
    public String getMethodParameter(String method, String key) {
        String value = parameters.get(method + "." + key);
        if (StringUtils.isEmpty(value)) {
            value = parameters.get(HIDE_KEY_PREFIX + method + "." + key);
        }
        if (StringUtils.isEmpty(value)) {
            return getParameter(key);
        }
        return value;
    }

    /**
     * 获取指定方法的参数值，如果不存在则返回默认值
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return 参数值
     */
    public String getMethodParameter(String method, String key, String defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return value;
    }

    /**
     * 获取指定方法的整型参数值
     * 
     * @param method 方法名
     * @param key 键
     * @return 整型参数值，如果不存在或为空则返回0
     */
    public int getMethodIntParameter(String method, String key) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    /**
     * 获取指定方法的整型参数值，如果不存在则返回默认值
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return 整型参数值
     */
    public int getMethodIntParameter(String method, String key, int defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * 获取指定方法的正整数参数值
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return 正整数参数值，如果不存在或小于等于0则返回默认值
     */
    public int getMethodPositiveIntParameter(String method, String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        int i = Integer.parseInt(value);
        if (i > 0) {
            return i;
        }
        return defaultValue;
    }

    /**
     * 获取指定方法的布尔型参数值
     * 
     * @param method 方法名
     * @param key 键
     * @return 布尔型参数值，如果不存在或为空则返回false
     */
    public boolean getMethodBooleanParameter(String method, String key) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取指定方法的布尔型参数值，如果不存在则返回默认值
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return 布尔型参数值
     */
    public boolean getMethodBooleanParameter(String method, String key, boolean defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 判断指定方法是否包含指定参数
     * 
     * @param method 方法名
     * @param key 键
     * @return 如果包含指定参数且不为空则返回true，否则返回false
     */
    public boolean hasMethodParameter(String method, String key) {
        String value = getMethodParameter(method, key);
        return value != null && value.length() > 0;
    }

    /**
     * 比较对象是否相等
     * 
     * @param o 待比较的对象
     * @return 如果对象相等则返回true，否则返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return parameters.equals(o);
    }

    /**
     * 计算对象的哈希码
     * 
     * @return 对象的哈希码
     */
    @Override
    public int hashCode() {
        return parameters.hashCode();
    }

    /**
     * 返回对象的字符串表示
     * 
     * @return 对象的字符串表示
     */
    @Override
    public String toString() {
        return StringUtils.toQueryString(getParameters());
    }
}
