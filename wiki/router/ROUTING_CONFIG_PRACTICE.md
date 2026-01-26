# Dubbo3 路由配置与最佳实践

## 1. 同机房调用方案

### 场景描述

企业在多个机房（如北京、上海、杭州）部署服务，需要优先调用同机房的提供者以降低延迟。

### 推荐方案：亲和路由

```yaml
# Nacos 配置中心
# 配置key: com.example.UserService.affinity-router

configVersion: v3.1
scope: service
key: com.example.UserService
enabled: true
runtime: true
affinityAware:
  key: region
  ratio: 80
```

### 提供端配置

```properties
# Provider application.properties
dubbo.application.name=user-service
dubbo.provider.region=beijing      # 提供者所在机房
```

### 消费端配置

```properties
# Consumer application.properties
dubbo.application.name=demo-app
dubbo.consumer.region=beijing       # 消费者所在机房
```

### 高级方案：亲和路由 + 条件路由

当需要处理特定业务的特殊需求时：

```yaml
# 亲和路由
configVersion: v3.1
scope: service
key: com.example.UserService
enabled: true
runtime: true
affinityAware:
  key: region
  ratio: 80

---
# 补充条件路由：处理特殊方法
configVersion: v3.1
scope: service
key: com.example.UserService
enabled: true
conditions:
  # 某个特殊方法必须调用北京机房
  - method=specialMethod => region=beijing
```

### 监控指标

```properties
# 监控关键指标
- 亲和匹配率：（亲和命中数 / 总请求数）
- 降级发生次数：当亲和比例 < ratio 时的发生次数
- 跨域调用延迟：非亲和匹配的请求延迟
```

---

## 2. 灰度发布方案

### 场景描述

新版本发布时，需要让部分用户（如内部用户、VIP用户）先体验新功能。

### 方案A：标签路由（简洁）

**适合**: 用户明确分组的场景

```yaml
# Provider端配置（标记提供者标签）
# application.properties
dubbo.provider.tag=gray        # 标记为灰度版本
# 或
dubbo.provider.tag=stable      # 标记为稳定版本

# Nacos 配置中心
# demo-app.tag-router
---
scope: application
force: false
runtime: false
enabled: true
key: demo-app
tags:
  - name: gray
    addresses:
      - 10.20.3.100:20880       # 灰度版本提供者
      - 10.20.3.101:20880
  - name: stable
    addresses:
      - 10.20.3.102:20880       # 稳定版本提供者
      - 10.20.3.103:20880
```

**消费端调用**

```java
// 灰度用户访问
RpcContext.getServiceContext().setAttachment("dubbo.tag", "gray");
DemoService demoService = // ...
demoService.sayHello("world");  // 调用灰度版本

// 普通用户访问（不设置标签）
DemoService demoService = // ...
demoService.sayHello("world");  // 调用稳定版本
```

### 方案B：条件路由（灵活）

**适合**: 条件复杂、需要自定义分流的场景

```yaml
# Nacos 配置中心
# com.example.DemoService.condition-router
---
configVersion: v3.1
scope: service
key: com.example.DemoService
force: false
runtime: true
enabled: true
conditions:
  # 内部用户（userId < 1000）访问灰度版本
  - attachments[userId]=1~999 => version=gray

  # VIP用户访问灰度版本
  - attachments[userLevel]=vip => version=gray

  # 其他用户访问稳定版本
  - => version=stable
```

**消费端调用**

```java
RpcInvocation invocation = new RpcInvocation();
invocation.setAttachment("userId", "500");      // 灰度用户
DemoService demoService = // ...
demoService.sayHello("world");                   // 调用灰度版本

// 普通用户
invocation.setAttachment("userId", "5000");     // 普通用户
demoService.sayHello("world");                   // 调用稳定版本
```

### 灰度发布流程建议

```
0. 部署准备
   - 部署灰度版本提供者（1-2台）
   - 部署稳定版本提供者（多台）

1. 第一阶段（1天）：内部灰度
   - 配置标签/条件路由指向灰度版本
   - 内部员工访问灰度功能
   - 监控关键指标：错误率、延迟

2. 第二阶段（1天）：小流量灰度
   - 逐步增加灰度用户比例：1% → 5% → 10%
   - 持续监控性能和错误

3. 第三阶段（全量发布）
   - 100% 切流到灰度版本
   - 下线旧版本提供者
   - 更新稳定版本标签指向新版本
```

---

## 3. 故障隔离与应急方案

### 场景描述

某个机房或提供者出现故障，需要快速隔离故障并保证服务可用。

### 应急隔离方案

```yaml
# Nacos 配置中心
# com.example.UserService.condition-router
# 优先级设置为最高，确保快速响应

---
configVersion: v3.1
scope: service
key: com.example.UserService
force: true              # 强制执行，无降级
runtime: true
enabled: true
priority: 1              # 最高优先级
conditions:
  # 黑名单：屏蔽故障机房所有请求
  - region=failover =>
```

**执行效果**
- 所有来自 `failover` 机房的消费者请求返回空列表
- 由于 `force=true`，不会降级到其他区域
- 消费端的容错机制（如重试）会启动，调用其他健康提供者

### 故障恢复方案

当故障恢复后，删除或禁用隔离规则：

```yaml
# 方案1：删除配置（推荐）
# 删除 com.example.UserService.condition-router 配置

# 方案2：禁用规则
---
enabled: false    # 禁用隔离规则
```

---

## 4. 多版本共存方案

### 场景描述

需要支持多个版本的 API 同时运行，根据调用者选择版本。

### 实现方案

```yaml
# Provider 端配置
# application.properties
dubbo.provider.version=2.0.0     # 版本号

---
# Nacos 配置中心
# com.example.UserService.condition-router

configVersion: v3.1
scope: service
key: com.example.UserService
enabled: true
conditions:
  # 请求指定 v2.0 版本
  - version=2.0 => version=2.0.0

  # 请求指定 v1.0 版本
  - version=1.0 => version=1.0.0

  # 默认请求最新版本
  - => version=2.0.0
```

**消费端调用**

```java
// 调用 v1.0 版本
@DubboReference(version = "1.0.0")
DemoService demoServiceV1;
demoServiceV1.sayHello("world");

// 调用 v2.0 版本
@DubboReference(version = "2.0.0")
DemoService demoServiceV2;
demoServiceV2.sayHello("world");

// 调用默认版本
@DubboReference
DemoService demoService;
demoService.sayHello("world");
```

---

## 5. 黑名单与白名单管理

### 黑名单示例

屏蔽特定 IP 的请求：

```yaml
---
scope: service
key: com.example.UserService
force: true
conditions:
  # 黑名单：屏蔽来自特定 IP 的消费者
  - host=192.168.1.100 =>

  # 或屏蔽特定地域
  - region=untrusted =>
```

### 白名单示例

仅允许特定 IP 的消费者访问：

```yaml
---
scope: service
key: com.example.UserService
force: true
conditions:
  # 白名单：只允许特定提供者
  - => address=10.20.3.100:20880,10.20.3.101:20880
```

---

## 6. 性能优化配置

### 配置建议

```yaml
# 1. 启用缓存（减少重复计算）
runtime: false    # 当规则不经常变化时，设置为false

# 2. 合理设置优先级
priority: 10      # 优先级越高越先执行

# 3. 避免过于复杂的条件表达式
conditions:
  # ✅ 好：简洁明了
  - version=v2.0 => region=beijing

  # ❌ 不好：过度复杂
  - (method=sayHello | method=sayHi) & version=v2.0 & region=(beijing|shanghai) & !host=192.168.1.* => ...

# 4. 使用条件路由而非脚本路由
# ✅ 推荐：条件路由
conditions:
  - userId=1~1000 => version=gray

# ❌ 避免：脚本路由（性能差）
type: javascript
rule: |
  if (invocation.getAttachment("userId") < 1000) { ... }
```

---

## 7. 多个路由规则组合

### 推荐组合方案

```yaml
# 第一层：规则覆盖（P1 - 故障隔离）
# 配置key: com.example.UserService.condition-router
---
priority: 1
force: true
conditions:
  - region=failover =>

---
# 第二层：亲和路由（P2 - 同机房优先）
# 配置key: com.example.UserService.affinity-router
configVersion: v3.1
scope: service
affinityAware:
  key: region
  ratio: 80

---
# 第三层：条件路由（P4 - 灾难恢复）
# 配置key: com.example.UserService.condition-router
---
priority: 10
conditions:
  - => address=10.20.3.100:20880,10.20.3.101:20880
```

**执行流程**

```
用户请求
  ↓
检查规则覆盖（P1）→ 是否来自 failover 机房？
  ↓ 否
检查亲和路由（P2）→ 是否有同机房提供者？
  ↓ 否或比例不足
检查条件路由（P4）→ 是否符合条件？
  ↓
负载均衡 → 选择具体提供者
  ↓
发送请求
```

---

## 8. 配置最佳实践检查清单

### 部署前检查

- [ ] 是否明确了路由规则的优先级？
- [ ] 是否设置了合理的 `force` 参数值？
- [ ] 是否进行了充分的灰度测试？
- [ ] 是否准备了回滚方案？
- [ ] 是否配置了监控告警？

### 运行时监控

- [ ] 是否监控了路由命中率？
- [ ] 是否监控了降级发生频率？
- [ ] 是否监控了请求延迟变化？
- [ ] 是否监控了错误率变化？

### 应急处理

- [ ] 是否准备了快速回滚脚本？
- [ ] 是否有故障隔离方案？
- [ ] 是否有多级降级方案？
- [ ] 是否有清晰的应急联系人？

---

## 9. 常见配置错误

### ❌ 错误1：force 参数设置不当

```yaml
# 错误：force=true 时条件为空，会导致所有请求返回空
conditions:
  - method=dangerous =>    # ❌ then 为空，force=true 会阻止所有请求
force: true
```

**正确做法**
```yaml
# ✅ 正确：白名单方式
conditions:
  - => address=safe-server:20880
force: true

# ✅ 或者：使用 force=false
conditions:
  - method=dangerous =>
force: false    # 允许降级
```

### ❌ 错误2：条件表达式过于复杂

```yaml
# ❌ 不好：嵌套太深
conditions:
  - (method=sayHello | method=sayHi) & ((version=v2.0 & region=beijing) | (version=v1.0 & region=shanghai)) => ...
```

**正确做法**
```yaml
# ✅ 正确：拆分为多个简单规则
conditions:
  - method=sayHello & version=v2.0 & region=beijing => target1
  - method=sayHi & version=v1.0 & region=shanghai => target2
```

### ❌ 错误3：忘记配置标签提供者

```java
// ❌ 错误：忘记在提供者端设置标签
// Provider 端没有设置 tag 参数

// Consumer 端期望调用有 tag 的提供者
RpcContext.getServiceContext().setAttachment("dubbo.tag", "gray");
```

**正确做法**
```properties
# Provider 端必须配置
dubbo.provider.tag=gray

# 或在 URL 中指定
dubbo.provider.url=dubbo://localhost:20880/...?tag=gray
```

---

## 10. 监控和告警

### 关键指标

```properties
# 1. 路由匹配率
routing.match.rate = 已匹配请求数 / 总请求数

# 2. 规则覆盖触发次数
routing.override.count = 黑名单拦截的请求数

# 3. 亲和降级次数
routing.affinity.fallback.count = 亲和不足触发降级的次数

# 4. 标签路由命中率
routing.tag.hit.rate = 标签匹配成功的请求数 / 总请求数

# 5. 条件路由平均耗时
routing.condition.avg.cost = 条件路由平均执行时间 (ms)
```

### 告警配置

```properties
# 告警规则
alert.routing.coverage.low = 路由覆盖率 < 95%
alert.routing.error.rate.high = 路由导致的错误率 > 1%
alert.routing.latency.increase = 延迟增加 > 50ms
alert.routing.fallback.frequent = 降级频率 > 10 次/分钟
```

---

**下一步**：查看 [ROUTING_PERFORMANCE_FAQ.md](ROUTING_PERFORMANCE_FAQ.md) 了解性能对比和常见问题解答。
