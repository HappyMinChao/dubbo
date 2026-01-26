# Dubbo3 路由规则完整指南

本文档提供了 Dubbo3 中所有可用路由规则的完整分析和对比，包括亲和路由、标签路由、条件路由、规则覆盖、脚本路由和网格路由。

## 📚 文档结构

本指南分为以下几个部分，建议按顺序阅读：

1. **[全景对比表](ROUTING_COMPARISON_TABLE.md)** - 6种路由规则的快速对比
2. **[路由规则详解](ROUTING_DETAILS.md)** - 每种路由的详细分析和源码解读
3. **[配置与实践](ROUTING_CONFIG_PRACTICE.md)** - 配置示例和最佳实践
4. **[性能与常见问题](ROUTING_PERFORMANCE_FAQ.md)** - 性能对比和Q&A

## 🚀 快速开始

### 根据场景选择路由规则

**同机房调用、跨域容灾**
- 推荐：亲和路由（Affinity Router）
- 原因：自动匹配 + 强大的容错能力（ratio 降级）
- 参考：[ROUTING_DETAILS.md#1-亲和路由](ROUTING_DETAILS.md#1-亲和路由)

**灰度发布、多租户隔离**
- 推荐：标签路由（Tag Router）或条件路由（Condition Router）
- 参考：[ROUTING_DETAILS.md#2-标签路由](ROUTING_DETAILS.md#2-标签路由)

**流量分发、版本控制**
- 推荐：条件路由（Condition Router）
- 原因：支持多维度条件表达式，灵活度最高
- 参考：[ROUTING_DETAILS.md#3-条件路由](ROUTING_DETAILS.md#3-条件路由)

**故障隔离、紧急禁用**
- 推荐：规则覆盖（通过条件路由实现）
- 原因：优先级最高，响应最快
- 参考：[ROUTING_DETAILS.md#4-规则覆盖](ROUTING_DETAILS.md#4-规则覆盖)

## 📊 核心特性速览

| 特性 | 亲和路由 | 标签路由 | 条件路由 | 规则覆盖 | 脚本路由 | 网格路由 |
|-----|--------|--------|--------|--------|--------|--------|
| 学习难度 | ⭐ 简单 | ⭐⭐ | ⭐⭐⭐ | ⭐ 简单 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 容错能力 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐ |
| 性能影响 | 低 | 中 | 中 | 低 | 高 | 中 |
| 优先级 | P2 | P3 | P4 | P1 | P5 | P6 |

## 📖 详细内容导航

- **想快速对比各种路由？** → 查看 [ROUTING_COMPARISON_TABLE.md](ROUTING_COMPARISON_TABLE.md)
- **想深入理解某种路由的实现？** → 查看 [ROUTING_DETAILS.md](ROUTING_DETAILS.md)
- **想了解如何配置和最佳实践？** → 查看 [ROUTING_CONFIG_PRACTICE.md](ROUTING_CONFIG_PRACTICE.md)
- **想对比性能或查看常见问题？** → 查看 [ROUTING_PERFORMANCE_FAQ.md](ROUTING_PERFORMANCE_FAQ.md)

## 🔗 相关源码

关键实现类位置：
- 亲和路由：`dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/router/affinity/AffinityStateRouter.java`
- 标签路由：`dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/router/tag/TagStateRouter.java`
- 条件路由：`dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/router/condition/ConditionStateRouter.java`
- 脚本路由：`dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/router/script/ScriptStateRouter.java`
- 网格路由：`dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/router/mesh/route/MeshRuleRouter.java`

## ℹ️ 文档更新时间

- 创建时间：2025-12-26
- 基于版本：Dubbo 3.x

---

**提示：** 这是一份综合指南，如果您有具体问题，建议先查看相应主题的详细文档。
