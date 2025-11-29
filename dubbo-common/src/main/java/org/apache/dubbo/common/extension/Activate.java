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
 * 激活注解。该注解用于在给定条件下自动激活某些扩展，
 * 例如：<code>@Activate</code>可以用于在有多个实现时加载某些<code>Filter</code>扩展。
 * <ol>
 * <li>{@link Activate#group()}指定组条件。框架SPI定义了有效的组值。
 * <li>{@link Activate#value()}指定{@link URL}条件中的参数键。
 * </ol>
 * SPI提供者可以调用{@link ExtensionLoader#getActivateExtension(URL, String, String)}来查找所有符合给定条件的已激活扩展。
 *
 * @see SPI
 * @see URL
 * @see ExtensionLoader
 * Activate. This annotation is useful for automatically activate certain extensions with the given criteria,
 * for examples: <code>@Activate</code> can be used to load certain <code>Filter</code> extension when there are
 * multiple implementations.
 * <ol>
 * <li>{@link Activate#group()} specifies group criteria. Framework SPI defines the valid group values.
 * <li>{@link Activate#value()} specifies parameter key in {@link URL} criteria.
 * </ol>
 * SPI provider can call {@link ExtensionLoader#getActivateExtension(URL, String, String)} to find out all activated
 * extensions with the given criteria.
 *
 * @see SPI
 * @see URL
 * @see ExtensionLoader
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Activate {
    /**
     * 当组匹配时激活当前扩展。传入{@link ExtensionLoader#getActivateExtension(URL, String, String)}的组将用于匹配。
     *
     * @return 要匹配的组名
     * @see ExtensionLoader#getActivateExtension(URL, String, String)
     * Activate the current extension when one of the groups matches. The group passed into
     * {@link ExtensionLoader#getActivateExtension(URL, String, String)} will be used for matching.
     *
     * @return group names to match
     * @see ExtensionLoader#getActivateExtension(URL, String, String)
     */
    String[] group() default {};

    /**
     * 当指定的键出现在URL参数中时激活当前扩展。
     * <p>
     * 例如，给定<code>@Activate("cache, validation")</code>，只有当URL参数中出现<code>cache</code>或<code>validation</code>键时，
     * 当前扩展才会被返回。
     * </p>
     *
     * @return URL参数键
     * @see ExtensionLoader#getActivateExtension(URL, String)
     * @see ExtensionLoader#getActivateExtension(URL, String, String)
     * Activate the current extension when the specified keys appear in the URL's parameters.
     * <p>
     * For example, given <code>@Activate("cache, validation")</code>, the current extension will be return only when
     * there's either <code>cache</code> or <code>validation</code> key appeared in the URL's parameters.
     * </p>
     *
     * @return URL parameter keys
     * @see ExtensionLoader#getActivateExtension(URL, String)
     * @see ExtensionLoader#getActivateExtension(URL, String, String)
     */
    String[] value() default {};

    /**
     * 相对排序信息，可选
     * 自2.7.0版本起已废弃
     *
     * @return 应该放在当前扩展之前的扩展列表
     * Relative ordering info, optional
     * Deprecated since 2.7.0
     *
     * @return extension list which should be put before the current one
     */
    @Deprecated
    String[] before() default {};

    /**
     * 相对排序信息，可选
     * 自2.7.0版本起已废弃
     *
     * @return 应该放在当前扩展之后的扩展列表
     * Relative ordering info, optional
     * Deprecated since 2.7.0
     *
     * @return extension list which should be put after the current one
     */
    @Deprecated
    String[] after() default {};

    /**
     * 绝对排序信息，可选
     *
     * 升序排列，较小的值将排在列表前面。
     *
     * @return 绝对排序信息
     * Absolute ordering info, optional
     *
     * Ascending order, smaller values will be in the front of the list.
     *
     * @return absolute ordering info
     */
    int order() default 0;

    /**
     * 当指定的类名全部匹配时激活当前扩展的加载类
     * @return 要全部匹配的类名
     * Activate loadClass when the current extension when the specified className all match
     * @return className names to all match
     */
    String[] onClass() default {};
}
