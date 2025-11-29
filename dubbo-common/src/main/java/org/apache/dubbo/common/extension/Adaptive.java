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

import org.apache.dubbo.common.URL;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为{@link ExtensionLoader}提供有用信息以注入依赖扩展实例。
 *
 * @see ExtensionLoader
 * @see URL
 * Provide helpful information for {@link ExtensionLoader} to inject dependency extension instance.
 *
 * @see ExtensionLoader
 * @see URL
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Adaptive {
    /**
     * 决定要注入哪个目标扩展。目标扩展的名称由URL中传递的参数决定，参数名称由此方法给出。
     * <p>
     * 如果在{@link URL}中找不到指定的参数，则将使用默认扩展进行依赖注入（在其接口的{@link SPI}中指定）。
     * <p>
     * 例如，给定<code>String[] {"key1", "key2"}</code>：
     * <ol>
     * <li>在URL中查找参数'key1'，使用其值作为扩展的名称</li>
     * <li>如果在URL中找不到'key1'（或其值为空），则尝试'key2'作为扩展名称</li>
     * <li>如果'key2'也不存在，则使用默认扩展</li>
     * <li>否则，抛出{@link IllegalStateException}</li>
     * </ol>
     * 如果参数名称为空，则从接口的类名生成默认参数名称，规则是：将类名从大写字母分割成几个部分，
     * 并用点'.'分隔这些部分，例如，对于{@code org.apache.dubbo.xxx.YyyInvokerWrapper}，生成的名称是
     * <code>String[] {"yyy.invoker.wrapper"}</code>。
     *
     * @return URL中的参数名称
     * Decide which target extension to be injected. The name of the target extension is decided by the parameter passed
     * in the URL, and the parameter names are given by this method.
     * <p>
     * If the specified parameters are not found from {@link URL}, then the default extension will be used for
     * dependency injection (specified in its interface's {@link SPI}).
     * <p>
     * For example, given <code>String[] {"key1", "key2"}</code>:
     * <ol>
     * <li>find parameter 'key1' in URL, use its value as the extension's name</li>
     * <li>try 'key2' for extension's name if 'key1' is not found (or its value is empty) in URL</li>
     * <li>use default extension if 'key2' doesn't exist either</li>
     * <li>otherwise, throw {@link IllegalStateException}</li>
     * </ol>
     * If the parameter names are empty, then a default parameter name is generated from interface's
     * class name with the rule: divide classname from capital char into several parts, and separate the parts with
     * dot '.', for example, for {@code org.apache.dubbo.xxx.YyyInvokerWrapper}, the generated name is
     * <code>String[] {"yyy.invoker.wrapper"}</code>.
     *
     * @return parameter names in URL
     */
    String[] value() default {};
}
