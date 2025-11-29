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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.lang.Prioritized;

/**
 * 加载策略接口
 * Loading strategy interface
 */
public interface LoadingStrategy extends Prioritized {

    /**
     * 获取目录路径
     * 
     * @return 目录路径
     * Get directory path
     * 
     * @return directory path
     */
    String directory();

    /**
     * 是否优先使用扩展类加载器
     * 
     * @return 如果优先使用扩展类加载器返回true，否则返回false
     * Whether to prefer extension class loader
     * 
     * @return return true if prefer extension class loader, otherwise return false
     */
    default boolean preferExtensionClassLoader() {
        return false;
    }

    /**
     * 获取排除的包
     * 
     * @return 排除的包数组
     * Get excluded packages
     * 
     * @return excluded packages array
     */
    default String[] excludedPackages() {
        return null;
    }

    /**
     * 限制某些不应从`org.apache.dubbo`包类型SPI类加载的类。
     * 例如，我们可以限制包为`org.xxx.xxx`的实现类可以作为SPI实现加载。
     *
     * @return 可以在`org.apache.dubbo`的SPI中加载的包
     * To restrict some class that should not be loaded from `org.apache.dubbo` package type SPI class.
     * For example, we can restrict the implementation class which package is `org.xxx.xxx`
     * can be loaded as SPI implementation.
     *
     * @return packages can be loaded in `org.apache.dubbo`'s SPI
     */
    default String[] includedPackages() {
        // default match all
        return null;
    }

    /**
     * 限制某些不应从`org.alibaba.dubbo`（为了兼容性目的）包类型SPI类加载的类。
     * 例如，我们可以限制包为`org.xxx.xxx`的实现类可以作为SPI实现加载。
     *
     * @return 可以在`org.alibaba.dubbo`的SPI中加载的包
     * To restrict some class that should not be loaded from `org.alibaba.dubbo`(for compatible purpose)
     * package type SPI class.
     * For example, we can restrict the implementation class which package is `org.xxx.xxx`
     * can be loaded as SPI implementation
     *
     * @return packages can be loaded in `org.alibaba.dubbo`'s SPI
     */
    default String[] includedPackagesInCompatibleType() {
        // default match all
        return null;
    }

    /**
     * 限制某些应从Dubbo的ClassLoader加载的类。
     * 例如，我们可以限制`org.apache.dubbo`包中的类声明应从Dubbo的ClassLoader加载，
     * 用户不能声明这些类。
     *
     * @return 应加载的类包
     * @since 3.0.4
     * To restrict some class that should load from Dubbo's ClassLoader.
     * For example, we can restrict the class declaration in `org.apache.dubbo` package should
     * be loaded from Dubbo's ClassLoader and users cannot declare these classes.
     *
     * @return class packages should load
     * @since 3.0.4
     */
    default String[] onlyExtensionClassLoaderPackages() {
        return new String[] {};
    }

    /**
     * 指示当前{@link LoadingStrategy}是否支持覆盖其他优先级较低的实例。
     *
     * @return 如果支持返回<code>true</code>，否则返回<code>false</code>
     * @since 2.7.7
     * Indicates current {@link LoadingStrategy} supports overriding other lower prioritized instances or not.
     *
     * @return if supports, return <code>true</code>, or <code>false</code>
     * @since 2.7.7
     */
    default boolean overridden() {
        return false;
    }

    /**
     * 获取名称
     * 
     * @return 类名
     * Get name
     * 
     * @return class name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 当SPI仅由dubbo框架类加载器加载时，表示所有LoadingStrategy都应该加载此SPI
     * when spi is loaded by dubbo framework classloader only, it indicates all LoadingStrategy should load this spi
     */
    String ALL = "ALL";
}
