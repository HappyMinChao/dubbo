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

import org.apache.dubbo.common.utils.Assert;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 服务消费者模型
 * 
 * 表示服务引用的配置和元数据信息，包括服务代理对象、方法模型、异步配置等。
 * 该类是Dubbo服务消费者的核心模型，用于存储和管理服务引用的相关信息。
 * 
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li>存储服务引用的代理对象</li>
 * <li>管理服务的所有方法模型信息</li>
 * <li>维护异步调用的方法配置</li>
 * <li>记录服务提供者应用名称集合</li>
 * <li>提供服务方法的查询和访问</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>服务引用时创建ConsumerModel</li>
 * <li>服务调用时查询方法模型</li>
 * <li>异步调用时获取方法异步配置</li>
 * <li>服务路由时获取目标应用信息</li>
 * </ul>
 * 
 * <p>
 * <b>配置绑定：</b>
 * 该模型绑定到引用的配置，例如分组（group）、版本（version）或方法级别的配置。
 * 
 * This model is bound to your reference's configuration, for example, group, version or method level configuration.
 */
public class ConsumerModel extends ServiceModel {
    /** 服务提供者应用名称集合，用于记录可用的服务提供者 */
    private final Set<String> apps = new TreeSet<>();

    /** 异步方法配置映射表，key为方法名，value为异步方法配置信息 */
    private final Map<String, AsyncMethodInfo> methodConfigs;
    /** 方法模型映射表，key为Method对象，value为消费者方法模型 */
    private Map<Method, ConsumerMethodModel> methodModels = new HashMap<>();

    /**
     * 构造函数
     * 创建服务消费者模型
     * 
     * <p>此构造函数创建一个ConsumerModel实例，传入的对象不能为null。
     * 如果服务名、服务实例、代理对象、方法为null，则构造函数将抛出{@link IllegalArgumentException}。
     * 
     * This constructor creates an instance of ConsumerModel and passed objects should not be null.
     * If service name, service instance, proxy object,methods should not be null. If these are null
     * then this constructor will throw {@link IllegalArgumentException}
     *
     * @param serviceKey 服务唯一标识键 (Name of the service)
     * @param proxyObject 服务代理对象 (Proxy object)
     * @param serviceDescriptor 服务描述符
     * @param methodConfigs 方法异步配置映射表
     * @param interfaceClassLoader 接口类加载器
     * @throws IllegalArgumentException 如果服务键为null或空字符串
     */
    public ConsumerModel(
            String serviceKey,
            Object proxyObject,
            ServiceDescriptor serviceDescriptor,
            Map<String, AsyncMethodInfo> methodConfigs,
            ClassLoader interfaceClassLoader) {

        super(proxyObject, serviceKey, serviceDescriptor, null, interfaceClassLoader);
        Assert.notEmptyString(serviceKey, "Service name can't be null or blank");

        this.methodConfigs = methodConfigs == null ? new HashMap<>() : methodConfigs;
    }

    /**
     * 构造函数
     * 创建服务消费者模型（带服务元数据）
     * 
     * @param serviceKey 服务唯一标识键
     * @param proxyObject 服务代理对象
     * @param serviceDescriptor 服务描述符
     * @param metadata 服务元数据
     * @param methodConfigs 方法异步配置映射表
     * @param interfaceClassLoader 接口类加载器
     * @throws IllegalArgumentException 如果服务键为null或空字符串
     */
    public ConsumerModel(
            String serviceKey,
            Object proxyObject,
            ServiceDescriptor serviceDescriptor,
            ServiceMetadata metadata,
            Map<String, AsyncMethodInfo> methodConfigs,
            ClassLoader interfaceClassLoader) {

        super(proxyObject, serviceKey, serviceDescriptor, null, metadata, interfaceClassLoader);
        Assert.notEmptyString(serviceKey, "Service name can't be null or blank");

        this.methodConfigs = methodConfigs == null ? new HashMap<>() : methodConfigs;
    }

    /**
     * 构造函数
     * 创建服务消费者模型（完整版本，包含模块模型）
     * 
     * @param serviceKey 服务唯一标识键
     * @param proxyObject 服务代理对象
     * @param serviceDescriptor 服务描述符
     * @param moduleModel 模块模型
     * @param metadata 服务元数据
     * @param methodConfigs 方法异步配置映射表
     * @param interfaceClassLoader 接口类加载器
     * @throws IllegalArgumentException 如果服务键为null或空字符串
     */
    public ConsumerModel(
            String serviceKey,
            Object proxyObject,
            ServiceDescriptor serviceDescriptor,
            ModuleModel moduleModel,
            ServiceMetadata metadata,
            Map<String, AsyncMethodInfo> methodConfigs,
            ClassLoader interfaceClassLoader) {
        super(proxyObject, serviceKey, serviceDescriptor, moduleModel, metadata, interfaceClassLoader);

        Assert.notEmptyString(serviceKey, "Service name can't be null or blank");

        this.methodConfigs = methodConfigs == null ? new HashMap<>() : methodConfigs;
    }

    /**
     * 获取方法的异步配置信息
     * 
     * @param methodName 方法名
     * @return 异步方法配置信息，如果未配置返回null
     */
    public AsyncMethodInfo getMethodConfig(String methodName) {
        return methodConfigs.get(methodName);
    }

    /**
     * 获取服务提供者应用名称集合
     * 
     * @return 应用名称集合
     */
    public Set<String> getApps() {
        return apps;
    }

    /**
     * 获取方法的异步配置信息
     * 
     * @param methodName 方法名
     * @return 异步方法配置信息，如果未配置返回null
     */
    public AsyncMethodInfo getAsyncInfo(String methodName) {
        return methodConfigs.get(methodName);
    }

    /**
     * 初始化方法模型
     * 遍历服务接口的所有方法，为每个方法创建对应的消费者方法模型
     */
    public void initMethodModels() {
        Class<?>[] interfaceList;
        if (getProxyObject() == null) {
            Class<?> serviceInterfaceClass = getServiceInterfaceClass();
            if (serviceInterfaceClass != null) {
                interfaceList = new Class[] {serviceInterfaceClass};
            } else {
                interfaceList = new Class[0];
            }
        } else {
            interfaceList = getProxyObject().getClass().getInterfaces();
        }
        for (Class<?> interfaceClass : interfaceList) {
            for (Method method : interfaceClass.getMethods()) {
                methodModels.put(method, new ConsumerMethodModel(method));
            }
        }
    }

    /**
     * 根据Method对象获取消费者端的方法模型
     * Return method model for the given method on consumer side
     *
     * @param method 方法对象 (method object)
     * @return 方法模型 (method model)
     */
    public ConsumerMethodModel getMethodModel(Method method) {
        return methodModels.get(method);
    }

    /**
     * 根据方法名获取消费者端的方法模型
     * Return method model for the given method on consumer side
     *
     * @param method 方法名 (method object)
     * @return 方法模型，如果未找到返回null (method model)
     */
    public ConsumerMethodModel getMethodModel(String method) {
        Optional<Map.Entry<Method, ConsumerMethodModel>> consumerMethodModelEntry = methodModels.entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(method))
                .findFirst();
        return consumerMethodModelEntry.map(Map.Entry::getValue).orElse(null);
    }

    /**
     * 根据方法名和参数类型获取消费者端的方法模型
     * 
     * @param method 方法名 (methodName)
     * @param argsType 方法参数类型数组 (method arguments type)
     * @return 方法模型，如果未找到返回null
     */
    public ConsumerMethodModel getMethodModel(String method, String[] argsType) {
        Optional<ConsumerMethodModel> consumerMethodModel = methodModels.entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(method))
                .map(Map.Entry::getValue)
                .filter(methodModel -> Arrays.equals(argsType, methodModel.getParameterTypes()))
                .findFirst();
        return consumerMethodModel.orElse(null);
    }

    /**
     * 获取当前服务的所有方法模型
     * Return all method models for the current service
     *
     * @return 方法模型列表 (method model list)
     */
    public List<ConsumerMethodModel> getAllMethodModels() {
        return new ArrayList<>(methodModels.values());
    }

    /**
     * 判断两个ConsumerModel是否相等
     * 比较apps、methodConfigs和methodModels是否相同
     * 
     * @param o 要比较的对象
     * @return 如果相等返回true，否则返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ConsumerModel that = (ConsumerModel) o;
        return Objects.equals(apps, that.apps)
                && Objects.equals(methodConfigs, that.methodConfigs)
                && Objects.equals(methodModels, that.methodModels);
    }

    /**
     * 计算哈希码
     * 
     * @return 哈希码值
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), apps, methodConfigs, methodModels);
    }
}
