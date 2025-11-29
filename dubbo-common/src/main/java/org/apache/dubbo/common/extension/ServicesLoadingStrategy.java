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
 * Services {@link LoadingStrategy}
 * Services加载策略
 *
 * @since 2.7.7
 */
public class ServicesLoadingStrategy implements LoadingStrategy {

    /**
     * 获取目录路径
     * 
     * @return 目录路径 "META-INF/services/"
     * Get directory path
     * 
     * @return directory path "META-INF/services/"
     */
    @Override
    public String directory() {
        return "META-INF/services/";
    }

    /**
     * 是否支持覆盖
     * 
     * @return 返回true，支持覆盖
     * Whether supports override
     * 
     * @return return true, supports override
     */
    @Override
    public boolean overridden() {
        return true;
    }

    /**
     * 获取优先级
     * 
     * @return 最小优先级
     * Get priority
     * 
     * @return minimum priority
     */
    @Override
    public int getPriority() {
        return MIN_PRIORITY;
    }

    /**
     * 获取名称
     * 
     * @return 名称 "SERVICES"
     * Get name
     * 
     * @return name "SERVICES"
     */
    @Override
    public String getName() {
        return "SERVICES";
    }
}
