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

/**
 * 配置处理模式枚举
 * 
 * 定义了对于唯一配置类型的处理模式，例如ApplicationConfig、ModuleConfig、MonitorConfig、SslConfig、MetricsConfig等。
 * 该枚举类型用于控制当多个同类型配置同时存在时的处理策略。
 * 
 * <p>
 * <b>作用：</b>
 * <ul>
 * <li>提供多种配置处理策略，灵活处理配置冲突</li>
 * <li>支持严格模式、覆盖模式和忽略模式</li>
 * <li>用于配置管理器中的配置合并逻辑</li>
 * <li>通过dubbo.config.mode属性进行配置</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>当应用中存在多个ApplicationConfig配置时，决定如何处理</li>
 * <li>配置中心和本地配置冲突时的合并策略</li>
 * <li>多模块应用中配置的统一管理</li>
 * </ul>
 * 
 * Config processing mode for unique config type, e.g. ApplicationConfig, ModuleConfig, MonitorConfig, SslConfig, MetricsConfig
 * @see ConfigManager#uniqueConfigTypes
 */
public enum ConfigMode {
    /**
     * 严格模式：只接受一个唯一配置类型的配置，如果发现多个配置则抛出异常
     * Strict mode: accept only one config for unique config type, throw exceptions if found more than one config for a unique config type.
     */
    STRICT,

    /**
     * 覆盖模式：接受最后一个配置，覆盖之前的配置
     * Override mode: accept last config, override previous config
     */
    OVERRIDE,

    /**
     * 全量覆盖模式：接受最后一个配置，覆盖之前配置的所有属性，无论之前的属性是否为空
     * Override mode: accept last config, override previous config regardless of whether the attribute of previous config is absent or not
     */
    OVERRIDE_ALL,

    /**
     * 条件覆盖模式：接受最后一个配置，仅当之前配置的属性为空时才覆盖
     * Override mode: accept last config, override previous config only when the attribute of previous config is absent
     */
    OVERRIDE_IF_ABSENT,

    /**
     * 忽略模式：接受第一个配置，忽略后续的配置
     * Ignore mode: accept first config, ignore later configs
     */
    IGNORE
}
