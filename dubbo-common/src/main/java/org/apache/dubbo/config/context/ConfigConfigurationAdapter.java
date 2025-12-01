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
package org.apache.dubbo.config.context;

import org.apache.dubbo.common.config.Configuration;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.AbstractConfig;

import java.util.Map;

/**
 * 配置对象到Configuration的适配器
 * 
 * 将Dubbo的{@link AbstractConfig}配置对象转换为{@link Configuration}接口，使其属性可以通过Configuration的方式访问。
 * 该类作为适配器，将配置对象的元数据暴露为key-value形式的属性。
 * 
 * <p>
 * <b>作用：</b>
 * <ul>
 * <li>将配置对象转换为Configuration接口</li>
 * <li>提供统一的属性访问方式</li>
 * <li>支持前缀过滤，获取特定命名空间的属性</li>
 * <li>将对象属性平面化为Map结构</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>将ApplicationConfig转换为可查询的Configuration</li>
 * <li>在配置层次结构中统一配置源</li>
 * <li>支持配置的动态查询和访问</li>
 * <li>将配置对象集成到CompositeConfiguration中</li>
 * </ul>
 * 
 * <p>
 * <b>示例：</b>
 * <pre>
 * ApplicationConfig appConfig = new ApplicationConfig();
 * appConfig.setName("demo-app");
 * 
 * // 创建适配器
 * ConfigConfigurationAdapter adapter = new ConfigConfigurationAdapter(appConfig, "dubbo.application");
 * 
 * // 通过Configuration接口访问属性
 * String name = (String) adapter.getInternalProperty("name");
 * </pre>
 * 
 * This class receives an {@link AbstractConfig} and exposes its attributes through {@link Configuration}
 * 
 * @see Configuration
 * @see AbstractConfig
 */
public class ConfigConfigurationAdapter implements Configuration {

    /** 配置元数据，存储配置对象的所有属性 */
    private final Map<String, String> metaData;

    /**
     * 构造函数
     * 创建配置适配器，将配置对象转换为Configuration
     * 
     * @param config 配置对象
     * @param prefix 属性前缀，用于过滤和命名空间隔离，可以为null或空字符串
     */
    public ConfigConfigurationAdapter(AbstractConfig config, String prefix) {
        if (StringUtils.hasText(prefix)) {
            metaData = config.getMetaData(prefix);
        } else {
            metaData = config.getMetaData();
        }
    }

    /**
     * 获取属性值
     * 
     * @param key 属性键
     * @return 属性值，如果不存在返回null
     */
    @Override
    public Object getInternalProperty(String key) {
        return metaData.get(key);
    }

    /**
     * 获取所有属性
     * 
     * @return 包含所有配置属性的Map
     */
    public Map<String, String> getProperties() {
        return metaData;
    }
}
