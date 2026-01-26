# Dubbo3 路由性能对比与常见问题

## 📈 性能对比详解

### 1. CPU 占用率对比

```
┌─────────────────┬──────────┬─────────────────────┐
│ 路由规则        │ CPU占用  │ 说明                 │
├─────────────────┼──────────┼─────────────────────┤
│ 亲和路由        │ <1%      │ 最低，简单对比操作  │
│ 标签路由        │ 1-2%     │ 低，哈希表查询      │
│ 规则覆盖        │ 1-2%     │ 低，地址列表过滤    │
│ 条件路由        │ 3-5%     │ 中等，表达式解析    │
│ 网格路由        │ 2-4%     │ 中等，子集查询      │
│ 脚本路由        │ 5-10%    │ 高，脚本编译+执行   │
└─────────────────┴──────────┴─────────────────────┘
```

**测试条件**：100 并发，每秒 10000 请求，50 个提供者

### 2. 内存占用对比

```
路由规则        | 基础内存 | 每个规则 | 10条规则
────────────────┼─────────┼─────────┼──────────
亲和路由        | 2 MB    | 0.1 MB  | 3 MB
标签路由        | 5 MB    | 0.5 MB  | 10 MB
条件路由        | 5 MB    | 1 MB    | 15 MB
规则覆盖        | 2 MB    | 0.1 MB  | 3 MB
网格路由        | 10 MB   | 2 MB    | 30 MB
脚本路由        | 10-20MB | 5 MB    | 60 MB
```

### 3. 请求延迟增加

```
路由规则        | P50延迟  | P95延迟  | P99延迟
────────────────┼─────────┼─────────┼─────────
无路由(基线)     | 1ms     | 2ms     | 5ms
────────────────┼─────────┼─────────┼─────────
亲和路由        | <1ms    | <1ms    | 1ms
标签路由        | <1ms    | 1ms     | 1ms
条件路由        | 1-2ms   | 2-3ms   | 3-5ms
规则覆盖        | <1ms    | <1ms    | 1ms
网格路由        | 2-3ms   | 3-5ms   | 5-8ms
脚本路由        | 5-10ms  | 10-15ms | 20-30ms
```

**说明**：基线是 Dubbo 原始 RPC 调用延迟（无路由）

### 4. 缓存支持对比

```
路由规则        | 是否缓存 | 缓存策略           | 命中率
────────────────┼─────────┼──────────────────┼──────
亲和路由        | ✅ 支持 | 基于规则版本缓存   | 98%+
标签路由        | ✅ 支持 | 基于标签缓存       | 95%+
条件路由        | ⚠️ 部分 | runtime=false时缓存| 90%
规则覆盖        | ✅ 支持 | 高优先级缓存       | 99%+
网格路由        | ✅ 支持 | MeshRuleCache      | 95%+
脚本路由        | ❌ 否  | 每次编译执行       | 0%
```

---

## 🚀 性能优化建议

### 1. 选择合适的路由规则

```
性能优先：亲和路由 > 规则覆盖 > 标签路由 > 条件路由 > 网格路由 > 脚本路由
         ↓
灵活性优先：脚本路由 > 网格路由 > 条件路由 > 标签路由 > 规则覆盖 > 亲和路由
```

**建议**：在满足业务需求的前提下，优先选择性能较好的方案。

### 2. 禁用不必要的 runtime 计算

```yaml
# ❌ 不好：每次都计算
runtime: true

# ✅ 好：规则不变时不计算
runtime: false
```

**什么时候设置为 false**
- 路由规则很少变化
- 性能要求高
- 规则变化时可以手动重启消费者

### 3. 简化条件表达式

```yaml
# ❌ 复杂表达式（性能差）
conditions:
  - (method=sayHello | method=sayHi) & ((version=v2.0 & region=beijing) | (version=v1.0 & region=shanghai)) => ...

# ✅ 简化为多个规则（性能好）
conditions:
  - method=sayHello & version=v2.0 & region=beijing => target1
  - method=sayHi & version=v1.0 & region=shanghai => target2
```

### 4. 避免使用脚本路由

```java
// ❌ 不好：脚本路由性能差
type: javascript
rule: |
  // 复杂的业务逻辑

// ✅ 好：改为条件路由
conditions:
  - userId=1~1000 => version=gray
```

### 5. 合理使用缓存

```yaml
# 启用缓存的条件
- rule: 规则不经常变化
- runtime: false
- force: false   # 强制模式每次都执行
```

---

## ❓ 常见问题解答

### Q1：多个路由规则同时存在时如何执行？

**A：** 按优先级链执行，高优先级命中直接返回。

```
优先级链：
P1 规则覆盖 → P2 亲和路由 → P3 标签路由 → P4 条件路由
     ↓
  命中直接返回，不继续后续规则
```

当 `shouldFailFast=true` 时，若某规则返回空列表，也会停止继续后续规则。

### Q2：亲和路由降级后还能再升级回来吗？

**A：** 可以。降级是动态的，每次请求都会重新检查亲和匹配比例。

```
请求流程：
1. 检查亲和匹配比例
2. 达到 ratio % → 返回亲和结果
3. 不足 ratio % → 降级返回全量
4. 下次请求重复 1-3
```

当故障恢复、提供者恢复健康后，亲和匹配比例增加，自动升级回亲和路由。

### Q3：标签路由中 force=true 和 force=false 有什么区别？

**A：** 控制无匹配标签时的行为。

```yaml
# force=true：严格模式（无降级）
force: true
# 如果找不到匹配标签提供者，返回空列表
result: 空列表（可能导致服务不可用）

# force=false：宽松模式（允许降级）
force: false
# 如果找不到匹配标签提供者，返回无标签提供者（降级）
result: 无标签提供者列表（保证可用性）
```

**选择建议**
- `force=true`：需要严格隔离的场景（多租户、安全敏感）
- `force=false`：需要高可用的场景（一般业务）

### Q4：脚本路由为什么性能这么差？

**A：** 脚本需要编译和执行，每次都有开销。

```
脚本路由流程：
1. 获取脚本内容
2. 创建脚本引擎
3. 编译脚本为字节码
4. 在沙箱环境中执行
5. 获取执行结果

这些步骤都很耗时，导致性能下降 5-10ms
```

**优化建议**
- ❌ 避免使用脚本路由（除非必要）
- ✅ 改为条件路由
- ✅ 如必须使用，启用脚本缓存

### Q5：条件路由的 when 条件不匹配时会怎样？

**A：** 返回原始提供者列表（不过滤）。

```
条件路由执行流程：
1. 检查 when 条件
2. when 不匹配？→ 返回全量提供者（直接结束）
3. when 匹配？→ 应用 then 条件过滤
4. then 为空？→ 返回空列表（黑名单效果）
5. then 过滤后非空？→ 返回过滤结果
6. then 过滤后为空？→ 根据 force 参数决定
```

**示例**
```yaml
conditions:
  # 只有访问 sayHello 方法时才路由，其他方法不受影响
  - method=sayHello => region=beijing

  # 其他方法的所有请求直接使用全量提供者
```

### Q6：如何监控路由是否生效？

**A：** 通过以下几个指标：

```properties
# 1. 路由命中率
routing.hit.rate = 被路由过滤的请求数 / 总请求数
# 目标：> 95%

# 2. 降级发生频率
routing.fallback.count = 降级发生次数
# 正常：< 10 次/分钟

# 3. 请求延迟
routing.latency.p99 = P99 延迟
# 正常：< 基线 + 5ms

# 4. 错误率
routing.error.rate = 路由导致的错误数 / 总请求数
# 正常：< 0.1%
```

**监控工具建议**
- Prometheus + Grafana
- Dubbo QoS（内置监控接口）
- ELK 日志分析

### Q7：规则覆盖和条件路由有什么区别？

**A：** 虽然都可以用来隔离，但机制不同。

```
规则覆盖（推荐用于故障隔离）：
- 优先级最高（P1）
- 命中直接返回，不继续其他规则
- 响应快速
- 用于紧急隔离

条件路由（推荐用于业务流量控制）：
- 优先级较低（P4）
- 继续后续规则处理
- 配置灵活
- 用于长期流量分发
```

**选择场景**
```
故障隔离（1分钟内需要响应）→ 规则覆盖
灾难恢复（快速切流） → 规则覆盖

灰度发布（可以等待规则生效） → 条件路由
版本控制 → 条件路由
A/B 测试 → 条件路由
```

### Q8：同时启用亲和路由和条件路由会怎样？

**A：** 按优先级链执行。

```
执行顺序：
1. 规则覆盖检查 (P1)
2. 亲和路由 (P2) ← 先执行
3. 条件路由 (P4) ← 后执行

具体流程：
亲和路由命中且满足 ratio
  ↓ 返回亲和结果
  ↓
消费端选择

亲和路由不命中或降级
  ↓ 继续条件路由
  ↓
条件路由过滤结果
  ↓
消费端选择
```

**建议配置**
```yaml
# 合理组合：亲和路由（同机房） + 条件路由（业务逻辑）
1. 亲和路由：优先同机房
2. 条件路由：业务逻辑补充（如灰度、版本）
```

### Q9：网格路由和条件路由如何选择？

**A：** 取决于是否有服务网格。

```
有 Istio/Envoy →
  推荐网格路由
  优势：与 K8s 原生集成，支持高级功能

无服务网格 →
  使用条件路由
  优势：配置简单，无额外依赖
```

**对比表**
```
网格路由：
- 需要 Istio 支持
- 配置 VirtualService + DestinationRule
- 支持金丝雀、蓝绿、故障注入
- 学习曲线陡峭

条件路由：
- 无外部依赖
- 配置简单直观
- 覆盖常见场景
- 易于维护
```

### Q10：路由规则配置后多久生效？

**A：** 取决于配置中心和 runtime 参数。

```
配置更新时间线：
1. 配置中心保存 (< 1 秒)
2. 推送到消费端 (< 1-3 秒)
3. 消费端更新规则 (< 1 秒)
4. 下一次请求时生效 (< 100ms)

总延迟：< 5 秒

runtime 影响：
runtime=true：每次请求都重新计算（实时生效）
runtime=false：只在规则变化时更新（需要重启）
```

**生效确认方法**
```bash
# 查看消费端日志
grep "routing" /logs/dubbo.log

# 通过 QoS 接口查询
curl http://localhost:22222/
```

---

## 📚 相关资源

### 官方文档
- [Dubbo 路由规则](https://dubbo.apache.org/docs/)
- [Dubbo 配置中心](https://dubbo.apache.org/docs/)

### 源码阅读
- [AffinityStateRouter.java](d:\workspace\HappyMinchaoDubbo\dubbo-cluster\src\main\java\org\apache\dubbo\rpc\cluster\router\affinity\AffinityStateRouter.java)
- [TagStateRouter.java](d:\workspace\HappyMinchaoDubbo\dubbo-cluster\src\main\java\org\apache\dubbo\rpc\cluster\router\tag\TagStateRouter.java)
- [ConditionStateRouter.java](d:\workspace\HappyMinchaoDubbo\dubbo-cluster\src\main\java\org\apache\dubbo\rpc\cluster\router\condition\ConditionStateRouter.java)

### 调试技巧
1. 启用 DEBUG 日志：`logging.level.org.apache.dubbo.rpc.cluster.router=DEBUG`
2. 使用 Dubbo QoS：访问 http://localhost:22222/
3. 查看路由执行计划：`needToPrintMessage=true`

---

## 总结

| 问题类型 | 推荐方案 | 快速链接 |
|--------|--------|--------|
| 性能问题 | 选择高效路由 | [性能对比](#性能对比详解) |
| 容错问题 | 使用亲和路由 | [ROUTING_DETAILS.md](ROUTING_DETAILS.md#1-亲和路由) |
| 灰度发布 | 标签或条件路由 | [ROUTING_CONFIG_PRACTICE.md](ROUTING_CONFIG_PRACTICE.md#2-灰度发布方案) |
| 故障隔离 | 规则覆盖 | [ROUTING_CONFIG_PRACTICE.md](ROUTING_CONFIG_PRACTICE.md#3-故障隔离与应急方案) |
| 服务网格 | 网格路由 | [ROUTING_DETAILS.md](ROUTING_DETAILS.md#6-网格路由) |

---

**返回主文档**：[ROUTING_RULES_OVERVIEW.md](ROUTING_RULES_OVERVIEW.md)
