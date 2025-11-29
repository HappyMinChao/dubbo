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
package org.apache.dubbo.common;

import org.apache.dubbo.common.beans.factory.ScopeBeanFactory;
import org.apache.dubbo.common.config.ConfigurationCache;
import org.apache.dubbo.common.convert.ConverterUtil;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.serialization.ClassHolder;
import org.apache.dubbo.common.ssl.CertManager;
import org.apache.dubbo.common.status.reporter.FrameworkStatusReportService;
import org.apache.dubbo.common.threadpool.manager.FrameworkExecutorRepository;
import org.apache.dubbo.common.utils.DefaultSerializeClassChecker;
import org.apache.dubbo.common.utils.SerializeSecurityConfigurator;
import org.apache.dubbo.common.utils.SerializeSecurityManager;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModelInitializer;

/**
 * 通用作用域模型初始化器
 * 用于初始化框架、应用和模块级别的模型组件
 */
public class CommonScopeModelInitializer implements ScopeModelInitializer {
    /**
     * 初始化框架模型
     * 注册框架级别的Bean组件
     * 
     * @param frameworkModel 框架模型
     */
    @Override
    public void initializeFrameworkModel(FrameworkModel frameworkModel) {
        ScopeBeanFactory beanFactory = frameworkModel.getBeanFactory();
        beanFactory.registerBean(FrameworkExecutorRepository.class);
        beanFactory.registerBean(ConverterUtil.class);
        beanFactory.registerBean(SerializeSecurityManager.class);
        beanFactory.registerBean(DefaultSerializeClassChecker.class);
        beanFactory.registerBean(CertManager.class);
        beanFactory.registerBean(ClassHolder.class);
    }

    /**
     * 初始化应用模型
     * 注册应用级别的Bean组件
     * 
     * @param applicationModel 应用模型
     */
    @Override
    public void initializeApplicationModel(ApplicationModel applicationModel) {
        ScopeBeanFactory beanFactory = applicationModel.getBeanFactory();
        beanFactory.registerBean(ShutdownHookCallbacks.class);
        beanFactory.registerBean(FrameworkStatusReportService.class);
        beanFactory.registerBean(new ConfigurationCache());
    }

    /**
     * 初始化模块模型
     * 注册模块级别的Bean组件
     * 
     * @param moduleModel 模块模型
     */
    @Override
    public void initializeModuleModel(ModuleModel moduleModel) {
        ScopeBeanFactory beanFactory = moduleModel.getBeanFactory();
        beanFactory.registerBean(new ConfigurationCache());
        beanFactory.registerBean(SerializeSecurityConfigurator.class);
    }
}
