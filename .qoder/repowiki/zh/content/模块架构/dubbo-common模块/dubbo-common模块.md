# dubbo-common模块

<cite>
**Referenced Files in This Document**   
- [URL.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/URL.java)
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java)
- [FrameworkModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/FrameworkModel.java)
- [ApplicationModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ApplicationModel.java)
- [ModuleModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ModuleModel.java)
- [ScopeModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ScopeModel.java)
- [Compiler.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/compiler/Compiler.java)
</cite>

## 目录
1. [简介](#简介)
2. [SPI扩展机制](#spi扩展机制)
3. [URL配置总线](#url配置总线)
4. [作用域模型层次](#作用域模型层次)
5. [编译器与字节码生成](#编译器与字节码生成)
6. [公共工具类与通用功能](#公共工具类与通用功能)

## 简介

dubbo-common模块是Dubbo框架的核心基础组件，为整个Dubbo生态系统提供基础功能支持。该模块实现了Dubbo的核心机制，包括SPI扩展机制、URL配置总线、作用域模型层次、编译器与字节码生成等关键功能。作为Dubbo的基石，dubbo-common模块确保了框架的可扩展性、灵活性和高性能。

**Section sources**
- [URL.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/URL.java#L1-L2770)
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java#L1-L1818)

## SPI扩展机制

### ExtensionLoader工作机制

dubbo-common模块的核心是SPI（Service Provider Interface）扩展机制，通过ExtensionLoader类实现。ExtensionLoader是Dubbo SPI扩展点加载机制的核心实现类，负责加载、缓存和管理所有的扩展实现。

ExtensionLoader的主要功能包括：
- 加载Dubbo扩展
- 自动注入依赖扩展（IOC）
- 自动包装扩展（AOP）
- 默认扩展是一个自适应实例（Adaptive）
- 支持扩展激活（Activate）

```mermaid
classDiagram
class ExtensionLoader {
+Class<?> type
+ExtensionInjector injector
+ConcurrentMap<Class<?>, Object> extensionInstances
+ConcurrentMap<Class<?>, String> cachedNames
+ConcurrentMap<String, Class<?>> cachedClasses
+ConcurrentMap<String, Object> cachedActivates
+ConcurrentMap<String, Set<String>> cachedActivateGroups
+ConcurrentMap<String, String[][]> cachedActivateValues
+ConcurrentMap<String, Holder<Object>> cachedInstances
+Holder<Object> cachedAdaptiveInstance
+Class<?> cachedAdaptiveClass
+String cachedDefaultName
+Throwable createAdaptiveInstanceError
+Set<Class<?>> cachedWrapperClasses
+Map<String, IllegalStateException> exceptions
+ScopeModel scopeModel
+AtomicBoolean destroyed
+getExtensionName(T extensionInstance) String
+getExtensionName(Class<?> extensionClass) String
+getActivateExtension(URL url, String key) T[]
+getActivateExtension(URL url, String[] values) T[]
+getActivateExtension(URL url, String key, String group) T[]
+getActivateExtension(URL url, String[] values, String group) T[]
+getLoadedExtension(String name) T
+getLoadedExtensions() Set~String~
+getLoadedExtensionInstances() T[]
+getExtension(String name) T
+getExtension(String name, boolean wrap) T
+getAdaptiveExtension() T
+hasExtension(String name) boolean
+getSupportedExtensions() Set~String~
+getSupportedExtensionInstances() Set~T~
+getDefaultExtension() T
+addExtension(String name, Class<?> clazz) void
+destroy() void
}
ExtensionLoader --> ExtensionInjector : "injector"
ExtensionLoader --> ScopeModel : "scopeModel"
ExtensionLoader --> ExtensionDirector : "extensionDirector"
```

**Diagram sources **
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java#L142-L1818)

### 扩展点加载流程

ExtensionLoader的扩展点加载流程遵循特定的策略，支持多种加载方式：

1. **加载策略**：
   - DubboInternalLoadingStrategy: META-INF/dubbo/internal/
   - DubboLoadingStrategy: META-INF/dubbo/
   - ServicesLoadingStrategy: META-INF/services/

2. **加载过程**：
   - 首先检查缓存中是否存在已加载的扩展类
   - 如果不存在，则通过类加载器从指定路径加载扩展定义文件
   - 解析扩展定义文件中的键值对，获取扩展实现类的全限定名
   - 验证扩展类是否符合要求（如是否为接口的实现类）
   - 将扩展类缓存到内存中，供后续使用

```mermaid
flowchart TD
Start([开始]) --> CheckCache["检查缓存中是否存在扩展类"]
CheckCache --> CacheHit{"缓存命中?"}
CacheHit --> |是| ReturnCached["返回缓存中的扩展类"]
CacheHit --> |否| LoadResource["通过类加载器加载资源"]
LoadResource --> ParseFile["解析扩展定义文件"]
ParseFile --> ValidateClass["验证扩展类"]
ValidateClass --> CacheClass["将扩展类缓存到内存"]
CacheClass --> ReturnClass["返回扩展类"]
ReturnCached --> End([结束])
ReturnClass --> End
```

**Diagram sources **
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java#L1434-L1458)

### SPI扩展生命周期

SPI扩展的完整生命周期包括创建、初始化、使用和销毁四个阶段：

```mermaid
stateDiagram-v2
[*] --> Created
Created --> Initialized : "初始化"
Initialized --> InUse : "使用"
InUse --> Destroyed : "销毁"
Destroyed --> [*]
note right of Created
扩展实例被创建
但尚未初始化
end note
note right of Initialized
扩展实例已完成初始化
可以被正常使用
end note
note right of InUse
扩展实例正在被使用
处于活跃状态
end note
note right of Destroyed
扩展实例被销毁
释放所有资源
end note
```

**Diagram sources **
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java#L427-L457)

**Section sources**
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java#L92-L1818)

## URL配置总线

### URL设计理念

URL在Dubbo中扮演着配置总线的角色，作为统一的资源配置和传递机制。URL类是Dubbo中最重要的基础类之一，它不仅表示网络资源定位符，还承载了丰富的配置信息。

URL的设计理念包括：
- **统一性**：所有配置信息都通过URL传递，确保配置的一致性和完整性
- **灵活性**：支持多种协议和格式，适应不同的使用场景
- **可扩展性**：通过参数机制支持自定义配置项
- **线程安全性**：URL对象是不可变的，保证线程安全

```mermaid
classDiagram
class URL {
+URLAddress urlAddress
+URLParam urlParam
+String serviceKey
+String protocolServiceKey
+Map<String, Object> attributes
+getProtocol() String
+setProtocol(String protocol) URL
+getUsername() String
+setUsername(String username) URL
+getPassword() String
+setPassword(String password) URL
+getHost() String
+setHost(String host) URL
+getPort() int
+setPort(int port) URL
+getAddress() String
+setAddress(String address) URL
+getIp() String
+getBackupAddress() String
+getBackupAddress(int defaultPort) String
+getBackupUrls() URL[]
+getPath() String
+setPath(String path) URL
+getAbsolutePath() String
+getParameters() Map~String, String~
+getParameter(String key) String
+getParameter(String key, String defaultValue) String
+addParameter(String key, String value) URL
+addParameterIfAbsent(String key, String value) URL
+removeParameter(String key) URL
+clearParameters() URL
+toFullString() String
+toParameterString() String
+toString() String
}
URL --> URLAddress : "urlAddress"
URL --> URLParam : "urlParam"
```

**Diagram sources **
- [URL.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/URL.java#L146-L2770)

### URL参数解析流程

URL参数的解析和处理流程是Dubbo配置管理的核心：

```mermaid
flowchart TD
Start([URL解析开始]) --> ParseProtocol["解析协议"]
ParseProtocol --> ParseAddress["解析地址"]
ParseAddress --> ParsePath["解析路径"]
ParsePath --> ParseParameters["解析参数"]
ParseParameters --> ValidateURL{"URL有效?"}
ValidateURL --> |否| ReturnError["返回错误"]
ValidateURL --> |是| ProcessParameters["处理参数"]
ProcessParameters --> DecodeParams["解码参数值"]
DecodeParams --> MergeParams["合并默认参数"]
MergeParams --> ValidateParams{"参数有效?"}
ValidateParams --> |否| ReturnError
ValidateParams --> |是| CreateURL["创建URL对象"]
CreateURL --> CacheURL["缓存URL"]
CacheURL --> ReturnURL["返回URL对象"]
ReturnError --> End([结束])
ReturnURL --> End
```

**Diagram sources **
- [URL.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/URL.java#L376-L421)

**Section sources**
- [URL.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/URL.java#L82-L2770)

## 作用域模型层次

### 架构设计

dubbo-common模块采用层次化的作用域模型设计，通过FrameworkModel、ApplicationModel和ModuleModel三个层次来组织和管理Dubbo的组件和配置。

```mermaid
graph TD
subgraph "FrameworkModel"
FM[框架模型]
end
subgraph "ApplicationModel"
AM[应用模型]
end
subgraph "ModuleModel"
MM[模块模型]
end
FM --> AM
AM --> MM
classDef model fill:#f9f,stroke:#333,stroke-width:2px;
class FM,AM,MM model
```

**Diagram sources **
- [FrameworkModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/FrameworkModel.java#L85-L583)
- [ApplicationModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ApplicationModel.java#L69-L691)
- [ModuleModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ModuleModel.java#L44-L360)

### 职责划分

各层次模型的职责划分如下：

#### FrameworkModel（框架模型）

FrameworkModel是Dubbo模型层次结构的最顶层，代表整个Dubbo框架实例，可以被多个应用共享。

```mermaid
classDiagram
class FrameworkModel {
+List<ApplicationModel> applicationModels
+ApplicationModel defaultAppModel
+FrameworkServiceRepository serviceRepository
+ApplicationModel internalApplicationModel
+ReentrantLock destroyLock
+newApplication() ApplicationModel
+defaultApplication() ApplicationModel
+getApplicationModels() ApplicationModel[]
+getAllApplicationModels() ApplicationModel[]
+getInternalApplicationModel() ApplicationModel
+getServiceRepository() FrameworkServiceRepository
}
FrameworkModel --> ApplicationModel : "包含"
FrameworkModel --> FrameworkServiceRepository : "服务仓库"
```

**Diagram sources **
- [FrameworkModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/FrameworkModel.java#L85-L583)

#### ApplicationModel（应用模型）

ApplicationModel代表一个使用Dubbo的应用程序，存储在RPC调用处理期间使用的基本元数据信息。

```mermaid
classDiagram
class ApplicationModel {
+List<ModuleModel> moduleModels
+List<ModuleModel> pubModuleModels
+Environment environment
+ConfigManager configManager
+ServiceRepository serviceRepository
+ApplicationDeployer deployer
+FrameworkModel frameworkModel
+ModuleModel internalModule
+ModuleModel defaultModule
+newModule() ModuleModel
+modelEnvironment() Environment
+getApplicationConfigManager() ConfigManager
+getApplicationServiceRepository() ServiceRepository
+getApplicationExecutorRepository() ExecutorRepository
+getCurrentConfig() ApplicationConfig
+getApplicationName() String
+getModuleModels() ModuleModel[]
+getPubModuleModels() ModuleModel[]
+getDefaultModule() ModuleModel
+getInternalModule() ModuleModel
}
ApplicationModel --> ModuleModel : "包含"
ApplicationModel --> Environment : "环境"
ApplicationModel --> ConfigManager : "配置管理"
ApplicationModel --> ServiceRepository : "服务仓库"
```

**Diagram sources **
- [ApplicationModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ApplicationModel.java#L69-L691)

#### ModuleModel（模块模型）

ModuleModel是服务的逻辑分组单位，每个模块可以包含多个服务的提供者和消费者。

```mermaid
classDiagram
class ModuleModel {
+ApplicationModel applicationModel
+ModuleServiceRepository serviceRepository
+ModuleEnvironment moduleEnvironment
+ModuleConfigManager moduleConfigManager
+ModuleDeployer deployer
+boolean lifeCycleManagedExternally
+modelEnvironment() ModuleEnvironment
+getConfigManager() ModuleConfigManager
+getServiceRepository() ModuleServiceRepository
+getDeployer() ModuleDeployer
+setDeployer(ModuleDeployer deployer) void
+registerInternalConsumer(Class<?> internalService, URL url, ServiceDescriptor serviceDescriptor, Object proxyObject) ConsumerModel
+isLifeCycleManagedExternally() boolean
+setLifeCycleManagedExternally(boolean lifeCycleManagedExternally) void
}
ModuleModel --> ApplicationModel : "所属"
ModuleModel --> ModuleServiceRepository : "服务仓库"
ModuleModel --> ModuleEnvironment : "环境"
ModuleModel --> ModuleConfigManager : "配置管理"
```

**Diagram sources **
- [ModuleModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ModuleModel.java#L44-L360)

**Section sources**
- [FrameworkModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/FrameworkModel.java#L85-L583)
- [ApplicationModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ApplicationModel.java#L69-L691)
- [ModuleModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ModuleModel.java#L44-L360)
- [ScopeModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ScopeModel.java#L42-L363)

## 编译器与字节码生成

### 编译器机制

dubbo-common模块提供了灵活的编译器机制，支持在运行时动态编译Java代码。

```mermaid
classDiagram
class Compiler {
<<interface>>
+compile(String code, ClassLoader classLoader) Class<?>
+compile(Class<?> neighbor, String code, ClassLoader classLoader) Class<?>
}
class AdaptiveCompiler {
+compile(String code, ClassLoader classLoader) Class<?>
+compile(Class<?> neighbor, String code, ClassLoader classLoader) Class<?>
}
class JdkCompiler {
+compile(String code, ClassLoader classLoader) Class<?>
+compile(Class<?> neighbor, String code, ClassLoader classLoader) Class<?>
}
class JavassistCompiler {
+compile(String code, ClassLoader classLoader) Class<?>
+compile(Class<?> neighbor, String code, ClassLoader classLoader) Class<?>
}
Compiler <|.. AdaptiveCompiler
Compiler <|.. JdkCompiler
Compiler <|.. JavassistCompiler
```

**Diagram sources **
- [Compiler.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/compiler/Compiler.java#L26-L54)

### 字节码生成

字节码生成机制允许Dubbo在运行时动态创建类，提高框架的灵活性和性能。

```mermaid
flowchart TD
Start([字节码生成开始]) --> GenerateCode["生成Java源代码"]
GenerateCode --> CompileCode["编译源代码"]
CompileCode --> LoadClass["加载字节码"]
LoadClass --> VerifyClass{"类验证?"}
VerifyClass --> |否| ReturnError["返回错误"]
VerifyClass --> |是| CacheClass["缓存生成的类"]
CacheClass --> ReturnClass["返回类对象"]
ReturnError --> End([结束])
ReturnClass --> End
```

**Diagram sources **
- [Compiler.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/compiler/Compiler.java#L26-L54)

**Section sources**
- [Compiler.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/compiler/Compiler.java#L17-L54)

## 公共工具类与通用功能

dubbo-common模块提供了丰富的公共工具类和通用功能，包括但不限于：

- 配置管理工具
- 线程池管理
- 序列化工具
- 网络工具
- 字符串处理工具
- 集合操作工具
- 反射工具
- 日志工具

这些工具类为Dubbo框架的其他模块提供了基础支持，确保了框架的稳定性和可维护性。

**Section sources**
- [URL.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/URL.java#L1-L2770)
- [ExtensionLoader.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java#L1-L1818)
- [FrameworkModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/FrameworkModel.java#L1-L583)
- [ApplicationModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ApplicationModel.java#L1-L691)
- [ModuleModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ModuleModel.java#L1-L360)
- [ScopeModel.java](file://dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ScopeModel.java#L1-L363)
- [Compiler.java](file://dubbo-common/src/main/java/org/apache/dubbo/common/compiler/Compiler.java#L1-L54)