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

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ServiceModel;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_VERSION;

/**
 * 2019-10-10
 *
 * @author dubbo
 * 基础服务元数据类，用于存储和操作服务的基本信息
 */
public class BaseServiceMetadata {
    // 冒号分隔符，用于构建服务键
    public static final char COLON_SEPARATOR = ':';

    // 服务键，唯一标识一个服务
    protected String serviceKey;
    // 服务接口名称
    protected String serviceInterfaceName;
    // 服务版本号
    protected String version;
    // 服务分组，使用volatile保证可见性
    protected volatile String group;
    // 服务模型
    private ServiceModel serviceModel;

    /**
     * 构建服务键
     * 格式为: group/path:version
     * 
     * @param path 服务路径
     * @param group 服务分组
     * @param version 服务版本
     * @return 服务键字符串
     */
    public static String buildServiceKey(String path, String group, String version) {
        int length = path == null ? 0 : path.length();
        length += group == null ? 0 : group.length();
        length += version == null ? 0 : version.length();
        length += 2;
        StringBuilder buf = new StringBuilder(length);
        if (StringUtils.isNotEmpty(group)) {
            buf.append(group).append('/');
        }
        buf.append(path);
        if (StringUtils.isNotEmpty(version)) {
            buf.append(':').append(version);
        }
        return buf.toString();
    }

    /**
     * 从服务键中提取版本号
     * 
     * @param serviceKey 服务键
     * @return 版本号，如果未找到则返回默认版本
     */
    public static String versionFromServiceKey(String serviceKey) {
        int index = serviceKey.indexOf(":");
        if (index == -1) {
            return DEFAULT_VERSION;
        }
        return serviceKey.substring(index + 1);
    }

    /**
     * 从服务键中提取分组信息
     * 
     * @param serviceKey 服务键
     * @return 分组名称，如果未找到则返回null
     */
    public static String groupFromServiceKey(String serviceKey) {
        int index = serviceKey.indexOf("/");
        if (index == -1) {
            return null;
        }
        return serviceKey.substring(0, index);
    }

    /**
     * 从服务键中提取接口名称
     * 
     * @param serviceKey 服务键
     * @return 接口名称
     */
    public static String interfaceFromServiceKey(String serviceKey) {
        int groupIndex = serviceKey.indexOf("/");
        int versionIndex = serviceKey.indexOf(":");
        groupIndex = (groupIndex == -1) ? 0 : groupIndex + 1;
        versionIndex = (versionIndex == -1) ? serviceKey.length() : versionIndex;
        return serviceKey.substring(groupIndex, versionIndex);
    }

    /**
     * 格式化显示服务键
     * 格式 : interface:version
     *
     * @return 显示用的服务键
     */
    public String getDisplayServiceKey() {
        StringBuilder serviceNameBuilder = new StringBuilder();
        serviceNameBuilder.append(serviceInterfaceName);
        serviceNameBuilder.append(COLON_SEPARATOR).append(version);
        return serviceNameBuilder.toString();
    }

    /**
     * 还原显示服务键
     * 与org.apache.dubbo.common.ServiceDescriptor#getDisplayServiceKey()相反
     *
     * @param displayKey 显示的服务键
     * @return 基础服务元数据对象
     */
    public static BaseServiceMetadata revertDisplayServiceKey(String displayKey) {
        String[] eles = StringUtils.split(displayKey, COLON_SEPARATOR);
        if (eles == null || eles.length < 1 || eles.length > 2) {
            return new BaseServiceMetadata();
        }
        BaseServiceMetadata serviceDescriptor = new BaseServiceMetadata();
        serviceDescriptor.setServiceInterfaceName(eles[0]);
        if (eles.length == 2) {
            serviceDescriptor.setVersion(eles[1]);
        }
        return serviceDescriptor;
    }

    /**
     * 构建不包含分组信息的服务键
     * 
     * @param interfaceName 接口名称
     * @param version 版本号
     * @return 不包含分组的服务键
     */
    public static String keyWithoutGroup(String interfaceName, String version) {
        if (StringUtils.isEmpty(version)) {
            return interfaceName + ":" + DEFAULT_VERSION;
        }
        return interfaceName + ":" + version;
    }

    /**
     * 获取服务键
     * 
     * @return 服务键
     */
    public String getServiceKey() {
        return serviceKey;
    }

    /**
     * 生成服务键
     * 根据服务接口名称、分组和版本号构建服务键
     */
    public void generateServiceKey() {
        this.serviceKey = buildServiceKey(serviceInterfaceName, group, version);
    }

    /**
     * 设置服务键
     * 
     * @param serviceKey 服务键
     */
    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    /**
     * 获取服务接口名称
     * 
     * @return 服务接口名称
     */
    public String getServiceInterfaceName() {
        return serviceInterfaceName;
    }

    /**
     * 设置服务接口名称
     * 
     * @param serviceInterfaceName 服务接口名称
     */
    public void setServiceInterfaceName(String serviceInterfaceName) {
        this.serviceInterfaceName = serviceInterfaceName;
    }

    /**
     * 获取服务版本号
     * 
     * @return 服务版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置服务版本号
     * 
     * @param version 服务版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取服务分组
     * 
     * @return 服务分组
     */
    public String getGroup() {
        return group;
    }

    /**
     * 设置服务分组
     * 
     * @param group 服务分组
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 获取服务模型
     * 
     * @return 服务模型
     */
    public ServiceModel getServiceModel() {
        return serviceModel;
    }

    /**
     * 设置服务模型
     * 
     * @param serviceModel 服务模型
     */
    public void setServiceModel(ServiceModel serviceModel) {
        this.serviceModel = serviceModel;
    }
}
