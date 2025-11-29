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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;

/**
 * 集群接口 (SPI, Singleton, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Computer_cluster">集群</a>
 * <a href="http://en.wikipedia.org/wiki/Fault-tolerant_system">容错系统</a>
 *
 */
@SPI(Cluster.DEFAULT)
public interface Cluster {

    // 默认集群策略为failover（失败转移）
    String DEFAULT = "failover";

    /**
     * 将目录调用者合并为一个虚拟调用者。
     *
     * @param <T> 泛型类型
     * @param directory 目录
     * @param buildFilterChain 是否构建过滤器链
     * @return 集群调用者
     * @throws RpcException RPC异常
     */
    @Adaptive
    <T> Invoker<T> join(Directory<T> directory, boolean buildFilterChain) throws RpcException;

    /**
     * 获取集群实例
     * 
     * @param scopeModel 作用域模型
     * @param name 集群名称
     * @return 集群实例
     */
    static Cluster getCluster(ScopeModel scopeModel, String name) {
        return getCluster(scopeModel, name, true);
    }

    /**
     * 获取集群实例
     * 
     * @param scopeModel 作用域模型
     * @param name 集群名称
     * @param wrap 是否包装
     * @return 集群实例
     */
    static Cluster getCluster(ScopeModel scopeModel, String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {
            name = Cluster.DEFAULT;
        }
        return ScopeModelUtil.getApplicationModel(scopeModel)
                .getExtensionLoader(Cluster.class)
                .getExtension(name, wrap);
    }
}
