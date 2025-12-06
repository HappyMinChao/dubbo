# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the `dubbo-common` module of Apache Dubbo, a high-performance RPC framework. This module contains the core common utilities and foundational components used across all other Dubbo modules. The codebase includes Chinese annotations (中文注释) alongside the original code to aid understanding.

## Build & Test Commands

Build the module:
```bash
mvn clean install
```

Run tests:
```bash
mvn test
```

Run specific test class:
```bash
mvn test -Dtest=ClassName
```

Skip tests during build:
```bash
mvn clean install -DskipTests
```

## Core Architecture

### 1. SPI Extension Mechanism (最核心的设计)

The SPI (Service Provider Interface) extension system is the heart of Dubbo's pluggability. Key components:

- **ExtensionLoader** (src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java): The core loader that manages all extension loading, caching, dependency injection (IOC), and AOP wrapping. This is the most critical class in the module.

- **@SPI Annotation**: Marks an interface as an extension point. Supports default extension names and scope definitions (FRAMEWORK, APPLICATION, MODULE, SELF).

- **@Adaptive Annotation**: Enables runtime adaptive extension selection based on URL parameters. The ExtensionLoader can auto-generate adaptive implementation code.

- **@Activate Annotation**: Controls conditional extension activation based on group, URL parameters, and class availability.

- **Extension Loading Strategy**: Three loading paths in priority order:
  1. `META-INF/dubbo/internal/` - Internal Dubbo extensions (DubboInternalLoadingStrategy)
  2. `META-INF/dubbo/` - User extensions (DubboLoadingStrategy)
  3. `META-INF/services/` - Java SPI compatibility (ServicesLoadingStrategy)

- **Extension Configuration Format**: Key-value pairs in files named after the fully qualified interface name
  ```
  extensionName=com.example.ExtensionImpl
  ```

### 2. URL as Configuration Bus

The **URL class** (src/main/java/org/apache/dubbo/common/URL.java) is Dubbo's unified configuration carrier:

- Immutable and thread-safe design
- Format: `protocol://username:password@host:port/path?param1=value1&param2=value2`
- Used throughout Dubbo for service addressing, parameter passing, and configuration
- Contains URLAddress (protocol, host, port, path) and URLParam (parameters) components
- Supports method-level parameter overrides (e.g., `methodName.timeout=1000`)
- Critical for service discovery, routing, and load balancing decisions

### 3. Scope Model Hierarchy

Dubbo uses a hierarchical scope system for component lifecycle management:

- **FrameworkModel**: Top-level, represents the entire Dubbo framework instance
- **ApplicationModel**: Application-level, one per Dubbo application
- **ModuleModel**: Module-level, supports multi-module applications
- Each scope has its own ExtensionDirector managing extension loaders

### 4. Compiler & Bytecode Generation

- **Compiler SPI** (org.apache.dubbo.common.compiler.Compiler): Supports JavassistCompiler and JdkCompiler
- Used for generating adaptive extension classes at runtime
- **ClassGenerator**: Javassist-based utility for dynamic class generation
- **Proxy** and **Wrapper**: Used for creating service proxies and method wrappers

### 5. Configuration System

- **Configuration Interface**: Abstract configuration source
- **CompositeConfiguration**: Combines multiple configuration sources with priority
- **Environment/ModuleEnvironment**: Manages environment-specific configurations
- **DynamicConfiguration**: Supports dynamic configuration updates via config centers

## Key Extension Points in This Module

When adding new extension implementations, place configuration files in:
- `src/main/resources/META-INF/dubbo/internal/` for internal Dubbo extensions
- `src/main/resources/META-INF/dubbo/` for user extensions

Examples:
- `org.apache.dubbo.common.threadpool.ThreadPool` - Thread pool implementations
- `org.apache.dubbo.common.store.DataStore` - Data storage implementations
- `org.apache.dubbo.common.compiler.Compiler` - Java code compiler implementations

## Working with Extensions

### Creating a New Extension

1. Define the SPI interface with `@SPI` annotation
2. Create implementation classes
3. Register implementations in `META-INF/dubbo/internal/[InterfaceName]`
4. Use ExtensionLoader to load: `ExtensionLoader.getExtensionLoader(Type.class).getExtension("name")`

### Extension Injection & Wrapping

- Extensions support setter-based dependency injection (IoC)
- Wrapper classes (constructors accepting the extension type) provide AOP functionality
- Use `@DisableInject` to prevent specific setter injection
- Lifecycle: ExtensionPostProcessor → instantiation → injection → initialization (Lifecycle.initialize())

## Important Packages

- `org.apache.dubbo.common.extension.*` - SPI extension mechanism
- `org.apache.dubbo.common.URL` - Configuration bus
- `org.apache.dubbo.rpc.model.*` - Scope model (Framework/Application/Module)
- `org.apache.dubbo.common.compiler.*` - Runtime compilation
- `org.apache.dubbo.common.bytecode.*` - Bytecode generation (Javassist)
- `org.apache.dubbo.common.config.*` - Configuration management
- `org.apache.dubbo.common.utils.*` - Common utilities

## Notes on Chinese Annotations

This codebase contains extensive Chinese comments (中文注释) added to the original Apache Dubbo code. When modifying code:
- Preserve existing Chinese annotations when editing methods/classes
- The Chinese comments explain complex Dubbo concepts and are valuable for understanding
- Original English Javadoc and Chinese explanatory comments coexist
