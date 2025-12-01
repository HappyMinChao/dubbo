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

import org.apache.dubbo.config.AbstractConfig;

/**
 * 配置验证器接口
 * 
 * 用于验证Dubbo配置对象的合法性和正确性。
 * 该接口定义了配置验证的基本规范，具体的验证逻辑由实现类提供。
 * 
 * <p>
 * <b>作用：</b>
 * <ul>
 * <li>定义配置验证的统一接口</li>
 * <li>支持不同类型配置的专门验证逻辑</li>
 * <li>在配置加载和使用前进行校验</li>
 * <li>确保配置的完整性和一致性</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>验证ApplicationConfig中的应用名称是否合法</li>
 * <li>检查RegistryConfig中的注册中心地址是否正确</li>
 * <li>验证ProtocolConfig中的端口范围是否有效</li>
 * <li>自定义配置验证规则的扩展点</li>
 * </ul>
 * 
 * <p>
 * <b>示例：</b>
 * <pre>
 * public class MyConfigValidator implements ConfigValidator {
 *     public void validate(AbstractConfig config) {
 *         if (config instanceof ApplicationConfig) {
 *             ApplicationConfig appConfig = (ApplicationConfig) config;
 *             if (StringUtils.isEmpty(appConfig.getName())) {
 *                 throw new IllegalStateException("Application name is required");
 *             }
 *         }
 *     }
 * }
 * </pre>
 * 
 * @see AbstractConfig
 * @see ConfigManager
 */
public interface ConfigValidator {

    /**
     * 验证配置对象
     * 检查配置对象的属性是否符合要求，如果验证失败应抛出异常
     * 
     * @param config 要验证的配置对象
     * @throws IllegalStateException 当配置不合法时抛出
     * @throws IllegalArgumentException 当配置参数无效时抛出
     */
    void validate(AbstractConfig config);
}
