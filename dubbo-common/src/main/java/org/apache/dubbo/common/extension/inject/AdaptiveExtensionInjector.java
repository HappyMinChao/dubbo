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
package org.apache.dubbo.common.extension.inject;

import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionAccessor;
import org.apache.dubbo.common.extension.ExtensionInjector;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自适应扩展注入器
 * Adaptive extension injector
 */
@Adaptive
public class AdaptiveExtensionInjector implements ExtensionInjector, Lifecycle {

    /** 扩展注入器集合 */
    private Collection<ExtensionInjector> injectors = Collections.emptyList();
    /** 扩展访问器 */
    private ExtensionAccessor extensionAccessor;

    /**
     * 构造函数
     */
    public AdaptiveExtensionInjector() {}

    /**
     * 设置扩展访问器
     * 
     * @param extensionAccessor 扩展访问器
     */
    @Override
    public void setExtensionAccessor(final ExtensionAccessor extensionAccessor) {
        this.extensionAccessor = extensionAccessor;
    }

    /**
     * 初始化
     * 
     * @throws IllegalStateException 非法状态异常
     */
    @Override
    public void initialize() throws IllegalStateException {
        ExtensionLoader<ExtensionInjector> loader = extensionAccessor.getExtensionLoader(ExtensionInjector.class);
        injectors = loader.getSupportedExtensions().stream()
                .map(loader::getExtension)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * 获取实例
     * 
     * @param type 类型
     * @param name 名称
     * @param <T> 类型泛型
     * @return 实例
     */
    @Override
    public <T> T getInstance(final Class<T> type, final String name) {
        return injectors.stream()
                .map(injector -> injector.getInstance(type, name))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 启动
     * 
     * @throws IllegalStateException 非法状态异常
     */
    @Override
    public void start() throws IllegalStateException {}

    /**
     * 销毁
     * 
     * @throws IllegalStateException 非法状态异常
     */
    @Override
    public void destroy() throws IllegalStateException {}
}
