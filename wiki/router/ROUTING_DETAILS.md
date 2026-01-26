# Dubbo3 路由规则详细分析

## 1. 亲和路由（AffinityStateRouter）

### 核心概念

```
特点：自动匹配 + 软降级（ratio参数）
实现：基于URL属性匹配（无需显式传递）
容错：当亲和匹配不足ratio%时，自动降级到全量
```

### 适用场景

- ✅ **同机房调用**：优先调用同机房的提供者
- ✅ **跨域容灾**：IDC级别的亲和性保证
- ✅ **机房故障恢复**：当一个机房故障时平滑降级

### 关键参数

```yaml
affinityKey: region          # 亲和属性名（如region, idc）
ratio: 80                    # 亲和匹配达到80%时才返回亲和结果
# 如果亲和结果 < 80%，自动降级返回全量提供者
```

### 源码流程（AffinityStateRouter.java 第137-157行）

```java
// 1. 过滤匹配亲和属性的提供者
BitList<Invoker<T>> result = invokers.clone();
result.removeIf(invoker -> !matchInvoker(invoker.getUrl(), url));

// 2. 检查匹配比例是否达到阈值
if (result.size() / (double) invokers.size() >= ratio / (double) 100) {
    // ✅ 达到阈值：返回亲和匹配结果
    return result;
} else {
    // ⏬ 未达到阈值：自动降级到全量提供者
    logger.warn("The affinity result is ignored...");
    return invokers;
}
```

### 配置示例

```yaml
# Nacos 配置中心: dubbo/config/group/{serviceName}.affinity-router
configVersion: v3.1
scope: service
key: com.example.UserService
enabled: true
runtime: true
affinityAware:
  key: region
  ratio: 80
```

### 工作流程图

```
消费者请求
    ↓
AffinityRouter 启用？ → 否 → 直接返回全量
    ↓ 是
获取消费者 region=beijing
    ↓
过滤所有 region=beijing 的提供者（3 个）
总共 10 个提供者，比例 30% < 80%
    ↓
FAIL: 比例不足 → 自动降级返回全量 10 个提供者
    ↓
消费者正常服务（跨域调用）
```

---

## 2. 标签路由（TagStateRouter）

### 核心概念

```
特点：显式传递标签 + 层级降级
实现：通过RpcInvocation attachment传递标签
容错：无匹配标签 → 返回无标签提供者
```

### 适用场景

- ✅ **灰度发布**：特定标签用户访问灰度版本
- ✅ **多租户隔离**：不同租户标签对应不同提供者
- ✅ **业务隔离**：特殊业务流量隔离处理

### 关键参数

```yaml
tags:
  - name: tag1
    addresses: [ip1:port1, ip2:port2]
  - name: tag2
    addresses: [ip3:port3]
force: false    # 强制使用标签，无匹配时返回空
```

### 源码流程（TagStateRouter.java 第118-161行）

```java
// 1. 从Invocation获取标签（需显式传递）
String tag = StringUtils.isEmpty(invocation.getAttachment(TAG_KEY))
        ? url.getParameter(TAG_KEY)
        : invocation.getAttachment(TAG_KEY);

// 2. 如果有标签，查找对应的提供者地址列表
if (StringUtils.isNotEmpty(tag)) {
    Set<String> addresses = tagnameToAddresses.get(tag);
    if (addresses != null) {
        result = filterInvoker(invokers,
            invoker -> addressMatches(invoker.getUrl(), addresses));

        // 结果非空 OR force=true时返回结果
        if (CollectionUtils.isNotEmpty(result) || tagRouterRule.isForce()) {
            return result;
        }
    }
}

// 3. 无匹配标签时，返回所有无标签提供者（降级）
return filterInvoker(result, invoker ->
    StringUtils.isEmpty(invoker.getUrl().getParameter(TAG_KEY)));
```

### 配置示例

```java
// 消费端：在RpcInvocation中传递标签
RpcContext.getServiceContext().setAttachment("dubbo.tag", "gray");
demoService.sayHello("Hello");

// 或通过@DubboReference注解
@DubboReference(tag = "gray")
DemoService demoService;
```

### 标签层级降级

```
标签选择器：beta|team1|partner1

Step1: 查找 beta|team1|partner1 完全匹配 → 失败
Step2: 查找 beta|team1 → 失败
Step3: 查找 beta → 成功，返回 beta 对应的提供者
```

---

## 3. 条件路由（ConditionStateRouter）

### 核心概念

```
特点：When-Then 条件表达式
实现：支持多维度条件匹配
容错：无匹配返回空 or 全量（取决于force参数）
```

### 适用场景

- ✅ **流量分发**：基于方法名、参数、版本分发流量
- ✅ **版本控制**：特定版本请求路由到特定提供者
- ✅ **A/B 测试**：根据用户属性进行测试版本分流
- ✅ **黑名单管理**：屏蔽特定条件的请求

### 语法示例

```yaml
conditions:
  # 基础条件路由（v3.0及以下）
  - method=sayHello => address=10.20.3.3:20880
  - version=v2.0 & method=getUser => region=hangzhou

  # 多目标路由（v3.1+）
  - from:
      match: env=gray
    to:
      - match: region=beijing
        weight: 100
      - match: region=shanghai
        weight: 50
```

### 源码流程（ConditionStateRouter.java 第205-278行）

```java
// 1. 检查when条件是否匹配
if (!matchWhen(url, invocation)) {
    return invokers;  // when不匹配，返回全量
}

// 2. 如果when匹配，应用then条件过滤
if (thenCondition == null) {
    return BitList.emptyList();  // then为空表示黑名单
}

BitList<Invoker<T>> result = invokers.clone();
result.removeIf(invoker -> !matchThen(invoker.getUrl(), url));

// 3. 根据force参数处理过滤结果为空的情况
if (result.isEmpty()) {
    if (force) {
        return BitList.emptyList();  // 强制返回空
    } else {
        return invokers;  // 降级返回全量
    }
}
return result;
```

### 条件表达式详解

#### 基本操作符

```
= ：相等
!= ：不相等
& ：且（多个条件并联）
, ：或（同一参数多个值）
~ ：范围匹配（如1~100表示1到100）
```

#### 支持的条件类型

```
method=xxx              - 方法名
version=xxx             - 版本号
region=xxx              - 地域属性
host=xxx.xxx.xxx.xxx    - IP地址（支持通配符*）
arguments[0]=xxx        - 请求参数
attachments[key]=xxx    - 自定义附件
```

#### 常见表达式

```yaml
# 示例1：特定方法路由
- method=getUser => region=beijing

# 示例2：版本控制
- version=v2.0 => address=10.20.3.3:20880

# 示例3：参数范围
- attachments[user_id]=1~100 => region=hangzhou

# 示例4：黑名单（then为空）
- region=failover =>

# 示例5：组合条件
- method=sayHello & version=v2.0 => host=10.20.3.*
```

---

## 4. 规则覆盖（RuleRouter 隐含）

### 核心概念

```
特点：优先级最高，强制覆盖其他规则
实现：通过地址黑/白名单
容错：直接禁用，无降级
```

### 适用场景

- ✅ **故障隔离**：快速隔离故障提供者
- ✅ **动态禁用**：紧急禁用某个提供者
- ✅ **灾难恢复**：故障机房快速切流

### 配置示例

```yaml
# 通过条件路由实现规则覆盖（最高优先级）
---
scope: service
key: com.example.UserService
force: true
runtime: true
enabled: true
priority: 1  # 优先级最高
conditions:
  # 黑名单：屏蔽故障机房
  - region=hangzhou =>
    # 空的then表示返回空列表，force=true时强制执行

# 或白名单：只允许健康提供者
  - region!=beijing => address=10.20.3.3:20880,10.20.3.4:20880
```

### 执行流程

```
优先级链：规则覆盖(P1) → 亲和路由(P2) → 标签路由(P3) → 条件路由(P4)
         ↓
    规则覆盖检查 - 如果命中直接返回结果，不继续后续路由
```

---

## 5. 脚本路由（ScriptStateRouter）

### 核心概念

```
特点：支持JavaScript脚本编写自定义逻辑
实现：基于javax.script的脚本引擎
容错：脚本异常返回全量提供者
```

### 适用场景

- ✅ **自定义复杂逻辑**：内置路由无法满足的复杂场景
- ✅ **动态计算**：实时计算路由目标
- ✅ **A/B 测试**：复杂的测试分流算法

### 脚本示例

```javascript
// 获取所有可用提供者
var invokers = invokers;
var result = new java.util.ArrayList();

// 自定义路由逻辑：根据user_id % 2进行分流
for (var i = 0; i < invokers.size(); i++) {
    var invoker = invokers.get(i);
    var userId = invocation.getAttachment("userId");

    if (userId % 2 == 0 && invoker.getUrl().getHost() == "10.20.3.3") {
        result.add(invoker);
    } else if (userId % 2 == 1 && invoker.getUrl().getHost() == "10.20.3.4") {
        result.add(invoker);
    }
}

return result.isEmpty() ? invokers : result;
```

### 脚本环境变量

```javascript
// 可用变量
invokers          // Invoker<?> 列表
invocation        // RpcInvocation 对象
url               // URL 对象（消费端URL）
RpcContext        // Dubbo RPC 上下文
logger            // 日志对象
```

### 配置示例

```yaml
type: javascript
rule: |
  // 上述脚本内容
  var result = new java.util.ArrayList();
  // ... 脚本逻辑
  return result;
```

### 风险提示 ⚠️

- ⚠️ **性能影响大**：脚本编译和执行开销
- ⚠️ **容错能力弱**：脚本异常难以调试
- ⚠️ **安全隐患**：脚本执行有权限限制但仍需谨慎
- ⚠️ **可维护性差**：业务逻辑写在脚本中，不易维护

---

## 6. 网格路由（MeshRuleRouter）

### 核心概念

```
特点：基于VirtualService和DestinationRule
实现：Istio风格的服务网格路由
容错：不匹配时返回原始提供者列表
```

### 适用场景

- ✅ **服务网格集成**：与Istio/Envoy配合
- ✅ **高级流量管理**：金丝雀部署、A/B测试、蓝绿发布
- ✅ **故障注入**：模拟故障进行混沌工程

### 配置示例（YAML格式）

```yaml
# VirtualService 定义路由规则
apiVersion: dubbo.apache.org/v1alpha1
kind: VirtualService
metadata:
  name: demo-vs
spec:
  hosts:
  - demo
  dubbo:
  - match:
    - sourceLabels:
        env: gray
    route:
    - destination:
        host: demo
        subset: v1
      weight: 100
    - destination:
        host: demo
        subset: v2
      weight: 0

---
# DestinationRule 定义目标子集
apiVersion: dubbo.apache.org/v1alpha1
kind: DestinationRule
metadata:
  name: demo-dr
spec:
  host: demo
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
```

### 源码流程（MeshRuleRouter.java 第83-140行）

```java
// 1. 遍历每个应用的VirtualService和DestinationRule
for (String appName : ruleCache.getAppList()) {
    // 2. 根据Invocation匹配VirtualService规则
    List<DubboRouteDestination> routeDestination =
        getDubboRouteDestination(ruleCache.getVsDestinationGroup(appName), invocation);

    if (routeDestination != null) {
        // 3. 按权重随机选择目标子集
        String subset = randomSelectDestination(ruleCache, appName, routeDestination, invokers);

        if (subset != null) {
            BitList<Invoker<T>> destination =
                meshRuleCache.getSubsetInvokers(appName, subset);
            result = result.or(destination);
        }
    }
}

// 4. 保护机制：如果过滤结果为空，返回原始列表
if (result.isEmpty()) {
    return invokers;
}
return invokers.and(result);
```

### 网格路由优势

- **与K8s原生集成**：使用CRD定义路由规则
- **支持高级功能**：
  - 金丝雀发布（Canary Deployment）
  - 蓝绿发布（Blue-Green Deployment）
  - 故障注入（Fault Injection）
  - 重试策略（Retry Policy）
- **统一治理**：Service Mesh 统一管理所有服务路由

---

## 核心实现类索引

| 类名 | 路径 | 职责 |
|------|------|------|
| AffinityStateRouter | dubbo-cluster/.../router/affinity/AffinityStateRouter.java | 亲和路由实现 |
| TagStateRouter | dubbo-cluster/.../router/tag/TagStateRouter.java | 标签路由实现 |
| ConditionStateRouter | dubbo-cluster/.../router/condition/ConditionStateRouter.java | 条件路由实现 |
| ScriptStateRouter | dubbo-cluster/.../router/script/ScriptStateRouter.java | 脚本路由实现 |
| MeshRuleRouter | dubbo-cluster/.../router/mesh/route/MeshRuleRouter.java | 网格路由实现 |
| AbstractStateRouter | dubbo-cluster/.../router/state/AbstractStateRouter.java | 路由基类 |

---

**下一步**：查看 [ROUTING_CONFIG_PRACTICE.md](ROUTING_CONFIG_PRACTICE.md) 了解配置最佳实践。
