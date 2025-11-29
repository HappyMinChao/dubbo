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
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ModuleEnvironment;
import org.apache.dubbo.common.context.ModuleExt;
import org.apache.dubbo.common.deploy.ApplicationDeployer;
import org.apache.dubbo.common.deploy.DeployState;
import org.apache.dubbo.common.deploy.ModuleDeployer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.config.context.ModuleConfigManager;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * 服务模块模型
 * 模块是Dubbo中服务的逻辑分组单位，每个模块可以包含多个服务的提供者和消费者
 * 一个应用(ApplicationModel)可以包含多个模块(ModuleModel)
 * 
 * Model of a service module
 */
public class ModuleModel extends ScopeModel {
    private static final Logger logger = LoggerFactory.getLogger(ModuleModel.class);

    public static final String NAME = "ModuleModel";

    /** 所属的应用模型 */
    private final ApplicationModel applicationModel;
    /** 模块服务仓库，用于管理该模块的服务提供者和消费者 */
    private volatile ModuleServiceRepository serviceRepository;
    /** 模块环境配置 */
    private volatile ModuleEnvironment moduleEnvironment;
    /** 模块配置管理器 */
    private volatile ModuleConfigManager moduleConfigManager;
    /** 模块部署器，负责模块的启动和停止 */
    private volatile ModuleDeployer deployer;
    /** 标识生命周期是否由外部管理 */
    private boolean lifeCycleManagedExternally = false;

    /**
     * 创建模块模型
     * 
     * @param applicationModel 所属的应用模型
     */
    protected ModuleModel(ApplicationModel applicationModel) {
        this(applicationModel, false);
    }

    /**
     * 创建模块模型
     * 
     * @param applicationModel 所属的应用模型
     * @param isInternal 是否为内部模块
     */
    protected ModuleModel(ApplicationModel applicationModel, boolean isInternal) {
        super(applicationModel, ExtensionScope.MODULE, isInternal);
        synchronized (instLock) {
            Assert.notNull(applicationModel, "ApplicationModel can not be null");
            this.applicationModel = applicationModel;
            // 将当前模块添加到应用模型中
            applicationModel.addModule(this, isInternal);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(getDesc() + " is created");
            }

            // 初始化模块
            initialize();

            // 创建服务仓库
            this.serviceRepository = new ModuleServiceRepository(this);

            // 初始化模块扩展
            initModuleExt();

            // 加载并执行模块初始化器
            ExtensionLoader<ScopeModelInitializer> initializerExtensionLoader =
                    this.getExtensionLoader(ScopeModelInitializer.class);
            Set<ScopeModelInitializer> initializers = initializerExtensionLoader.getSupportedExtensionInstances();
            for (ScopeModelInitializer initializer : initializers) {
                initializer.initializeModuleModel(this);
            }
            // 验证必要的组件已正确初始化
            Assert.notNull(getServiceRepository(), "ModuleServiceRepository can not be null");
            Assert.notNull(getConfigManager(), "ModuleConfigManager can not be null");
            Assert.assertTrue(getConfigManager().isInitialized(), "ModuleConfigManager can not be initialized");

            // 通知应用检查状态
            ApplicationDeployer applicationDeployer = applicationModel.getDeployer();
            if (applicationDeployer != null) {
                applicationDeployer.notifyModuleChanged(this, DeployState.PENDING);
            }
        }
    }

    /**
     * 初始化模块扩展
     * 加载所有模块扩展实例并逐一初始化
     * 注意：此方法在构造函数中已经进行了同步
     */
    private void initModuleExt() {
        Set<ModuleExt> exts = this.getExtensionLoader(ModuleExt.class).getSupportedExtensionInstances();
        for (ModuleExt ext : exts) {
            ext.initialize();
        }
    }

    /**
     * 销毁模块时的回调方法
     * 执行模块的清理和资源释放操作
     */
    @Override
    protected void onDestroy() {
        synchronized (instLock) {
            // 1. 从应用模型中移除当前模块
            applicationModel.removeModule(this);

            // 2. 设置停止状态并执行预销毁操作
            if (deployer != null) {
                deployer.preDestroy();
            }

            // 3. 释放服务资源
            if (deployer != null) {
                deployer.postDestroy();
            }

            // 通知销毁事件
            notifyDestroy();

            // 销毁服务仓库
            if (serviceRepository != null) {
                serviceRepository.destroy();
                serviceRepository = null;
            }

            // 销毁模块环境
            if (moduleEnvironment != null) {
                moduleEnvironment.destroy();
                moduleEnvironment = null;
            }

            // 销毁模块配置管理器
            if (moduleConfigManager != null) {
                moduleConfigManager.destroy();
                moduleConfigManager = null;
            }

            // 如果没有公共模块，尝试销毁应用
            applicationModel.tryDestroy();
        }
    }

    /**
     * 获取所属的应用模型
     * 
     * @return 应用模型
     */
    public ApplicationModel getApplicationModel() {
        return applicationModel;
    }

    /**
     * 获取模块服务仓库
     * 
     * @return 模块服务仓库
     */
    public ModuleServiceRepository getServiceRepository() {
        return serviceRepository;
    }

    /**
     * 添加类加载器
     * 将类加载器添加到模块中，并刷新模块环境的类加载器
     * 
     * @param classLoader 类加载器
     */
    @Override
    public void addClassLoader(ClassLoader classLoader) {
        super.addClassLoader(classLoader);
        if (moduleEnvironment != null) {
            moduleEnvironment.refreshClassLoaders();
        }
    }

    /**
     * 获取模块环境
     * 懒加载方式获取模块环境配置
     * 
     * @return 模块环境
     */
    @Override
    public ModuleEnvironment modelEnvironment() {
        if (moduleEnvironment == null) {
            moduleEnvironment =
                    (ModuleEnvironment) this.getExtensionLoader(ModuleExt.class).getExtension(ModuleEnvironment.NAME);
        }
        return moduleEnvironment;
    }

    /**
     * 获取模块配置管理器
     * 懒加载方式获取模块配置管理器
     * 
     * @return 模块配置管理器
     */
    public ModuleConfigManager getConfigManager() {
        if (moduleConfigManager == null) {
            moduleConfigManager = (ModuleConfigManager)
                    this.getExtensionLoader(ModuleExt.class).getExtension(ModuleConfigManager.NAME);
        }
        return moduleConfigManager;
    }

    /**
     * 获取模块部署器
     * 
     * @return 模块部署器
     */
    public ModuleDeployer getDeployer() {
        return deployer;
    }

    /**
     * 设置模块部署器
     * 
     * @param deployer 模块部署器
     */
    public void setDeployer(ModuleDeployer deployer) {
        this.deployer = deployer;
    }

    /**
     * 获取销毁锁
     * 从框架模型中获取销毁锁，确保销毁操作的线程安全
     * 
     * @return 销毁锁
     */
    @Override
    protected Lock acquireDestroyLock() {
        return getApplicationModel().getFrameworkModel().acquireDestroyLock();
    }

    /**
     * 设置模块环境
     * 仅用于单元测试
     * 
     * @param moduleEnvironment 模块环境
     * @deprecated 仅用于单元测试
     */
    @Deprecated
    public void setModuleEnvironment(ModuleEnvironment moduleEnvironment) {
        this.moduleEnvironment = moduleEnvironment;
    }

    /**
     * 注册内部消费者
     * 用于动态注册内部服务的消费者模型
     * 
     * @param internalService 内部服务接口
     * @param url 服务URL
     * @param serviceDescriptor 服务描述符
     * @param proxyObject 代理对象
     * @return 消费者模型
     */
    public ConsumerModel registerInternalConsumer(
            Class<?> internalService, URL url, ServiceDescriptor serviceDescriptor, Object proxyObject) {
        // 创建服务元数据
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setVersion(url.getVersion());
        serviceMetadata.setGroup(url.getGroup());
        serviceMetadata.setDefaultGroup(url.getGroup());
        serviceMetadata.setServiceInterfaceName(internalService.getName());
        serviceMetadata.setServiceType(internalService);
        String serviceKey = URL.buildKey(internalService.getName(), url.getGroup(), url.getVersion());
        serviceMetadata.setServiceKey(serviceKey);
        
        // 创建消费者模型
        ConsumerModel consumerModel = new ConsumerModel(
                serviceMetadata.getServiceKey(),
                proxyObject,
                serviceDescriptor == null
                        ? serviceRepository.lookupService(serviceMetadata.getServiceInterfaceName())
                        : serviceDescriptor,
                this,
                serviceMetadata,
                new HashMap<>(0),
                ClassUtils.getClassLoader(internalService));

        logger.info("[INSTANCE_REGISTER] Dynamically registering consumer model " + serviceKey + " into model "
                + this.getDesc());
        // 注册消费者
        serviceRepository.registerConsumer(consumerModel);
        return consumerModel;
    }

    /**
     * 注册内部消费者
     * 
     * @param internalService 内部服务接口
     * @param url 服务URL
     * @param serviceDescriptor 服务描述符
     * @return 消费者模型
     */
    public ConsumerModel registerInternalConsumer(
            Class<?> internalService, URL url, ServiceDescriptor serviceDescriptor) {
        return registerInternalConsumer(internalService, url, serviceDescriptor, null);
    }

    /**
     * 注册内部消费者
     * 
     * @param internalService 内部服务接口
     * @param url 服务URL
     * @return 消费者模型
     */
    public ConsumerModel registerInternalConsumer(Class<?> internalService, URL url) {
        return registerInternalConsumer(internalService, url, null, null);
    }

    /**
     * 检查生命周期是否由外部管理
     * 
     * @return 如果生命周期由外部管理返回true，否则返回false
     */
    public boolean isLifeCycleManagedExternally() {
        return lifeCycleManagedExternally;
    }

    /**
     * 设置生命周期是否由外部管理
     * 
     * @param lifeCycleManagedExternally 是否由外部管理
     */
    public void setLifeCycleManagedExternally(boolean lifeCycleManagedExternally) {
        this.lifeCycleManagedExternally = lifeCycleManagedExternally;
    }
}
