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
package org.apache.dubbo.config.deploy;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.config.ReferenceCache;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.common.config.configcenter.wrapper.CompositeDynamicConfiguration;
import org.apache.dubbo.common.constants.LoggerCodeConstants;
import org.apache.dubbo.common.deploy.AbstractDeployer;
import org.apache.dubbo.common.deploy.ApplicationDeployListener;
import org.apache.dubbo.common.deploy.ApplicationDeployer;
import org.apache.dubbo.common.deploy.DeployListener;
import org.apache.dubbo.common.deploy.DeployState;
import org.apache.dubbo.common.deploy.ModuleDeployer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.threadpool.manager.FrameworkExecutorRepository;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.DubboShutdownHook;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.TracingConfig;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.utils.CompositeReferenceCache;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.metadata.report.MetadataReportFactory;
import org.apache.dubbo.metadata.report.MetadataReportInstance;
import org.apache.dubbo.metrics.collector.DefaultMetricsCollector;
import org.apache.dubbo.metrics.config.event.ConfigCenterEvent;
import org.apache.dubbo.metrics.event.MetricsEventBus;
import org.apache.dubbo.metrics.report.DefaultMetricsReporterFactory;
import org.apache.dubbo.metrics.report.MetricsReporter;
import org.apache.dubbo.metrics.report.MetricsReporterFactory;
import org.apache.dubbo.metrics.service.MetricsServiceExporter;
import org.apache.dubbo.metrics.utils.MetricsSupportUtil;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils;
import org.apache.dubbo.registry.support.RegistryManager;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ModuleServiceRepository;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;
import org.apache.dubbo.tracing.DubboObservationRegistry;
import org.apache.dubbo.tracing.utils.ObservationSupportUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.dubbo.common.config.ConfigurationUtils.parseProperties;
import static org.apache.dubbo.common.constants.CommonConstants.REGISTRY_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.COMMON_METRICS_COLLECTOR_EXCEPTION;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_FAILED_EXECUTE_DESTROY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_FAILED_INIT_CONFIG_CENTER;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_FAILED_START_MODEL;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_REFRESH_INSTANCE_ERROR;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_REGISTER_INSTANCE_ERROR;
import static org.apache.dubbo.common.constants.MetricsConstants.PROTOCOL_DEFAULT;
import static org.apache.dubbo.common.constants.MetricsConstants.PROTOCOL_PROMETHEUS;
import static org.apache.dubbo.common.utils.StringUtils.isEmpty;
import static org.apache.dubbo.common.utils.StringUtils.isNotEmpty;
import static org.apache.dubbo.config.Constants.DEFAULT_APP_NAME;
import static org.apache.dubbo.metadata.MetadataConstants.DEFAULT_METADATA_PUBLISH_DELAY;
import static org.apache.dubbo.metadata.MetadataConstants.METADATA_PUBLISH_DELAY_KEY;
import static org.apache.dubbo.remoting.Constants.CLIENT_KEY;

/**
 * initialize and start application instance
 */
public class DefaultApplicationDeployer extends AbstractDeployer<ApplicationModel> implements ApplicationDeployer {

    private static final ErrorTypeAwareLogger logger =
            LoggerFactory.getErrorTypeAwareLogger(DefaultApplicationDeployer.class);

    private final ApplicationModel applicationModel;

    private final ConfigManager configManager;

    private final Environment environment;

    private final ReferenceCache referenceCache;

    private final FrameworkExecutorRepository frameworkExecutorRepository;
    private final ExecutorRepository executorRepository;

    private final AtomicBoolean hasPreparedApplicationInstance = new AtomicBoolean(false);
    private volatile boolean hasPreparedInternalModule = false;

    private ScheduledFuture<?> asyncMetadataFuture;
    private volatile CompletableFuture<Boolean> startFuture;
    private final DubboShutdownHook dubboShutdownHook;

    private volatile MetricsServiceExporter metricsServiceExporter;

    private final Object stateLock = new Object();
    private final Object startLock = new Object();
    private final Object destroyLock = new Object();
    private final Object internalModuleLock = new Object();

    public DefaultApplicationDeployer(ApplicationModel applicationModel) {
        super(applicationModel);
        this.applicationModel = applicationModel;
        configManager = applicationModel.getApplicationConfigManager();
        environment = applicationModel.modelEnvironment();

        referenceCache = new CompositeReferenceCache(applicationModel);
        frameworkExecutorRepository =
                applicationModel.getFrameworkModel().getBeanFactory().getBean(FrameworkExecutorRepository.class);
        executorRepository = ExecutorRepository.getInstance(applicationModel);
        dubboShutdownHook = new DubboShutdownHook(applicationModel);

        // load spi listener
        Set<ApplicationDeployListener> deployListeners = applicationModel
                .getExtensionLoader(ApplicationDeployListener.class)
                .getSupportedExtensionInstances();
        for (ApplicationDeployListener listener : deployListeners) {
            this.addDeployListener(listener);
        }
    }

    public static ApplicationDeployer get(ScopeModel moduleOrApplicationModel) {
        ApplicationModel applicationModel = ScopeModelUtil.getApplicationModel(moduleOrApplicationModel);
        ApplicationDeployer applicationDeployer = applicationModel.getDeployer();
        if (applicationDeployer == null) {
            applicationDeployer = applicationModel.getBeanFactory().getOrRegisterBean(DefaultApplicationDeployer.class);
        }
        return applicationDeployer;
    }

    @Override
    public ApplicationModel getApplicationModel() {
        return applicationModel;
    }

    private <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        return applicationModel.getExtensionLoader(type);
    }

    private void unRegisterShutdownHook() {
        dubboShutdownHook.unregister();
    }

    /**
     * 检查是否注册消费者实例
     * 通过检查应用配置中的registerConsumer属性来决定是否注册纯消费者的实例
     *
     * @return 如果需要注册消费者实例返回true，否则返回false
     */
    private boolean isRegisterConsumerInstance() {
        Boolean registerConsumer = getApplicationOrElseThrow().getRegisterConsumer();
        if (registerConsumer == null) {
            return false;
        }
        return Boolean.TRUE.equals(registerConsumer);
    }

    @Override
    public ReferenceCache getReferenceCache() {
        return referenceCache;
    }

    /**
     * 初始化应用程序
     * 包括注册关闭钩子、启动配置中心、加载应用配置、初始化模块部署器等
     */
    @Override
    public void initialize() {
        // 如果已经初始化，直接返回
        if (initialized) {
            return;
        }
        // 确保初始化过程是线程安全的
        synchronized (startLock) {
            // 双重检查是否已初始化
            if (initialized) {
                return;
            }
            // 执行初始化操作
            onInitialize();

            // 注册关闭钩子
            registerShutdownHook();

            // 启动配置中心
            startConfigCenter();

            // 加载应用配置
            loadApplicationConfigs();

            // 初始化模块部署器
            initModuleDeployers();

            // 初始化指标报告器
            initMetricsReporter();

            // 初始化指标服务
            initMetricsService();

            // 初始化观测注册表（自3.2.3版本起）
            initObservationRegistry();

            // 启动元数据中心（自2.7.8版本起）
            startMetadataCenter();

            // 标记为已初始化
            initialized = true;

            // 记录日志信息
            if (logger.isInfoEnabled()) {
                logger.info(getIdentifier() + " has been initialized!");
            }
        }
    }

    private void registerShutdownHook() {
        dubboShutdownHook.register();
    }

    /**
     * 初始化模块部署器
     * 确保创建默认模块并初始化所有模块的部署器
     */
    private void initModuleDeployers() {
        // make sure created default module
        applicationModel.getDefaultModule();
        // deployer initialize
        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
            moduleModel.getDeployer().initialize();
        }
    }

    /**
     * 加载应用配置
     * 从配置管理器加载所有配置
     */
    private void loadApplicationConfigs() {
        configManager.loadConfigs();
    }

    /**
     * 启动配置中心
     * 加载应用配置、配置中心配置，并设置环境变量
     */
    private void startConfigCenter() {

        // 从属性文件中加载应用配置
        configManager.loadConfigsOfTypeFromProps(ApplicationConfig.class);

        // 尝试设置模型名称
        if (StringUtils.isBlank(applicationModel.getModelName())) {
            applicationModel.setModelName(applicationModel.tryGetApplicationName());
        }

        // 从属性文件中加载配置中心配置
        configManager.loadConfigsOfTypeFromProps(ConfigCenterConfig.class);

        // 如有必要，使用注册中心作为配置中心
        useRegistryAsConfigCenterIfNecessary();

        // 检查配置中心配置
        Collection<ConfigCenterConfig> configCenters = configManager.getConfigCenters();
        if (CollectionUtils.isEmpty(configCenters)) {
            ConfigCenterConfig configCenterConfig = new ConfigCenterConfig();
            configCenterConfig.setScopeModel(applicationModel);
            configCenterConfig.refresh();
            ConfigValidationUtils.validateConfigCenterConfig(configCenterConfig);
            if (configCenterConfig.isValid()) {
                configManager.addConfigCenter(configCenterConfig);
                configCenters = configManager.getConfigCenters();
            }
        } else {
            for (ConfigCenterConfig configCenterConfig : configCenters) {
                configCenterConfig.refresh();
                ConfigValidationUtils.validateConfigCenterConfig(configCenterConfig);
            }
        }

        // 如果存在配置中心配置，则设置动态配置
        if (CollectionUtils.isNotEmpty(configCenters)) {
            CompositeDynamicConfiguration compositeDynamicConfiguration = new CompositeDynamicConfiguration();
            for (ConfigCenterConfig configCenter : configCenters) {
                // 将配置中心的外部配置传递给环境
                environment.updateExternalConfigMap(configCenter.getExternalConfiguration());
                environment.updateAppExternalConfigMap(configCenter.getAppExternalConfiguration());

                // 从远程配置中心获取配置
                compositeDynamicConfiguration.addConfiguration(prepareEnvironment(configCenter));
            }
            environment.setDynamicConfiguration(compositeDynamicConfiguration);
        }
    }

    /**
     * 启动元数据中心
     * 加载元数据配置并初始化元数据报告实例
     */
    private void startMetadataCenter() {

        // 如有必要，使用注册中心作为元数据中心
        useRegistryAsMetadataCenterIfNecessary();

        // 获取应用配置
        ApplicationConfig applicationConfig = getApplicationOrElseThrow();

        // 获取元数据存储类型
        String metadataType = applicationConfig.getMetadataType();
        // 获取元数据配置集合
        Collection<MetadataReportConfig> metadataReportConfigs = configManager.getMetadataConfigs();
        // 如果没有元数据配置
        if (CollectionUtils.isEmpty(metadataReportConfigs)) {
            // 如果元数据类型为远程存储但没有配置元数据中心，则抛出异常
            if (REMOTE_METADATA_STORAGE_TYPE.equals(metadataType)) {
                throw new IllegalStateException(
                        "No MetadataConfig found, Metadata Center address is required when 'metadata=remote' is enabled.");
            }
            return;
        }

        // 获取元数据报告实例并初始化
        MetadataReportInstance metadataReportInstance =
                applicationModel.getBeanFactory().getBean(MetadataReportInstance.class);
        List<MetadataReportConfig> validMetadataReportConfigs = new ArrayList<>(metadataReportConfigs.size());
        for (MetadataReportConfig metadataReportConfig : metadataReportConfigs) {
            // 验证元数据配置是否有效
            if (ConfigValidationUtils.isValidMetadataConfig(metadataReportConfig)) {
                ConfigValidationUtils.validateMetadataConfig(metadataReportConfig);
                validMetadataReportConfigs.add(metadataReportConfig);
            }
        }
        // 初始化元数据报告实例
        metadataReportInstance.init(validMetadataReportConfigs);
        // 如果元数据报告实例未初始化成功，则抛出异常
        if (!metadataReportInstance.isInitialized()) {
            throw new IllegalStateException(String.format(
                    "%s MetadataConfigs found, but none of them is valid.", metadataReportConfigs.size()));
        }
    }

    /**
     * 如有必要，使用注册中心作为配置中心
     * 为了兼容性目的，当没有显式指定配置中心且注册中心的useAsConfigCenter为null或true时，
     * 使用注册中心作为默认配置中心
     */
    private void useRegistryAsConfigCenterIfNecessary() {
        // 使用DynamicConfiguration的加载状态来判断配置中心是否已初始化
        if (environment.getDynamicConfiguration().isPresent()) {
            return;
        }

        // 如果已存在配置中心配置，则直接返回
        if (CollectionUtils.isNotEmpty(configManager.getConfigCenters())) {
            return;
        }

        // 加载注册中心配置
        configManager.loadConfigsOfTypeFromProps(RegistryConfig.class);

        // 获取默认注册中心配置
        List<RegistryConfig> defaultRegistries = configManager.getDefaultRegistries();
        if (!defaultRegistries.isEmpty()) {
            defaultRegistries.stream()
                    .filter(this::isUsedRegistryAsConfigCenter)
                    .map(this::registryAsConfigCenter)
                    .forEach(configCenter -> {
                        if (configManager.getConfigCenter(configCenter.getId()).isPresent()) {
                            return;
                        }
                        configManager.addConfigCenter(configCenter);
                        logger.info("use registry as config-center: " + configCenter);
                    });
        }
    }

    private void initMetricsService() {
        this.metricsServiceExporter =
                getExtensionLoader(MetricsServiceExporter.class).getDefaultExtension();
        metricsServiceExporter.init();
    }

    /**
     * 初始化指标报告器
     * 初始化应用的指标报告器，根据配置决定使用哪种协议的报告器
     */
    private void initMetricsReporter() {
        if (!MetricsSupportUtil.isSupportMetrics()) {
            return;
        }
        DefaultMetricsCollector collector = applicationModel.getBeanFactory().getBean(DefaultMetricsCollector.class);
        Optional<MetricsConfig> configOptional = configManager.getMetrics();
        // If no specific metrics type is configured and there is no Prometheus dependency in the dependencies.
        MetricsConfig metricsConfig = configOptional.orElse(new MetricsConfig(applicationModel));
        if (PROTOCOL_PROMETHEUS.equals(metricsConfig.getProtocol()) && !MetricsSupportUtil.isSupportPrometheus()) {
            return;
        }
        if (StringUtils.isBlank(metricsConfig.getProtocol())) {
            metricsConfig.setProtocol(
                    MetricsSupportUtil.isSupportPrometheus() ? PROTOCOL_PROMETHEUS : PROTOCOL_DEFAULT);
        }
        collector.setCollectEnabled(true);
        collector.collectApplication();
        collector.setThreadpoolCollectEnabled(
                Optional.ofNullable(metricsConfig.getEnableThreadpool()).orElse(true));
        collector.setMetricsInitEnabled(
                Optional.ofNullable(metricsConfig.getEnableMetricsInit()).orElse(true));
        MetricsReporterFactory metricsReporterFactory =
                getExtensionLoader(MetricsReporterFactory.class).getAdaptiveExtension();
        MetricsReporter metricsReporter = null;
        try {
            metricsReporter = metricsReporterFactory.createMetricsReporter(metricsConfig.toUrl());
        } catch (IllegalStateException e) {
            if (e.getMessage().startsWith("No such extension org.apache.dubbo.metrics.report.MetricsReporterFactory")) {
                logger.warn(COMMON_METRICS_COLLECTOR_EXCEPTION, "", "", e.getMessage());
                return;
            } else {
                throw e;
            }
        }
        metricsReporter.init();
        applicationModel.getBeanFactory().registerBean(metricsReporter);
        // If the protocol is not the default protocol, the default protocol is also initialized.
        if (!PROTOCOL_DEFAULT.equals(metricsConfig.getProtocol())) {
            DefaultMetricsReporterFactory defaultMetricsReporterFactory =
                    new DefaultMetricsReporterFactory(applicationModel);
            MetricsReporter defaultMetricsReporter =
                    defaultMetricsReporterFactory.createMetricsReporter(metricsConfig.toUrl());
            defaultMetricsReporter.init();
            applicationModel.getBeanFactory().registerBean(defaultMetricsReporter);
        }
    }

    /**
     * 初始化观测注册表(Micrometer)
     * 初始化Dubbo的观测注册表，用于支持Micrometer观测功能
     */
    private void initObservationRegistry() {
        if (!ObservationSupportUtil.isSupportObservation()) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Not found micrometer-observation or plz check the version of micrometer-observation version if already introduced, need > 1.10.0");
            }
            return;
        }
        if (!ObservationSupportUtil.isSupportTracing()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Not found micrometer-tracing dependency, skip init ObservationRegistry.");
            }
            return;
        }
        Optional<TracingConfig> configOptional = configManager.getTracing();
        if (!configOptional.isPresent() || !configOptional.get().getEnabled()) {
            return;
        }

        DubboObservationRegistry dubboObservationRegistry =
                new DubboObservationRegistry(applicationModel, configOptional.get());
        dubboObservationRegistry.initObservationRegistry();
    }

    private boolean isUsedRegistryAsConfigCenter(RegistryConfig registryConfig) {
        return isUsedRegistryAsCenter(
                registryConfig, registryConfig::getUseAsConfigCenter, "config", DynamicConfigurationFactory.class);
    }

    /**
     * 将注册中心配置转换为配置中心配置
     *
     * @param registryConfig 注册中心配置
     * @return 配置中心配置
     */
    private ConfigCenterConfig registryAsConfigCenter(RegistryConfig registryConfig) {
        String protocol = registryConfig.getProtocol();
        Integer port = registryConfig.getPort();
        URL url = URL.valueOf(registryConfig.getAddress(), registryConfig.getScopeModel());
        String id = "config-center-" + protocol + "-" + url.getHost() + "-" + port;
        ConfigCenterConfig cc = new ConfigCenterConfig();
        cc.setId(id);
        cc.setScopeModel(applicationModel);
        if (cc.getParameters() == null) {
            cc.setParameters(new HashMap<>());
        }
        if (CollectionUtils.isNotEmptyMap(registryConfig.getParameters())) {
            cc.getParameters().putAll(registryConfig.getParameters()); // copy the parameters
        }
        cc.getParameters().put(CLIENT_KEY, registryConfig.getClient());
        cc.setProtocol(protocol);
        cc.setPort(port);
        if (StringUtils.isNotEmpty(registryConfig.getGroup())) {
            cc.setGroup(registryConfig.getGroup());
        }
        cc.setAddress(getRegistryCompatibleAddress(registryConfig));
        cc.setNamespace(registryConfig.getGroup());
        cc.setUsername(registryConfig.getUsername());
        cc.setPassword(registryConfig.getPassword());
        if (registryConfig.getTimeout() != null) {
            cc.setTimeout(registryConfig.getTimeout().longValue());
        }
        cc.setHighestPriority(false);
        return cc;
    }

    /**
     * 如有必要，使用注册中心作为元数据中心
     * 当元数据配置中没有指定地址时，使用注册中心作为元数据中心
     */
    private void useRegistryAsMetadataCenterIfNecessary() {

        // 获取原始元数据配置集合
        Collection<MetadataReportConfig> originMetadataConfigs = configManager.getMetadataConfigs();
        // 如果已有元数据配置指定了地址，则直接返回
        if (originMetadataConfigs.stream().anyMatch(m -> Objects.nonNull(m.getAddress()))) {
            return;
        }

        // 获取需要覆盖的元数据配置（地址为空的配置）
        Collection<MetadataReportConfig> metadataConfigsToOverride = originMetadataConfigs.stream()
                .filter(m -> Objects.isNull(m.getAddress()))
                .collect(Collectors.toList());

        // 如果需要覆盖的配置超过1个，则直接返回
        if (metadataConfigsToOverride.size() > 1) {
            return;
        }

        // 获取需要覆盖的元数据配置对象
        MetadataReportConfig metadataConfigToOverride =
                metadataConfigsToOverride.stream().findFirst().orElse(null);

        // 获取默认注册中心配置
        List<RegistryConfig> defaultRegistries = configManager.getDefaultRegistries();
        if (!defaultRegistries.isEmpty()) {
            defaultRegistries.stream()
                    .filter(this::isUsedRegistryAsMetadataCenter)
                    .map(registryConfig -> registryAsMetadataCenter(registryConfig, metadataConfigToOverride))
                    .forEach(metadataReportConfig ->
                            overrideMetadataReportConfig(metadataConfigToOverride, metadataReportConfig));
        }
    }

    private void overrideMetadataReportConfig(
            MetadataReportConfig metadataConfigToOverride, MetadataReportConfig metadataReportConfig) {
        if (metadataReportConfig.getId() == null) {
            Collection<MetadataReportConfig> metadataReportConfigs = configManager.getMetadataConfigs();
            if (CollectionUtils.isNotEmpty(metadataReportConfigs)) {
                for (MetadataReportConfig existedConfig : metadataReportConfigs) {
                    if (existedConfig.getId() == null
                            && existedConfig.getAddress().equals(metadataReportConfig.getAddress())) {
                        return;
                    }
                }
            }
            configManager.removeConfig(metadataConfigToOverride);
            configManager.addMetadataReport(metadataReportConfig);
        } else {
            Optional<MetadataReportConfig> configOptional =
                    configManager.getConfig(MetadataReportConfig.class, metadataReportConfig.getId());
            if (configOptional.isPresent()) {
                return;
            }
            configManager.removeConfig(metadataConfigToOverride);
            configManager.addMetadataReport(metadataReportConfig);
        }
        logger.info("use registry as metadata-center: " + metadataReportConfig);
    }

    /**
     * 检查是否使用注册中心作为元数据中心
     *
     * @param registryConfig 注册中心配置
     * @return 如果使用注册中心作为元数据中心返回true，否则返回false
     */
    private boolean isUsedRegistryAsMetadataCenter(RegistryConfig registryConfig) {
        return isUsedRegistryAsCenter(
                registryConfig, registryConfig::getUseAsMetadataCenter, "metadata", MetadataReportFactory.class);
    }

    /**
     * 检查是否使用注册中心作为中心基础设施
     *
     * @param registryConfig       注册中心配置
     * @param usedRegistryAsCenter 配置值
     * @param centerType           中心类型名称
     * @param extensionClass       中心基础设施的扩展类
     * @return 如果使用返回true，否则返回false
     * @since 2.7.8
     */
    private boolean isUsedRegistryAsCenter(
            RegistryConfig registryConfig,
            Supplier<Boolean> usedRegistryAsCenter,
            String centerType,
            Class<?> extensionClass) {
        final boolean supported;

        Boolean configuredValue = usedRegistryAsCenter.get();
        if (configuredValue != null) { // If configured, take its value.
            supported = configuredValue.booleanValue();
        } else { // Or check the extension existence
            String protocol = registryConfig.getProtocol();
            supported = supportsExtension(extensionClass, protocol);
            if (logger.isInfoEnabled()) {
                logger.info(format(
                        "No value is configured in the registry, the %s extension[name : %s] %s as the %s center",
                        extensionClass.getSimpleName(),
                        protocol,
                        supported ? "supports" : "does not support",
                        centerType));
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info(format(
                    "The registry[%s] will be %s as the %s center",
                    registryConfig, supported ? "used" : "not used", centerType));
        }
        return supported;
    }

    /**
     * 检查是否支持指定类和名称的扩展
     *
     * @param extensionClass 扩展类
     * @param name           扩展名称
     * @return 如果支持返回true，否则返回false
     * @since 2.7.8
     */
    private boolean supportsExtension(Class<?> extensionClass, String name) {
        if (isNotEmpty(name)) {
            ExtensionLoader<?> extensionLoader = getExtensionLoader(extensionClass);
            return extensionLoader.hasExtension(name);
        }
        return false;
    }

    /**
     * 将注册中心配置转换为元数据中心配置
     *
     * @param registryConfig              注册中心配置
     * @param originMetadataReportConfig 原始元数据报告配置
     * @return 元数据报告配置
     */
    private MetadataReportConfig registryAsMetadataCenter(
            RegistryConfig registryConfig, MetadataReportConfig originMetadataReportConfig) {
        MetadataReportConfig metadataReportConfig = originMetadataReportConfig == null
                ? new MetadataReportConfig(registryConfig.getApplicationModel())
                : originMetadataReportConfig;
        if (metadataReportConfig.getId() == null) {
            metadataReportConfig.setId(registryConfig.getId());
        }
        metadataReportConfig.setScopeModel(applicationModel);
        if (metadataReportConfig.getParameters() == null) {
            metadataReportConfig.setParameters(new HashMap<>());
        }
        if (CollectionUtils.isNotEmptyMap(registryConfig.getParameters())) {
            for (Map.Entry<String, String> entry :
                    registryConfig.getParameters().entrySet()) {
                metadataReportConfig
                        .getParameters()
                        .putIfAbsent(entry.getKey(), entry.getValue()); // copy the parameters
            }
        }
        metadataReportConfig.getParameters().put(CLIENT_KEY, registryConfig.getClient());
        if (metadataReportConfig.getGroup() == null) {
            metadataReportConfig.setGroup(registryConfig.getGroup());
        }
        if (metadataReportConfig.getAddress() == null) {
            metadataReportConfig.setAddress(getRegistryCompatibleAddress(registryConfig));
        }
        if (metadataReportConfig.getUsername() == null) {
            metadataReportConfig.setUsername(registryConfig.getUsername());
        }
        if (metadataReportConfig.getPassword() == null) {
            metadataReportConfig.setPassword(registryConfig.getPassword());
        }
        if (metadataReportConfig.getTimeout() == null) {
            metadataReportConfig.setTimeout(registryConfig.getTimeout());
        }
        return metadataReportConfig;
    }

    /**
     * 获取与注册中心兼容的地址
     *
     * @param registryConfig 注册中心配置
     * @return 兼容的地址
     */
    private String getRegistryCompatibleAddress(RegistryConfig registryConfig) {
        String registryAddress = registryConfig.getAddress();
        String[] addresses = REGISTRY_SPLIT_PATTERN.split(registryAddress);
        if (ArrayUtils.isEmpty(addresses)) {
            throw new IllegalStateException("Invalid registry address found.");
        }
        String address = addresses[0];
        // since 2.7.8
        // Issue : https://github.com/apache/dubbo/issues/6476
        StringBuilder metadataAddressBuilder = new StringBuilder();
        URL url = URL.valueOf(address, registryConfig.getScopeModel());
        String protocolFromAddress = url.getProtocol();
        if (isEmpty(protocolFromAddress)) {
            // If the protocol from address is missing, is like :
            // "dubbo.registry.address = 127.0.0.1:2181"
            String protocolFromConfig = registryConfig.getProtocol();
            metadataAddressBuilder.append(protocolFromConfig).append("://");
        }
        metadataAddressBuilder.append(address);
        return metadataAddressBuilder.toString();
    }

    /**
     * 启动应用程序引导程序
     *
     * @return 启动结果的Future对象
     */
    @Override
    public Future start() {
        // 使用启动锁同步，确保同一时间只有一个线程可以执行启动逻辑
        synchronized (startLock) {
            // 检查应用状态，如果正在停止、已停止或启动失败，则不允许重新启动
            if (isStopping() || isStopped() || isFailed()) {
                throw new IllegalStateException(getIdentifier() + " is stopping or stopped, can not start again");
            }

            try {
                // 检查是否有待处理的模块（新添加但尚未启动的模块）
                boolean hasPendingModule = hasPendingModule();

                // 如果应用正在启动中
                if (isStarting()) {
                    // 当前正在启动，可能是由模块或应用程序同时触发的启动
                    // 如果有新的待处理模块，则启动这些模块
                    if (hasPendingModule) {
                        startModules();
                    }
                    // 如果已经在启动过程中，复用之前的startFuture对象
                    return startFuture;
                }

                // 如果应用已经启动完成且没有新的模块需要处理，则直接返回完成的Future
                if ((isStarted() || isCompletion()) && !hasPendingModule) {
                    return CompletableFuture.completedFuture(false);
                }

                // 状态转换：pending -> starting（首次启动）或 started -> starting（重新启动）
                onStarting();

                // 初始化应用配置和相关组件
                initialize();

                // 执行具体的启动逻辑
                doStart();
            } catch (Throwable e) {
                // 启动失败时记录错误并抛出异常
                onFailed(getIdentifier() + " start failure", e);
                throw e;
            }

            // 返回启动结果的Future对象
            return startFuture;
        }
    }

    /**
     * 检查是否存在待处理的模块
     * 待处理的模块是指那些尚未启动的模块
     *
     * @return 如果存在待处理模块返回true，否则返回false
     */
    private boolean hasPendingModule() {
        boolean found = false;
        // 遍历所有模块模型，检查是否有模块处于待处理状态
        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
            if (moduleModel.getDeployer().isPending()) {
                found = true;
                break;
            }
        }
        return found;
    }

    @Override
    public Future getStartFuture() {
        return startFuture;
    }

    /**
     * 执行实际的启动逻辑
     * 主要包括启动模块和准备应用实例
     */
    private void doStart() {
        // 启动所有模块
        startModules();

        // 准备应用实例
        //        prepareApplicationInstance();

        // 忽略启动后的新模块检查
        //        executorRepository.getSharedExecutor().submit(() -> {
        //            try {
        //                while (isStarting()) {
        //                    // 当任何模块状态改变时通知
        //                    synchronized (stateLock) {
        //                        try {
        //                            stateLock.wait(500);
        //                        } catch (InterruptedException e) {
        //                            // 忽略中断异常
        //                        }
        //                    }
        //
        //                    // 如果有新模块，则重新启动
        //                    if (hasPendingModule()) {
        //                        startModules();
        //                    }
        //                }
        //            } catch (Throwable e) {
        //                onFailed(getIdentifier() + " check start occurred an exception", e);
        //            }
        //        });
    }

    /**
     * 启动所有模块
     * 首先确保内部模块已初始化和启动，然后启动所有待处理的模块
     */
    private void startModules() {
        // 确保首先初始化和启动内部模块
        prepareInternalModule();

        // 过滤并启动待处理的模块，在启动过程中忽略新添加的模块，启动模块时抛出异常
        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
            if (moduleModel.getDeployer().isPending()) {
                moduleModel.getDeployer().start();
            }
        }
    }

    /**
     * 准备应用实例
     * 导出指标服务并在需要时注册本地服务实例
     *
     * @param moduleModel 模块模型
     */
    @Override
    public void prepareApplicationInstance(ModuleModel moduleModel) {
        if (hasPreparedApplicationInstance.get()) {
            return;
        }

        // export MetricsService
        exportMetricsService();

        if (moduleModel.getDeployer().hasRegistryInteraction()) {
            ApplicationConfig applicationConfig = configManager.getApplicationOrElseThrow();
            if (DEFAULT_APP_NAME.equals(applicationConfig.getName())) {
                throw new IllegalStateException("Application name must be set when registry is enabled.");
            }
        }

        if (isRegisterConsumerInstance() || moduleModel.getDeployer().hasRegistryInteraction()) {
            if (hasPreparedApplicationInstance.compareAndSet(false, true)) {
                // register the local ServiceInstance if required
                registerServiceInstance();
            }
        }
    }

    @Override
    public synchronized void exportMetadataService() {
        doExportMetadataService();
    }

    /**
     * 准备内部模块
     * 确保内部模块已初始化和启动
     */
    @Override
    public void prepareInternalModule() {
        // 如果内部模块已经准备好，直接返回
        if (hasPreparedInternalModule) {
            return;
        }
        // 使用内部模块锁进行同步
        synchronized (internalModuleLock) {
            // 双重检查，确保内部模块已经准备好
            if (hasPreparedInternalModule) {
                return;
            }

            // 获取内部模块的部署器
            ModuleDeployer internalModuleDeployer =
                    applicationModel.getInternalModule().getDeployer();
            // 如果内部模块未完成启动，则启动它
            if (!internalModuleDeployer.isCompletion()) {
                Future future = internalModuleDeployer.start();
                // 等待内部模块启动完成，最多等待5秒
                try {
                    future.get(5, TimeUnit.SECONDS);
                    hasPreparedInternalModule = true;
                } catch (Exception e) {
                    logger.warn(
                            CONFIG_FAILED_START_MODEL,
                            "",
                            "",
                            "wait for internal module startup failed: " + e.getMessage(),
                            e);
                }
            }
        }
    }

    private void exportMetricsService() {
        boolean exportMetrics = applicationModel
                .getApplicationConfigManager()
                .getMetrics()
                .map(MetricsConfig::getExportMetricsService)
                .orElse(true);
        if (exportMetrics) {
            try {
                metricsServiceExporter.export();
            } catch (Exception e) {
                logger.error(
                        LoggerCodeConstants.COMMON_METRICS_COLLECTOR_EXCEPTION,
                        "",
                        "",
                        "exportMetricsService an exception occurred when handle starting event",
                        e);
            }
        }
    }

    /**
     * 取消导出指标服务
     * 取消导出指标服务，释放相关资源
     */
    private void unexportMetricsService() {
        if (metricsServiceExporter != null) {
            try {
                metricsServiceExporter.unexport();
            } catch (Exception ignored) {
                // ignored
            }
        }
    }

    /**
     * 检查是否导出了服务
     * 检查所有模块中是否有配置了服务导出
     *
     * @return 如果导出了服务返回true，否则返回false
     */
    private boolean hasExportedServices() {
        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
            if (CollectionUtils.isNotEmpty(moduleModel.getConfigManager().getServices())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否在后台运行
     * 如果有任何模块在后台运行，则返回true
     *
     * @return 是否在后台运行
     */
    @Override
    public boolean isBackground() {
        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
            if (moduleModel.getDeployer().isBackground()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 准备环境配置
     * 从配置中心获取配置并更新环境变量
     *
     * @param configCenter 配置中心配置
     * @return 动态配置对象
     */
    private DynamicConfiguration prepareEnvironment(ConfigCenterConfig configCenter) {
        // 检查配置中心是否有效
        if (configCenter.isValid()) {
            // 检查并更新初始化状态
            if (!configCenter.checkOrUpdateInitialized(true)) {
                return null;
            }

            DynamicConfiguration dynamicConfiguration;
            try {
                // 获取动态配置对象
                dynamicConfiguration = getDynamicConfiguration(configCenter.toUrl());
            } catch (Exception e) {
                // 如果配置中心检查不通过，则记录警告日志
                if (!configCenter.isCheck()) {
                    logger.warn(
                            CONFIG_FAILED_INIT_CONFIG_CENTER,
                            "",
                            "",
                            "The configuration center failed to initialize",
                            e);
                    configCenter.setInitialized(false);
                    return null;
                } else {
                    throw new IllegalStateException(e);
                }
            }

            // 获取应用模型
            ApplicationModel applicationModel = getApplicationModel();

            // 如果配置文件不为空，则获取全局远程配置
            if (StringUtils.isNotEmpty(configCenter.getConfigFile())) {
                String configContent =
                        dynamicConfiguration.getProperties(configCenter.getConfigFile(), configCenter.getGroup());
                // 如果配置内容不为空，则记录日志
                if (StringUtils.isNotEmpty(configContent)) {
                    logger.info(String.format(
                            "Got global remote configuration from config center with key-%s and group-%s: \n %s",
                            configCenter.getConfigFile(), configCenter.getGroup(), configContent));
                }

                String appGroup = "";
                String appConfigContent = null;
                String appConfigFile = null;
                // 获取应用配置
                Optional<ApplicationConfig> applicationOptional = getApplication();
                if (applicationOptional.isPresent()) {
                    appGroup = applicationOptional.get().getName();
                    // 如果应用组不为空，则获取应用特定的远程配置
                    if (isNotEmpty(appGroup)) {
                        appConfigFile = isNotEmpty(configCenter.getAppConfigFile())
                                ? configCenter.getAppConfigFile()
                                : configCenter.getConfigFile();
                        appConfigContent = dynamicConfiguration.getProperties(appConfigFile, appGroup);
                        // 如果应用配置内容不为空，则记录日志
                        if (StringUtils.isNotEmpty(appConfigContent)) {
                            logger.info(String.format(
                                    "Got application specific remote configuration from config center with key %s and group %s: \n %s",
                                    appConfigFile, appGroup, appConfigContent));
                        }
                    }
                }

                try {
                    // 解析配置属性
                    Map<String, String> configMap = parseProperties(configContent);
                    Map<String, String> appConfigMap = parseProperties(appConfigContent);

                    // 更新环境配置映射
                    environment.updateExternalConfigMap(configMap);
                    environment.updateAppExternalConfigMap(appConfigMap);

                    // 添加指标事件
                    MetricsEventBus.publish(ConfigCenterEvent.toChangeEvent(
                            applicationModel,
                            configCenter.getConfigFile(),
                            configCenter.getGroup(),
                            configCenter.getProtocol(),
                            ConfigChangeType.ADDED.name(),
                            configMap.size()));
                    if (isNotEmpty(appGroup)) {
                        MetricsEventBus.publish(ConfigCenterEvent.toChangeEvent(
                                applicationModel,
                                appConfigFile,
                                appGroup,
                                configCenter.getProtocol(),
                                ConfigChangeType.ADDED.name(),
                                appConfigMap.size()));
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to parse configurations from Config Center.", e);
                }
            }
            return dynamicConfiguration;
        }
        return null;
    }

    /**
     * Get the instance of {@link DynamicConfiguration} by the specified connection {@link URL} of config-center
     *
     * @param connectionURL of config-center
     * @return non-null
     * @since 2.7.5
     */
    private DynamicConfiguration getDynamicConfiguration(URL connectionURL) {
        String protocol = connectionURL.getProtocol();

        DynamicConfigurationFactory factory =
                ConfigurationUtils.getDynamicConfigurationFactory(applicationModel, protocol);
        return factory.getDynamicConfiguration(connectionURL);
    }

    private volatile boolean registered = false;

    private final AtomicInteger instanceRefreshScheduleTimes = new AtomicInteger(0);

    /**
     * 用于指示有多少线程正在更新服务
     */
    private final AtomicInteger serviceRefreshState = new AtomicInteger(0);

    /**
     * 注册服务实例
     * 包括注册元数据和实例信息，并启动定时任务定期刷新元数据和实例信息
     */
    public synchronized void registerServiceInstance() {
        if (!registered) {
            try {
                registered = true;

                ServiceInstanceMetadataUtils.registerMetadataAndInstance(applicationModel);

            } catch (Exception e) {
                logger.error(
                        CONFIG_REGISTER_INSTANCE_ERROR,
                        "configuration server disconnected",
                        "",
                        "Register instance error.",
                        e);
            }
            // scheduled task for updating Metadata and ServiceInstance
            asyncMetadataFuture = frameworkExecutorRepository
                    .getSharedScheduledExecutor()
                    .scheduleWithFixedDelay(
                            () -> {

                                // ignore refresh metadata on stopping
                                if (applicationModel.isDestroyed()) {
                                    return;
                                }

                                // refresh for 30 times (default for 30s) when deployer is not started, prevent submit
                                // too many revision
                                if (instanceRefreshScheduleTimes.incrementAndGet() % 30 != 0 && !isCompletion()) {
                                    return;
                                }

                                // refresh for 5 times (default for 5s) when services are being updated by other
                                // threads, prevent submit too many revision
                                // note: should not always wait here
                                if (serviceRefreshState.get() != 0 && instanceRefreshScheduleTimes.get() % 5 != 0) {
                                    return;
                                }

                                try {
                                    if (!applicationModel.isDestroyed() && registered) {
                                        ServiceInstanceMetadataUtils.refreshMetadataAndInstance(applicationModel);
                                    }
                                } catch (Exception e) {
                                    if (!applicationModel.isDestroyed()) {
                                        logger.error(
                                                CONFIG_REFRESH_INSTANCE_ERROR,
                                                "",
                                                "",
                                                "Refresh instance and metadata error.",
                                                e);
                                    }
                                }
                            },
                            0,
                            ConfigurationUtils.get(
                                    applicationModel, METADATA_PUBLISH_DELAY_KEY, DEFAULT_METADATA_PUBLISH_DELAY),
                            TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 刷新服务实例
     * 刷新实例和元数据信息
     */
    @Override
    public void refreshServiceInstance() {
        if (registered) {
            try {
                ServiceInstanceMetadataUtils.refreshMetadataAndInstance(applicationModel);
            } catch (Exception e) {
                logger.error(CONFIG_REFRESH_INSTANCE_ERROR, "", "", "Refresh instance and metadata error.", e);
            }
        }
    }

    /**
     * 增加服务刷新计数
     * 增加正在刷新服务的线程计数
     */
    @Override
    public void increaseServiceRefreshCount() {
        serviceRefreshState.incrementAndGet();
    }

    /**
     * 减少服务刷新计数
     * 减少正在刷新服务的线程计数
     */
    @Override
    public void decreaseServiceRefreshCount() {
        serviceRefreshState.decrementAndGet();
    }

    /**
     * 注销服务实例
     * 从注册中心注销元数据和实例信息
     */
    private void unregisterServiceInstance() {
        if (registered) {
            ServiceInstanceMetadataUtils.unregisterMetadataAndInstance(applicationModel);
        }
    }

    @Override
    public void stop() {
        applicationModel.destroy();
    }

    /**
     * 在销毁前执行清理操作
     * 包括离线处理、注销服务实例、取消注册指标服务、注销关闭钩子等
     */
    @Override
    public void preDestroy() {
        synchronized (destroyLock) {
            if (isStopping() || isStopped()) {
                return;
            }
            onStopping();

            offline();

            unregisterServiceInstance();

            unexportMetricsService();

            unRegisterShutdownHook();
            if (asyncMetadataFuture != null) {
                asyncMetadataFuture.cancel(true);
            }
        }
    }

    /**
     * 执行离线操作
     * 遍历所有模块的服务仓库，将已导出的服务从注册中心注销
     */
    private void offline() {
        try {
            for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
                ModuleServiceRepository serviceRepository = moduleModel.getServiceRepository();
                List<ProviderModel> exportedServices = serviceRepository.getExportedServices();
                for (ProviderModel exportedService : exportedServices) {
                    List<ProviderModel.RegisterStatedURL> statedUrls = exportedService.getStatedUrl();
                    for (ProviderModel.RegisterStatedURL statedURL : statedUrls) {
                        if (statedURL.isRegistered()) {
                            doOffline(statedURL);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error(
                    LoggerCodeConstants.INTERNAL_ERROR, "", "", "Exceptions occurred when unregister services.", t);
        }
    }

    /**
     * 执行单个服务的离线操作
     * 从注册中心注销指定的服务URL
     *
     * @param statedURL 已注册的服务URL信息
     */
    private void doOffline(ProviderModel.RegisterStatedURL statedURL) {
        RegistryFactory registryFactory = statedURL
                .getRegistryUrl()
                .getOrDefaultApplicationModel()
                .getExtensionLoader(RegistryFactory.class)
                .getAdaptiveExtension();
        Registry registry = registryFactory.getRegistry(statedURL.getRegistryUrl());
        registry.unregister(statedURL.getProviderUrl());
        statedURL.setRegistered(false);
    }

    @Override
    public void postDestroy() {
        synchronized (destroyLock) {
            // expect application model is destroyed before here
            if (isStopped()) {
                return;
            }
            try {
                destroyRegistries();
                destroyMetadataReports();

                executeShutdownCallbacks();

                // TODO should we close unused protocol server which only used by this application?
                // protocol server will be closed on all applications of same framework are stopped currently, but no
                // associate to application
                // see org.apache.dubbo.config.deploy.FrameworkModelCleaner#destroyProtocols
                // see
                // org.apache.dubbo.config.bootstrap.DubboBootstrapMultiInstanceTest#testMultiProviderApplicationStopOneByOne

                // destroy all executor services
                destroyExecutorRepository();

                onStopped();
            } catch (Throwable ex) {
                String msg = getIdentifier() + " an error occurred while stopping application: " + ex.getMessage();
                onFailed(msg, ex);
            }
        }
    }

    /**
     * 执行关闭回调函数
     * 调用所有注册的关闭钩子回调函数
     */
    private void executeShutdownCallbacks() {
        ShutdownHookCallbacks shutdownHookCallbacks =
                applicationModel.getBeanFactory().getBean(ShutdownHookCallbacks.class);
        shutdownHookCallbacks.callback();
    }

    /**
     * 通知模块状态变更
     * 当模块状态发生变化时调用此方法，用于同步状态锁并通知所有等待的线程
     *
     * @param moduleModel 模块模型
     * @param state       新的状态
     */
    @Override
    public void notifyModuleChanged(ModuleModel moduleModel, DeployState state) {
        checkState(moduleModel, state);

        // notify module state changed or module changed
        synchronized (stateLock) {
            stateLock.notifyAll();
        }
    }

    /**
     * 检查并更新应用状态
     * 根据模块的状态计算应用的整体状态，并执行相应的状态转换操作
     *
     * @param moduleModel  模块模型
     * @param moduleState  模块状态
     */
    @Override
    public void checkState(ModuleModel moduleModel, DeployState moduleState) {
        synchronized (stateLock) {
            if (!moduleModel.isInternal() && moduleState == DeployState.STARTED) {
                prepareApplicationInstance(moduleModel);
            }
            DeployState newState = calculateState();
            switch (newState) {
                case STARTED:
                    onStarted();
                    break;
                case COMPLETION:
                    onCompletion();
                    break;
                case STARTING:
                    onStarting();
                    break;
                case STOPPING:
                    onStopping();
                    break;
                case STOPPED:
                    onStopped();
                    break;
                case FAILED:
                    Throwable error = null;
                    ModuleModel errorModule = null;
                    for (ModuleModel module : applicationModel.getModuleModels()) {
                        ModuleDeployer deployer = module.getDeployer();
                        if (deployer.isFailed() && deployer.getError() != null) {
                            error = deployer.getError();
                            errorModule = module;
                            break;
                        }
                    }
                    onFailed(getIdentifier() + " found failed module: " + errorModule.getDesc(), error);
                    break;
                case PENDING:
                    // cannot change to pending from other state
                    // setPending();
                    break;
                default:
            }
        }
    }

    /**
     * 计算应用状态
     * 根据所有模块的状态统计信息来确定应用的整体状态
     *
     * @return 应用的部署状态
     */
    private DeployState calculateState() {
        int total = 0, pending = 0, starting = 0, started = 0, completion = 0, stopping = 0, stopped = 0, failed = 0;
        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
            ModuleDeployer deployer = moduleModel.getDeployer();
            if (deployer == null) {
                pending++;
            } else if (deployer.isPending()) {
                pending++;
            } else if (deployer.isStarting()) {
                starting++;
            } else if (deployer.isCompletion()) {
                completion++;
            } else if (deployer.isStarted()) {
                started++;
            } else if (deployer.isStopping()) {
                stopping++;
            } else if (deployer.isStopped()) {
                stopped++;
            } else if (deployer.isFailed()) {
                failed++;
            }
            total++;
        }
        // any module is failed
        if (failed > 0) {
            return DeployState.FAILED;
        }
        // all modules have not starting or started
        if (pending == total) {
            return DeployState.PENDING;
        }
        // all modules have completed
        if (completion == total) {
            return DeployState.COMPLETION;
        }
        // all modules are stopped
        if (stopped == total) {
            return DeployState.STOPPED;
        }
        // some module is starting or pending, it's in starting state
        if (starting > 0 || pending > 0) {
            return DeployState.STARTING;
        }
        // some module is stopping or stopped, it's in stopping state
        if (stopping > 0 || stopped > 0) {
            return DeployState.STOPPING;
        }
        // all modules have been started
        if (started > 0) {
            return DeployState.STARTED;
        }
        return DeployState.UNKNOWN;
    }

    /**
     * 处理初始化事件
     * 通知所有监听器应用正在初始化
     */
    private void onInitialize() {
        for (DeployListener<ApplicationModel> listener : listeners) {
            try {
                listener.onInitialize(applicationModel);
            } catch (Throwable e) {
                logger.error(
                        CONFIG_FAILED_START_MODEL,
                        "",
                        "",
                        getIdentifier() + " an exception occurred when handle initialize event",
                        e);
            }
        }
    }

    /**
     * 导出元数据服务
     * 在应用启动过程中导出元数据服务
     */
    private void doExportMetadataService() {
        if (!isStarting() && !isStarted() && !isCompletion()) {
            return;
        }
        for (DeployListener<ApplicationModel> listener : listeners) {
            try {
                if (listener instanceof ApplicationDeployListener) {
                    ((ApplicationDeployListener) listener).onModuleStarted(applicationModel);
                }
            } catch (Throwable e) {
                logger.error(
                        CONFIG_FAILED_START_MODEL,
                        "",
                        "",
                        getIdentifier() + " an exception occurred when handle starting event",
                        e);
            }
        }
    }

    /**
     * 处理启动中状态
     * 将应用状态从待处理(PENDING)或已启动(STARTED)转换为启动中(STARTING)
     */
    private void onStarting() {
        // pending -> starting
        // started -> starting
        // completion -> starting
        if (!(isPending() || isStarted() || isCompletion())) {
            return;
        }
        setStarting();
        startFuture = new CompletableFuture();
        if (logger.isInfoEnabled()) {
            logger.info(getIdentifier() + " is starting.");
        }
    }

    /**
     * 处理已启动状态
     * 将应用状态从启动中(STARTING)转换为已启动(STARTED)，并启动指标收集器
     */
    private void onStarted() {
        // starting -> started
        if (!isStarting()) {
            return;
        }
        setStarted();
        startMetricsCollector();
        if (logger.isInfoEnabled()) {
            logger.info(getIdentifier() + " is ready.");
        }
        // refresh metadata
        try {
            if (registered) {
                ServiceInstanceMetadataUtils.refreshMetadataAndInstance(applicationModel);
            }
        } catch (Exception e) {
            logger.error(CONFIG_REFRESH_INSTANCE_ERROR, "", "", "Refresh instance and metadata error.", e);
        }
    }

    /**
     * 处理完成状态
     * 将应用状态从已启动(STARTED)转换为已完成(COMPLETION)
     */
    private void onCompletion() {
        try {
            // started -> completion
            if (!isStarted()) {
                return;
            }
            setCompletion();
            if (logger.isInfoEnabled()) {
                logger.info(getIdentifier() + " has completed.");
            }
        } finally {
            // complete future
            completeStartFuture(true);
        }
    }

    /**
     * 启动指标收集器
     * 如果启用了线程池指标收集，则注册默认采样器
     */
    private void startMetricsCollector() {
        DefaultMetricsCollector collector = applicationModel.getBeanFactory().getBean(DefaultMetricsCollector.class);
        if (Objects.nonNull(collector) && collector.isThreadpoolCollectEnabled()) {
            collector.registryDefaultSample();
        }
    }

    /**
     * 完成启动Future
     * 根据启动结果完成启动Future对象
     *
     * @param success 启动是否成功
     */
    private void completeStartFuture(boolean success) {
        if (startFuture != null) {
            startFuture.complete(success);
        }
    }

    /**
     * 处理停止中状态
     * 将应用状态转换为停止中(STOPPING)
     */
    private void onStopping() {
        try {
            if (isStopping() || isStopped()) {
                return;
            }
            setStopping();
            if (logger.isInfoEnabled()) {
                logger.info(getIdentifier() + " is stopping.");
            }
        } finally {
            completeStartFuture(false);
        }
    }

    /**
     * 处理已停止状态
     * 将应用状态转换为已停止(STOPPED)
     */
    private void onStopped() {
        try {
            if (isStopped()) {
                return;
            }
            setStopped();
            if (logger.isInfoEnabled()) {
                logger.info(getIdentifier() + " has stopped.");
            }
        } finally {
            completeStartFuture(false);
        }
    }

    /**
     * 处理失败状态
     * 将应用状态转换为失败(FAILED)，并记录错误日志
     *
     * @param msg 错误消息
     * @param ex  异常对象
     */
    private void onFailed(String msg, Throwable ex) {
        try {
            setFailed(ex);
            logger.error(CONFIG_FAILED_START_MODEL, "", "", msg, ex);
        } finally {
            completeStartFuture(false);
        }
    }

    /**
     * 销毁执行器仓库
     * 关闭服务导出和引用的执行器，并销毁所有执行器
     */
    private void destroyExecutorRepository() {
        // shutdown export/refer executor
        executorRepository.shutdownServiceExportExecutor();
        executorRepository.shutdownServiceReferExecutor();
        ExecutorRepository.getInstance(applicationModel).destroyAll();
    }

    /**
     * 销毁注册中心
     * 销毁应用模型中的所有注册中心实例
     */
    private void destroyRegistries() {
        RegistryManager.getInstance(applicationModel).destroyAll();
    }

    /**
     * 销毁服务发现实例
     * 销毁所有服务发现实例
     */
    private void destroyServiceDiscoveries() {
        RegistryManager.getInstance(applicationModel).getServiceDiscoveries().forEach(serviceDiscovery -> {
            try {
                serviceDiscovery.destroy();
            } catch (Throwable ignored) {
                logger.warn(CONFIG_FAILED_EXECUTE_DESTROY, "", "", ignored.getMessage(), ignored);
            }
        });
        if (logger.isDebugEnabled()) {
            logger.debug(getIdentifier() + "'s all ServiceDiscoveries have been destroyed.");
        }
    }

    /**
     * 销毁元数据报告实例
     * 销毁应用的元数据报告工厂
     */
    private void destroyMetadataReports() {
        // only destroy MetadataReport of this application
        List<MetadataReportFactory> metadataReportFactories =
                getExtensionLoader(MetadataReportFactory.class).getLoadedExtensionInstances();
        for (MetadataReportFactory metadataReportFactory : metadataReportFactories) {
            metadataReportFactory.destroy();
        }
    }

    /**
     * 获取应用配置或抛出异常
     * 如果应用配置不存在则抛出IllegalStateException异常
     *
     * @return 应用配置对象
     */
    private ApplicationConfig getApplicationOrElseThrow() {
        return configManager.getApplicationOrElseThrow();
    }

    /**
     * 获取应用配置（可选）
     *
     * @return 应用配置对象的Optional包装
     */
    private Optional<ApplicationConfig> getApplication() {
        return configManager.getApplication();
    }
}
