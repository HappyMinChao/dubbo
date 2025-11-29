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

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 扩展的统一访问器
 * Uniform accessor for extension
 */
public interface ExtensionAccessor {

    /**
     * 获取扩展导演
     * 
     * @return 扩展导演
     * Get extension director
     * 
     * @return extension director
     */
    ExtensionDirector getExtensionDirector();

    /**
     * 获取扩展加载器
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 扩展加载器
     * Get extension loader
     * 
     * @param type extension type
     * @param <T> extension type generic
     * @return extension loader
     */
    default <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        return getExtensionDirector().getExtensionLoader(type);
    }

    /**
     * 根据类型和名称获取扩展
     * 
     * @param type 扩展类型
     * @param name 扩展名称
     * @param <T> 扩展类型泛型
     * @return 扩展实例
     * Get extension by type and name
     * 
     * @param type extension type
     * @param name extension name
     * @param <T> extension type generic
     * @return extension instance
     */
    default <T> T getExtension(Class<T> type, String name) {
        ExtensionLoader<T> extensionLoader = getExtensionLoader(type);
        return extensionLoader != null ? extensionLoader.getExtension(name) : null;
    }

    /**
     * 获取自适应扩展
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 自适应扩展实例
     * Get adaptive extension
     * 
     * @param type extension type
     * @param <T> extension type generic
     * @return adaptive extension instance
     */
    default <T> T getAdaptiveExtension(Class<T> type) {
        ExtensionLoader<T> extensionLoader = getExtensionLoader(type);
        return extensionLoader != null ? extensionLoader.getAdaptiveExtension() : null;
    }

    /**
     * 获取默认扩展
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 默认扩展实例
     * Get default extension
     * 
     * @param type extension type
     * @param <T> extension type generic
     * @return default extension instance
     */
    default <T> T getDefaultExtension(Class<T> type) {
        ExtensionLoader<T> extensionLoader = getExtensionLoader(type);
        return extensionLoader != null ? extensionLoader.getDefaultExtension() : null;
    }

    /**
     * 获取激活的扩展列表
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 激活的扩展列表
     * Get activate extensions list
     * 
     * @param type extension type
     * @param <T> extension type generic
     * @return activate extensions list
     */
    default <T> List<T> getActivateExtensions(Class<T> type) {
        ExtensionLoader<T> extensionLoader = getExtensionLoader(type);
        return extensionLoader != null ? extensionLoader.getActivateExtensions() : Collections.emptyList();
    }

    /**
     * 获取第一个激活的扩展
     * 
     * @param type 扩展类型
     * @param <T> 扩展类型泛型
     * @return 第一个激活的扩展实例
     * Get first activate extension
     * 
     * @param type extension type
     * @param <T> extension type generic
     * @return first activate extension instance
     */
    default <T> T getFirstActivateExtension(Class<T> type) {
        ExtensionLoader<T> extensionLoader = getExtensionLoader(type);
        if (extensionLoader == null) {
            throw new IllegalArgumentException("ExtensionLoader for [" + type + "] is not found");
        }
        List<T> extensions = extensionLoader.getActivateExtensions();
        if (extensions.isEmpty()) {
            throw new IllegalArgumentException("No activate extensions for [" + type + "] found");
        }
        return extensions.get(0);
    }

    /**
     * 获取支持的扩展名称集合
     * 
     * @param type 扩展类型
     * @return 支持的扩展名称集合
     * Get supported extensions name set
     * 
     * @param type extension type
     * @return supported extensions name set
     */
    default Set<String> getSupportedExtensions(Class<?> type) {
        ExtensionLoader<?> extensionLoader = getExtensionLoader(type);
        return extensionLoader != null ? extensionLoader.getSupportedExtensions() : Collections.emptySet();
    }
}
