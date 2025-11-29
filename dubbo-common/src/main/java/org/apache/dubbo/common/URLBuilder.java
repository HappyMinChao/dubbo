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

import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.apache.dubbo.common.constants.CommonConstants.SCOPE_MODEL;

/**
 * URL构建器类，用于构建和修改URL对象
 */
public final class URLBuilder extends ServiceConfigURL {
    /** 
     * 协议
     */
    private String protocol;

    /** 
     * 用户名
     */
    private String username;

    /** 
     * 密码
     */
    private String password;

    /** 
     * 主机地址，默认注册到注册中心
     */
    // by default, host to registry
    private String host;

    /** 
     * 端口号，默认注册到注册中心
     */
    // by default, port to registry
    private int port;

    /** 
     * 路径
     */
    private String path;

    /** 
     * 参数映射表
     */
    private final Map<String, String> parameters;

    /** 
     * 属性映射表
     */
    private final Map<String, Object> attributes;

    /** 
     * 方法参数映射表
     */
    private Map<String, Map<String, String>> methodParameters;

    /**
     * 默认构造函数
     */
    public URLBuilder() {
        protocol = null;
        username = null;
        password = null;
        host = null;
        port = 0;
        path = null;
        parameters = new HashMap<>();
        attributes = new HashMap<>();
        methodParameters = new HashMap<>();
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机地址
     * @param port 端口号
     */
    public URLBuilder(String protocol, String host, int port) {
        this(protocol, null, null, host, port, null, null);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机地址
     * @param port 端口号
     * @param pairs 键值对数组
     */
    public URLBuilder(String protocol, String host, int port, String[] pairs) {
        this(protocol, null, null, host, port, null, CollectionUtils.toStringMap(pairs));
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机地址
     * @param port 端口号
     * @param parameters 参数映射表
     */
    public URLBuilder(String protocol, String host, int port, Map<String, String> parameters) {
        this(protocol, null, null, host, port, null, parameters);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机地址
     * @param port 端口号
     * @param path 路径
     */
    public URLBuilder(String protocol, String host, int port, String path) {
        this(protocol, null, null, host, port, path, null);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机地址
     * @param port 端口号
     * @param path 路径
     * @param pairs 键值对数组
     */
    public URLBuilder(String protocol, String host, int port, String path, String... pairs) {
        this(protocol, null, null, host, port, path, CollectionUtils.toStringMap(pairs));
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机地址
     * @param port 端口号
     * @param path 路径
     * @param parameters 参数映射表
     */
    public URLBuilder(String protocol, String host, int port, String path, Map<String, String> parameters) {
        this(protocol, null, null, host, port, path, parameters);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param username 用户名
     * @param password 密码
     * @param host 主机地址
     * @param port 端口号
     * @param path 路径
     * @param parameters 参数映射表
     */
    public URLBuilder(
            String protocol,
            String username,
            String password,
            String host,
            int port,
            String path,
            Map<String, String> parameters) {
        this(protocol, username, password, host, port, path, parameters, null);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param username 用户名
     * @param password 密码
     * @param host 主机地址
     * @param port 端口号
     * @param path 路径
     * @param parameters 参数映射表
     * @param attributes 属性映射表
     */
    public URLBuilder(
            String protocol,
            String username,
            String password,
            String host,
            int port,
            String path,
            Map<String, String> parameters,
            Map<String, Object> attributes) {
        this.protocol = protocol;
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        this.path = path;
        this.parameters = parameters != null ? parameters : new HashMap<>();
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    /**
     * 从URL对象创建URLBuilder实例
     * 
     * @param url URL对象
     * @return URLBuilder实例
     */
    public static URLBuilder from(URL url) {
        String protocol = url.getProtocol();
        String username = url.getUsername();
        String password = url.getPassword();
        String host = url.getHost();
        int port = url.getPort();
        String path = url.getPath();
        Map<String, String> parameters = new HashMap<>(url.getParameters());
        Map<String, Object> attributes = new HashMap<>(url.getAttributes());
        return new URLBuilder(protocol, username, password, host, port, path, parameters, attributes);
    }

    /**
     * 构建ServiceConfigURL对象
     * 
     * @return ServiceConfigURL对象
     */
    public ServiceConfigURL build() {
        if (StringUtils.isEmpty(username) && StringUtils.isNotEmpty(password)) {
            throw new IllegalArgumentException("Invalid url, password without username!");
        }
        port = Math.max(port, 0);
        // trim the leading "/"
        int firstNonSlash = 0;
        if (path != null) {
            while (firstNonSlash < path.length() && path.charAt(firstNonSlash) == '/') {
                firstNonSlash++;
            }
            if (firstNonSlash >= path.length()) {
                path = "";
            } else if (firstNonSlash > 0) {
                path = path.substring(firstNonSlash);
            }
        }
        return new ServiceConfigURL(protocol, username, password, host, port, path, parameters, attributes);
    }

    /**
     * 设置属性值
     * 
     * @param key 属性键
     * @param obj 属性值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder putAttribute(String key, Object obj) {
        attributes.put(key, obj);
        return this;
    }

    /**
     * 移除属性值
     * 
     * @param key 属性键
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder removeAttribute(String key) {
        attributes.remove(key);
        return this;
    }

    /**
     * 设置协议
     * 
     * @param protocol 协议
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * 设置用户名
     * 
     * @param username 用户名
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * 设置密码
     * 
     * @param password 密码
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setPassword(String password) {
        this.password = password;
        return this;
    }

    /**
     * 设置主机地址
     * 
     * @param host 主机地址
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * 设置端口号
     * 
     * @param port 端口号
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * 设置地址
     * 
     * @param address 地址
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setAddress(String address) {
        int i = address.lastIndexOf(':');
        String host;
        int port = this.port;
        if (i >= 0) {
            host = address.substring(0, i);
            port = Integer.parseInt(address.substring(i + 1));
        } else {
            host = address;
        }
        this.host = host;
        this.port = port;
        return this;
    }

    /**
     * 设置路径
     * 
     * @param path 路径
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setPath(String path) {
        this.path = path;
        return this;
    }

    /**
     * 设置作用域模型
     * 
     * @param scopeModel 作用域模型
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder setScopeModel(ScopeModel scopeModel) {
        this.attributes.put(SCOPE_MODEL, scopeModel);
        return this;
    }

    /**
     * 添加编码后的参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameterAndEncoded(String key, String value) {
        if (StringUtils.isEmpty(value)) {
            return this;
        }
        return addParameter(key, URL.encode(value));
    }

    /**
     * 添加布尔型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, boolean value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加字符型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, char value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加字节型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, byte value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加短整型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, short value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加整型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, int value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加长整型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, long value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加浮点型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, float value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加双精度浮点型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, double value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加枚举型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, Enum<?> value) {
        if (value == null) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加数值型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, Number value) {
        if (value == null) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加字符序列型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, CharSequence value) {
        if (value == null || value.length() == 0) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加字符串型参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameter(String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return this;
        }

        parameters.put(key, value);
        return this;
    }

    /**
     * 添加方法参数
     * 
     * @param method 方法名
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    public URLBuilder addMethodParameter(String method, String key, String value) {
        if (StringUtils.isEmpty(method) || StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return this;
        }
        URL.putMethodParameter(method, key, value, methodParameters);
        return this;
    }

    /**
     * 如果参数不存在则添加参数
     * 
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameterIfAbsent(String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return this;
        }
        if (hasParameter(key)) {
            return this;
        }
        parameters.put(key, value);
        return this;
    }

    /**
     * 如果方法参数不存在则添加方法参数
     * 
     * @param method 方法名
     * @param key 参数键
     * @param value 参数值
     * @return URLBuilder实例
     */
    public URLBuilder addMethodParameterIfAbsent(String method, String key, String value) {
        if (StringUtils.isEmpty(method) || StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return this;
        }
        if (hasMethodParameter(method, key)) {
            return this;
        }
        URL.putMethodParameter(method, key, value, methodParameters);
        return this;
    }

    /**
     * 添加参数映射表
     * 
     * @param parameters 参数映射表
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameters(Map<String, String> parameters) {
        if (CollectionUtils.isEmptyMap(parameters)) {
            return this;
        }

        boolean hasAndEqual = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String oldValue = this.parameters.get(entry.getKey());
            String newValue = entry.getValue();
            if (!Objects.equals(oldValue, newValue)) {
                hasAndEqual = false;
                break;
            }
        }
        // return immediately if there's no change
        if (hasAndEqual) {
            return this;
        }

        this.parameters.putAll(parameters);
        return this;
    }

    /**
     * 添加方法参数映射表
     * 
     * @param methodParameters 方法参数映射表
     * @return URLBuilder实例
     */
    public URLBuilder addMethodParameters(Map<String, Map<String, String>> methodParameters) {
        if (CollectionUtils.isEmptyMap(methodParameters)) {
            return this;
        }

        this.methodParameters.putAll(methodParameters);
        return this;
    }

    /**
     * 如果参数不存在则添加参数映射表
     * 
     * @param parameters 参数映射表
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParametersIfAbsent(Map<String, String> parameters) {
        if (CollectionUtils.isEmptyMap(parameters)) {
            return this;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            this.parameters.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * 添加参数键值对数组
     * 
     * @param pairs 参数键值对数组
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameters(String... pairs) {
        if (ArrayUtils.isEmpty(pairs)) {
            return this;
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Map pairs can not be odd number.");
        }
        Map<String, String> map = new HashMap<>();
        int len = pairs.length / 2;
        for (int i = 0; i < len; i++) {
            map.put(pairs[2 * i], pairs[2 * i + 1]);
        }
        return addParameters(map);
    }

    /**
     * 添加参数字符串
     * 
     * @param query 参数字符串
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder addParameterString(String query) {
        if (StringUtils.isEmpty(query)) {
            return this;
        }
        return addParameters(StringUtils.parseQueryString(query));
    }

    /**
     * 移除参数
     * 
     * @param key 参数键
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder removeParameter(String key) {
        if (StringUtils.isEmpty(key)) {
            return this;
        }
        return removeParameters(key);
    }

    /**
     * 移除参数集合
     * 
     * @param keys 参数键集合
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder removeParameters(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return this;
        }
        return removeParameters(keys.toArray(new String[0]));
    }

    /**
     * 移除参数数组
     * 
     * @param keys 参数键数组
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder removeParameters(String... keys) {
        if (ArrayUtils.isEmpty(keys)) {
            return this;
        }
        for (String key : keys) {
            parameters.remove(key);
        }
        return this;
    }

    /**
     * 清空参数
     * 
     * @return URLBuilder实例
     */
    @Override
    public URLBuilder clearParameters() {
        parameters.clear();
        return this;
    }

    /**
     * 判断是否包含参数
     * 
     * @param key 参数键
     * @return 如果包含参数则返回true，否则返回false
     */
    @Override
    public boolean hasParameter(String key) {
        String value = getParameter(key);
        return StringUtils.isNotEmpty(value);
    }

    /**
     * 判断是否包含方法参数
     * 
     * @param method 方法名
     * @param key 参数键
     * @return 如果包含方法参数则返回true，否则返回false
     */
    @Override
    public boolean hasMethodParameter(String method, String key) {
        if (method == null) {
            String suffix = "." + key;
            for (String fullKey : parameters.keySet()) {
                if (fullKey.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        }
        if (key == null) {
            String prefix = method + ".";
            for (String fullKey : parameters.keySet()) {
                if (fullKey.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
        String value = getMethodParameter(method, key);
        return StringUtils.isNotEmpty(value);
    }

    /**
     * 获取参数值
     * 
     * @param key 参数键
     * @return 参数值
     */
    @Override
    public String getParameter(String key) {
        return parameters.get(key);
    }

    /**
     * 获取方法参数值
     * 
     * @param method 方法名
     * @param key 参数键
     * @return 参数值
     */
    @Override
    public String getMethodParameter(String method, String key) {
        Map<String, String> keyMap = methodParameters.get(method);
        String value = null;
        if (keyMap != null) {
            value = keyMap.get(key);
        }
        return value;
    }
}
