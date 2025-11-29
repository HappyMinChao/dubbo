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

import org.apache.dubbo.common.URL;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 服务提供者模型
 * 
 * 表示已发布的服务的元数据信息，包含服务实例、方法模型、URL信息等。
 * 该类是Dubbo服务提供者的核心模型，用于存储和管理服务提供者的相关信息。
 * 
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li>存储服务提供者的实例对象</li>
 * <li>管理服务的所有方法模型信息</li>
 * <li>维护服务的注册URL状态</li>
 * <li>记录服务的最后调用时间</li>
 * <li>提供服务方法的查询和访问</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>服务导出时创建ProviderModel</li>
 * <li>服务调用时查询方法模型</li>
 * <li>服务注册和注销时更新URL状态</li>
 * <li>服务监控和统计时获取调用信息</li>
 * </ul>
 * 
 * <p>
 * ProviderModel is about published services
 */
public class ProviderModel extends ServiceModel {
    /** 已注册的URL列表，包含注册状态信息 */
    private final List<RegisterStatedURL> urls;
    /** 方法模型映射表，key为方法名，value为该方法的所有重载版本 */
    private final Map<String, List<ProviderMethodModel>> methods = new HashMap<>();

    /**
     * 服务引用的URL列表
     * The url of the reference service
     */
    private List<URL> serviceUrls = new ArrayList<>();

    /** 最后一次调用时间戳，用于服务监控和统计 */
    private volatile long lastInvokeTime = 0;

    /**
     * 构造函数
     * 创建服务提供者模型
     * 
     * @param serviceKey 服务唯一标识键
     * @param serviceInstance 服务实例对象
     * @param serviceDescriptor 服务描述符
     * @param interfaceClassLoader 接口类加载器
     * @throws IllegalArgumentException 如果服务实例为null
     */
    public ProviderModel(
            String serviceKey,
            Object serviceInstance,
            ServiceDescriptor serviceDescriptor,
            ClassLoader interfaceClassLoader) {
        super(serviceInstance, serviceKey, serviceDescriptor, null, interfaceClassLoader);
        if (null == serviceInstance) {
            throw new IllegalArgumentException("Service[" + serviceKey + "]Target is NULL.");
        }

        this.urls = new CopyOnWriteArrayList<>();
    }

    /**
     * 构造函数
     * 创建服务提供者模型（带服务元数据）
     * 
     * @param serviceKey 服务唯一标识键
     * @param serviceInstance 服务实例对象
     * @param serviceDescriptor 服务描述符
     * @param serviceMetadata 服务元数据
     * @param interfaceClassLoader 接口类加载器
     * @throws IllegalArgumentException 如果服务实例为null
     */
    public ProviderModel(
            String serviceKey,
            Object serviceInstance,
            ServiceDescriptor serviceDescriptor,
            ServiceMetadata serviceMetadata,
            ClassLoader interfaceClassLoader) {
        super(serviceInstance, serviceKey, serviceDescriptor, null, serviceMetadata, interfaceClassLoader);
        if (null == serviceInstance) {
            throw new IllegalArgumentException("Service[" + serviceKey + "]Target is NULL.");
        }

        initMethod(serviceDescriptor.getServiceInterfaceClass());
        this.urls = new ArrayList<>(1);
    }

    /**
     * 构造函数
     * 创建服务提供者模型（完整版本，包含模块模型）
     * 
     * @param serviceKey 服务唯一标识键
     * @param serviceInstance 服务实例对象
     * @param serviceModel 服务描述符
     * @param moduleModel 模块模型
     * @param serviceMetadata 服务元数据
     * @param interfaceClassLoader 接口类加载器
     * @throws IllegalArgumentException 如果服务实例为null
     */
    public ProviderModel(
            String serviceKey,
            Object serviceInstance,
            ServiceDescriptor serviceModel,
            ModuleModel moduleModel,
            ServiceMetadata serviceMetadata,
            ClassLoader interfaceClassLoader) {
        super(serviceInstance, serviceKey, serviceModel, moduleModel, serviceMetadata, interfaceClassLoader);
        if (null == serviceInstance) {
            throw new IllegalArgumentException("Service[" + serviceKey + "]Target is NULL.");
        }

        initMethod(serviceModel.getServiceInterfaceClass());
        this.urls = new ArrayList<>(1);
    }

    /**
     * 获取服务实例对象
     * 
     * @return 服务实例对象
     */
    public Object getServiceInstance() {
        return getProxyObject();
    }

    /**
     * 获取已注册的URL列表
     * 
     * @return 包含注册状态的URL列表
     */
    public List<RegisterStatedURL> getStatedUrl() {
        return urls;
    }

    /**
     * 添加注册状态的URL
     * 
     * @param url 包含注册状态的URL对象
     */
    public void addStatedUrl(RegisterStatedURL url) {
        this.urls.add(url);
    }

    /**
     * 注册状态URL类
     * 
     * 封装服务提供者URL、注册中心URL以及注册状态信息。
     * 用于跟踪服务在注册中心的注册状态。
     */
    public static class RegisterStatedURL {
        /** 注册中心URL */
        private volatile URL registryUrl;
        /** 服务提供者URL */
        private volatile URL providerUrl;
        /** 注册状态标识 */
        private volatile boolean registered;

        /**
         * 构造函数
         * 
         * @param providerUrl 服务提供者URL
         * @param registryUrl 注册中心URL
         * @param registered 是否已注册
         */
        public RegisterStatedURL(URL providerUrl, URL registryUrl, boolean registered) {
            this.providerUrl = providerUrl;
            this.registered = registered;
            this.registryUrl = registryUrl;
        }

        /**
         * 获取服务提供者URL
         * 
         * @return 服务提供者URL
         */
        public URL getProviderUrl() {
            return providerUrl;
        }

        /**
         * 设置服务提供者URL
         * 
         * @param providerUrl 服务提供者URL
         */
        public void setProviderUrl(URL providerUrl) {
            this.providerUrl = providerUrl;
        }

        /**
         * 检查是否已注册
         * 
         * @return 如果已注册返回true，否则返回false
         */
        public boolean isRegistered() {
            return registered;
        }

        /**
         * 设置注册状态
         * 
         * @param registered 注册状态
         */
        public void setRegistered(boolean registered) {
            this.registered = registered;
        }

        /**
         * 获取注册中心URL
         * 
         * @return 注册中心URL
         */
        public URL getRegistryUrl() {
            return registryUrl;
        }

        /**
         * 设置注册中心URL
         * 
         * @param registryUrl 注册中心URL
         */
        public void setRegistryUrl(URL registryUrl) {
            this.registryUrl = registryUrl;
        }
    }

    /**
     * 获取所有方法模型
     * 
     * @return 所有方法模型的列表
     */
    public List<ProviderMethodModel> getAllMethodModels() {
        List<ProviderMethodModel> result = new ArrayList<>();
        for (List<ProviderMethodModel> models : methods.values()) {
            result.addAll(models);
        }
        return result;
    }

    /**
     * 根据方法名和参数类型获取方法模型
     * 
     * @param methodName 方法名
     * @param argTypes 参数类型数组
     * @return 匹配的方法模型，如果未找到返回null
     */
    public ProviderMethodModel getMethodModel(String methodName, String[] argTypes) {
        List<ProviderMethodModel> methodModels = methods.get(methodName);
        if (methodModels != null) {
            for (ProviderMethodModel methodModel : methodModels) {
                if (Arrays.equals(argTypes, methodModel.getMethodArgTypes())) {
                    return methodModel;
                }
            }
        }
        return null;
    }

    /**
     * 根据方法名获取方法模型列表
     * 支持方法重载，返回同名方法的所有版本
     * 
     * @param methodName 方法名
     * @return 方法模型列表，如果未找到返回空列表
     */
    public List<ProviderMethodModel> getMethodModelList(String methodName) {
        List<ProviderMethodModel> resultList = methods.get(methodName);
        return resultList == null ? Collections.emptyList() : resultList;
    }

    /**
     * 初始化方法模型
     * 遍历服务接口的所有方法，为每个方法创建对应的方法模型
     * 
     * @param serviceInterfaceClass 服务接口类
     */
    private void initMethod(Class<?> serviceInterfaceClass) {
        Method[] methodsToExport = serviceInterfaceClass.getMethods();

        for (Method method : methodsToExport) {
            method.setAccessible(true);

            List<ProviderMethodModel> methodModels = methods.computeIfAbsent(method.getName(), k -> new ArrayList<>());
            methodModels.add(new ProviderMethodModel(method));
        }
    }

    /**
     * 获取服务URL列表
     * 
     * @return 服务URL列表
     */
    public List<URL> getServiceUrls() {
        return serviceUrls;
    }

    /**
     * 设置服务URL列表
     * 
     * @param urls 服务URL列表
     */
    public void setServiceUrls(List<URL> urls) {
        this.serviceUrls = urls;
    }

    /**
     * 获取最后一次调用时间
     * 
     * @return 最后一次调用时间戳（毫秒）
     */
    public long getLastInvokeTime() {
        return lastInvokeTime;
    }

    /**
     * 更新最后一次调用时间
     * 将最后调用时间更新为当前时间
     */
    public void updateLastInvokeTime() {
        this.lastInvokeTime = System.currentTimeMillis();
    }

    /**
     * 判断两个ProviderModel是否相等
     * 比较URLs和methods是否相同
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
        ProviderModel that = (ProviderModel) o;
        return Objects.equals(urls, that.urls) && Objects.equals(methods, that.methods);
    }

    /**
     * 计算哈希码
     * 
     * @return 哈希码值
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), urls, methods);
    }
}
