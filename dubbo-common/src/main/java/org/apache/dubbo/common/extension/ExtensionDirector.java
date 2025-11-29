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

import org.apache.dubbo.rpc.model.ScopeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ExtensionDirector是一个作用域扩展加载器管理器。
 *
 * <p></p>
 * <p>ExtensionDirector支持多层级，子级可以继承父级的扩展实例。</p>
 * <p>查找和创建扩展实例的方式类似于Java类加载器。</p>
 *
 * ExtensionDirector is a scoped extension loader manager.
 *
 * <p></p>
 * <p>ExtensionDirector supports multiple levels, and the child can inherit the parent's extension instances. </p>
 * <p>The way to find and create an extension instance is similar to Java classloader.</p>
 */
public class ExtensionDirector implements ExtensionAccessor {

    /** 扩展加载器映射表 */
    private final ConcurrentMap<Class<?>, ExtensionLoader<?>> extensionLoadersMap = new ConcurrentHashMap<>(64);
    /** 扩展作用域映射表 */
    private final ConcurrentMap<Class<?>, ExtensionScope> extensionScopeMap = new ConcurrentHashMap<>(64);
    /** 父级扩展导演 */
    private final ExtensionDirector parent;
    /** 扩展作用域 */
    private final ExtensionScope scope;
    /** 扩展后处理器列表 */
    private final List<ExtensionPostProcessor> extensionPostProcessors = new ArrayList<>();
    /** 作用域模型 */
    private final ScopeModel scopeModel;
    /** 销毁标志 */
    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * 构造函数
     * 
     * @param parent 父级扩展导演
     * @param scope 扩展作用域
     * @param scopeModel 作用域模型
     */
    public ExtensionDirector(ExtensionDirector parent, ExtensionScope scope, ScopeModel scopeModel) {
        this.parent = parent;
        this.scope = scope;
        this.scopeModel = scopeModel;
    }

    /**
     * 添加扩展后处理器
     * 
     * @param processor 扩展后处理器
     */
    public void addExtensionPostProcessor(ExtensionPostProcessor processor) {
        if (!this.extensionPostProcessors.contains(processor)) {
            this.extensionPostProcessors.add(processor);
        }
    }

    /**
     * 获取扩展后处理器列表
     * 
     * @return 扩展后处理器列表
     */
    public List<ExtensionPostProcessor> getExtensionPostProcessors() {
        return extensionPostProcessors;
    }

    /**
     * 获取扩展导演
     * 
     * @return 扩展导演
     */
    @Override
    public ExtensionDirector getExtensionDirector() {
        return this;
    }

    /**
     * 获取扩展加载器
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 扩展加载器
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        checkDestroyed();
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type
                    + ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        // 1. 在本地缓存中查找
        ExtensionLoader<T> loader = (ExtensionLoader<T>) extensionLoadersMap.get(type);

        ExtensionScope scope = extensionScopeMap.get(type);
        if (scope == null) {
            SPI annotation = type.getAnnotation(SPI.class);
            scope = annotation.scope();
            extensionScopeMap.put(type, scope);
        }

        if (loader == null && scope == ExtensionScope.SELF) {
            // 在自身作用域中创建实例
            loader = createExtensionLoader0(type);
        }

        // 2. 在父级中查找
        if (loader == null) {
            if (this.parent != null) {
                loader = this.parent.getExtensionLoader(type);
            }
        }

        // 3. 创建实例
        if (loader == null) {
            loader = createExtensionLoader(type);
        }

        return loader;
    }

    /**
     * 创建扩展加载器
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 扩展加载器
     */
    private <T> ExtensionLoader<T> createExtensionLoader(Class<T> type) {
        ExtensionLoader<T> loader = null;
        if (isScopeMatched(type)) {
            // 如果作用域匹配，则创建实例
            loader = createExtensionLoader0(type);
        }
        return loader;
    }

    /**
     * 创建扩展加载器0
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 扩展加载器
     */
    @SuppressWarnings("unchecked")
    private <T> ExtensionLoader<T> createExtensionLoader0(Class<T> type) {
        checkDestroyed();
        ExtensionLoader<T> loader;
        extensionLoadersMap.putIfAbsent(type, new ExtensionLoader<T>(type, this, scopeModel));
        loader = (ExtensionLoader<T>) extensionLoadersMap.get(type);
        return loader;
    }

    /**
     * 判断作用域是否匹配
     * 
     * @param type 扩展类型
     * @return 如果作用域匹配返回true，否则返回false
     */
    private boolean isScopeMatched(Class<?> type) {
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        return defaultAnnotation.scope().equals(scope);
    }

    /**
     * 判断是否有扩展注解
     * 
     * @param type 扩展类型
     * @return 如果有扩展注解返回true，否则返回false
     */
    private static boolean withExtensionAnnotation(Class<?> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    /**
     * 获取父级扩展导演
     * 
     * @return 父级扩展导演
     */
    public ExtensionDirector getParent() {
        return parent;
    }

    /**
     * 移除所有缓存的加载器
     */
    public void removeAllCachedLoader() {}

    /**
     * 销毁扩展导演
     */
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            for (ExtensionLoader<?> extensionLoader : extensionLoadersMap.values()) {
                extensionLoader.destroy();
            }
            extensionLoadersMap.clear();
            extensionScopeMap.clear();
            extensionPostProcessors.clear();
        }
    }

    /**
     * 检查是否已销毁
     */
    private void checkDestroyed() {
        if (destroyed.get()) {
            throw new IllegalStateException("ExtensionDirector is destroyed");
        }
    }
}
