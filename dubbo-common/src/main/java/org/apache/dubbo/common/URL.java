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

import org.apache.dubbo.common.config.Configuration;
import org.apache.dubbo.common.config.InmemoryConfiguration;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.constants.RemotingConstants;
import org.apache.dubbo.common.convert.ConverterUtil;
import org.apache.dubbo.common.url.component.PathURLAddress;
import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.url.component.URLAddress;
import org.apache.dubbo.common.url.component.URLParam;
import org.apache.dubbo.common.url.component.URLPlainParam;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.LRUCache;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;
import org.apache.dubbo.rpc.model.ServiceModel;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;

import static org.apache.dubbo.common.BaseServiceMetadata.COLON_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.ADDRESS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_CHAR_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.HOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.LOCALHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PASSWORD_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PORT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.USERNAME_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.utils.StringUtils.isBlank;

/**
 * URL - Uniform Resource Locator (Immutable, ThreadSafe)
 * <p>
 * url example:
 * <ul>
 * <li>http://www.facebook.com/friends?param1=value1&amp;param2=value2
 * <li>http://username:password@10.20.130.230:8080/list?version=1.0.0
 * <li>ftp://username:password@192.168.1.7:21/1/read.txt
 * <li>registry://192.168.1.7:9090/org.apache.dubbo.service1?param1=value1&amp;param2=value2
 * </ul>
 * <p>
 * Some strange example below:
 * <ul>
 * <li>192.168.1.3:20880<br>
 * for this case, url protocol = null, url host = 192.168.1.3, port = 20880, url path = null
 * <li>file:///home/user1/router.js?type=script<br>
 * for this case, url protocol = file, url host = null, url path = home/user1/router.js
 * <li>file://home/user1/router.js?type=script<br>
 * for this case, url protocol = file, url host = home, url path = user1/router.js
 * <li>file:///D:/1/router.js?type=script<br>
 * for this case, url protocol = file, url host = null, url path = D:/1/router.js
 * <li>file:/D:/1/router.js?type=script<br>
 * same as above file:///D:/1/router.js?type=script
 * <li>/home/user1/router.js?type=script <br>
 * for this case, url protocol = null, url host = null, url path = home/user1/router.js
 * <li>home/user1/router.js?type=script <br>
 * for this case, url protocol = null, url host = home, url path = user1/router.js
 * </ul>
 *
 * @see java.net.URL
 * @see java.net.URI
 */
/**
 * URL - 统一资源定位符 (不可变, 线程安全)
 * <p>
 * URL示例:
 * <ul>
 * <li>http://www.facebook.com/friends?param1=value1&amp;param2=value2
 * <li>http://username:password@10.20.130.230:8080/list?version=1.0.0
 * <li>ftp://username:password@192.168.1.7:21/1/read.txt
 * <li>registry://192.168.1.7:9090/org.apache.dubbo.service1?param1=value1&amp;param2=value2
 * </ul>
 * <p>
 * 以下是一些特殊情况的示例:
 * <ul>
 * <li>192.168.1.3:20880<br>
 * 在这种情况下, URL协议 = null, URL主机 = 192.168.1.3, 端口 = 20880, URL路径 = null
 * <li>file:///home/user1/router.js?type=script<br>
 * 在这种情况下, URL协议 = file, URL主机 = null, URL路径 = home/user1/router.js
 * <li>file://home/user1/router.js?type=script<br>
 * 在这种情况下, URL协议 = file, URL主机 = home, URL路径 = user1/router.js
 * <li>file:///D:/1/router.js?type=script<br>
 * 在这种情况下, URL协议 = file, URL主机 = null, URL路径 = D:/1/router.js
 * <li>file:/D:/1/router.js?type=script<br>
 * 与上面的file:///D:/1/router.js?type=script相同
 * <li>/home/user1/router.js?type=script <br>
 * 在这种情况下, URL协议 = null, URL主机 = null, URL路径 = home/user1/router.js
 * <li>home/user1/router.js?type=script <br>
 * 在这种情况下, URL协议 = null, URL主机 = home, URL路径 = user1/router.js
 * </ul>
 *
 * @see java.net.URL
 * @see java.net.URI
 */
public /*final**/ class URL implements Serializable {

    private static final long serialVersionUID = -1985165475234910535L;

    /** 
     * 缓存的URL映射
     */
    private static final Map<String, URL> cachedURLs = new LRUCache<>();

    /** 
     * URL地址
     */
    private final URLAddress urlAddress;
    /** 
     * URL参数
     */
    private final URLParam urlParam;

    // ==== cache ====
    /** 
     * 服务键
     */
    private transient String serviceKey;
    /** 
     * 协议服务键
     */
    private transient String protocolServiceKey;
    /** 
     * 属性映射
     */
    protected volatile Map<String, Object> attributes;

    /**
     * 默认构造函数
     */
    protected URL() {
        this.urlAddress = null;
        this.urlParam = URLParam.parse(new HashMap<>());
        this.attributes = null;
    }

    /**
     * 构造函数
     * 
     * @param urlAddress URL地址
     * @param urlParam URL参数
     */
    public URL(URLAddress urlAddress, URLParam urlParam) {
        this(urlAddress, urlParam, null);
    }

    /**
     * 构造函数
     * 
     * @param urlAddress URL地址
     * @param urlParam URL参数
     * @param attributes 属性映射
     */
    public URL(URLAddress urlAddress, URLParam urlParam, Map<String, Object> attributes) {
        this.urlAddress = urlAddress;
        this.urlParam = null == urlParam ? URLParam.parse(new HashMap<>()) : urlParam;

        if (attributes != null && !attributes.isEmpty()) {
            this.attributes = attributes;
        } else {
            this.attributes = null;
        }
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机
     * @param port 端口
     */
    public URL(String protocol, String host, int port) {
        this(protocol, null, null, host, port, null, (Map<String, String>) null);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机
     * @param port 端口
     * @param pairs 键值对数组
     */
    public URL(
            String protocol,
            String host,
            int port,
            String[] pairs) { // varargs ... conflict with the following path argument, use array instead.
        this(protocol, null, null, host, port, null, CollectionUtils.toStringMap(pairs));
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机
     * @param port 端口
     * @param parameters 参数映射
     */
    public URL(String protocol, String host, int port, Map<String, String> parameters) {
        this(protocol, null, null, host, port, null, parameters);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机
     * @param port 端口
     * @param path 路径
     */
    public URL(String protocol, String host, int port, String path) {
        this(protocol, null, null, host, port, path, (Map<String, String>) null);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机
     * @param port 端口
     * @param path 路径
     * @param pairs 键值对
     */
    public URL(String protocol, String host, int port, String path, String... pairs) {
        this(protocol, null, null, host, port, path, CollectionUtils.toStringMap(pairs));
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param host 主机
     * @param port 端口
     * @param path 路径
     * @param parameters 参数映射
     */
    public URL(String protocol, String host, int port, String path, Map<String, String> parameters) {
        this(protocol, null, null, host, port, path, parameters);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param username 用户名
     * @param password 密码
     * @param host 主机
     * @param port 端口
     * @param path 路径
     */
    public URL(String protocol, String username, String password, String host, int port, String path) {
        this(protocol, username, password, host, port, path, (Map<String, String>) null);
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param username 用户名
     * @param password 密码
     * @param host 主机
     * @param port 端口
     * @param path 路径
     * @param pairs 键值对
     */
    public URL(String protocol, String username, String password, String host, int port, String path, String... pairs) {
        this(protocol, username, password, host, port, path, CollectionUtils.toStringMap(pairs));
    }

    /**
     * 构造函数
     * 
     * @param protocol 协议
     * @param username 用户名
     * @param password 密码
     * @param host 主机
     * @param port 端口
     * @param path 路径
     * @param parameters 参数映射
     */
    public URL(
            String protocol,
            String username,
            String password,
            String host,
            int port,
            String path,
            Map<String, String> parameters) {
        if (StringUtils.isEmpty(username) && StringUtils.isNotEmpty(password)) {
            throw new IllegalArgumentException("Invalid url, password without username!");
        }

        this.urlAddress = new PathURLAddress(protocol, username, password, path, host, port);
        this.urlParam = URLParam.parse(parameters);
        this.attributes = null;
    }

    protected URL(
            String protocol,
            String username,
            String password,
            String host,
            int port,
            String path,
            Map<String, String> parameters,
            boolean modifiable) {
        if (StringUtils.isEmpty(username) && StringUtils.isNotEmpty(password)) {
            throw new IllegalArgumentException("Invalid url, password without username!");
        }

        this.urlAddress = new PathURLAddress(protocol, username, password, path, host, port);
        this.urlParam = URLParam.parse(parameters);
        this.attributes = null;
    }

    public static URL cacheableValueOf(String url) {
        URL cachedURL = cachedURLs.get(url);
        if (cachedURL != null) {
            return cachedURL;
        }
        cachedURL = valueOf(url, false);
        cachedURLs.put(url, cachedURL);
        return cachedURL;
    }

    /**
     * parse decoded url string, formatted dubbo://host:port/path?param=value, into strutted URL.
     *
     * @param url, decoded url string
     * @return
     */
    public static URL valueOf(String url) {
        return valueOf(url, false);
    }

    public static URL valueOf(String url, ScopeModel scopeModel) {
        return valueOf(url).setScopeModel(scopeModel);
    }

    /**
     * parse normal or encoded url string into strutted URL:
     * - dubbo://host:port/path?param=value
     * - URL.encode("dubbo://host:port/path?param=value")
     *
     * @param url,     url string
     * @param encoded, encoded or decoded
     * @return
     */
    public static URL valueOf(String url, boolean encoded) {
        if (encoded) {
            return URLStrParser.parseEncodedStr(url);
        }
        return URLStrParser.parseDecodedStr(url);
    }

    public static URL valueOf(String url, String... reserveParams) {
        URL result = valueOf(url);
        if (reserveParams == null || reserveParams.length == 0) {
            return result;
        }
        Map<String, String> newMap = new HashMap<>(reserveParams.length);
        Map<String, String> oldMap = result.getParameters();
        for (String reserveParam : reserveParams) {
            String tmp = oldMap.get(reserveParam);
            if (StringUtils.isNotEmpty(tmp)) {
                newMap.put(reserveParam, tmp);
            }
        }
        return result.clearParameters().addParameters(newMap);
    }

    public static URL valueOf(URL url, String[] reserveParams, String[] reserveParamPrefixes) {
        Map<String, String> newMap = new HashMap<>();
        Map<String, String> oldMap = url.getParameters();
        if (reserveParamPrefixes != null && reserveParamPrefixes.length != 0) {
            for (Map.Entry<String, String> entry : oldMap.entrySet()) {
                for (String reserveParamPrefix : reserveParamPrefixes) {
                    if (entry.getKey().startsWith(reserveParamPrefix) && StringUtils.isNotEmpty(entry.getValue())) {
                        newMap.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        if (reserveParams != null) {
            for (String reserveParam : reserveParams) {
                String tmp = oldMap.get(reserveParam);
                if (StringUtils.isNotEmpty(tmp)) {
                    newMap.put(reserveParam, tmp);
                }
            }
        }
        return newMap.isEmpty()
                ? new ServiceConfigURL(
                        url.getProtocol(),
                        url.getUsername(),
                        url.getPassword(),
                        url.getHost(),
                        url.getPort(),
                        url.getPath(),
                        (Map<String, String>) null,
                        url.getAttributes())
                : new ServiceConfigURL(
                        url.getProtocol(),
                        url.getUsername(),
                        url.getPassword(),
                        url.getHost(),
                        url.getPort(),
                        url.getPath(),
                        newMap,
                        url.getAttributes());
    }

    public static String encode(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static String decode(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static String appendDefaultPort(String address, int defaultPort) {
        if (StringUtils.isNotEmpty(address) && defaultPort > 0) {
            int i = address.indexOf(':');
            if (i < 0) {
                return address + ":" + defaultPort;
            } else if (Integer.parseInt(address.substring(i + 1)) == 0) {
                return address.substring(0, i + 1) + defaultPort;
            }
        }
        return address;
    }

    public URLAddress getUrlAddress() {
        return urlAddress;
    }

    public URLParam getUrlParam() {
        return urlParam;
    }

    public String getProtocol() {
        return urlAddress == null ? null : urlAddress.getProtocol();
    }

    public URL setProtocol(String protocol) {
        if (urlAddress == null) {
            return new ServiceConfigURL(protocol, getHost(), getPort(), getPath(), getParameters());
        } else {
            URLAddress newURLAddress = urlAddress.setProtocol(protocol);
            return returnURL(newURLAddress);
        }
    }

    public String getUsername() {
        return urlAddress == null ? null : urlAddress.getUsername();
    }

    public URL setUsername(String username) {
        if (urlAddress == null) {
            return new ServiceConfigURL(getProtocol(), getHost(), getPort(), getPath(), getParameters())
                    .setUsername(username);
        } else {
            URLAddress newURLAddress = urlAddress.setUsername(username);
            return returnURL(newURLAddress);
        }
    }

    public String getPassword() {
        return urlAddress == null ? null : urlAddress.getPassword();
    }

    public URL setPassword(String password) {
        if (urlAddress == null) {
            return new ServiceConfigURL(getProtocol(), getHost(), getPort(), getPath(), getParameters())
                    .setPassword(password);
        } else {
            URLAddress newURLAddress = urlAddress.setPassword(password);
            return returnURL(newURLAddress);
        }
    }

    /**
     * 获取授权信息，参考 https://datatracker.ietf.org/doc/html/rfc3986
     *
     * @return 授权信息
     */
    public String getAuthority() {
        StringBuilder ret = new StringBuilder();

        ret.append(getUserInformation());

        if (StringUtils.isNotEmpty(getHost())) {
            if (StringUtils.isNotEmpty(getUsername()) || StringUtils.isNotEmpty(getPassword())) {
                ret.append('@');
            }
            ret.append(getHost());
            if (getPort() != 0) {
                ret.append(':');
                ret.append(getPort());
            }
        }

        return ret.length() == 0 ? null : ret.toString();
    }

    /**
     * 获取用户信息，参考 https://datatracker.ietf.org/doc/html/rfc3986
     *
     * @return 用户信息
     */
    public String getUserInformation() {
        StringBuilder ret = new StringBuilder();

        if (StringUtils.isEmpty(getUsername()) && StringUtils.isEmpty(getPassword())) {
            return ret.toString();
        }

        if (StringUtils.isNotEmpty(getUsername())) {
            ret.append(getUsername());
        }

        ret.append(':');

        if (StringUtils.isNotEmpty(getPassword())) {
            ret.append(getPassword());
        }

        return ret.length() == 0 ? null : ret.toString();
    }

    /**
     * 获取主机名
     * 
     * @return 主机名
     */
    public String getHost() {
        return urlAddress == null ? null : urlAddress.getHost();
    }

    /**
     * 设置主机名
     * 
     * @param host 主机名
     * @return URL对象
     */
    public URL setHost(String host) {
        if (urlAddress == null) {
            return new ServiceConfigURL(getProtocol(), host, getPort(), getPath(), getParameters());
        } else {
            URLAddress newURLAddress = urlAddress.setHost(host);
            return returnURL(newURLAddress);
        }
    }

    /**
     * 获取端口号
     * 
     * @return 端口号
     */
    public int getPort() {
        return urlAddress == null ? 0 : urlAddress.getPort();
    }

    /**
     * 设置端口号
     * 
     * @param port 端口号
     * @return URL对象
     */
    public URL setPort(int port) {
        if (urlAddress == null) {
            return new ServiceConfigURL(getProtocol(), getHost(), port, getPath(), getParameters());
        } else {
            URLAddress newURLAddress = urlAddress.setPort(port);
            return returnURL(newURLAddress);
        }
    }

    /**
     * 获取端口号，如果端口号无效则返回默认端口号
     * 
     * @param defaultPort 默认端口号
     * @return 端口号
     */
    public int getPort(int defaultPort) {
        int port = getPort();
        return port <= 0 ? defaultPort : port;
    }

    /**
     * 获取地址
     * 
     * @return 地址
     */
    public String getAddress() {
        return urlAddress == null ? null : urlAddress.getAddress();
    }

    /**
     * 设置地址
     * 
     * @param address 地址
     * @return URL对象
     */
    public URL setAddress(String address) {
        int i = address.lastIndexOf(':');
        String host;
        int port = this.getPort();
        if (i >= 0) {
            host = address.substring(0, i);
            port = Integer.parseInt(address.substring(i + 1));
        } else {
            host = address;
        }
        if (urlAddress == null) {
            return new ServiceConfigURL(getProtocol(), host, port, getPath(), getParameters());
        } else {
            URLAddress newURLAddress = urlAddress.setAddress(host, port);
            return returnURL(newURLAddress);
        }
    }

    /**
     * 获取IP地址
     * 
     * @return IP地址
     */
    public String getIp() {
        return urlAddress == null ? null : urlAddress.getIp();
    }

    /**
     * 获取备份地址
     * 
     * @return 备份地址
     */
    public String getBackupAddress() {
        return getBackupAddress(0);
    }

    /**
     * 获取备份地址
     * 
     * @param defaultPort 默认端口号
     * @return 备份地址
     */
    public String getBackupAddress(int defaultPort) {
        StringBuilder address = new StringBuilder(appendDefaultPort(getAddress(), defaultPort));
        String[] backups = getParameter(RemotingConstants.BACKUP_KEY, new String[0]);
        if (ArrayUtils.isNotEmpty(backups)) {
            for (String backup : backups) {
                address.append(',');
                address.append(appendDefaultPort(backup, defaultPort));
            }
        }
        return address.toString();
    }

    /**
     * 获取备份URL列表
     * 
     * @return 备份URL列表
     */
    public List<URL> getBackupUrls() {
        List<URL> urls = new ArrayList<>();
        urls.add(this);
        String[] backups = getParameter(RemotingConstants.BACKUP_KEY, new String[0]);
        if (ArrayUtils.isNotEmpty(backups)) {
            for (String backup : backups) {
                urls.add(this.setAddress(backup));
            }
        }
        return urls;
    }

    /**
     * 获取路径
     * 
     * @return 路径
     */
    public String getPath() {
        return urlAddress == null ? null : urlAddress.getPath();
    }

    /**
     * 设置路径
     * 
     * @param path 路径
     * @return URL对象
     */
    public URL setPath(String path) {
        if (urlAddress == null) {
            return new ServiceConfigURL(getProtocol(), getHost(), getPort(), path, getParameters());
        } else {
            URLAddress newURLAddress = urlAddress.setPath(path);
            return returnURL(newURLAddress);
        }
    }

    /**
     * 获取绝对路径
     * 
     * @return 绝对路径
     */
    public String getAbsolutePath() {
        String path = getPath();
        if (path != null && !path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    /**
     * 获取原始参数
     * 
     * @return 参数映射
     */
    public Map<String, String> getOriginalParameters() {
        return this.getParameters();
    }

    /**
     * 获取参数
     * 
     * @return 参数映射
     */
    public Map<String, String> getParameters() {
        return urlParam.getParameters();
    }

    /**
     * 获取所有参数
     * 
     * @return 所有参数映射
     */
    public Map<String, String> getAllParameters() {
        return this.getParameters();
    }

    /**
     * 获取要选择(过滤)的参数
     *
     * @param nameToSelect 用于选择参数名的{@link Predicate}
     * @return 非空的{@link Map}
     * @since 2.7.8
     */
    public Map<String, String> getParameters(Predicate<String> nameToSelect) {
        Map<String, String> selectedParameters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : getParameters().entrySet()) {
            String name = entry.getKey();
            if (nameToSelect.test(name)) {
                selectedParameters.put(name, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(selectedParameters);
    }

    /**
     * 获取参数并解码
     * 
     * @param key 键
     * @return 解码后的参数值
     */
    public String getParameterAndDecoded(String key) {
        return getParameterAndDecoded(key, null);
    }

    /**
     * 获取参数并解码
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 解码后的参数值
     */
    public String getParameterAndDecoded(String key, String defaultValue) {
        return decode(getParameter(key, defaultValue));
    }

    /**
     * 获取原始参数
     * 
     * @param key 键
     * @return 参数值
     */
    public String getOriginalParameter(String key) {
        return getParameter(key);
    }

    /**
     * 获取参数
     * 
     * @param key 键
     * @return 参数值
     */
    public String getParameter(String key) {
        return urlParam.getParameter(key);
    }

    /**
     * 获取参数，如果参数不存在则返回默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 参数值或默认值
     */
    public String getParameter(String key, String defaultValue) {
        String value = getParameter(key);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 获取参数数组，如果参数不存在则返回默认数组
     * 
     * @param key 键
     * @param defaultValue 默认数组
     * @return 参数数组或默认数组
     */
    public String[] getParameter(String key, String[] defaultValue) {
        String value = getParameter(key);
        return StringUtils.isEmpty(value) ? defaultValue : COMMA_SPLIT_PATTERN.split(value);
    }

    /**
     * 获取参数列表，如果参数不存在则返回默认列表
     * 
     * @param key 键
     * @param defaultValue 默认列表
     * @return 参数列表或默认列表
     */
    public List<String> getParameter(String key, List<String> defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        String[] strArray = COMMA_SPLIT_PATTERN.split(value);
        return Arrays.asList(strArray);
    }

    /**
     * 获取参数
     *
     * @param key       参数键
     * @param valueType 参数值类型
     * @param <T>       参数值类型
     * @return 如果存在则返回参数，否则返回<code>null</code>
     * @since 2.7.8
     */
    public <T> T getParameter(String key, Class<T> valueType) {
        return getParameter(key, valueType, null);
    }

    /**
     * 获取参数
     *
     * @param key          参数键
     * @param valueType    参数值类型
     * @param defaultValue 如果参数不存在则使用的默认值
     * @param <T>          参数值类型
     * @return 如果存在则返回参数，否则使用<code>defaultValue</code>。
     * @since 2.7.8
     */
    public <T> T getParameter(String key, Class<T> valueType, T defaultValue) {
        String value = getParameter(key);
        T result = null;
        if (!isBlank(value)) {
            result = getOrDefaultFrameworkModel()
                    .getBeanFactory()
                    .getBean(ConverterUtil.class)
                    .convertIfPossible(value, valueType);
        }
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

    /**
     * 设置作用域模型
     * 
     * @param scopeModel 作用域模型
     * @return URL对象
     */
    public URL setScopeModel(ScopeModel scopeModel) {
        return putAttribute(CommonConstants.SCOPE_MODEL, scopeModel);
    }

    /**
     * 获取作用域模型
     * 
     * @return 作用域模型
     */
    public ScopeModel getScopeModel() {
        return (ScopeModel) getAttribute(CommonConstants.SCOPE_MODEL);
    }

    /**
     * 获取或使用默认框架模型
     * 
     * @return 框架模型
     */
    public FrameworkModel getOrDefaultFrameworkModel() {
        return ScopeModelUtil.getFrameworkModel(getScopeModel());
    }

    /**
     * 获取或使用默认应用模型
     * 
     * @return 应用模型
     */
    public ApplicationModel getOrDefaultApplicationModel() {
        return ScopeModelUtil.getApplicationModel(getScopeModel());
    }

    /**
     * 获取应用模型
     * 
     * @return 应用模型
     */
    public ApplicationModel getApplicationModel() {
        return ScopeModelUtil.getOrNullApplicationModel(getScopeModel());
    }

    /**
     * 获取或使用默认模块模型
     * 
     * @return 模块模型
     */
    public ModuleModel getOrDefaultModuleModel() {
        return ScopeModelUtil.getModuleModel(getScopeModel());
    }

    /**
     * 设置服务模型
     * 
     * @param serviceModel 服务模型
     * @return URL对象
     */
    public URL setServiceModel(ServiceModel serviceModel) {
        return putAttribute(CommonConstants.SERVICE_MODEL, serviceModel);
    }

    /**
     * 获取服务模型
     * 
     * @return 服务模型
     */
    public ServiceModel getServiceModel() {
        return (ServiceModel) getAttribute(CommonConstants.SERVICE_MODEL);
    }

    /**
     * 获取URL参数
     * 
     * @param key 键
     * @return URL对象
     */
    public URL getUrlParameter(String key) {
        String value = getParameterAndDecoded(key);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return URL.valueOf(value);
    }

    /**
     * 获取double类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return double类型参数值
     */
    public double getParameter(String key, double defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    /**
     * 获取float类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return float类型参数值
     */
    public float getParameter(String key, float defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Float.parseFloat(value);
    }

    /**
     * 获取long类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return long类型参数值
     */
    public long getParameter(String key, long defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    /**
     * 获取int类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return int类型参数值
     */
    public int getParameter(String key, int defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * 获取short类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return short类型参数值
     */
    public short getParameter(String key, short defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Short.parseShort(value);
    }

    /**
     * 获取byte类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return byte类型参数值
     */
    public byte getParameter(String key, byte defaultValue) {
        String value = getParameter(key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Byte.parseByte(value);
    }

    /**
     * 获取正数float类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正数float类型参数值
     */
    public float getPositiveParameter(String key, float defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        float value = getParameter(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取正数double类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正数double类型参数值
     */
    public double getPositiveParameter(String key, double defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        double value = getParameter(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取正数long类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正数long类型参数值
     */
    public long getPositiveParameter(String key, long defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        long value = getParameter(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取正数int类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正数int类型参数值
     */
    public int getPositiveParameter(String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        int value = getParameter(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取正数short类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正数short类型参数值
     */
    public short getPositiveParameter(String key, short defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        short value = getParameter(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取正数byte类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 正数byte类型参数值
     */
    public byte getPositiveParameter(String key, byte defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        byte value = getParameter(key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取char类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return char类型参数值
     */
    public char getParameter(String key, char defaultValue) {
        String value = getParameter(key);
        return StringUtils.isEmpty(value) ? defaultValue : value.charAt(0);
    }

    /**
     * 获取boolean类型参数
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return boolean类型参数值
     */
    public boolean getParameter(String key, boolean defaultValue) {
        String value = getParameter(key);
        return StringUtils.isEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 判断是否存在指定参数
     * 
     * @param key 键
     * @return 如果存在返回true，否则返回false
     */
    public boolean hasParameter(String key) {
        String value = getParameter(key);
        return StringUtils.isNotEmpty(value);
    }

    /**
     * 获取方法参数并解码
     * 
     * @param method 方法名
     * @param key 键
     * @return 解码后的参数值
     */
    public String getMethodParameterAndDecoded(String method, String key) {
        return URL.decode(getMethodParameter(method, key));
    }

    /**
     * 获取方法参数并解码
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return 解码后的参数值
     */
    public String getMethodParameterAndDecoded(String method, String key, String defaultValue) {
        return URL.decode(getMethodParameter(method, key, defaultValue));
    }

    /**
     * 获取方法参数
     * 
     * @param method 方法名
     * @param key 键
     * @return 参数值
     */
    public String getMethodParameter(String method, String key) {
        return urlParam.getMethodParameter(method, key);
    }

    /**
     * 严格获取方法参数
     * 
     * @param method 方法名
     * @param key 键
     * @return 参数值
     */
    public String getMethodParameterStrict(String method, String key) {
        return urlParam.getMethodParameterStrict(method, key);
    }

    /**
     * 获取方法参数，如果不存在则返回默认值
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return 参数值或默认值
     */
    public String getMethodParameter(String method, String key, String defaultValue) {
        String value = getMethodParameter(method, key);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 获取方法的double类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return double类型参数值
     */
    public double getMethodParameter(String method, String key, double defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    /**
     * 获取方法的float类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return float类型参数值
     */
    public float getMethodParameter(String method, String key, float defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Float.parseFloat(value);
    }

    /**
     * 获取方法的long类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return long类型参数值
     */
    public long getMethodParameter(String method, String key, long defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    /**
     * 获取方法的int类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return int类型参数值
     */
    public int getMethodParameter(String method, String key, int defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * 获取方法的short类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return short类型参数值
     */
    public short getMethodParameter(String method, String key, short defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Short.parseShort(value);
    }

    /**
     * 获取方法的byte类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return byte类型参数值
     */
    public byte getMethodParameter(String method, String key, byte defaultValue) {
        String value = getMethodParameter(method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Byte.parseByte(value);
    }

    public double getMethodPositiveParameter(String method, String key, double defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        double value = getMethodParameter(method, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public float getMethodPositiveParameter(String method, String key, float defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        float value = getMethodParameter(method, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public long getMethodPositiveParameter(String method, String key, long defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        long value = getMethodParameter(method, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public int getMethodPositiveParameter(String method, String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        int value = getMethodParameter(method, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public short getMethodPositiveParameter(String method, String key, short defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        short value = getMethodParameter(method, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public byte getMethodPositiveParameter(String method, String key, byte defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        byte value = getMethodParameter(method, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    /**
     * 获取方法的char类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return char类型参数值
     */
    public char getMethodParameter(String method, String key, char defaultValue) {
        String value = getMethodParameter(method, key);
        return StringUtils.isEmpty(value) ? defaultValue : value.charAt(0);
    }

    /**
     * 获取方法的boolean类型参数
     * 
     * @param method 方法名
     * @param key 键
     * @param defaultValue 默认值
     * @return boolean类型参数值
     */
    public boolean getMethodParameter(String method, String key, boolean defaultValue) {
        String value = getMethodParameter(method, key);
        return StringUtils.isEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 判断方法是否存在指定参数
     * 
     * @param method 方法名
     * @param key 键
     * @return 如果存在返回true，否则返回false
     */
    public boolean hasMethodParameter(String method, String key) {
        if (method == null) {
            String suffix = "." + key;
            for (String fullKey : getParameters().keySet()) {
                if (fullKey.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        }
        if (key == null) {
            String prefix = method + ".";
            for (String fullKey : getParameters().keySet()) {
                if (fullKey.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
        String value = getMethodParameterStrict(method, key);
        return StringUtils.isNotEmpty(value);
    }

    /**
     * 获取任意方法的参数
     * 
     * @param key 键
     * @return 参数值
     */
    public String getAnyMethodParameter(String key) {
        return urlParam.getAnyMethodParameter(key);
    }

    /**
     * 判断是否存在指定方法的参数
     * 
     * @param method 方法名
     * @return 如果存在返回true，否则返回false
     */
    public boolean hasMethodParameter(String method) {
        return urlParam.hasMethodParameter(method);
    }

    /**
     * 判断是否为本地主机
     * 
     * @return 如果是本地主机返回true，否则返回false
     */
    public boolean isLocalHost() {
        return NetUtils.isLocalHost(getHost()) || getParameter(LOCALHOST_KEY, false);
    }

    /**
     * 判断是否为任意主机
     * 
     * @return 如果是任意主机返回true，否则返回false
     */
    public boolean isAnyHost() {
        return ANYHOST_VALUE.equals(getHost()) || getParameter(ANYHOST_KEY, false);
    }

    /**
     * 添加参数并编码
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加参数并编码
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameterAndEncoded(String key, String value) {
        if (StringUtils.isEmpty(value)) {
            return this;
        }
        return addParameter(key, encode(value));
    }

    /**
     * 添加boolean类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加boolean类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, boolean value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加char类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, char value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加byte类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加byte类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, byte value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加short类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加short类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, short value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加int类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加int类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, int value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加long类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加long类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, long value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加float类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加float类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, float value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加double类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加double类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, double value) {
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加枚举类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加枚举类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, Enum<?> value) {
        if (value == null) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加数字类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加数字类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, Number value) {
        if (value == null) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加字符序列类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    /**
     * 添加字符序列类型参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, CharSequence value) {
        if (value == null || value.length() == 0) {
            return this;
        }
        return addParameter(key, String.valueOf(value));
    }

    /**
     * 添加参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameter(String key, String value) {
        URLParam newParam = urlParam.addParameter(key, value);
        return returnURL(newParam);
    }

    /**
     * 如果参数不存在则添加参数
     * 
     * @param key 键
     * @param value 值
     * @return URL对象
     */
    public URL addParameterIfAbsent(String key, String value) {
        URLParam newParam = urlParam.addParameterIfAbsent(key, value);
        return returnURL(newParam);
    }

    /**
     * 添加参数映射到新的URL
     *
     * @param parameters 键值对形式的参数
     * @return 新的URL
     */
    public URL addParameters(Map<String, String> parameters) {
        URLParam newParam = urlParam.addParameters(parameters);
        return returnURL(newParam);
    }

    /**
     * 如果参数不存在则添加参数映射
     * 
     * @param parameters 参数映射
     * @return URL对象
     */
    public URL addParametersIfAbsent(Map<String, String> parameters) {
        URLParam newURLParam = urlParam.addParametersIfAbsent(parameters);
        return returnURL(newURLParam);
    }

    /**
     * 添加参数对
     * 
     * @param pairs 参数对
     * @return URL对象
     */
    public URL addParameters(String... pairs) {
        if (ArrayUtils.isEmpty(pairs)) {
            return this;
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数对不能是奇数个");
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
     * @return URL对象
     */
    public URL addParameterString(String query) {
        if (StringUtils.isEmpty(query)) {
            return this;
        }
        return addParameters(StringUtils.parseQueryString(query));
    }

    /**
     * 移除参数
     * 
     * @param key 键
     * @return URL对象
     */
    public URL removeParameter(String key) {
        if (StringUtils.isEmpty(key)) {
            return this;
        }
        return removeParameters(key);
    }

    /**
     * 移除参数集合
     * 
     * @param keys 键集合
     * @return URL对象
     */
    public URL removeParameters(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return this;
        }
        return removeParameters(keys.toArray(new String[0]));
    }

    /**
     * 移除参数数组
     * 
     * @param keys 键数组
     * @return URL对象
     */
    public URL removeParameters(String... keys) {
        URLParam newURLParam = urlParam.removeParameters(keys);
        return returnURL(newURLParam);
    }

    /**
     * 清除所有参数
     * 
     * @return URL对象
     */
    public URL clearParameters() {
        URLParam newURLParam = urlParam.clearParameters();
        return returnURL(newURLParam);
    }

    /**
     * 获取原始参数
     * 
     * @param key 键
     * @return 参数值
     */
    public String getRawParameter(String key) {
        if (PROTOCOL_KEY.equals(key)) {
            return urlAddress.getProtocol();
        }
        if (USERNAME_KEY.equals(key)) {
            return urlAddress.getUsername();
        }
        if (PASSWORD_KEY.equals(key)) {
            return urlAddress.getPassword();
        }
        if (HOST_KEY.equals(key)) {
            return urlAddress.getHost();
        }
        if (PORT_KEY.equals(key)) {
            return String.valueOf(urlAddress.getPort());
        }
        if (PATH_KEY.equals(key)) {
            return urlAddress.getPath();
        }
        return urlParam.getParameter(key);
    }

    /**
     * 转换为原始映射
     * 
     * @return 参数映射
     */
    public Map<String, String> toOriginalMap() {
        Map<String, String> map = new HashMap<>(getOriginalParameters());
        return addSpecialKeys(map);
    }

    /**
     * 转换为映射
     * 
     * @return 参数映射
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(getParameters());
        return addSpecialKeys(map);
    }

    /**
     * 添加特殊键到映射中
     * 
     * @param map 参数映射
     * @return 添加了特殊键的映射
     */
    private Map<String, String> addSpecialKeys(Map<String, String> map) {
        if (getProtocol() != null) {
            map.put(PROTOCOL_KEY, getProtocol());
        }
        if (getUsername() != null) {
            map.put(USERNAME_KEY, getUsername());
        }
        if (getPassword() != null) {
            map.put(PASSWORD_KEY, getPassword());
        }
        if (getHost() != null) {
            map.put(HOST_KEY, getHost());
        }
        if (getPort() > 0) {
            map.put(PORT_KEY, String.valueOf(getPort()));
        }
        if (getPath() != null) {
            map.put(PATH_KEY, getPath());
        }
        if (getAddress() != null) {
            map.put(ADDRESS_KEY, getAddress());
        }
        return map;
    }

    /**
     * 转换为字符串表示
     * 
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return buildString(false, true); // 不显示用户名和密码
    }

    /**
     * 转换为字符串表示
     * 
     * @param parameters 参数
     * @return 字符串表示
     */
    public String toString(String... parameters) {
        return buildString(false, true, parameters); // 不显示用户名和密码
    }

    /**
     * 转换为身份字符串表示
     * 
     * @return 身份字符串表示
     */
    public String toIdentityString() {
        return buildString(true, false); // 只返回身份信息，参见equals和hashCode方法
    }

    /**
     * 转换为身份字符串表示
     * 
     * @param parameters 参数
     * @return 身份字符串表示
     */
    public String toIdentityString(String... parameters) {
        return buildString(
                true, false, parameters); // 只返回身份信息，参见equals和hashCode方法
    }

    /**
     * 转换为完整字符串表示
     * 
     * @return 完整字符串表示
     */
    public String toFullString() {
        return buildString(true, true);
    }

    /**
     * 转换为完整字符串表示
     * 
     * @param parameters 参数
     * @return 完整字符串表示
     */
    public String toFullString(String... parameters) {
        return buildString(true, true, parameters);
    }

    /**
     * 转换为参数字符串表示
     * 
     * @return 参数字符串表示
     */
    public String toParameterString() {
        return toParameterString(new String[0]);
    }

    /**
     * 转换为参数字符串表示
     * 
     * @param parameters 参数
     * @return 参数字符串表示
     */
    public String toParameterString(String... parameters) {
        StringBuilder buf = new StringBuilder();
        buildParameters(buf, false, parameters);
        return buf.toString();
    }

    /**
     * 构建参数
     * 
     * @param buf 字符串构建器
     * @param concat 是否连接
     * @param parameters 参数
     */
    protected void buildParameters(StringBuilder buf, boolean concat, String[] parameters) {
        if (CollectionUtils.isNotEmptyMap(getParameters())) {
            List<String> includes = (ArrayUtils.isEmpty(parameters) ? null : Arrays.asList(parameters));
            boolean first = true;
            for (Map.Entry<String, String> entry : new TreeMap<>(getParameters()).entrySet()) {
                if (StringUtils.isNotEmpty(entry.getKey()) && (includes == null || includes.contains(entry.getKey()))) {
                    if (first) {
                        if (concat) {
                            buf.append('?');
                        }
                        first = false;
                    } else {
                        buf.append('&');
                    }
                    buf.append(entry.getKey());
                    buf.append('=');
                    buf.append(entry.getValue() == null ? "" : entry.getValue().trim());
                }
            }
        }
    }

    /**
     * 构建字符串
     * 
     * @param appendUser 是否追加用户信息
     * @param appendParameter 是否追加参数
     * @param parameters 参数
     * @return 字符串
     */
    private String buildString(boolean appendUser, boolean appendParameter, String... parameters) {
        return buildString(appendUser, appendParameter, false, false, parameters);
    }

    /**
     * 构建字符串
     * 
     * @param appendUser 是否追加用户信息
     * @param appendParameter 是否追加参数
     * @param useIP 是否使用IP
     * @param useService 是否使用服务
     * @param parameters 参数
     * @return 字符串
     */
    private String buildString(
            boolean appendUser, boolean appendParameter, boolean useIP, boolean useService, String... parameters) {
        StringBuilder buf = new StringBuilder();
        if (StringUtils.isNotEmpty(getProtocol())) {
            buf.append(getProtocol());
            buf.append("://");
        }
        if (appendUser && StringUtils.isNotEmpty(getUsername())) {
            buf.append(getUsername());
            if (StringUtils.isNotEmpty(getPassword())) {
                buf.append(':');
                buf.append(getPassword());
            }
            buf.append('@');
        }
        String host;
        if (useIP) {
            host = urlAddress.getIp();
        } else {
            host = getHost();
        }
        if (StringUtils.isNotEmpty(host)) {
            buf.append(host);
            if (getPort() > 0) {
                buf.append(':');
                buf.append(getPort());
            }
        }
        String path;
        if (useService) {
            path = getServiceKey();
        } else {
            path = getPath();
        }
        if (StringUtils.isNotEmpty(path)) {
            buf.append('/');
            buf.append(path);
        }

        if (appendParameter) {
            buildParameters(buf, true, parameters);
        }
        return buf.toString();
    }

    /**
     * 转换为Java URL对象
     * 
     * @return Java URL对象
     */
    public java.net.URL toJavaURL() {
        try {
            return new java.net.URL(toString());
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 转换为InetSocketAddress对象
     * 
     * @return InetSocketAddress对象
     */
    public InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(getHost(), getPort());
    }

    /**
     * 获取冒号分隔的键，格式为"{interface}:[version]:[group]"
     *
     * @return 冒号分隔的键
     */
    public String getColonSeparatedKey() {
        StringBuilder serviceNameBuilder = new StringBuilder();
        serviceNameBuilder.append(this.getServiceInterface());
        append(serviceNameBuilder, VERSION_KEY, false);
        append(serviceNameBuilder, GROUP_KEY, false);
        return serviceNameBuilder.toString();
    }

    /**
     * The format is "{interface}:[version]"
     *
     * @return
     */
    public String getCompatibleColonSeparatedKey() {
        StringBuilder serviceNameBuilder = new StringBuilder();
        serviceNameBuilder.append(this.getServiceInterface());
        compatibleAppend(serviceNameBuilder, VERSION_KEY);
        compatibleAppend(serviceNameBuilder, GROUP_KEY);
        return serviceNameBuilder.toString();
    }

    private void append(StringBuilder target, String parameterName, boolean first) {
        String parameterValue = this.getParameter(parameterName);
        if (!isBlank(parameterValue)) {
            if (!first) {
                target.append(':');
            }
            target.append(parameterValue);
        } else {
            target.append(':');
        }
    }

    private void compatibleAppend(StringBuilder target, String parameterName) {
        String parameterValue = this.getParameter(parameterName);
        if (!isBlank(parameterValue)) {
            target.append(':');
            target.append(parameterValue);
        }
    }

    /**
     * The format of return value is '{group}/{interfaceName}:{version}'
     *
     * @return
     */
    public String getServiceKey() {
        if (serviceKey != null) {
            return serviceKey;
        }
        String inf = getServiceInterface();
        if (inf == null) {
            return null;
        }
        serviceKey = buildKey(inf, getGroup(), getVersion());
        return serviceKey;
    }

    /**
     * Format : interface:version
     *
     * @return
     */
    public String getDisplayServiceKey() {
        if (StringUtils.isEmpty(getVersion())) {
            return getServiceInterface();
        }
        return getServiceInterface() + COLON_SEPARATOR + getVersion();
    }

    /**
     * The format of return value is '{group}/{path/interfaceName}:{version}'
     *
     * @return
     */
    public String getPathKey() {
        String inf = StringUtils.isNotEmpty(getPath()) ? getPath() : getServiceInterface();
        if (inf == null) {
            return null;
        }
        return buildKey(inf, getGroup(), getVersion());
    }

    public static String buildKey(String path, String group, String version) {
        return BaseServiceMetadata.buildServiceKey(path, group, version);
    }

    public String getProtocolServiceKey() {
        if (protocolServiceKey != null) {
            return protocolServiceKey;
        }
        this.protocolServiceKey = getServiceKey();
        /*
        Special treatment for urls begins with 'consumer://', that is, a consumer subscription url instance with no protocol specified.
        If protocol is specified on the consumer side, then this method will return as normal.
        */
        if (!CONSUMER.equals(getProtocol())) {
            this.protocolServiceKey += (GROUP_CHAR_SEPARATOR + getProtocol());
        }
        return protocolServiceKey;
    }

    /**
     * 转换为不解析的服务字符串
     * 
     * @return 服务字符串
     */
    public String toServiceStringWithoutResolving() {
        return buildString(true, false, false, true);
    }

    /**
     * 转换为服务字符串
     * 
     * @return 服务字符串
     */
    public String toServiceString() {
        return buildString(true, false, true, true);
    }

    public String toServiceString(String... parameters) {
        return buildString(true, true, true, true, parameters);
    }

    @Deprecated
    public String getServiceName() {
        return getServiceInterface();
    }

    public String getServiceInterface() {
        return getParameter(INTERFACE_KEY, getPath());
    }

    public URL setServiceInterface(String service) {
        return addParameter(INTERFACE_KEY, service);
    }

    /**
     * @see #getParameter(String, int)
     * @deprecated Replace to <code>getParameter(String, int)</code>
     */
    @Deprecated
    public int getIntParameter(String key) {
        return getParameter(key, 0);
    }

    /**
     * @see #getParameter(String, int)
     * @deprecated Replace to <code>getParameter(String, int)</code>
     */
    @Deprecated
    public int getIntParameter(String key, int defaultValue) {
        return getParameter(key, defaultValue);
    }

    /**
     * @see #getPositiveParameter(String, int)
     * @deprecated Replace to <code>getPositiveParameter(String, int)</code>
     */
    @Deprecated
    public int getPositiveIntParameter(String key, int defaultValue) {
        return getPositiveParameter(key, defaultValue);
    }

    /**
     * @see #getParameter(String, boolean)
     * @deprecated Replace to <code>getParameter(String, boolean)</code>
     */
    @Deprecated
    public boolean getBooleanParameter(String key) {
        return getParameter(key, false);
    }

    /**
     * @see #getParameter(String, boolean)
     * @deprecated Replace to <code>getParameter(String, boolean)</code>
     */
    @Deprecated
    public boolean getBooleanParameter(String key, boolean defaultValue) {
        return getParameter(key, defaultValue);
    }

    /**
     * @see #getMethodParameter(String, String, int)
     * @deprecated Replace to <code>getMethodParameter(String, String, int)</code>
     */
    @Deprecated
    public int getMethodIntParameter(String method, String key) {
        return getMethodParameter(method, key, 0);
    }

    /**
     * @see #getMethodParameter(String, String, int)
     * @deprecated Replace to <code>getMethodParameter(String, String, int)</code>
     */
    @Deprecated
    public int getMethodIntParameter(String method, String key, int defaultValue) {
        return getMethodParameter(method, key, defaultValue);
    }

    /**
     * @see #getMethodPositiveParameter(String, String, int)
     * @deprecated Replace to <code>getMethodPositiveParameter(String, String, int)</code>
     */
    @Deprecated
    public int getMethodPositiveIntParameter(String method, String key, int defaultValue) {
        return getMethodPositiveParameter(method, key, defaultValue);
    }

    /**
     * @see #getMethodParameter(String, String, boolean)
     * @deprecated Replace to <code>getMethodParameter(String, String, boolean)</code>
     */
    @Deprecated
    public boolean getMethodBooleanParameter(String method, String key) {
        return getMethodParameter(method, key, false);
    }

    /**
     * @see #getMethodParameter(String, String, boolean)
     * @deprecated Replace to <code>getMethodParameter(String, String, boolean)</code>
     */
    @Deprecated
    public boolean getMethodBooleanParameter(String method, String key, boolean defaultValue) {
        return getMethodParameter(method, key, defaultValue);
    }

    public Configuration toConfiguration() {
        InmemoryConfiguration configuration = new InmemoryConfiguration();
        configuration.addProperties(getParameters());
        return configuration;
    }

    private volatile int hashCodeCache = -1;

    @Override
    public int hashCode() {
        if (hashCodeCache == -1) {
            hashCodeCache = Objects.hash(urlAddress, urlParam);
        }
        return hashCodeCache;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof URL)) {
            return false;
        }
        URL other = (URL) obj;
        return Objects.equals(this.getUrlAddress(), other.getUrlAddress())
                && Objects.equals(this.getUrlParam(), other.getUrlParam());
    }

    public static void putMethodParameter(
            String method, String key, String value, Map<String, Map<String, String>> methodParameters) {
        Map<String, String> subParameter = methodParameters.computeIfAbsent(method, k -> new HashMap<>());
        subParameter.put(key, value);
    }

    protected <T extends URL> T newURL(URLAddress urlAddress, URLParam urlParam) {
        return (T) new ServiceConfigURL(urlAddress, urlParam, attributes);
    }

    /* methods introduced for CompositeURL, CompositeURL must override to make the implementations meaningful */

    public String getApplication(String defaultValue) {
        String value = getApplication();
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    public String getApplication() {
        return getParameter(APPLICATION_KEY);
    }

    public String getRemoteApplication() {
        return getParameter(REMOTE_APPLICATION_KEY);
    }

    public String getGroup() {
        return getParameter(GROUP_KEY);
    }

    public String getGroup(String defaultValue) {
        String value = getGroup();
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    public String getVersion() {
        return getParameter(VERSION_KEY);
    }

    public String getVersion(String defaultValue) {
        String value = getVersion();
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    public String getConcatenatedParameter(String key) {
        return getParameter(key);
    }

    public String getCategory(String defaultValue) {
        String value = getCategory();
        if (StringUtils.isEmpty(value)) {
            value = defaultValue;
        }
        return value;
    }

    public String[] getCategory(String[] defaultValue) {
        String value = getCategory();
        return StringUtils.isEmpty(value) ? defaultValue : COMMA_SPLIT_PATTERN.split(value);
    }

    public String getCategory() {
        return getParameter(CATEGORY_KEY);
    }

    public String getSide(String defaultValue) {
        String value = getSide();
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    public String getSide() {
        return getParameter(SIDE_KEY);
    }

    /* Service Config URL, START*/
    public Map<String, Object> getAttributes() {
        return attributes == null ? Collections.emptyMap() : attributes;
    }

    public URL addAttributes(Map<String, Object> attributeMap) {
        if (attributeMap != null) {
            attributes.putAll(attributeMap);
        }
        return this;
    }

    public Object getAttribute(String key) {
        return attributes == null ? null : attributes.get(key);
    }

    public Object getAttribute(String key, Object defaultValue) {
        Object val = attributes == null ? null : attributes.get(key);
        return val != null ? val : defaultValue;
    }

    public URL putAttribute(String key, Object obj) {
        if (attributes == null) {
            this.attributes = new HashMap<>();
        }
        attributes.put(key, obj);
        return this;
    }

    public URL removeAttribute(String key) {
        if (attributes != null) {
            attributes.remove(key);
        }
        return this;
    }

    public boolean hasAttribute(String key) {
        return attributes != null && attributes.containsKey(key);
    }

    /* Service Config URL, END*/

    private URL returnURL(URLAddress newURLAddress) {
        if (urlAddress == newURLAddress) {
            return this;
        }
        return newURL(newURLAddress, urlParam);
    }

    private URL returnURL(URLParam newURLParam) {
        if (urlParam == newURLParam) {
            return this;
        }
        return newURL(urlAddress, newURLParam);
    }

    /* add service scope operations, see InstanceAddressURL */
    public Map<String, String> getOriginalServiceParameters(String service) {
        return getServiceParameters(service);
    }

    public Map<String, String> getServiceParameters(String service) {
        return getParameters();
    }

    public String getOriginalServiceParameter(String service, String key) {
        return this.getServiceParameter(service, key);
    }

    public String getServiceParameter(String service, String key) {
        return getParameter(key);
    }

    public String getServiceParameter(String service, String key, String defaultValue) {
        String value = getServiceParameter(service, key);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    public int getServiceParameter(String service, String key, int defaultValue) {
        return getParameter(key, defaultValue);
    }

    public double getServiceParameter(String service, String key, double defaultValue) {
        String value = getServiceParameter(service, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    public float getServiceParameter(String service, String key, float defaultValue) {
        String value = getServiceParameter(service, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Float.parseFloat(value);
    }

    public long getServiceParameter(String service, String key, long defaultValue) {
        String value = getServiceParameter(service, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    public short getServiceParameter(String service, String key, short defaultValue) {
        String value = getServiceParameter(service, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Short.parseShort(value);
    }

    public byte getServiceParameter(String service, String key, byte defaultValue) {
        String value = getServiceParameter(service, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Byte.parseByte(value);
    }

    public char getServiceParameter(String service, String key, char defaultValue) {
        String value = getServiceParameter(service, key);
        return StringUtils.isEmpty(value) ? defaultValue : value.charAt(0);
    }

    public boolean getServiceParameter(String service, String key, boolean defaultValue) {
        String value = getServiceParameter(service, key);
        return StringUtils.isEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public boolean hasServiceParameter(String service, String key) {
        String value = getServiceParameter(service, key);
        return StringUtils.isNotEmpty(value);
    }

    public float getPositiveServiceParameter(String service, String key, float defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        float value = getServiceParameter(service, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public double getPositiveServiceParameter(String service, String key, double defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        double value = getServiceParameter(service, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public long getPositiveServiceParameter(String service, String key, long defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        long value = getServiceParameter(service, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public int getPositiveServiceParameter(String service, String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        int value = getServiceParameter(service, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public short getPositiveServiceParameter(String service, String key, short defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        short value = getServiceParameter(service, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public byte getPositiveServiceParameter(String service, String key, byte defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <= 0");
        }
        byte value = getServiceParameter(service, key, defaultValue);
        return value <= 0 ? defaultValue : value;
    }

    public String getServiceMethodParameterAndDecoded(String service, String method, String key) {
        return URL.decode(getServiceMethodParameter(service, method, key));
    }

    public String getServiceMethodParameterAndDecoded(String service, String method, String key, String defaultValue) {
        return URL.decode(getServiceMethodParameter(service, method, key, defaultValue));
    }

    public String getServiceMethodParameterStrict(String service, String method, String key) {
        return getMethodParameterStrict(method, key);
    }

    public String getServiceMethodParameter(String service, String method, String key) {
        return getMethodParameter(method, key);
    }

    public String getServiceMethodParameter(String service, String method, String key, String defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    public double getServiceMethodParameter(String service, String method, String key, double defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    public float getServiceMethodParameter(String service, String method, String key, float defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Float.parseFloat(value);
    }

    public long getServiceMethodParameter(String service, String method, String key, long defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    public int getServiceMethodParameter(String service, String method, String key, int defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public short getServiceMethodParameter(String service, String method, String key, short defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Short.parseShort(value);
    }

    public byte getServiceMethodParameter(String service, String method, String key, byte defaultValue) {
        String value = getServiceMethodParameter(service, method, key);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        return Byte.parseByte(value);
    }

    public boolean hasServiceMethodParameter(String service, String method, String key) {
        return hasMethodParameter(method, key);
    }

    public boolean hasServiceMethodParameter(String service, String method) {
        return hasMethodParameter(method);
    }

    public URL toSerializableURL() {
        return returnURL(URLPlainParam.toURLPlainParam(urlParam));
    }
}
