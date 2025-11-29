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
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.resource.GlobalResourcesRepository;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.metadata.definition.TypeDefinitionBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 框架模型
 * 
 * Dubbo框架的核心模型，代表整个Dubbo框架实例，可以被多个应用共享。
 * 该类是Dubbo模型层次结构的最顶层，管理所有的ApplicationModel实例。
 * 
 * <p>
 * <b>模型层次结构：</b>
 * <pre>
 * FrameworkModel (框架模型) - 当前类
 *   └─ ApplicationModel (应用模型)
 *       └─ ModuleModel (模块模型)
 *           └─ ServiceModel (服务模型)
 * </pre>
 * 
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li>管理多个ApplicationModel实例</li>
 * <li>提供全局默认框架模型实例</li>
 * <li>维护框架级别的服务仓库</li>
 * <li>管理内部应用模型</li>
 * <li>控制框架的生命周期（初始化和销毁）</li>
 * <li>统一管理全局资源</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>多应用共享Dubbo框架实例</li>
 * <li>需要隔离不同应用的配置和资源</li>
 * <li>单元测试中需要独立的框架实例</li>
 * <li>容器环境中的多租户场景</li>
 * </ul>
 * 
 * <p>
 * <b>重要说明：</b>
 * <ul>
 * <li>通常情况下使用全局默认实例，通过{@link #defaultModel()}获取</li>
 * <li>在销毁默认框架模型时，可能返回不一致的模型实例，建议尽量避免使用默认模型</li>
 * <li>每个框架模型都包含一个内部应用模型，用于Dubbo内部使用</li>
 * </ul>
 * 
 * Model of dubbo framework, it can be shared with multiple applications.
 */
public class FrameworkModel extends ScopeModel {

    // ========================= Static Fields Start ===================================

    /** 日志记录器 */
    protected static final Logger LOGGER = LoggerFactory.getLogger(FrameworkModel.class);

    /** 框架模型名称 */
    public static final String NAME = "FrameworkModel";
    /** 框架模型实例索引计数器 */
    private static final AtomicLong index = new AtomicLong(1);

    /** 全局锁，用于同步静态操作 */
    private static final Object globalLock = new Object();

    /** 全局默认的框架模型实例 */
    private static volatile FrameworkModel defaultInstance;

    /** 所有框架模型实例列表 */
    private static final List<FrameworkModel> allInstances = new CopyOnWriteArrayList<>();

    // ========================= Static Fields End ===================================

    /** 应用模型索引计数器，内部应用索引为0，默认应用索引从1开始 */
    // internal app index is 0, default app index is 1
    private final AtomicLong appIndex = new AtomicLong(0);

    /** 默认的应用模型 */
    private volatile ApplicationModel defaultAppModel;

    /** 所有应用模型列表（包括内部应用模型） */
    private final List<ApplicationModel> applicationModels = new CopyOnWriteArrayList<>();

    /** 公共应用模型列表（不包括内部应用模型） */
    private final List<ApplicationModel> pubApplicationModels = new CopyOnWriteArrayList<>();

    /** 框架级别的服务仓库 */
    private final FrameworkServiceRepository serviceRepository;

    /** 内部应用模型，用于Dubbo内部使用 */
    private final ApplicationModel internalApplicationModel;

    /** 销毁锁，用于同步销毁操作 */
    private final ReentrantLock destroyLock = new ReentrantLock();

    /**
     * 构造函数
     * 创建框架模型实例
     * 
     * <p>此构造函数将执行以下操作：
     * <ul>
     * <li>注册框架模型实例到全局列表</li>
     * <li>初始化框架模型</li>
     * <li>初始化类型定义构建器</li>
     * <li>创建框架服务仓库</li>
     * <li>执行所有ScopeModelInitializer的初始化</li>
     * <li>创建内部应用模型</li>
     * </ul>
     * 
     * Use {@link FrameworkModel#newModel()} to create a new model
     */
    public FrameworkModel() {
        super(null, ExtensionScope.FRAMEWORK, false);
        synchronized (globalLock) {
            synchronized (instLock) {
                this.setInternalId(String.valueOf(index.getAndIncrement()));
                // 注册框架模型实例
                // register FrameworkModel instance early
                allInstances.add(this);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(getDesc() + " is created");
                }
                initialize();

                TypeDefinitionBuilder.initBuilders(this);

                serviceRepository = new FrameworkServiceRepository(this);

                ExtensionLoader<ScopeModelInitializer> initializerExtensionLoader =
                        this.getExtensionLoader(ScopeModelInitializer.class);
                Set<ScopeModelInitializer> initializers = initializerExtensionLoader.getSupportedExtensionInstances();
                for (ScopeModelInitializer initializer : initializers) {
                    initializer.initializeFrameworkModel(this);
                }

                internalApplicationModel = new ApplicationModel(this, true);
                internalApplicationModel
                        .getApplicationConfigManager()
                        .setApplication(new ApplicationConfig(
                                internalApplicationModel, CommonConstants.DUBBO_INTERNAL_APPLICATION));
                internalApplicationModel.setModelName(CommonConstants.DUBBO_INTERNAL_APPLICATION);
            }
        }
    }

    /**
     * 销毁框架模型时的回调方法
     * 
     * <p>此方法将执行以下操作：
     * <ul>
     * <li>销毁所有应用模型</li>
     * <li>检查所有应用模型是否已销毁</li>
     * <li>通知销毁事件并清理框架资源</li>
     * <li>从全局列表中移除当前实例</li>
     * <li>重置默认框架模型</li>
     * <li>如果所有框架模型都已销毁，清理全局静态资源</li>
     * </ul>
     */
    @Override
    protected void onDestroy() {
        synchronized (instLock) {
            if (defaultInstance == this) {
                // 注意：在销毁默认框架模型时，FrameworkModel.defaultModel()或ApplicationModel.defaultModel()
                // 将返回一个损坏的模型，可能导致不可预测的问题
                // NOTE: During destroying the default FrameworkModel, the FrameworkModel.defaultModel() or
                // ApplicationModel.defaultModel()
                // will return a broken model, maybe cause unpredictable problem.
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Destroying default framework model: " + getDesc());
                }
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(getDesc() + " is destroying ...");
            }

            // 销毁所有应用模型
            // destroy all application model
            for (ApplicationModel applicationModel : new ArrayList<>(applicationModels)) {
                applicationModel.destroy();
            }
            // 检查所有应用模型是否已销毁
            // check whether all application models are destroyed
            checkApplicationDestroy();

            // 通知销毁并清理框架资源
            // notify destroy and clean framework resources
            // see org.apache.dubbo.config.deploy.FrameworkModelCleaner
            notifyDestroy();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(getDesc() + " is destroyed");
            }

            // 从allInstances中移除并重置默认框架模型
            // remove from allInstances and reset default FrameworkModel
            synchronized (globalLock) {
                allInstances.remove(this);
                resetDefaultFrameworkModel();
            }

            // 如果所有框架模型都已销毁，清理全局静态资源，完全关闝dubbo
            // if all FrameworkModels are destroyed, clean global static resources, shutdown dubbo completely
            destroyGlobalResources();
        }
    }

    /**
     * 检查应用模型是否已销毁
     * 如果还有未销毁的应用模型，抛出IllegalStateException
     * 
     * @throws IllegalStateException 如果还有未销毁的应用模型
     */
    private void checkApplicationDestroy() {
        synchronized (instLock) {
            if (applicationModels.size() > 0) {
                List<String> remainApplications =
                        applicationModels.stream().map(ScopeModel::getDesc).collect(Collectors.toList());
                throw new IllegalStateException(
                        "Not all application models are completely destroyed, remaining " + remainApplications.size()
                                + " application models may be created during destruction: " + remainApplications);
            }
        }
    }

    /**
     * 销毁全局资源
     * 当所有框架模型实例都已销毁时，清理全局资源仓库
     */
    private void destroyGlobalResources() {
        synchronized (globalLock) {
            if (allInstances.isEmpty()) {
                GlobalResourcesRepository.getInstance().destroy();
            }
        }
    }

    /**
     * 获取全局默认的框架模型实例
     * 
     * <p><b>注意：</b>在销毁默认框架模型时，FrameworkModel.defaultModel()或ApplicationModel.defaultModel()
     * 将返回一个损坏的模型，可能导致不可预测的问题。
     * <b>建议：</b>尽量避免使用默认模型。
     * 
     * During destroying the default FrameworkModel, the FrameworkModel.defaultModel() or ApplicationModel.defaultModel()
     * will return a broken model, maybe cause unpredictable problem.
     * Recommendation: Avoid using the default model as much as possible.
     * @return 全局默认框架模型 (the global default FrameworkModel)
     */
    public static FrameworkModel defaultModel() {
        FrameworkModel instance = defaultInstance;
        if (instance == null) {
            synchronized (globalLock) {
                resetDefaultFrameworkModel();
                if (defaultInstance == null) {
                    defaultInstance = new FrameworkModel();
                }
                instance = defaultInstance;
            }
        }
        Assert.notNull(instance, "Default FrameworkModel is null");
        return instance;
    }

    /**
     * 获取所有框架模型实例
     * Get all framework model instances
     * 
     * @return 框架模型实例列表（不可修改）
     */
    public static List<FrameworkModel> getAllInstances() {
        synchronized (globalLock) {
            return Collections.unmodifiableList(new ArrayList<>(allInstances));
        }
    }

    /**
     * 销毁所有框架模型实例，完全关闝dubbo引擎
     * Destroy all framework model instances, shutdown dubbo engine completely.
     */
    public static void destroyAll() {
        synchronized (globalLock) {
            for (FrameworkModel frameworkModel : new ArrayList<>(allInstances)) {
                frameworkModel.destroy();
            }
        }
    }

    /**
     * 创建新的应用模型
     * 
     * @return 新创建的应用模型实例
     */
    public ApplicationModel newApplication() {
        synchronized (instLock) {
            return new ApplicationModel(this);
        }
    }

    /**
     * 获取或创建默认的应用模型
     * Get or create default application model
     * 
     * @return 默认应用模型
     */
    public ApplicationModel defaultApplication() {
        ApplicationModel appModel = this.defaultAppModel;
        if (appModel == null) {
            // 在获取实例锁之前检查是否已销毁，避免在销毁期间阻塞
            // check destroyed before acquire inst lock, avoid blocking during destroying
            checkDestroyed();
            resetDefaultAppModel();
            if ((appModel = this.defaultAppModel) == null) {
                synchronized (instLock) {
                    if (this.defaultAppModel == null) {
                        this.defaultAppModel = newApplication();
                    }
                    appModel = this.defaultAppModel;
                }
            }
        }
        Assert.notNull(appModel, "Default ApplicationModel is null");
        return appModel;
    }

    /**
     * 获取默认应用模型
     * 
     * @return 默认应用模型，可能为null
     */
    ApplicationModel getDefaultAppModel() {
        return defaultAppModel;
    }

    /**
     * 添加应用模型到框架模型
     * 
     * @param applicationModel 要添加的应用模型
     * @throws IllegalStateException 如果框架模型已销毁
     */
    void addApplication(ApplicationModel applicationModel) {
        // 如果正在销毁，不能添加新应用
        // can not add new application if it's destroying
        checkDestroyed();
        synchronized (instLock) {
            if (!this.applicationModels.contains(applicationModel)) {
                applicationModel.setInternalId(buildInternalId(getInternalId(), appIndex.getAndIncrement()));
                this.applicationModels.add(applicationModel);
                if (!applicationModel.isInternal()) {
                    this.pubApplicationModels.add(applicationModel);
                }
            }
        }
    }

    /**
     * 从框架模型中移除应用模型
     * 
     * @param model 要移除的应用模型
     */
    void removeApplication(ApplicationModel model) {
        synchronized (instLock) {
            this.applicationModels.remove(model);
            if (!model.isInternal()) {
                this.pubApplicationModels.remove(model);
            }
            resetDefaultAppModel();
        }
    }

    /**
     * 尝试销毁协议
     * 
     * 协议是需要尽快销毁的特殊资源。
     * 由于协议内部的连接没有按应用分类，尝试提前销毁协议可能只适用于单例应用场景。
     * 
     * Protocols are special resources that need to be destroyed as soon as possible.
     *
     * Since connections inside protocol are not classified by applications, trying to destroy protocols in advance might only work for singleton application scenario.
     */
    void tryDestroyProtocols() {
        synchronized (instLock) {
            if (pubApplicationModels.size() == 0) {
                notifyProtocolDestroy();
            }
        }
    }

    /**
     * 尝试销毁框架模型
     * 当所有公共应用模型都已移除时，销毁框架模型
     */
    void tryDestroy() {
        synchronized (instLock) {
            if (pubApplicationModels.size() == 0) {
                destroy();
            }
        }
    }

    /**
     * 检查框架模型是否已销毁
     * 
     * @throws IllegalStateException 如果框架模型已销毁
     */
    private void checkDestroyed() {
        if (isDestroyed()) {
            throw new IllegalStateException("FrameworkModel is destroyed");
        }
    }

    /**
     * 重置默认应用模型
     * 当默认应用模型已销毁时，从pubApplicationModels中选择第一个作为新的默认应用模型
     */
    private void resetDefaultAppModel() {
        synchronized (instLock) {
            if (this.defaultAppModel != null && !this.defaultAppModel.isDestroyed()) {
                return;
            }
            ApplicationModel oldDefaultAppModel = this.defaultAppModel;
            if (pubApplicationModels.size() > 0) {
                this.defaultAppModel = pubApplicationModels.get(0);
            } else {
                this.defaultAppModel = null;
            }
            if (defaultInstance == this && oldDefaultAppModel != this.defaultAppModel) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Reset global default application from " + safeGetModelDesc(oldDefaultAppModel) + " to "
                            + safeGetModelDesc(this.defaultAppModel));
                }
            }
        }
    }

    /**
     * 重置默认框架模型
     * 当默认框架模型已销毁时，从allInstances中选择第一个作为新的默认框架模型
     */
    private static void resetDefaultFrameworkModel() {
        synchronized (globalLock) {
            if (defaultInstance != null && !defaultInstance.isDestroyed()) {
                return;
            }
            FrameworkModel oldDefaultFrameworkModel = defaultInstance;
            if (allInstances.size() > 0) {
                defaultInstance = allInstances.get(0);
            } else {
                defaultInstance = null;
            }
            if (oldDefaultFrameworkModel != defaultInstance) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Reset global default framework from " + safeGetModelDesc(oldDefaultFrameworkModel)
                            + " to " + safeGetModelDesc(defaultInstance));
                }
            }
        }
    }

    /**
     * 安全地获取模型描述
     * 如果模型为null，返回null而不是抛出异常
     * 
     * @param scopeModel 作用域模型
     * @return 模型描述，如果模型为null则返回null
     */
    private static String safeGetModelDesc(ScopeModel scopeModel) {
        return scopeModel != null ? scopeModel.getDesc() : null;
    }

    /**
     * 获取所有应用模型（不包括内部应用模型）
     * Get all application models except for the internal application model.
     * 
     * @return 应用模型列表（不可修改）
     */
    public List<ApplicationModel> getApplicationModels() {
        synchronized (globalLock) {
            return Collections.unmodifiableList(pubApplicationModels);
        }
    }

    /**
     * 获取所有应用模型（包括内部应用模型）
     * Get all application models including the internal application model.
     * 
     * @return 应用模型列表（不可修改）
     */
    public List<ApplicationModel> getAllApplicationModels() {
        synchronized (globalLock) {
            return Collections.unmodifiableList(applicationModels);
        }
    }

    /**
     * 获取内部应用模型
     * 内部应用模型用于Dubbo内部使用
     * 
     * @return 内部应用模型
     */
    public ApplicationModel getInternalApplicationModel() {
        return internalApplicationModel;
    }

    /**
     * 获取框架服务仓库
     * 
     * @return 框架服务仓库
     */
    public FrameworkServiceRepository getServiceRepository() {
        return serviceRepository;
    }

    /**
     * 获取销毁锁
     * 
     * @return 销毁锁
     */
    @Override
    protected Lock acquireDestroyLock() {
        return destroyLock;
    }

    /**
     * 获取模型环境
     * FrameworkModel不支持环境访问
     * 
     * @throws UnsupportedOperationException 总是抛出此异常
     */
    @Override
    public Environment modelEnvironment() {
        throw new UnsupportedOperationException("Environment is inaccessible for FrameworkModel");
    }

    /**
     * 检查类加载器是否可以被移除
     * 只有当类加载器不被任何应用模型使用时，才可以移除
     * 
     * @param classLoader 要检查的类加载器
     * @return 如果可以移除返回true，否则返回false
     */
    @Override
    protected boolean checkIfClassLoaderCanRemoved(ClassLoader classLoader) {
        return super.checkIfClassLoaderCanRemoved(classLoader)
                && applicationModels.stream()
                        .noneMatch(applicationModel -> applicationModel.containsClassLoader(classLoader));
    }
}
