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

import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.context.ApplicationExt;
import org.apache.dubbo.common.deploy.ApplicationDeployer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.context.ConfigManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * 应用模型
 * 
 * {@link ExtensionLoader}、{@code DubboBootstrap} 和此类目前被设计为单例或静态的。
 * 因此从它们返回的实例属于进程作用域。如果你想在一个进程中支持多个Dubbo服务器，
 * 你可能需要重构这三个类。
 * 
 * 代表一个使用Dubbo的应用程序，并存储在RPC调用处理期间使用的基本元数据信息。
 * 
 * ApplicationModel包含许多ProviderModel（关于已发布的服务）和许多ConsumerModel（关于已订阅的服务）。
 * 
 * 层级结构：
 * - FrameworkModel（框架模型）
 *   - ApplicationModel（应用模型）- 当前类
 *     - ModuleModel（模块模型）
 *       - ServiceModel（服务模型）
 * 
 * {@link ExtensionLoader}, {@code DubboBootstrap} and this class are at present designed to be
 * singleton or static (by itself totally static or uses some static fields). So the instances
 * returned from them are of process scope. If you want to support multiple dubbo servers in one
 * single process, you may need to refactor those three classes.
 * <p>
 * Represent an application which is using Dubbo and store basic metadata info for using
 * during the processing of RPC invoking.
 * <p>
 * ApplicationModel includes many ProviderModel which is about published services
 * and many Consumer Model which is about subscribed services.
 * <p>
 */
public class ApplicationModel extends ScopeModel {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModel.class);
    public static final String NAME = "ApplicationModel";
    
    /** 所有模块模型列表（包含内部模块和公共模块） */
    private final List<ModuleModel> moduleModels = new CopyOnWriteArrayList<>();
    /** 公共模块模型列表（不包含内部模块） */
    private final List<ModuleModel> pubModuleModels = new CopyOnWriteArrayList<>();
    /** 应用环境配置 */
    private volatile Environment environment;
    /** 应用配置管理器 */
    private volatile ConfigManager configManager;
    /** 应用服务仓库 */
    private volatile ServiceRepository serviceRepository;
    /** 应用部署器，负责应用的启动和停止 */
    private volatile ApplicationDeployer deployer;

    /** 所属的框架模型 */
    private final FrameworkModel frameworkModel;

    /** 内部模块，用于Dubbo框架内部服务 */
    private final ModuleModel internalModule;

    /** 默认模块，用于未明确指定模块的服务 */
    private volatile ModuleModel defaultModule;

    /** 模块索引计数器，内部模块索引为0，默认模块索引为1 */
    private final AtomicInteger moduleIndex = new AtomicInteger(0);

    // --------- 静态方法 ----------//

    /**
     * 获取应用模型或默认模型
     * 如果传入的应用模型不为null则返回该模型，否则返回默认模型
     * 
     * @param applicationModel 应用模型
     * @return 应用模型
     */
    public static ApplicationModel ofNullable(ApplicationModel applicationModel) {
        if (applicationModel != null) {
            return applicationModel;
        } else {
            return defaultModel();
        }
    }

    /**
     * 获取全局默认应用模型
     * 
     * 警告：在销毁默认FrameworkModel期间，FrameworkModel.defaultModel()或ApplicationModel.defaultModel()
     * 将返回一个损坏的模型，可能导致不可预测的问题。
     * 建议：尽可能避免使用默认模型。
     *
     * @return 全局默认应用模型
     */
    public static ApplicationModel defaultModel() {
        // 应该从默认的FrameworkModel获取，避免不同步
        return FrameworkModel.defaultModel().defaultApplication();
    }

    // ------------- 实例方法 ---------------//

    /**
     * 创建应用模型
     * 
     * @param frameworkModel 所属的框架模型
     */
    protected ApplicationModel(FrameworkModel frameworkModel) {
        this(frameworkModel, false);
    }

    /**
     * 创建应用模型
     * 
     * @param frameworkModel 所属的框架模型
     * @param isInternal 是否为内部应用
     */
    protected ApplicationModel(FrameworkModel frameworkModel, boolean isInternal) {
        super(frameworkModel, ExtensionScope.APPLICATION, isInternal);
        synchronized (instLock) {
            Assert.notNull(frameworkModel, "FrameworkModel can not be null");
            this.frameworkModel = frameworkModel;
            // 将当前应用添加到框架模型中
            frameworkModel.addApplication(this);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(getDesc() + " is created");
            }
            // 初始化应用
            initialize();

            // 创建内部模块
            this.internalModule = new ModuleModel(this, true);
            // 创建服务仓库
            this.serviceRepository = new ServiceRepository(this);

            // 加载并初始化应用初始化监听器
            ExtensionLoader<ApplicationInitListener> extensionLoader =
                    this.getExtensionLoader(ApplicationInitListener.class);
            Set<String> listenerNames = extensionLoader.getSupportedExtensions();
            for (String listenerName : listenerNames) {
                extensionLoader.getExtension(listenerName).init();
            }

            // 初始化应用扩展
            initApplicationExts();

            // 加载并执行应用模型初始化器
            ExtensionLoader<ScopeModelInitializer> initializerExtensionLoader =
                    this.getExtensionLoader(ScopeModelInitializer.class);
            Set<ScopeModelInitializer> initializers = initializerExtensionLoader.getSupportedExtensionInstances();
            for (ScopeModelInitializer initializer : initializers) {
                initializer.initializeApplicationModel(this);
            }

            // 验证必要的组件已正确初始化
            Assert.notNull(getApplicationServiceRepository(), "ApplicationServiceRepository can not be null");
            Assert.notNull(getApplicationConfigManager(), "ApplicationConfigManager can not be null");
            Assert.assertTrue(
                    getApplicationConfigManager().isInitialized(), "ApplicationConfigManager can not be initialized");
        }
    }

    /**
     * 初始化应用扩展
     * 加载所有应用扩展实例并逐一初始化
     * 注意：此方法在构造函数中已经进行了同步
     */
    private void initApplicationExts() {
        Set<ApplicationExt> exts = this.getExtensionLoader(ApplicationExt.class).getSupportedExtensionInstances();
        for (ApplicationExt ext : exts) {
            ext.initialize();
        }
    }

    /**
     * 销毁应用时的回调方法
     * 按顺序执行应用的清理和资源释放操作
     */
    @Override
    protected void onDestroy() {
        synchronized (instLock) {
            // 1. 从框架模型中移除当前应用
            frameworkModel.removeApplication(this);

            // 2. 预销毁，设置停止状态
            if (deployer != null) {
                // 首先销毁注册中心并从注册中心注销服务，以通知消费者停止消费此实例
                deployer.preDestroy();
            }

            // 3. 尝试销毁协议，停止此实例接收来自连接的新请求
            frameworkModel.tryDestroyProtocols();

            // 4. 销毁应用资源（除内部模块外的所有模块）
            for (ModuleModel moduleModel : moduleModels) {
                if (moduleModel != internalModule) {
                    moduleModel.destroy();
                }
            }
            // 5. 最后销毁内部模块
            internalModule.destroy();

            // 6. 后销毁，释放注册中心资源
            if (deployer != null) {
                deployer.postDestroy();
            }

            // 7. 销毁其他资源（例如ZookeeperTransporter）
            notifyDestroy();

            // 清理应用级别的组件
            if (environment != null) {
                environment.destroy();
                environment = null;
            }
            if (configManager != null) {
                configManager.destroy();
                configManager = null;
            }
            if (serviceRepository != null) {
                serviceRepository.destroy();
                serviceRepository = null;
            }

            // 8. 如果没有应用，销毁框架
            frameworkModel.tryDestroy();
        }
    }

    /**
     * 获取所属的框架模型
     * 
     * @return 框架模型
     */
    public FrameworkModel getFrameworkModel() {
        return frameworkModel;
    }

    /**
     * 创建新的模块
     * 
     * @return 新创建的模块模型
     */
    public ModuleModel newModule() {
        synchronized (instLock) {
            return new ModuleModel(this);
        }
    }

    /**
     * 获取应用环境
     * 懒加载方式获取应用环境配置
     * 
     * @return 应用环境
     */
    @Override
    public Environment modelEnvironment() {
        if (environment == null) {
            environment =
                    (Environment) this.getExtensionLoader(ApplicationExt.class).getExtension(Environment.NAME);
        }
        return environment;
    }

    /**
     * 获取应用配置管理器
     * 懒加载方式获取应用配置管理器
     * 
     * @return 应用配置管理器
     */
    public ConfigManager getApplicationConfigManager() {
        if (configManager == null) {
            configManager = (ConfigManager)
                    this.getExtensionLoader(ApplicationExt.class).getExtension(ConfigManager.NAME);
        }
        return configManager;
    }

    /**
     * 获取应用服务仓库
     * 
     * @return 应用服务仓库
     */
    public ServiceRepository getApplicationServiceRepository() {
        return serviceRepository;
    }

    /**
     * 获取应用执行器仓库
     * 
     * @return 应用执行器仓库
     */
    public ExecutorRepository getApplicationExecutorRepository() {
        return ExecutorRepository.getInstance(this);
    }

    /**
     * 检查是否不存在应用配置
     * 
     * @return 如果不存在应用配置返回true，否则返回false
     */
    public boolean NotExistApplicationConfig() {
        return !getApplicationConfigManager().getApplication().isPresent();
    }

    /**
     * 获取当前应用配置
     * 
     * @return 应用配置
     */
    public ApplicationConfig getCurrentConfig() {
        return getApplicationConfigManager().getApplicationOrElseThrow();
    }

    /**
     * 获取应用名称
     * 
     * @return 应用名称
     */
    public String getApplicationName() {
        return getCurrentConfig().getName();
    }

    /**
     * 尝试获取应用名称
     * 如果应用配置不存在则返回null
     * 
     * @return 应用名称或null
     */
    public String tryGetApplicationName() {
        Optional<ApplicationConfig> appCfgOptional =
                getApplicationConfigManager().getApplication();
        return appCfgOptional.isPresent() ? appCfgOptional.get().getName() : null;
    }

    /**
     * 添加模块到应用
     * 
     * @param moduleModel 模块模型
     * @param isInternal 是否为内部模块
     */
    void addModule(ModuleModel moduleModel, boolean isInternal) {
        synchronized (instLock) {
            if (!this.moduleModels.contains(moduleModel)) {
                checkDestroyed();
                this.moduleModels.add(moduleModel);
                moduleModel.setInternalId(buildInternalId(getInternalId(), moduleIndex.getAndIncrement()));
                if (!isInternal) {
                    pubModuleModels.add(moduleModel);
                }
            }
        }
    }

    /**
     * 从应用中移除模块
     * 
     * @param moduleModel 模块模型
     */
    public void removeModule(ModuleModel moduleModel) {
        synchronized (instLock) {
            this.moduleModels.remove(moduleModel);
            this.pubModuleModels.remove(moduleModel);
            if (moduleModel == defaultModule) {
                defaultModule = findDefaultModule();
            }
        }
    }

    /**
     * 尝试销毁应用
     * 当所有模块都被销毁（或者只剩内部模块）时，销毁应用
     */
    void tryDestroy() {
        synchronized (instLock) {
            if (this.moduleModels.isEmpty()
                    || (this.moduleModels.size() == 1 && this.moduleModels.get(0) == internalModule)) {
                destroy();
            }
        }
    }

    /**
     * 检查应用是否已被销毁
     * 如果应用已被销毁则抛出异常
     */
    private void checkDestroyed() {
        if (isDestroyed()) {
            throw new IllegalStateException("ApplicationModel is destroyed");
        }
    }

    /**
     * 获取所有模块列表（不可修改）
     * 
     * @return 模块模型列表
     */
    public List<ModuleModel> getModuleModels() {
        return Collections.unmodifiableList(moduleModels);
    }

    /**
     * 获取公共模块列表（不包含内部模块，不可修改）
     * 
     * @return 公共模块模型列表
     */
    public List<ModuleModel> getPubModuleModels() {
        return Collections.unmodifiableList(pubModuleModels);
    }

    /**
     * 获取默认模块
     * 如果默认模块不存在则创建一个
     * 
     * @return 默认模块模型
     */
    public ModuleModel getDefaultModule() {
        if (defaultModule == null) {
            synchronized (instLock) {
                if (defaultModule == null) {
                    defaultModule = findDefaultModule();
                    if (defaultModule == null) {
                        defaultModule = this.newModule();
                    }
                }
            }
        }
        return defaultModule;
    }

    /**
     * 查找默认模块
     * 返回第一个非内部模块作为默认模块
     * 
     * @return 默认模块模型或null
     */
    private ModuleModel findDefaultModule() {
        synchronized (instLock) {
            for (ModuleModel moduleModel : moduleModels) {
                if (moduleModel != internalModule) {
                    return moduleModel;
                }
            }
            return null;
        }
    }

    /**
     * 获取内部模块
     * 内部模块用于Dubbo框架内部服务
     * 
     * @return 内部模块模型
     */
    public ModuleModel getInternalModule() {
        return internalModule;
    }

    /**
     * 添加类加载器
     * 将类加载器添加到应用中，并刷新环境的类加载器
     * 
     * @param classLoader 类加载器
     */
    @Override
    public void addClassLoader(ClassLoader classLoader) {
        super.addClassLoader(classLoader);
        if (environment != null) {
            environment.refreshClassLoaders();
        }
    }

    /**
     * 移除类加载器
     * 从应用中移除类加载器，并刷新环境的类加载器
     * 
     * @param classLoader 类加载器
     */
    @Override
    public void removeClassLoader(ClassLoader classLoader) {
        super.removeClassLoader(classLoader);
        if (environment != null) {
            environment.refreshClassLoaders();
        }
    }

    /**
     * 检查类加载器是否可以被移除
     * 只有当所有模块都不再使用该类加载器时才可以移除
     * 
     * @param classLoader 类加载器
     * @return 如果可以移除返回true，否则返回false
     */
    @Override
    protected boolean checkIfClassLoaderCanRemoved(ClassLoader classLoader) {
        return super.checkIfClassLoaderCanRemoved(classLoader) && !containsClassLoader(classLoader);
    }

    /**
     * 检查是否包含指定的类加载器
     * 检查任何模块是否使用了该类加载器
     * 
     * @param classLoader 类加载器
     * @return 如果包含该类加载器返回true，否则返回false
     */
    protected boolean containsClassLoader(ClassLoader classLoader) {
        return moduleModels.stream()
                .anyMatch(moduleModel -> moduleModel.getClassLoaders().contains(classLoader));
    }

    /**
     * 获取应用部署器
     * 
     * @return 应用部署器
     */
    public ApplicationDeployer getDeployer() {
        return deployer;
    }

    /**
     * 设置应用部署器
     * 
     * @param deployer 应用部署器
     */
    public void setDeployer(ApplicationDeployer deployer) {
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
        return frameworkModel.acquireDestroyLock();
    }

    // =============================== Deprecated Methods Start =======================================

    /**
     * @deprecated use {@link ServiceRepository#allConsumerModels()}
     */
    @Deprecated
    public static Collection<ConsumerModel> allConsumerModels() {
        return defaultModel().getApplicationServiceRepository().allConsumerModels();
    }

    /**
     * @deprecated use {@link ServiceRepository#allProviderModels()}
     */
    @Deprecated
    public static Collection<ProviderModel> allProviderModels() {
        return defaultModel().getApplicationServiceRepository().allProviderModels();
    }

    /**
     * @deprecated use {@link FrameworkServiceRepository#lookupExportedService(String)}
     */
    @Deprecated
    public static ProviderModel getProviderModel(String serviceKey) {
        return defaultModel().getDefaultModule().getServiceRepository().lookupExportedService(serviceKey);
    }

    /**
     * @deprecated ConsumerModel should fetch from context
     */
    @Deprecated
    public static ConsumerModel getConsumerModel(String serviceKey) {
        return defaultModel().getDefaultModule().getServiceRepository().lookupReferredService(serviceKey);
    }

    /**
     * @deprecated Replace to {@link ScopeModel#modelEnvironment()}
     */
    @Deprecated
    public static Environment getEnvironment() {
        return defaultModel().modelEnvironment();
    }

    /**
     * @deprecated Replace to {@link ApplicationModel#getApplicationConfigManager()}
     */
    @Deprecated
    public static ConfigManager getConfigManager() {
        return defaultModel().getApplicationConfigManager();
    }

    /**
     * @deprecated Replace to {@link ApplicationModel#getApplicationServiceRepository()}
     */
    @Deprecated
    public static ServiceRepository getServiceRepository() {
        return defaultModel().getApplicationServiceRepository();
    }

    /**
     * @deprecated Replace to {@link ApplicationModel#getApplicationExecutorRepository()}
     */
    @Deprecated
    public static ExecutorRepository getExecutorRepository() {
        return defaultModel().getApplicationExecutorRepository();
    }

    /**
     * @deprecated Replace to {@link ApplicationModel#getCurrentConfig()}
     */
    @Deprecated
    public static ApplicationConfig getApplicationConfig() {
        return defaultModel().getCurrentConfig();
    }

    /**
     * @deprecated Replace to {@link ApplicationModel#getApplicationName()}
     */
    @Deprecated
    public static String getName() {
        return defaultModel().getCurrentConfig().getName();
    }

    /**
     * @deprecated Replace to {@link ApplicationModel#getApplicationName()}
     */
    @Deprecated
    public static String getApplication() {
        return getName();
    }

    // only for unit test
    @Deprecated
    public static void reset() {
        if (FrameworkModel.defaultModel().getDefaultAppModel() != null) {
            FrameworkModel.defaultModel().getDefaultAppModel().destroy();
        }
    }

    /**
     * @deprecated only for ut
     */
    @Deprecated
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * @deprecated only for ut
     */
    @Deprecated
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * @deprecated only for ut
     */
    @Deprecated
    public void setServiceRepository(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    // =============================== Deprecated Methods End =======================================
}
