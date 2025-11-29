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
import org.apache.dubbo.common.url.component.URLItemCache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY_PREFIX;
import static org.apache.dubbo.common.utils.StringUtils.EMPTY_STRING;
import static org.apache.dubbo.common.utils.StringUtils.decodeHexByte;
import static org.apache.dubbo.common.utils.Utf8Utils.decodeUtf8;

/**
 * URL字符串解析器
 * 用于解析和处理URL字符串的工具类
 */
public final class URLStrParser {
    /** 编码后的问号 */
    public static final String ENCODED_QUESTION_MARK = "%3F";
    /** 编码后的时间戳键 */
    public static final String ENCODED_TIMESTAMP_KEY = "timestamp%3D";
    /** 编码后的进程ID键 */
    public static final String ENCODED_PID_KEY = "pid%3D";
    /** 编码后的与符号 */
    public static final String ENCODED_AND_MARK = "%26";
    /** 空格字符 */
    private static final char SPACE = 0x20;

    /** 解码临时缓冲区 */
    private static final ThreadLocal<TempBuf> DECODE_TEMP_BUF = ThreadLocal.withInitial(() -> new TempBuf(1024));

    /**
     * 私有构造函数，防止实例化
     */
    private URLStrParser() {
        // empty
    }

    /**
     * 解析已解码的URL字符串
     * 
     * @param decodedURLStr : 经过{@link URL#decode}处理的字符串
     *                      decodedURLStr format: protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [protocol://][username:password@][host:port]/[path][?k1=v1&k2=v2]
     * @return 解析后的URL对象
     */
    public static URL parseDecodedStr(String decodedURLStr) {
        Map<String, String> parameters = null;
        int pathEndIdx = decodedURLStr.indexOf('?');
        if (pathEndIdx >= 0) {
            parameters = parseDecodedParams(decodedURLStr, pathEndIdx + 1);
        } else {
            pathEndIdx = decodedURLStr.length();
        }

        String decodedBody = decodedURLStr.substring(0, pathEndIdx);
        return parseURLBody(decodedURLStr, decodedBody, parameters);
    }

    /**
     * 解析已解码的参数
     * 
     * @param str 字符串
     * @param from 起始位置
     * @return 参数映射表
     */
    private static Map<String, String> parseDecodedParams(String str, int from) {
        int len = str.length();
        if (from >= len) {
            return Collections.emptyMap();
        }

        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        Map<String, String> params = new HashMap<>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        for (i = from; i < len; i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    addParam(str, false, nameStart, valueStart, i, params, tempBuf);
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        addParam(str, false, nameStart, valueStart, i, params, tempBuf);
        return params;
    }

    /**
     * 解析URL主体部分
     * 
     * @param fullURLStr  完整的URL字符串
     * @param decodedBody 已解码的URL主体部分，格式为: [protocol://][username:password@][host:port]/[path]
     * @param parameters  参数映射表
     * @return URL对象
     */
    private static URL parseURLBody(String fullURLStr, String decodedBody, Map<String, String> parameters) {
        int starIdx = 0, endIdx = decodedBody.length();
        // 忽略'#'后面的内容
        int poundIndex = decodedBody.indexOf('#');
        if (poundIndex != -1) {
            endIdx = poundIndex;
        }

        String protocol = null;
        int protoEndIdx = decodedBody.indexOf("://");
        if (protoEndIdx >= 0) {
            if (protoEndIdx == 0) {
                throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
            }
            protocol = decodedBody.substring(0, protoEndIdx);
            starIdx = protoEndIdx + 3;
        } else {
            // case: file:/path/to/file.txt
            protoEndIdx = decodedBody.indexOf(":/");
            if (protoEndIdx >= 0) {
                if (protoEndIdx == 0) {
                    throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
                }
                protocol = decodedBody.substring(0, protoEndIdx);
                starIdx = protoEndIdx + 1;
            }
        }

        String path = null;
        int pathStartIdx = indexOf(decodedBody, '/', starIdx, endIdx);
        if (pathStartIdx >= 0) {
            path = decodedBody.substring(pathStartIdx + 1, endIdx);
            endIdx = pathStartIdx;
        }

        String username = null;
        String password = null;
        int pwdEndIdx = lastIndexOf(decodedBody, '@', starIdx, endIdx);
        if (pwdEndIdx > 0) {
            int passwordStartIdx = indexOf(decodedBody, ':', starIdx, pwdEndIdx);
            if (passwordStartIdx != -1) { // 容忍不完整的用户名密码输入，如'1234@'
                username = decodedBody.substring(starIdx, passwordStartIdx);
                password = decodedBody.substring(passwordStartIdx + 1, pwdEndIdx);
            } else {
                username = decodedBody.substring(starIdx, pwdEndIdx);
            }
            starIdx = pwdEndIdx + 1;
        }

        String host = null;
        int port = 0;
        int hostEndIdx = lastIndexOf(decodedBody, ':', starIdx, endIdx);
        if (hostEndIdx > 0 && hostEndIdx < decodedBody.length() - 1) {
            if (lastIndexOf(decodedBody, '%', starIdx, endIdx) > hostEndIdx) {
                // ipv6地址带作用域ID
                // 例如: fe80:0:0:0:894:aeec:f37d:23e1%en0
                // 参见 https://howdoesinternetwork.com/2013/ipv6-zone-id
                // 忽略
            } else {
                port = Integer.parseInt(decodedBody.substring(hostEndIdx + 1, endIdx));
                endIdx = hostEndIdx;
            }
        }

        if (endIdx > starIdx) {
            host = decodedBody.substring(starIdx, endIdx);
        }

        // 检查缓存
        protocol = URLItemCache.intern(protocol);
        path = URLItemCache.checkPath(path);

        return new ServiceConfigURL(protocol, username, password, host, port, path, parameters);
    }

    /**
     * 将原始URL解析为数组
     * 
     * @param rawURLStr 原始URL字符串
     * @param pathEndIdx 路径结束索引
     * @return 包含URL主体和参数的字符串数组
     */
    public static String[] parseRawURLToArrays(String rawURLStr, int pathEndIdx) {
        String[] parts = new String[2];
        int paramStartIdx = pathEndIdx + 3; // 跳过ENCODED_QUESTION_MARK
        if (pathEndIdx == -1) {
            pathEndIdx = rawURLStr.indexOf('?');
            if (pathEndIdx == -1) {
                // 没有参数的URL，仍然进行解码
                rawURLStr = URL.decode(rawURLStr);
            } else {
                paramStartIdx = pathEndIdx + 1;
            }
        }
        if (pathEndIdx >= 0) {
            parts[0] = rawURLStr.substring(0, pathEndIdx);
            parts[1] = rawURLStr.substring(paramStartIdx);
        } else {
            parts = new String[] {rawURLStr};
        }
        return parts;
    }

    /**
     * 解析参数
     * 
     * @param rawParams 原始参数字符串
     * @param encoded 是否已编码
     * @return 参数映射表
     */
    public static Map<String, String> parseParams(String rawParams, boolean encoded) {
        if (encoded) {
            return parseEncodedParams(rawParams, 0);
        }
        return parseDecodedParams(rawParams, 0);
    }

    /**
     * 解析已编码的URL字符串
     * 
     * @param encodedURLStr : 经过{@link URL#encode(String)}处理的字符串
     *                      encodedURLStr解码后格式: protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [protocol://][username:password@][host:port]/[path][?k1=v1&k2=v2]
     * @return 解析后的URL对象
     */
    public static URL parseEncodedStr(String encodedURLStr) {
        Map<String, String> parameters = null;
        int pathEndIdx = encodedURLStr.toUpperCase().indexOf("%3F"); // '?'
        if (pathEndIdx >= 0) {
            parameters = parseEncodedParams(encodedURLStr, pathEndIdx + 3);
        } else {
            pathEndIdx = encodedURLStr.length();
        }

        // decodedBody format: [protocol://][username:password@][host:port]/[path]
        String decodedBody = decodeComponent(encodedURLStr, 0, pathEndIdx, false, DECODE_TEMP_BUF.get());
        return parseURLBody(encodedURLStr, decodedBody, parameters);
    }

    /**
     * 解析已编码的参数
     * 
     * @param str 字符串
     * @param from 起始位置
     * @return 参数映射表
     */
    private static Map<String, String> parseEncodedParams(String str, int from) {
        int len = str.length();
        if (from >= len) {
            return Collections.emptyMap();
        }

        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        Map<String, String> params = new HashMap<>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        for (i = from; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == '%') {
                if (i + 3 > len) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                ch = (char) decodeHexByte(str, i + 1);
                i += 2;
            }

            switch (ch) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    addParam(str, true, nameStart, valueStart, i - 2, params, tempBuf);
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        addParam(str, true, nameStart, valueStart, i, params, tempBuf);
        return params;
    }

    /**
     * 添加参数
     * 
     * @param str 字符串
     * @param isEncoded 是否已编码
     * @param nameStart 名称起始位置
     * @param valueStart 值起始位置
     * @param valueEnd 值结束位置
     * @param params 参数映射表
     * @param tempBuf 临时缓冲区
     * @return 如果成功添加参数返回true，否则返回false
     */
    private static boolean addParam(
            String str,
            boolean isEncoded,
            int nameStart,
            int valueStart,
            int valueEnd,
            Map<String, String> params,
            TempBuf tempBuf) {
        if (nameStart >= valueEnd) {
            return false;
        }

        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }

        if (isEncoded) {
            String name = decodeComponent(str, nameStart, valueStart - 3, false, tempBuf);
            String value;
            if (valueStart >= valueEnd) {
                value = "";
            } else {
                value = decodeComponent(str, valueStart, valueEnd, false, tempBuf);
            }
            URLItemCache.putParams(params, name, value);
            // 兼容低版本注册"default."键
            if (name.startsWith(DEFAULT_KEY_PREFIX)) {
                params.putIfAbsent(name.substring(DEFAULT_KEY_PREFIX.length()), value);
            }
        } else {
            String name = str.substring(nameStart, valueStart - 1);
            String value;
            if (valueStart >= valueEnd) {
                value = "";
            } else {
                value = str.substring(valueStart, valueEnd);
            }
            URLItemCache.putParams(params, name, value);
            // 兼容低版本注册"default."键
            if (name.startsWith(DEFAULT_KEY_PREFIX)) {
                params.putIfAbsent(name.substring(DEFAULT_KEY_PREFIX.length()), value);
            }
        }
        return true;
    }

    /**
     * 解码组件
     * 
     * @param s 字符串
     * @param from 起始位置
     * @param toExcluded 结束位置（不包含）
     * @param isPath 是否是路径
     * @param tempBuf 临时缓冲区
     * @return 解码后的字符串
     */
    private static String decodeComponent(String s, int from, int toExcluded, boolean isPath, TempBuf tempBuf) {
        int len = toExcluded - from;
        if (len <= 0) {
            return EMPTY_STRING;
        }

        int firstEscaped = -1;
        for (int i = from; i < toExcluded; i++) {
            char c = s.charAt(i);
            if (c == '%' || c == '+' && !isPath) {
                firstEscaped = i;
                break;
            }
        }
        if (firstEscaped == -1) {
            return s.substring(from, toExcluded);
        }

        // 每个编码字节占用3个字符（例如"%20"）
        int decodedCapacity = (toExcluded - firstEscaped) / 3;
        byte[] buf = tempBuf.byteBuf(decodedCapacity);
        char[] charBuf = tempBuf.charBuf(len);
        s.getChars(from, firstEscaped, charBuf, 0);

        int charBufIdx = firstEscaped - from;
        return decodeUtf8Component(s, firstEscaped, toExcluded, isPath, buf, charBuf, charBufIdx);
    }

    /**
     * 解码UTF-8组件
     * 
     * @param str 字符串
     * @param firstEscaped 第一个转义字符的位置
     * @param toExcluded 结束位置（不包含）
     * @param isPath 是否是路径
     * @param buf 字节数组缓冲区
     * @param charBuf 字符数组缓冲区
     * @param charBufIdx 字符数组索引
     * @return 解码后的字符串
     */
    private static String decodeUtf8Component(
            String str, int firstEscaped, int toExcluded, boolean isPath, byte[] buf, char[] charBuf, int charBufIdx) {
        int bufIdx;
        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = str.charAt(i);
            if (c != '%') {
                charBuf[charBufIdx++] = c != '+' || isPath ? c : SPACE;
                continue;
            }

            bufIdx = 0;
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                buf[bufIdx++] = decodeHexByte(str, i + 1);
                i += 3;
            } while (i < toExcluded && str.charAt(i) == '%');
            i--;

            charBufIdx += decodeUtf8(buf, 0, bufIdx, charBuf, charBufIdx);
        }
        return new String(charBuf, 0, charBufIdx);
    }

    /**
     * 查找字符在字符串中的位置
     * 
     * @param str 字符串
     * @param ch 要查找的字符
     * @param from 起始位置
     * @param toExclude 结束位置（不包含）
     * @return 字符在字符串中的位置，如果未找到返回-1
     */
    private static int indexOf(String str, char ch, int from, int toExclude) {
        from = Math.max(from, 0);
        toExclude = Math.min(toExclude, str.length());
        if (from > toExclude) {
            return -1;
        }

        for (int i = from; i < toExclude; i++) {
            if (str.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从后往前查找字符在字符串中的位置
     * 
     * @param str 字符串
     * @param ch 要查找的字符
     * @param from 起始位置
     * @param toExclude 结束位置（不包含）
     * @return 字符在字符串中的位置，如果未找到返回-1
     */
    private static int lastIndexOf(String str, char ch, int from, int toExclude) {
        from = Math.max(from, 0);
        toExclude = Math.min(toExclude, str.length() - 1);
        if (from > toExclude) {
            return -1;
        }

        for (int i = toExclude; i >= from; i--) {
            if (str.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 临时缓冲区类
     */
    private static final class TempBuf {

        /** 字符数组 */
        private final char[] chars;

        /** 字节数组 */
        private final byte[] bytes;

        /**
         * 构造函数
         * 
         * @param bufSize 缓冲区大小
         */
        TempBuf(int bufSize) {
            this.chars = new char[bufSize];
            this.bytes = new byte[bufSize];
        }

        /**
         * 获取字符缓冲区
         * 
         * @param size 缓冲区大小
         * @return 字符数组
         */
        public char[] charBuf(int size) {
            char[] chars = this.chars;
            if (size <= chars.length) {
                return chars;
            }
            return new char[size];
        }

        /**
         * 获取字节缓冲区
         * 
         * @param size 缓冲区大小
         * @return 字节数组
         */
        public byte[] byteBuf(int size) {
            byte[] bytes = this.bytes;
            if (size <= bytes.length) {
                return bytes;
            }
            return new byte[size];
        }
    }
}
