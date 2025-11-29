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

/**
 * 扩展初始化前后调用的后处理器。
 * A Post-processor called before or after extension initialization.
 */
public interface ExtensionPostProcessor {

    /**
     * 在扩展初始化之前进行后处理
     * 
     * @param instance 扩展实例
     * @param name 扩展名称
     * @return 处理后的实例
     * @throws Exception 处理异常
     * Post-process before extension initialization
     * 
     * @param instance extension instance
     * @param name extension name
     * @return processed instance
     * @throws Exception processing exception
     */
    default Object postProcessBeforeInitialization(Object instance, String name) throws Exception {
        return instance;
    }

    /**
     * 在扩展初始化之后进行后处理
     * 
     * @param instance 扩展实例
     * @param name 扩展名称
     * @return 处理后的实例
     * @throws Exception 处理异常
     * Post-process after extension initialization
     * 
     * @param instance extension instance
     * @param name extension name
     * @return processed instance
     * @throws Exception processing exception
     */
    default Object postProcessAfterInitialization(Object instance, String name) throws Exception {
        return instance;
    }
}
