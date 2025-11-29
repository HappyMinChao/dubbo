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

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION;

/**
 * 版本工具类
 * 提供Dubbo版本管理相关功能
 *
 * @author dubbo
 */
public final class Version {
    // 日志记录器
    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(Version.class);

    // 匹配前缀数字的正则表达式模式
    private static final Pattern PREFIX_DIGITS_PATTERN = Pattern.compile("^([0-9]*).*");

    // Dubbo RPC协议版本，为了兼容性，不能在2.0.10 ~ 2.6.2之间
    public static final String DEFAULT_DUBBO_PROTOCOL_VERSION = "2.0.2";
    // 版本1.0.0表示v2.6.2之前的Dubbo RPC协议
    public static final int LEGACY_DUBBO_PROTOCOL_VERSION = 10000; // 1.0.0
    // Dubbo实现版本
    private static String VERSION;
    // 最新提交ID
    private static String LATEST_COMMIT_ID;

    /**
     * 为了协议兼容性目的。
     * 因为{@link #isSupportResponseAttachment}在每次调用时都会检查，int比较比字符串比较有更好的性能。
     */
    public static final int LOWEST_VERSION_FOR_RESPONSE_ATTACHMENT = 2000200; // 2.0.2

    // 最高协议版本
    public static final int HIGHEST_PROTOCOL_VERSION = 2009900; // 2.0.99
    // 版本到整数的映射缓存
    private static final Map<String, Integer> VERSION2INT = new HashMap<>();

    // 静态初始化块
    static {
        // 获取dubbo版本和最后提交ID
        try {
            tryLoadVersionFromResource();
            checkDuplicate();
        } catch (Throwable e) {
            logger.warn(
                    COMMON_UNEXPECTED_EXCEPTION,
                    "",
                    "",
                    "继续旧逻辑，忽略异常 " + e.getMessage(),
                    e);
        }
        if (StringUtils.isEmpty(VERSION)) {
            VERSION = getVersion(Version.class, "");
        }
        if (StringUtils.isEmpty(LATEST_COMMIT_ID)) {
            LATEST_COMMIT_ID = "";
        }
    }

    /**
     * 尝试从资源加载版本信息
     * 
     * @throws IOException IO异常
     */
    private static void tryLoadVersionFromResource() throws IOException {
        Enumeration<URL> configLoader =
                Version.class.getClassLoader().getResources(CommonConstants.DUBBO_VERSIONS_KEY + "/dubbo-common");
        if (configLoader.hasMoreElements()) {
            URL url = configLoader.nextElement();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("revision=")) {
                        VERSION = line.substring("revision=".length());
                    } else if (line.startsWith("git.commit.id=")) {
                        LATEST_COMMIT_ID = line.substring("git.commit.id=".length());
                    }
                }
            }
        }
    }

    /**
     * 私有构造函数，防止实例化
     */
    private Version() {}

    /**
     * 获取协议版本
     * 
     * @return 协议版本字符串
     */
    public static String getProtocolVersion() {
        return DEFAULT_DUBBO_PROTOCOL_VERSION;
    }

    /**
     * 获取版本号
     * 
     * @return 版本号字符串
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * 获取最新提交ID
     * 
     * @return 最新提交ID
     */
    public static String getLastCommitId() {
        return LATEST_COMMIT_ID;
    }

    /**
     * 比较两个版本号
     *
     * @param version1 第一个版本号
     * @param version2 第二个版本号
     * @return 如果{@code version1 == version2}返回{@code 0}；
     *         如果{@code version1 < version2}返回小于{@code 0}的值；
     *         如果{@code version1 > version2}返回大于{@code 0}的值
     */
    public static int compare(String version1, String version2) {
        return Integer.compare(getIntVersion(version1), getIntVersion(version2));
    }

    /**
     * 检查框架发布版本号是否为2.7.0或更高版本
     * 
     * @param version 版本号
     * @return 如果是2.7.0或更高版本返回true，否则返回false
     */
    public static boolean isRelease270OrHigher(String version) {
        if (StringUtils.isEmpty(version)) {
            return false;
        }

        return getIntVersion(version) >= 2070000;
    }

    /**
     * 检查框架发布版本号是否为2.6.3或更高版本
     *
     * @param version SDK版本号
     * @return 如果是2.6.3或更高版本返回true，否则返回false
     */
    public static boolean isRelease263OrHigher(String version) {
        return getIntVersion(version) >= 2060300;
    }

    /**
     * 检查Dubbo 2.x协议版本号是否支持响应附件
     * Dubbo 2.x协议版本号限制在2.0.2/2000200 ~ 2.0.99/2009900范围内，其他版本被认为是无效的或非官方发布的。
     *
     * @param version 协议版本号
     * @return 如果支持响应附件返回true，否则返回false
     */
    public static boolean isSupportResponseAttachment(String version) {
        if (StringUtils.isEmpty(version)) {
            return false;
        }
        int iVersion = getIntVersion(version);
        if (iVersion >= LOWEST_VERSION_FOR_RESPONSE_ATTACHMENT && iVersion <= HIGHEST_PROTOCOL_VERSION) {
            return true;
        }

        return false;
    }

    /**
     * 将版本字符串转换为整数表示
     * 
     * @param version 版本字符串
     * @return 整数形式的版本号
     */
    public static int getIntVersion(String version) {
        Integer v = VERSION2INT.get(version);
        if (v == null) {
            try {
                v = parseInt(version);
                // 例如，版本号2.6.3将被转换为2060300
                if (version.split("\\.").length == 3) {
                    v = v * 100;
                }
            } catch (Exception e) {
                logger.warn(
                        COMMON_UNEXPECTED_EXCEPTION,
                        "",
                        "",
                        "请确保您的版本值具有正确的格式: "
                                + "\n 1. 只包含数字: 2.0.0; \n 2. 带字符串后缀: 2.6.7-stable. "
                                + "\n如果您使用的是v2.6.2之前的Dubbo版本，版本值与jar版本相同。");
                v = LEGACY_DUBBO_PROTOCOL_VERSION;
            }
            VERSION2INT.put(version, v);
        }
        return v;
    }

    /**
     * 将版本字符串解析为整数
     * 
     * @param version 版本字符串
     * @return 解析后的整数
     */
    private static int parseInt(String version) {
        int v = 0;
        String[] vArr = version.split("\\.");
        int len = vArr.length;
        for (int i = 0; i < len; i++) {
            String subV = getPrefixDigits(vArr[i]);
            if (StringUtils.isNotEmpty(subV)) {
                v += Integer.parseInt(subV) * Math.pow(10, (len - i - 1) * 2);
            }
        }
        return v;
    }

    /**
     * 从给定的版本字符串中获取前缀数字
     * 
     * @param v 版本字符串
     * @return 前缀数字字符串
     */
    private static String getPrefixDigits(String v) {
        Matcher matcher = PREFIX_DIGITS_PATTERN.matcher(v);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 获取指定类的版本号
     * 
     * @param cls 目标类
     * @param defaultVersion 默认版本号
     * @return 类的版本号
     */
    public static String getVersion(Class<?> cls, String defaultVersion) {
        try {
            // 首先从MANIFEST.MF中查找版本信息
            Package pkg = cls.getPackage();
            String version = null;
            if (pkg != null) {
                version = pkg.getImplementationVersion();
                if (StringUtils.isNotEmpty(version)) {
                    return version;
                }

                version = pkg.getSpecificationVersion();
                if (StringUtils.isNotEmpty(version)) {
                    return version;
                }
            }

            // 如果在MANIFEST.MF中找不到，则从jar文件名猜测版本
            CodeSource codeSource = cls.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                logger.info("获取类 " + cls.getName() + " 的版本时没有codeSource，使用默认版本 "
                        + defaultVersion);
                return defaultVersion;
            }

            URL location = codeSource.getLocation();
            if (location == null) {
                logger.info("获取类 " + cls.getName() + " 的版本时没有位置信息，使用默认版本 "
                        + defaultVersion);
                return defaultVersion;
            }
            String file = location.getFile();
            if (!StringUtils.isEmpty(file) && file.endsWith(".jar")) {
                version = getFromFile(file);
            }

            // 如果未找到版本信息则返回默认版本
            return StringUtils.isEmpty(version) ? defaultVersion : version;
        } catch (Throwable e) {
            // 当抛出任何异常时返回默认版本
            logger.error(
                    COMMON_UNEXPECTED_EXCEPTION,
                    "",
                    "",
                    "返回默认版本，忽略异常 " + e.getMessage(),
                    e);
            return defaultVersion;
        }
    }

    /**
     * 从文件路径中提取版本号
     * 文件路径格式: path/to/group-module-x.y.z.jar, 返回 x.y.z
     * 
     * @param file 文件路径
     * @return 版本号字符串
     */
    private static String getFromFile(String file) {
        // 移除后缀".jar": "path/to/group-module-x.y.z"
        file = file.substring(0, file.length() - 4);

        // 移除路径: "group-module-x.y.z"
        int i = file.lastIndexOf('/');
        if (i >= 0) {
            file = file.substring(i + 1);
        }

        // 移除分组: "module-x.y.z"
        i = file.indexOf("-");
        if (i >= 0) {
            file = file.substring(i + 1);
        }

        // 移除模块名: "x.y.z"
        while (file.length() > 0 && !Character.isDigit(file.charAt(0))) {
            i = file.indexOf("-");
            if (i >= 0) {
                file = file.substring(i + 1);
            } else {
                break;
            }
        }
        return file;
    }

    /**
     * 检查重复的构件
     */
    private static void checkDuplicate() {
        try {
            checkArtifacts(loadArtifactIds());
        } catch (Throwable e) {
            logger.error(COMMON_UNEXPECTED_EXCEPTION, "", "", e.getMessage(), e);
        }
    }

    /**
     * 检查构件集合
     * 
     * @param artifactIds 构件ID集合
     * @throws IOException IO异常
     */
    private static void checkArtifacts(Set<String> artifactIds) throws IOException {
        if (!artifactIds.isEmpty()) {
            for (String artifactId : artifactIds) {
                checkArtifact(artifactId);
            }
        }
    }

    /**
     * 检查单个构件
     * 
     * @param artifactId 构件ID
     * @throws IOException IO异常
     */
    private static void checkArtifact(String artifactId) throws IOException {
        Enumeration<URL> artifactEnumeration =
                Version.class.getClassLoader().getResources(CommonConstants.DUBBO_VERSIONS_KEY + artifactId);
        while (artifactEnumeration.hasMoreElements()) {
            URL url = artifactEnumeration.nextElement();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] artifactInfo = line.split("=");
                    if (artifactInfo.length == 2) {
                        String key = artifactInfo[0];
                        String value = artifactInfo[1];
                        checkVersion(artifactId, url, key, value);
                    }
                }
            }
        }
    }

    /**
     * 检查版本一致性
     * 
     * @param artifactId 构件ID
     * @param url 资源URL
     * @param key 键
     * @param value 值
     */
    private static void checkVersion(String artifactId, URL url, String key, String value) {
        if ("revision".equals(key) && !value.equals(VERSION)) {
            String error = "在 " + artifactId + " 中发现不一致的版本 " + value + "，来自 " + url.getPath() + ", "
                    + "期望的dubbo-common版本是 " + VERSION;
            logger.error(COMMON_UNEXPECTED_EXCEPTION, "", "", error);
        }
        if ("git.commit.id".equals(key) && !value.equals(LATEST_COMMIT_ID)) {
            String error = "在 " + artifactId + " 中发现不一致的git构建提交ID " + value + "，来自 "
                    + url.getPath() + ", " + "期望的dubbo-common版本是 " + LATEST_COMMIT_ID;
            logger.error(COMMON_UNEXPECTED_EXCEPTION, "", "", error);
        }
    }

    /**
     * 加载构件ID集合
     * 
     * @return 构件ID集合
     * @throws IOException IO异常
     */
    private static Set<String> loadArtifactIds() throws IOException {
        Enumeration<URL> artifactsEnumeration =
                Version.class.getClassLoader().getResources(CommonConstants.DUBBO_VERSIONS_KEY + "/.artifacts");
        Set<String> artifactIds = new HashSet<>();
        while (artifactsEnumeration.hasMoreElements()) {
            URL url = artifactsEnumeration.nextElement();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    if (StringUtils.isEmpty(line)) {
                        continue;
                    }
                    artifactIds.add(line);
                }
            }
        }
        return artifactIds;
    }
}
