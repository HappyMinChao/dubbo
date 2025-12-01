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
 * 扩展导演（扩展加载器管理器）
 * 
 * ExtensionDirector是一个作用域扩展加载器管理器，负责管理和协调多个扩展加载器的创建、查找和生命周期。
 * 该类是Dubbo SPI机制的核心组件，提供了扩展加载器的统一管理和层次化的扩展实例继承机制。
 * 
 * <p>
 * <b>作用：</b>
 * <ul>
 * <li>管理和缓存所有扩展加载器实例</li>
 * <li>支持多层级的扩展导演结构，子级可以继承父级的扩展实例</li>
 * <li>根据扩展作用域（FRAMEWORK、APPLICATION、MODULE、SELF）管理扩展的可见性</li>
 * <li>提供扩展后处理器机制，支持扩展实例的增强和定制</li>
 * <li>负责扩展加载器的创建、查找和销毁</li>
 * </ul>
 * 
 * <p>
 * <b>层次结构：</b>
 * <pre>
 * FrameworkModel
 *   └── ExtensionDirector (FRAMEWORK scope)
 *       └── ApplicationModel
 *           └── ExtensionDirector (APPLICATION scope)
 *               └── ModuleModel
 *                   └── ExtensionDirector (MODULE scope)
 * </pre>
 * 
 * <p>
 * <b>扩展查找策略：</b>
 * 类似于Java类加载器的双亲委派机制，查找扩展加载器时遵循以下顺序：
 * <ol>
 * <li>在本地缓存中查找</li>
 * <li>如果是SELF作用域，在当前ExtensionDirector中创建</li>
 * <li>在父级ExtensionDirector中查找</li>
 * <li>如果作用域匹配，在当前ExtensionDirector中创建</li>
 * </ol>
 * 
 * <p>
 * <b>扩展作用域：</b>
 * <ul>
 * <li>FRAMEWORK - 框架级扩展，所有应用和模块共享</li>
 * <li>APPLICATION - 应用级扩展，同一应用的所有模块共享</li>
 * <li>MODULE - 模块级扩展，仅在当前模块可见</li>
 * <li>SELF - 自身作用域扩展，不会从父级继承</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>在ScopeModel中创建和管理扩展加载器</li>
 * <li>支持多租户和多模块应用的扩展隔离</li>
 * <li>提供扩展的层次化管理和继承</li>
 * <li>管理扩展的生命周期</li>
 * </ul>
 * 
 * <p>
 * <b>扩展后处理器：</b>
 * ExtensionPostProcessor可以在扩展实例创建后进行拦截和增强，例如：
 * <ul>
 * <li>依赖注入</li>
 * <li>AOP代理</li>
 * <li>属性配置</li>
 * <li>生命周期回调</li>
 * </ul>
 * 
 * <p>
 * <b>线程安全：</b>
 * <ul>
 * <li>使用ConcurrentHashMap保证并发访问安全</li>
 * <li>使用AtomicBoolean控制销毁状态</li>
 * <li>扩展加载器的创建使用putIfAbsent保证单例</li>
 * </ul>
 * 
 * <p>
 * <b>示例：</b>
 * <pre>
 * // 获取扩展导演
 * ExtensionDirector director = scopeModel.getExtensionDirector();
 * 
 * // 获取扩展加载器
 * ExtensionLoader&lt;Protocol&gt; loader = director.getExtensionLoader(Protocol.class);
 * 
 * // 获取扩展实例
 * Protocol protocol = loader.getExtension("dubbo");
 * </pre>
 * 
 * <p>ExtensionDirector支持多层级，子级可以继承父级的扩展实例。</p>
 * <p>查找和创建扩展实例的方式类似于Java类加载器。</p>
 *
 * ExtensionDirector is a scoped extension loader manager.
 *
 * <p></p>
 * <p>ExtensionDirector supports multiple levels, and the child can inherit the parent's extension instances. </p>
 * <p>The way to find and create an extension instance is similar to Java classloader.</p>
 * 
 * @see ExtensionLoader
 * @see ExtensionAccessor
 * @see ExtensionScope
 * @see ScopeModel
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
     * 创建扩展导演实例
     * 
     * @param parent 父级扩展导演，可以为null表示顶层扩展导演
     * @param scope 扩展作用域，定义该导演管理的扩展范围
     * @param scopeModel 作用域模型，关联的模型实例
     */
    public ExtensionDirector(ExtensionDirector parent, ExtensionScope scope, ScopeModel scopeModel) {
        this.parent = parent;
        this.scope = scope;
        this.scopeModel = scopeModel;
    }

    /**
     * 添加扩展后处理器
     * 扩展后处理器用于在扩展实例创建后进行拦截和增强
     * 
     * @param processor 扩展后处理器实例
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
     * 根据扩展类型获取对应的扩展加载器，如果不存在则创建新的加载器
     * 
     * <p>查找顺序：
     * <ol>
     * <li>在本地缓存中查找</li>
     * <li>如果是SELF作用域，直接创建</li>
     * <li>在父级扩展导演中查找</li>
     * <li>如果作用域匹配，创建新的加载器</li>
     * </ol>
     * 
     * @param type 扩展接口类型，必须是接口且标注了@SPI注解
     * @param <T> 扩展类型泛型
     * @return 扩展加载器实例
     * @throws IllegalArgumentException 如果type为null、不是接口或未标注@SPI注解
     * @throws IllegalStateException 如果ExtensionDirector已销毁
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
     * 根据作用域匹配情况决定是否创建扩展加载器
     * 
     * @param type 扩展接口类型
     * @param <T> 扩展类型泛型
     * @return 扩展加载器实例，如果作用域不匹配返回null
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
     * 创建扩展加载器（内部方法）
     * 实际创建扩展加载器实例并缓存
     * 
     * @param type 扩展接口类型
     * @param <T> 扩展类型泛型
     * @return 新创建或已缓存的扩展加载器实例
     * @throws IllegalStateException 如果ExtensionDirector已销毁
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
     * 检查扩展接口声明的作用域是否与当前ExtensionDirector的作用域一致
     * 
     * @param type 扩展接口类型
     * @return 如果作用域匹配返回true，否则返回false
     */
    private boolean isScopeMatched(Class<?> type) {
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        return defaultAnnotation.scope().equals(scope);
    }

    /**
     * 判断是否有扩展注解
     * 检查类型是否标注了@SPI注解
     * 
     * @param type 要检查的类型
     * @return 如果标注了@SPI注解返回true，否则返回false
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
     * 当前为空实现，预留方法
     */
    public void removeAllCachedLoader() {}

    /**
     * 销毁扩展导演
     * 销毁所有管理的扩展加载器，清理缓存和资源
     * 该方法是幂等的，多次调用只会执行一次
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
     * 在执行操作前检查ExtensionDirector是否已被销毁
     * 
     * @throws IllegalStateException 如果ExtensionDirector已被销毁
     */
    private void checkDestroyed() {
        if (destroyed.get()) {
            throw new IllegalStateException("ExtensionDirector is destroyed");
        }
    }
}
