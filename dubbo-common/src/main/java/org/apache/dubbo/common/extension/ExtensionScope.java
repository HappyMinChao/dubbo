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

import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;

/**
 * 扩展SPI作用域
 * Extension SPI Scope
 * @see SPI
 * @see ExtensionDirector
 */
public enum ExtensionScope {

    /**
     * 框架作用域
     * 
     * 扩展实例在框架内使用，与所有应用程序和模块共享。
     *
     * <p>框架作用域SPI扩展只能获取{@link FrameworkModel}，
     * 无法获取{@link ApplicationModel}和{@link ModuleModel}。</p>
     *
     * <p></p>
     * 考虑因素：
     * <ol>
     * <li>某些SPI需要在框架内的应用程序之间共享数据</li>
     * <li>无状态SPI在框架内共享是安全的</li>
     * </ol>
     * 
     * The extension instance is used within framework, shared with all applications and modules.
     *
     * <p>Framework scope SPI extension can only obtain {@link FrameworkModel},
     * cannot get the {@link ApplicationModel} and {@link ModuleModel}.</p>
     *
     * <p></p>
     * Consideration:
     * <ol>
     * <li>Some SPI need share data between applications inside framework</li>
     * <li>Stateless SPI is safe shared inside framework</li>
     * </ol>
     */
    FRAMEWORK,

    /**
     * 应用程序作用域
     * 
     * 扩展实例在一个应用程序内使用，与该应用程序的所有模块共享，
     * 不同的应用程序创建不同的扩展实例。
     *
     * <p>应用程序作用域SPI扩展可以获取{@link FrameworkModel}和{@link ApplicationModel}，
     * 无法获取{@link ModuleModel}。</p>
     *
     * <p></p>
     * 考虑因素：
     * <ol>
     * <li>在框架内的不同应用程序之间隔离扩展数据</li>
     * <li>在应用程序内的所有模块之间共享扩展数据</li>
     * </ol>
     * 
     * The extension instance is used within one application, shared with all modules of the application,
     * and different applications create different extension instances.
     *
     * <p>Application scope SPI extension can obtain {@link FrameworkModel} and {@link ApplicationModel},
     * cannot get the {@link ModuleModel}.</p>
     *
     * <p></p>
     * Consideration:
     * <ol>
     * <li>Isolate extension data in different applications inside framework</li>
     * <li>Share extension data between all modules inside application</li>
     * </ol>
     */
    APPLICATION,

    /**
     * 模块作用域
     * 
     * 扩展实例在一个模块内使用，不同的模块创建不同的扩展实例。
     *
     * <p>模块作用域SPI扩展可以获取{@link FrameworkModel}、{@link ApplicationModel}和{@link ModuleModel}。</p>
     *
     * <p></p>
     * 考虑因素：
     * <ol>
     * <li>在应用程序内的不同模块之间隔离扩展数据</li>
     * </ol>
     * 
     * The extension instance is used within one module, and different modules create different extension instances.
     *
     * <p>Module scope SPI extension can obtain {@link FrameworkModel}, {@link ApplicationModel} and {@link ModuleModel}.</p>
     *
     * <p></p>
     * Consideration:
     * <ol>
     * <li>Isolate extension data in different modules inside application</li>
     * </ol>
     */
    MODULE,

    /**
     * 自给自足作用域
     * 
     * 为每个作用域创建一个实例，用于特殊的SPI扩展，如{@link ExtensionInjector}
     * 
     * self-sufficient, creates an instance for per scope, for special SPI extension, like {@link ExtensionInjector}
     */
    SELF
}