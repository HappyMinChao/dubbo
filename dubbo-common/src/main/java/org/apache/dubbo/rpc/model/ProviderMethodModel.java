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
package org.apache.dubbo.rpc.model;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 服务提供者方法模型
 * 
 * 封装服务提供者方法的元数据信息，包括方法对象、方法名、参数类型等。
 * 该类用于存储和管理服务提供者端方法的详细信息，支持方法调用时的参数类型匹配和泛型处理。
 * 
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li>存储方法的反射对象（Method）</li>
 * <li>缓存方法名称和参数类型信息</li>
 * <li>提供参数类型的字符串表示，用于方法匹配</li>
 * <li>支持泛型参数类型的获取</li>
 * <li>提供属性映射表，支持扩展属性存储</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>服务导出时构建方法模型</li>
 * <li>服务调用时进行方法匹配</li>
 * <li>RPC调用时获取方法参数类型信息</li>
 * <li>方法级别的配置和监控</li>
 * </ul>
 * 
 * <p>
 * <b>注意：</b>该类已被标记为@Deprecated，建议使用{@link MethodDescriptor}替代。
 * 
 * Replaced with {@link MethodDescriptor}
 */
@Deprecated
public class ProviderMethodModel {
    /** 方法的反射对象 */
    private final Method method;
    /** 方法名称 */
    private final String methodName;
    /** 方法参数类型数组 */
    private final Class<?>[] parameterClasses;
    /** 方法参数类型的字符串表示数组，用于方法签名匹配 */
    private final String[] methodArgTypes;
    /** 方法的泛型参数类型数组 */
    private final Type[] genericParameterTypes;
    /** 属性映射表，用于存储方法级别的扩展属性 */
    private final ConcurrentMap<String, Object> attributeMap = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 根据Method对象创建提供者方法模型
     * 
     * @param method 方法的反射对象
     */
    public ProviderMethodModel(Method method) {
        this.method = method;
        this.methodName = method.getName();
        this.parameterClasses = method.getParameterTypes();
        this.methodArgTypes = getArgTypes(method);
        this.genericParameterTypes = method.getGenericParameterTypes();
    }

    /**
     * 获取方法的反射对象
     * 
     * @return Method对象
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 获取方法名称
     * 
     * @return 方法名称
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 获取方法参数类型的字符串数组
     * 用于方法签名匹配和远程调用时的方法识别
     * 
     * @return 参数类型名称数组
     */
    public String[] getMethodArgTypes() {
        return methodArgTypes;
    }

    /**
     * 获取属性映射表
     * 用于存储和获取方法级别的扩展属性
     * 
     * @return 属性映射表
     */
    public ConcurrentMap<String, Object> getAttributeMap() {
        return attributeMap;
    }

    /**
     * 将方法参数类型转换为字符串数组
     * 将Class对象转换为其完全限定名，便于序列化和传输
     * 
     * @param method 方法对象
     * @return 参数类型名称数组
     */
    private static String[] getArgTypes(Method method) {
        String[] methodArgTypes = new String[0];
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 0) {
            methodArgTypes = new String[parameterTypes.length];
            int index = 0;
            for (Class<?> paramType : parameterTypes) {
                methodArgTypes[index++] = paramType.getName();
            }
        }
        return methodArgTypes;
    }

    /**
     * 获取方法参数类型数组
     * 
     * @return 参数类型Class数组
     */
    public Class<?>[] getParameterClasses() {
        return parameterClasses;
    }

    /**
     * 获取方法的泛型参数类型数组
     * 用于处理泛型方法的参数类型信息
     * 
     * @return 泛型参数类型数组
     */
    public Type[] getGenericParameterTypes() {
        return genericParameterTypes;
    }
}
