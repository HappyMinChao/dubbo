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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展接口标记注解
 * 
 * 用于标记一个接口为Dubbo的SPI扩展点。被标记的接口可以通过ExtensionLoader加载其实现类。
 * 该注解是Dubbo SPI机制的基础，所有的扩展接口必须使用此注解标记。
 * 
 * <p>
 * <b>作用：</b>
 * <ul>
 * <li>标识一个接口为扩展点，使其可被ExtensionLoader识别</li>
 * <li>指定默认的扩展实现名称</li>
 * <li>定义扩展的作用域（FRAMEWORK、APPLICATION、MODULE、SELF）</li>
 * <li>支持扩展的动态加载和替换</li>
 * </ul>
 * 
 * <p>
 * <b>扩展配置文件格式：</b>
 * 以{@code Protocol}为例，其配置文件位于'META-INF/dubbo/com.xxx.Protocol'，采用键值对格式：
 * <pre>
 *     xxx=com.foo.XxxProtocol
 *     yyy=com.foo.YyyProtocol
 * </pre>
 * 
 * <p>
 * <b>为什么使用键值对格式：</b>
 * <ul>
 * <li>当扩展实现引用了不存在的第三方库时，类将无法初始化</li>
 * <li>使用键值对格式可以将异常信息与扩展ID映射，便于问题定位</li>
 * <li>避免报告“扩展无法加载”而不知道具体是哪个实现失败</li>
 * </ul>
 * 
 * <p>
 * <b>扩展作用域：</b>
 * <ul>
 * <li>FRAMEWORK - 框架级扩展，全局共享</li>
 * <li>APPLICATION - 应用级扩展，同一应用共享（默认）</li>
 * <li>MODULE - 模块级扩展，同一模块共享</li>
 * <li>SELF - 自身作用域，不共享</li>
 * </ul>
 * 
 * <p>
 * <b>示例：</b>
 * <pre>
 * &#64;SPI("dubbo")
 * public interface Protocol {
 *     // ...
 * }
 * </pre>
 * 
 * <p>
 * <b>扩展接口标记</b>
 * <p/>
 * 扩展配置文件的变更<br/>
 * 以<code>Protocol</code>为例，其配置文件'META-INF/dubbo/com.xxx.Protocol'从以下格式：<br/>
 * <pre>
 *     com.foo.XxxProtocol
 *     com.foo.YyyProtocol
 * </pre>
 * <p>
 * 变更为键值对形式：<br/>
 * <pre>
 *     xxx=com.foo.XxxProtocol
 *     yyy=com.foo.YyyProtocol
 * </pre>
 * <br/>
 * 这样变更的原因是：
 * <p>
 * 如果扩展实现中通过静态字段或方法引用了第三方库，而该第三方库不存在时，其类将无法初始化。
 * 在这种情况下，如果使用之前的格式，dubbo无法确定扩展的ID，因此无法将异常信息与扩展映射起来。
 * <p/>
 * 例如：
 * <p>
 * 加载Extension("mina")失败。当用户配置使用mina时，dubbo会报告扩展无法加载，
 * 而不是报告具体哪个扩展实现失败以及确切的原因。
 * </p>
 *
 * Marker for extension interface
 * <p/>
 * Changes on extension configuration file <br/>
 * Use <code>Protocol</code> as an example, its configuration file 'META-INF/dubbo/com.xxx.Protocol' is changed from: <br/>
 * <pre>
 *     com.foo.XxxProtocol
 *     com.foo.YyyProtocol
 * </pre>
 * <p>
 * to key-value pair <br/>
 * <pre>
 *     xxx=com.foo.XxxProtocol
 *     yyy=com.foo.YyyProtocol
 * </pre>
 * <br/>
 * The reason for this change is:
 * <p>
 * If there's third party library referenced by static field or by method in extension implementation, its class will
 * fail to initialize if the third party library doesn't exist. In this case, dubbo cannot figure out extension's id
 * therefore cannot be able to map the exception information with the extension, if the previous format is used.
 * <p/>
 * For example:
 * <p>
 * Fails to load Extension("mina"). When user configure to use mina, dubbo will complain the extension cannot be loaded,
 * instead of reporting which extract extension implementation fails and the extract reason.
 * </p>
 * 
 * @see ExtensionLoader
 * @see ExtensionScope
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SPI {

    /**
     * 默认扩展名称
     * 指定该扩展接口的默认实现，当未指定具体扩展时使用
     * 
     * @return 默认扩展名称，默认为空字符串
     * default extension name
     */
    String value() default "";

    /**
     * SPI的作用域
     * 定义扩展实例的生命周期和可见性范围
     * 
     * @return 扩展作用域，默认为应用级作用域
     * scope of SPI, default value is application scope.
     */
    ExtensionScope scope() default ExtensionScope.APPLICATION;
}
