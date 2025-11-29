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
 * Dubbo内部{@link LoadingStrategy}
 * Dubbo internal {@link LoadingStrategy}
 *
 * @since 2.7.7
 */
public class DubboInternalLoadingStrategy implements LoadingStrategy {

    /**
     * 获取目录路径
     * 
     * @return 目录路径 "META-INF/dubbo/internal/"
     * Get directory path
     * 
     * @return directory path "META-INF/dubbo/internal/"
     */
    @Override
    public String directory() {
        return "META-INF/dubbo/internal/";
    }

    /**
     * 获取优先级
     * 
     * @return 最大优先级
     * Get priority
     * 
     * @return maximum priority
     */
    @Override
    public int getPriority() {
        return MAX_PRIORITY;
    }

    /**
     * 获取名称
     * 
     * @return 名称 "DUBBO_INTERNAL"
     * Get name
     * 
     * @return name "DUBBO_INTERNAL"
     */
    @Override
    public String getName() {
        return "DUBBO_INTERNAL";
    }
}
