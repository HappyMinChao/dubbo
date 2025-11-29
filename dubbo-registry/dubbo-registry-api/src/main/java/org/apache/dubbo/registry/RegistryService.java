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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;

import java.util.List;

/**
 * 注册中心服务接口 (SPI, Prototype, ThreadSafe)
 * 提供服务注册与发现的核心接口
 *
 * @see org.apache.dubbo.registry.Registry
 * @see org.apache.dubbo.registry.RegistryFactory#getRegistry(URL)
 */
public interface RegistryService {

    /**
     * 注册数据，例如：提供者服务、消费者地址、路由规则、覆盖规则等数据。
     * <p>
     * 注册需要支持以下约定：<br>
     * 1. 当URL设置了check=false参数时，注册失败不抛出异常并在后台重试。否则抛出异常。<br>
     * 2. 当URL设置了dynamic=false参数时，需要持久化存储，否则在注册方异常退出时应自动删除。<br>
     * 3. 当URL设置了category=routers时，表示分类存储，默认分类是providers，可以通过分类部分通知数据。<br>
     * 4. 当注册中心重启、网络抖动时，数据不能丢失，包括自动删除断线的数据。<br>
     * 5. 允许具有相同URL但参数不同的URL共存，它们不能相互覆盖。<br>
     *
     * @param url  注册信息，不允许为空，例如: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void register(URL url);

    /**
     * 取消注册
     * <p>
     * 取消注册需要支持以下约定：<br>
     * 1. 如果是dynamic=false的持久化存储数据，找不到注册数据则抛出IllegalStateException，否则忽略。<br>
     * 2. 根据完整URL匹配进行取消注册。<br>
     *
     * @param url 注册信息，不允许为空，例如: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void unregister(URL url);

    /**
     * 订阅符合条件的注册数据，并在注册数据变更时自动推送。
     * <p>
     * 订阅需要支持以下约定：<br>
     * 1. 当URL设置了check=false参数时，注册失败不抛出异常并在后台重试。<br>
     * 2. 当URL设置了category=routers时，只通知指定的分类数据。多个分类用逗号分隔，允许使用星号匹配，表示订阅所有分类数据。<br>
     * 3. 允许使用interface、group、version和classifier作为条件查询，例如：interface=org.apache.dubbo.foo.BarService&version=1.0.0<br>
     * 4. 查询条件允许使用星号匹配，订阅所有接口的所有分组的所有版本的所有包，例如：interface=*&group=*&version=*&classifier=*<br>
     * 5. 当注册中心重启和网络抖动时，需要自动恢复订阅请求。<br>
     * 6. 允许具有相同URL但参数不同的URL共存，它们不能相互覆盖。<br>
     * 7. 订阅过程必须阻塞，当第一次通知完成后再返回。<br>
     *
     * @param url      订阅条件，不允许为空，例如: consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener 变更事件的监听器，不允许为空
     */
    void subscribe(URL url, NotifyListener listener);

    /**
     * 取消订阅
     * <p>
     * 取消订阅需要支持以下约定：<br>
     * 1. 如果没有订阅，直接忽略。<br>
     * 2. 根据完整URL匹配进行取消订阅。<br>
     *
     * @param url      订阅条件，不允许为空，例如: consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener 变更事件的监听器，不允许为空
     */
    void unsubscribe(URL url, NotifyListener listener);

    /**
     * 查询符合条件的注册数据。与订阅的推送模式相对应，这是拉取模式，只返回一个结果。
     *
     * @param url 查询条件，不允许为空，例如: consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @return 注册信息列表，可能为空，其含义与{@link org.apache.dubbo.registry.NotifyListener#notify(List<URL>)}的参数相同。
     * @see org.apache.dubbo.registry.NotifyListener#notify(List)
     */
    List<URL> lookup(URL url);
}
