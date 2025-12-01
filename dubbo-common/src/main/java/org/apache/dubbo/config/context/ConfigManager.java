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
package org.apache.dubbo.config.context;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.context.ApplicationExt;
import org.apache.dubbo.common.extension.DisableInject;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.ConfigKeys;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.config.TracingConfig;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.apache.dubbo.config.AbstractConfig.getTagName;

/**
 * 应用级配置管理器
 * 
 * 管理Dubbo应用级别的所有配置对象，包括应用配置、协议配置、注册中心配置、配置中心配置等。
 * 该类采用无锁设计（通过ConcurrentHashMap），以实现快速的读操作。
 * 写操作使用配置类型的子配置映射表进行加锁，以确保安全地检查和添加新配置。
 * 
 * <p>
 * <b>作用：</b>
 * <ul>
 * <li>统一管理应用级别的配置对象</li>
 * <li>提供配置的增删改查功能</li>
 * <li>支持配置的默认值和多实例管理</li>
 * <li>维护唯一配置类型的处理逻辑</li>
 * <li>作为ApplicationModel的扩展，集成到应用模型中</li>
 * </ul>
 * 
 * <p>
 * <b>管理的配置类型：</b>
 * <ul>
 * <li>ApplicationConfig - 应用配置（唯一）</li>
 * <li>MonitorConfig - 监控中心配置（唯一）</li>
 * <li>MetricsConfig - 指标配置（唯一）</li>
 * <li>TracingConfig - 链路追踪配置（唯一）</li>
 * <li>SslConfig - SSL配置（唯一）</li>
 * <li>ProtocolConfig - 协议配置（多实例）</li>
 * <li>RegistryConfig - 注册中心配置（多实例）</li>
 * <li>ConfigCenterConfig - 配置中心配置（多实例）</li>
 * <li>MetadataReportConfig - 元数据中心配置（多实例）</li>
 * </ul>
 * 
 * <p>
 * <b>使用场景：</b>
 * <ul>
 * <li>应用启动时初始化和管理配置</li>
 * <li>配置中心推送配置时更新管理的配置</li>
 * <li>在运行时动态获取和修改配置</li>
 * <li>Spring集成时统一管理Bean配置</li>
 * </ul>
 * 
 * <p>
 * <b>线程安全：</b>
 * <ul>
 * <li>读操作无锁，通过ConcurrentHashMap实现快速访问</li>
 * <li>写操作使用配置类型级别的锁，确保安全性</li>
 * <li>支持并发访问和修改</li>
 * </ul>
 * 
 * A lock-free config manager (through ConcurrentHashMap), for fast read operation.
 * The Write operation lock with sub configs map of config type, for safely check and add new config.
 * 
 * @see AbstractConfigManager
 * @see ApplicationExt
 * @see ApplicationModel
 */
public class ConfigManager extends AbstractConfigManager implements ApplicationExt {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    /** 配置管理器名称 */
    public static final String NAME = "config";
    /** Bean名称 */
    public static final String BEAN_NAME = "dubboConfigManager";
    /** 配置模式属性键 */
    public static final String DUBBO_CONFIG_MODE = ConfigKeys.DUBBO_CONFIG_MODE;

    /**
     * 构造函数
     * 创建应用级配置管理器
     * 
     * @param applicationModel 应用模型
     */
    public ConfigManager(ApplicationModel applicationModel) {
        super(
                applicationModel,
                Arrays.asList(
                        ApplicationConfig.class,
                        MonitorConfig.class,
                        MetricsConfig.class,
                        SslConfig.class,
                        ProtocolConfig.class,
                        RegistryConfig.class,
                        ConfigCenterConfig.class,
                        MetadataReportConfig.class,
                        TracingConfig.class));
    }

    public static ProtocolConfig getProtocolOrDefault(URL url) {
        return getProtocolOrDefault(url.getOrDefaultApplicationModel(), url.getProtocol());
    }

    public static ProtocolConfig getProtocolOrDefault(String idOrName) {
        return getProtocolOrDefault(ApplicationModel.defaultModel(), idOrName);
    }

    private static ProtocolConfig getProtocolOrDefault(ApplicationModel applicationModel, String idOrName) {
        return applicationModel.getApplicationConfigManager().getOrAddProtocol(idOrName);
    }

    // ApplicationConfig correlative methods

    /**
     * Set application config
     */
    @DisableInject
    public void setApplication(ApplicationConfig application) {
        addConfig(application);
    }

    public Optional<ApplicationConfig> getApplication() {
        return ofNullable(getSingleConfig(getTagName(ApplicationConfig.class)));
    }

    public ApplicationConfig getApplicationOrElseThrow() {
        return getApplication().orElseThrow(() -> new IllegalStateException("There's no ApplicationConfig specified."));
    }

    // MonitorConfig correlative methods

    @DisableInject
    public void setMonitor(MonitorConfig monitor) {
        addConfig(monitor);
    }

    public Optional<MonitorConfig> getMonitor() {
        return ofNullable(getSingleConfig(getTagName(MonitorConfig.class)));
    }

    @DisableInject
    public void setMetrics(MetricsConfig metrics) {
        addConfig(metrics);
    }

    public Optional<MetricsConfig> getMetrics() {
        return ofNullable(getSingleConfig(getTagName(MetricsConfig.class)));
    }

    @DisableInject
    public void setTracing(TracingConfig tracing) {
        addConfig(tracing);
    }

    public Optional<TracingConfig> getTracing() {
        return ofNullable(getSingleConfig(getTagName(TracingConfig.class)));
    }

    @DisableInject
    public void setSsl(SslConfig sslConfig) {
        addConfig(sslConfig);
    }

    public Optional<SslConfig> getSsl() {
        return ofNullable(getSingleConfig(getTagName(SslConfig.class)));
    }

    // ConfigCenterConfig correlative methods

    public void addConfigCenter(ConfigCenterConfig configCenter) {
        addConfig(configCenter);
    }

    public void addConfigCenters(Iterable<ConfigCenterConfig> configCenters) {
        configCenters.forEach(this::addConfigCenter);
    }

    public Optional<Collection<ConfigCenterConfig>> getDefaultConfigCenter() {
        Collection<ConfigCenterConfig> defaults =
                getDefaultConfigs(getConfigsMap(getTagName(ConfigCenterConfig.class)));
        if (CollectionUtils.isEmpty(defaults)) {
            defaults = getConfigCenters();
        }
        return ofNullable(defaults);
    }

    public Optional<ConfigCenterConfig> getConfigCenter(String id) {
        return getConfig(ConfigCenterConfig.class, id);
    }

    public Collection<ConfigCenterConfig> getConfigCenters() {
        return getConfigs(getTagName(ConfigCenterConfig.class));
    }

    // MetadataReportConfig correlative methods

    public void addMetadataReport(MetadataReportConfig metadataReportConfig) {
        addConfig(metadataReportConfig);
    }

    public void addMetadataReports(Iterable<MetadataReportConfig> metadataReportConfigs) {
        metadataReportConfigs.forEach(this::addMetadataReport);
    }

    public Collection<MetadataReportConfig> getMetadataConfigs() {
        return getConfigs(getTagName(MetadataReportConfig.class));
    }

    public Collection<MetadataReportConfig> getDefaultMetadataConfigs() {
        Collection<MetadataReportConfig> defaults =
                getDefaultConfigs(getConfigsMap(getTagName(MetadataReportConfig.class)));
        if (CollectionUtils.isEmpty(defaults)) {
            return getMetadataConfigs();
        }
        return defaults;
    }

    // ProtocolConfig correlative methods

    public void addProtocol(ProtocolConfig protocolConfig) {
        addConfig(protocolConfig);
    }

    public void addProtocols(Iterable<ProtocolConfig> protocolConfigs) {
        if (protocolConfigs != null) {
            protocolConfigs.forEach(this::addProtocol);
        }
    }

    public Optional<ProtocolConfig> getProtocol(String idOrName) {
        return getConfig(ProtocolConfig.class, idOrName);
    }

    public ProtocolConfig getOrAddProtocol(String idOrName) {
        Optional<ProtocolConfig> protocol = getProtocol(idOrName);
        if (protocol.isPresent()) {
            return protocol.get();
        }

        // Avoiding default protocol configuration overriding custom protocol configuration
        // due to `getOrAddProtocol` being called when they are not loaded
        idOrName = idOrName + ".default";
        protocol = getProtocol(idOrName);
        if (protocol.isPresent()) {
            return protocol.get();
        }

        ProtocolConfig protocolConfig = addConfig(new ProtocolConfig(idOrName));

        // addProtocol triggers refresh when other protocols exist in the ConfigManager.
        // so refresh is only done when ProtocolConfig is not refreshed.
        if (!protocolConfig.isRefreshed()) {
            protocolConfig.refresh();
        }
        return protocolConfig;
    }

    public List<ProtocolConfig> getDefaultProtocols() {
        return getDefaultConfigs(ProtocolConfig.class);
    }

    @Override
    @SuppressWarnings("RedundantMethodOverride")
    public <C extends AbstractConfig> List<C> getDefaultConfigs(Class<C> cls) {
        return getDefaultConfigs(getConfigsMap(getTagName(cls)));
    }

    public Collection<ProtocolConfig> getProtocols() {
        return getConfigs(getTagName(ProtocolConfig.class));
    }

    // RegistryConfig correlative methods

    public void addRegistry(RegistryConfig registryConfig) {
        addConfig(registryConfig);
    }

    public void addRegistries(Iterable<RegistryConfig> registryConfigs) {
        if (registryConfigs != null) {
            registryConfigs.forEach(this::addRegistry);
        }
    }

    public Optional<RegistryConfig> getRegistry(String id) {
        return getConfig(RegistryConfig.class, id);
    }

    public List<RegistryConfig> getDefaultRegistries() {
        return getDefaultConfigs(getConfigsMap(getTagName(RegistryConfig.class)));
    }

    public Collection<RegistryConfig> getRegistries() {
        return getConfigs(getTagName(RegistryConfig.class));
    }

    @Override
    public void refreshAll() {
        // refresh all configs here
        getApplication().ifPresent(ApplicationConfig::refresh);
        getMonitor().ifPresent(MonitorConfig::refresh);
        getMetrics().ifPresent(MetricsConfig::refresh);
        getTracing().ifPresent(TracingConfig::refresh);
        getSsl().ifPresent(SslConfig::refresh);

        getProtocols().forEach(ProtocolConfig::refresh);
        getRegistries().forEach(RegistryConfig::refresh);
        getConfigCenters().forEach(ConfigCenterConfig::refresh);
        getMetadataConfigs().forEach(MetadataReportConfig::refresh);
    }

    @Override
    public void loadConfigs() {
        // application config has load before starting config center
        // load dubbo.applications.xxx
        loadConfigsOfTypeFromProps(ApplicationConfig.class);

        // load dubbo.monitors.xxx
        loadConfigsOfTypeFromProps(MonitorConfig.class);

        // load dubbo.metrics.xxx
        loadConfigsOfTypeFromProps(MetricsConfig.class);

        // load dubbo.tracing.xxx
        loadConfigsOfTypeFromProps(TracingConfig.class);

        // load multiple config types:
        // load dubbo.protocols.xxx
        loadConfigsOfTypeFromProps(ProtocolConfig.class);

        // load dubbo.registries.xxx
        loadConfigsOfTypeFromProps(RegistryConfig.class);

        // load dubbo.metadata-report.xxx
        loadConfigsOfTypeFromProps(MetadataReportConfig.class);

        // config centers has been loaded before starting config center
        // loadConfigsOfTypeFromProps(ConfigCenterConfig.class);

        refreshAll();

        checkConfigs();

        // set model name
        if (StringUtils.isBlank(applicationModel.getModelName())) {
            applicationModel.setModelName(applicationModel.getApplicationName());
        }
    }

    private void checkConfigs() {
        // check config types (ignore metadata-center)
        List<Class<? extends AbstractConfig>> multipleConfigTypes = Arrays.asList(
                ApplicationConfig.class,
                ProtocolConfig.class,
                RegistryConfig.class,
                MonitorConfig.class,
                MetricsConfig.class,
                TracingConfig.class,
                SslConfig.class);

        for (Class<? extends AbstractConfig> configType : multipleConfigTypes) {
            checkDefaultAndValidateConfigs(configType);
        }

        // check port conflicts
        Map<Integer, ProtocolConfig> protocolPortMap = new LinkedHashMap<>();
        for (ProtocolConfig protocol : getProtocols()) {
            Integer port = protocol.getPort();
            if (port == null || port == -1) {
                continue;
            }
            ProtocolConfig prevProtocol = protocolPortMap.get(port);
            if (prevProtocol != null) {
                throw new IllegalStateException("Duplicated port used by protocol configs, port: " + port
                        + ", configs: " + Arrays.asList(prevProtocol, protocol));
            }
            protocolPortMap.put(port, protocol);
        }

        // Log the current configurations.
        logger.info("The current configurations or effective configurations are as follows:");
        for (Class<? extends AbstractConfig> configType : multipleConfigTypes) {
            getConfigs(configType).forEach((config) -> logger.info(config.toString()));
        }
    }

    public ConfigMode getConfigMode() {
        return configMode;
    }
}
