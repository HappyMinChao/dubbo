# Tag Router 代码实战指南

## 一、核心代码片段解读

### 1. TagStateRouter.doRoute() - 路由核心逻辑

```java
@Override
public BitList<Invoker<T>> doRoute(
        BitList<Invoker<T>> invokers,
        URL url,
        Invocation invocation,
        boolean needToPrintMessage,
        Holder<RouterSnapshotNode<T>> nodeHolder,
        Holder<String> messageHolder)
        throws RpcException {
    
    // 第1步: 检查 invokers 是否为空
    if (CollectionUtils.isEmpty(invokers)) {
        if (needToPrintMessage) {
            messageHolder.set("Directly Return. Reason: Invokers from previous router is empty.");
        }
        return invokers;
    }

    // 第2步: 获取规则副本（volatile 变量，避免并发修改）
    final TagRouterRule tagRouterRuleCopy = tagRouterRule;
    
    // 第3步: 检查规则是否有效且启用
    if (tagRouterRuleCopy == null || !tagRouterRuleCopy.isValid() || !tagRouterRuleCopy.isEnabled()) {
        if (needToPrintMessage) {
            messageHolder.set("Disable Tag Router. Reason: tagRouterRule is invalid or disabled");
        }
        // 降级到静态标签路由（使用URL参数中的tag）
        return filterUsingStaticTag(invokers, url, invocation);
    }

    BitList<Invoker<T>> result = invokers;
    
    // 第4步: 获取请求的TAG
    // 优先级: Invocation attachment > URL parameter
    String tag = StringUtils.isEmpty(invocation.getAttachment(TAG_KEY))
            ? url.getParameter(TAG_KEY)
            : invocation.getAttachment(TAG_KEY);

    // 第5步: 判断是否指定了TAG
    if (StringUtils.isNotEmpty(tag)) {
        // ===== 有TAG的处理逻辑 =====
        Map<String, Set<String>> tagnameToAddresses = tagRouterRuleCopy.getTagnameToAddresses();
        
        // 级联查询地址（支持多级标签降级）
        Set<String> addresses = selectAddressByTagLevel(tagnameToAddresses, tag, isForceUseTag(invocation));
        
        // 如果动态查询到了地址
        if (addresses != null) { // null 表示该标签未配置
            // 按 addresses 过滤 invokers
            result = filterInvoker(invokers, invoker -> addressMatches(invoker.getUrl(), addresses));
            
            // 如果有结果 或 强制使用标签，直接返回
            if (CollectionUtils.isNotEmpty(result) || tagRouterRuleCopy.isForce()) {
                if (needToPrintMessage) {
                    messageHolder.set(
                            "Use tag " + tag + " to route. Reason: result is not null OR it's null but force=true");
                }
                return result;
            }
        } else {
            // 动态查询失败，检查静态标签
            result = filterInvoker(
                    invokers, invoker -> tag.equals(invoker.getUrl().getParameter(TAG_KEY)));
        }
        
        // 如果有结果 或 强制使用标签，返回
        if (CollectionUtils.isNotEmpty(result) || isForceUseTag(invocation)) {
            if (needToPrintMessage) {
                messageHolder.set("Use tag " + tag
                        + " to route. Reason: result is not empty or ForceUseTag key is true in invocation");
            }
            return result;
        }
        
        // 都失败了，FAILOVER 降级处理
        else {
            BitList<Invoker<T>> tmp = filterInvoker(
                    invokers, invoker -> addressNotMatches(invoker.getUrl(), tagRouterRuleCopy.getAddresses()));
            if (needToPrintMessage) {
                messageHolder.set("FAILOVER: return all Providers without any tags");
            }
            // 返回所有没有任何标签的 invokers
            return filterInvoker(
                    tmp, invoker -> StringUtils.isEmpty(invoker.getUrl().getParameter(TAG_KEY)));
        }
    } else {
        // ===== 无TAG的处理逻辑 =====
        Set<String> addresses = tagRouterRuleCopy.getAddresses();
        
        if (CollectionUtils.isNotEmpty(addresses)) {
            // 排除所有带标签的地址
            result = filterInvoker(invokers, invoker -> addressNotMatches(invoker.getUrl(), addresses));
            
            // 如果全部被排除，返回空
            if (CollectionUtils.isEmpty(result)) {
                if (needToPrintMessage) {
                    messageHolder.set("all addresses are in dynamic tag group, return empty list");
                }
                return result;
            }
        }
        
        // 继续过滤掉有静态标签的 invokers
        if (needToPrintMessage) {
            messageHolder.set("filter using the static tag group");
        }
        return filterInvoker(result, invoker -> {
            String localTag = invoker.getUrl().getParameter(TAG_KEY);
            return StringUtils.isEmpty(localTag);
        });
    }
}
```

**关键注意点**:
1. `tagRouterRuleCopy` 使用 `volatile` 的本地副本，避免并发问题
2. `SelectAddressByTagLevel` 支持多级标签级联降级
3. 四层防护: 动态标签 → 静态标签 → force 判断 → failover
4. 无TAG时，需要排除所有带标签的地址

---

### 2. selectAddressByTagLevel() - 标签级联查询

```java
public static Set<String> selectAddressByTagLevel(
        Map<String, Set<String>> tagAddresses, 
        String tagSelector, 
        boolean isForce) {
    
    // 情况1: 强制查询 或 标签内无分隔符
    // 在这种情况下，直接返回对应标签的地址，不进行降级
    if (isForce || StringUtils.isNotContains(tagSelector, TAG_SEPERATOR)) {
        return tagAddresses.get(tagSelector);
    }
    
    // 情况2: 支持级联查询（多级标签）
    // 例: "beta|team1|partner1" → 支持逐级降级
    String[] selectors = StringUtils.split(tagSelector, TAG_SEPERATOR);
    
    // 从最长的标签开始，逐个缩短
    // i=3: "beta|team1|partner1"
    // i=2: "beta|team1"
    // i=1: "beta"
    for (int i = selectors.length; i > 0; i--) {
        // 拼接前 i 个元素
        String selectorTmp = StringUtils.join(selectors, TAG_SEPERATOR, 0, i);
        Set<String> addresses = tagAddresses.get(selectorTmp);
        
        // 找到了，立即返回
        if (CollectionUtils.isNotEmpty(addresses)) {
            return addresses;
        }
    }
    
    // 全部失败，返回 null
    // 这会触发静态标签或 failover 处理
    return null;
}

/*
 * 时间复杂度分析:
 * 
 * 最坏情况: O(n*m)
 *   n: selectors 分段数 (通常 1-5)
 *   m: String.join() 的复杂度，约为 O(n)
 * 
 * 平均情况: O(1) 到 O(n)
 *   首次命中时立即返回
 * 
 * 优化建议:
 *   可使用 Trie 树预构建标签前缀树
 *   从而避免重复拼接字符串
 */
```

**执行轨迹示例**:

```
输入:
  tagAddresses = {
    "gray|bj|zone1" → {"10.0.0.1:20880", "10.0.0.2:20880"},
    "gray|bj" → {"10.0.0.3:20880"},
    "gray" → {"10.0.0.4:20880"}
  }
  tagSelector = "gray|bj|zone2"
  isForce = false

执行过程:
  step1: selectors = ["gray", "bj", "zone2"], length=3
  step2: i=3, selectorTmp="gray|bj|zone2"
         addresses = tagAddresses.get("gray|bj|zone2") = null
         继续
  step3: i=2, selectorTmp="gray|bj"
         addresses = tagAddresses.get("gray|bj") = {"10.0.0.3:20880"}
         命中! 返回 {"10.0.0.3:20880"}

输出: {"10.0.0.3:20880"}
```

---

### 3. checkAddressMatch() - IP地址匹配

```java
private boolean checkAddressMatch(Set<String> addresses, String host, int port) {
    // 遍历所有配置的地址
    for (String address : addresses) {
        try {
            // 方式1: 使用 IP 表达式匹配（支持通配符、范围等）
            // 例: "192.168.1.*:20880", "192.168.1.1-10:20880"
            if (NetUtils.matchIpExpression(address, host, port)) {
                return true;  // 匹配成功
            }
            
            // 方式2: ANYHOST 匹配
            // 例: "*:20880" 匹配任意 IP 的 20880 端口
            if ((ANYHOST_VALUE + ":" + port).equals(address)) {
                return true;
            }
            
        } catch (Exception e) {
            // IP 格式有问题，记录错误日志，继续检查下一个
            logger.error(
                    CLUSTER_TAG_ROUTE_INVALID,
                    "tag route address is invalid",
                    "",
                    "The format of ip address is invalid in tag route. Address :" + address,
                    e);
        }
    }
    
    // 全部失败，返回 false
    return false;
}

/*
 * 支持的地址格式:
 * 
 * 1. 精确匹配
 *    address: "192.168.1.10:20880"
 *    host: "192.168.1.10", port: 20880
 *    结果: ✓
 * 
 * 2. 通配符 (单段)
 *    address: "192.168.1.*:20880"
 *    host: "192.168.1.100", port: 20880
 *    结果: ✓
 * 
 * 3. 范围
 *    address: "192.168.1.1-10:20880"
 *    host: "192.168.1.5", port: 20880
 *    结果: ✓
 * 
 * 4. ANYHOST
 *    address: "*:20880"
 *    host: "任意", port: 20880
 *    结果: ✓
 * 
 * 时间复杂度:
 *   O(n*k)
 *   n: addresses 集合大小
 *   k: IP 表达式匹配的复杂度
 */
```

---

### 4. TagRouterRule.init() - 规则初始化

```java
public void init(TagStateRouter<?> router) {
    // 第1步: 检查规则有效性
    if (!isValid()) {
        return;  // 无效规则，不做初始化
    }

    BitList<? extends Invoker<?>> invokers = router.getInvokers();

    // 第2步: 处理带 addresses 字段的标签（所有版本支持）
    // 这是静态配置，规则下发时直接指定地址
    tags.stream()
            .filter(tag -> CollectionUtils.isNotEmpty(tag.getAddresses()))
            .forEach(tag -> {
                // 构建 标签→地址 映射
                tagnameToAddresses.put(tag.getName(), new HashSet<>(tag.getAddresses()));
                
                // 构建反向映射 地址→标签
                tag.getAddresses().forEach(addr -> {
                    Set<String> tagNames = addressToTagnames.computeIfAbsent(addr, k -> new HashSet<>());
                    tagNames.add(tag.getName());
                });
            });

    // 第3步: 处理带 match 字段的标签（仅 v3.0+ 支持）
    // 这是动态配置，根据 Invoker URL 参数动态匹配
    if (this.getVersion() != null && this.getVersion().startsWith(RULE_VERSION_V30)) {
        if (CollectionUtils.isNotEmpty(invokers)) {
            tags.stream()
                    .filter(tag -> CollectionUtils.isEmpty(tag.getAddresses()))  // 仅处理无 addresses 的
                    .forEach(tag -> {
                        Set<String> addresses = new HashSet<>();
                        List<ParamMatch> paramMatchers = tag.getMatch();
                        
                        // 遍历所有 invokers，检查是否匹配该标签的所有条件
                        invokers.forEach(invoker -> {
                            boolean isMatch = true;
                            
                            // 逐个检查 ParamMatch 条件
                            for (ParamMatch matcher : paramMatchers) {
                                // 获取 invoker URL 中的原始参数值
                                String paramValue = invoker.getUrl().getOriginalParameter(matcher.getKey());
                                
                                // 检查是否匹配
                                if (!matcher.isMatch(paramValue)) {
                                    isMatch = false;
                                    break;  // 有一个不匹配就结束
                                }
                            }
                            
                            // 所有条件都匹配，则将该 invoker 的地址添加到标签
                            if (isMatch) {
                                addresses.add(invoker.getUrl().getAddress());
                            }
                        });
                        
                        // 仅当有匹配的地址时才添加到映射
                        if (CollectionUtils.isNotEmpty(addresses)) {
                            tagnameToAddresses.put(tag.getName(), addresses);
                        }
                    });
        }
    }
    
    /*
     * 初始化完成后的数据结构:
     * 
     * tagnameToAddresses:
     *   "gray" → {"10.0.0.1:20880", "10.0.0.2:20880"}
     *   "gray|bj" → {"10.0.0.3:20880"}
     *   ...
     * 
     * addressToTagnames:
     *   "10.0.0.1:20880" → {"gray", "gray|bj"}
     *   "10.0.0.3:20880" → {"gray|bj"}
     *   ...
     * 
     * 用处:
     *   tagnameToAddresses: 支持 doRoute() 中的标签查询
     *   addressToTagnames: 备用，可用于反向查询某地址的标签
     */
}
```

**关键步骤**:
1. **步骤2** (addresses字段): 直接使用规则中的地址列表
2. **步骤3** (match字段): 动态扫描所有 invoker，根据参数匹配动态绑定地址
3. 两个数据结构的同时构建，支持快速查询

---

### 5. filterInvoker() - invoker 过滤

```java
private <T> BitList<Invoker<T>> filterInvoker(
        BitList<Invoker<T>> invokers, 
        Predicate<Invoker<T>> predicate) {
    
    // 优化1: 快速路径
    // 如果所有 invokers 都满足谓词，无需创建新列表
    if (invokers.stream().allMatch(predicate)) {
        return invokers;  // 直接返回原列表
    }

    // 优化2: 创建副本后再过滤
    // 使用 clone() 创建一个新的 BitList（节省内存）
    BitList<Invoker<T>> newInvokers = invokers.clone();
    
    // 按谓词移除不符合条件的 invoker
    newInvokers.removeIf(invoker -> !predicate.test(invoker));

    return newInvokers;
}

/*
 * 调用示例:
 * 
 * // 过滤1: 按 addresses 匹配
 * result = filterInvoker(invokers, 
 *   invoker -> addressMatches(invoker.getUrl(), addresses));
 * 
 * // 过滤2: 按 tag 匹配
 * result = filterInvoker(invokers, 
 *   invoker -> tag.equals(invoker.getUrl().getParameter(TAG_KEY)));
 * 
 * // 过滤3: 排除 tagged 地址
 * result = filterInvoker(invokers, 
 *   invoker -> addressNotMatches(invoker.getUrl(), taggedAddresses));
 * 
 * 时间复杂度: O(n*k)
 *   n: invokers 数量
 *   k: 谓词 predicate 的执行成本
 */
```

**性能特点**:
- 快速路径: 所有都符合 → 直接返回原列表 O(n)
- 慢速路径: 部分符合 → clone + removeIf O(n)
- 避免频繁创建新的 List 对象

---

## 二、使用场景代码示例

### 场景1: 灰度发布

**YAML 规则配置**:

```yaml
---
configVersion: v3.0
force: false
runtime: true
enabled: true
priority: 1
key: user-service

tags:
  - name: gray
    match:
      - key: gray_flag
        value:
          exact: "true"
      - key: region
        value:
          exact: "cn-bj"
```

**消费端调用代码**:

```java
@Reference(version = "1.0.0")
private UserService userService;

public void callUserService() {
    // 方式1: 通过 RpcContext 设置 TAG
    RpcContext.getContext().setAttachment("tag", "gray");
    
    // 方式2: 通过 Invocation 设置 TAG
    // RpcInvocation invocation = new RpcInvocation();
    // invocation.setAttachment("tag", "gray");
    
    // 执行调用
    User user = userService.getUser(123);
    // 该调用会自动路由到灰度版本的提供者
}
```

**提供端服务发布**:

```java
@Service(version = "1.0.0")
@Configuration
public class UserServiceImpl implements UserService {
    
    @Override
    public User getUser(Long id) {
        return new User(id, "Gray User");
    }
}

@SpringBootApplication
@DubboComponentScan(basePackages = {"com.example.service"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// application.yml
dubbo:
  application:
    name: user-service
    version: 1.0.0
  protocol:
    name: dubbo
    port: 20880
  registry:
    address: nacos://127.0.0.1:8848
  
  # 服务提供者的标签配置
  provider:
    parameters:
      gray_flag: "true"
      region: "cn-bj"
```

**路由工作流**:

```
1. 消费端设置 tag="gray"
   RpcContext.getContext().setAttachment("tag", "gray")

2. TagStateRouter.doRoute() 执行
   - 获取 tag="gray"
   - 从配置中心拉取规则
   - 初始化 tagnameToAddresses:
     "gray" → {provider1_address, provider2_address}
   
3. 参数匹配过程
   - 检查 provider1 的 gray_flag=true ✓
   - 检查 provider1 的 region=cn-bj ✓
   - provider1 匹配成功，添加到 "gray" 标签
   
   - 检查 provider2 的 gray_flag=false ✗
   - provider2 匹配失败，不添加

4. 返回过滤结果
   - selectAddressByTagLevel("gray") → 返回 {provider1_address}
   - filterInvoker() → 返回 [provider1_invoker]
   - 负载均衡选择 provider1 执行

5. 调用完成
   User user = provider1.getUser(123)
```

---

### 场景2: 多地域容灾

**YAML 规则配置**:

```yaml
---
force: false
runtime: true
enabled: true
key: order-service

tags:
  - name: region|bj|zone1
    addresses:
      - "192.168.1.1:20880"
      - "192.168.1.2:20880"
  
  - name: region|bj|zone2
    addresses:
      - "192.168.1.3:20880"
  
  - name: region|bj
    addresses:
      - "192.168.1.4:20880"
  
  - name: region|sh
    addresses:
      - "192.168.2.1:20880"
  
  - name: region
    addresses:
      - "10.0.0.1:20880"  # 全国备份
```

**消费端调用代码**:

```java
@Reference
private OrderService orderService;

@Autowired
private RegionDetector regionDetector;

public Order createOrder(OrderRequest request) {
    // 检测用户所在地域
    String region = regionDetector.detectRegion();  // 例: "region|bj|zone1"
    
    // 设置标签
    RpcContext.getContext().setAttachment("tag", region);
    
    try {
        // 优先路由到用户同地域的服务器
        return orderService.create(request);
    } catch (Exception e) {
        // 若同地域无可用服务，自动降级到其他地域（由 TagRouter 自动处理）
        logger.warn("Order creation failed in region: " + region, e);
        throw e;
    }
}
```

**路由降级示例**:

```
场景: 用户来自北京zone2，但zone2无可用实例

消费端请求:
  tag = "region|bj|zone2"

TagRouter 处理过程:
  step1: selectAddressByTagLevel("region|bj|zone2")
         查询 "region|bj|zone2" → {192.168.1.3}
         若 192.168.1.3 宕机，返回空
  
  step2: 检查静态标签
         查询 "region|bj|zone2" 静态TAG → 无
  
  step3: FAILOVER 降级
         继续在 "region|bj|zone2" 基础上降级
         ...实际上这里需要重新调用一次，从 "region|bj" 开始

最优方案: 第一次调用中，tagSelector 应改为 "region|bj"
  tagSelector = "region|bj"
  step1: selectAddressByTagLevel("region|bj")
         查询 "region|bj" → {192.168.1.4}
         成功 ✓

或者使用多级标签设计:
  first-try: "region|bj|zone2" → 失败
  fallback: "region|bj" → 成功
```

---

### 场景3: VIP 用户专线

**YAML 规则配置**:

```yaml
---
configVersion: v3.0
force: true
runtime: true
enabled: true
key: payment-service

tags:
  - name: vip
    match:
      - key: user_level
        value:
          exact: "vip"
  
  - name: normal
    match:
      - key: user_level
        value:
          exact: "normal"
  
  - name: free
    match:
      - key: user_level
        value:
          exact: "free"
```

**消费端调用代码**:

```java
@Service
public class PaymentService {
    
    @Reference
    private PaymentGateway paymentGateway;
    
    public PaymentResult process(Payment payment) {
        // 确定用户等级
        String userLevel = getUserLevel(payment.getUserId());
        
        // 设置标签，路由到对应的提供者集群
        RpcContext.getContext().setAttachment("tag", userLevel);
        
        return paymentGateway.pay(payment);
    }
    
    private String getUserLevel(Long userId) {
        // 从数据库或缓存查询用户等级
        // VIP 用户: "vip" → 高性能服务器
        // 普通用户: "normal" → 标准服务器
        // 免费用户: "free" → 共享服务器
        return userRepository.getUserLevel(userId);
    }
}
```

**提供端配置**:

```java
// VIP 提供者 (高配)
@Service
@Configuration
@ConditionalOnProperty(
    name = "payment.tier", 
    havingValue = "vip"
)
public class VIPPaymentGatewayImpl implements PaymentGateway {
    // 高性能实现，支持大额交易
}

// 普通提供者
@Service
@Configuration
@ConditionalOnProperty(
    name = "payment.tier", 
    havingValue = "normal"
)
public class NormalPaymentGatewayImpl implements PaymentGateway {
    // 标准实现
}

// application-vip.yml
payment:
  tier: vip

dubbo:
  provider:
    parameters:
      user_level: "vip"
```

**路由工作流**:

```
VIP 用户支付 ¥10000:
  1. 设置 tag="vip"
  2. TagRouter 初始化时动态匹配:
     - 检查 VIP提供者1 的 user_level=vip ✓
     - 检查 VIP提供者2 的 user_level=vip ✓
     - tagnameToAddresses["vip"] = {vip_provider1, vip_provider2}
  3. selectAddressByTagLevel("vip") → {vip_provider1, vip_provider2}
  4. 路由到 VIP 高性能服务器处理
  5. ✅ 支持大额交易，处理速度快

普通用户支付 ¥100:
  1. 设置 tag="normal"
  2. 路由到普通服务器处理
  3. ✅ 标准处理流程

免费用户支付 ¥1:
  1. 设置 tag="free"
  2. 路由到共享服务器处理
  3. ✅ 最佳性价比
```

---

## 三、常见陷阱及解决方案

### 陷阱1: TAG 未生效

**问题**:
```
消费端设置了 tag，但仍然路由到了非预期的提供者
```

**排查步骤**:

```java
// step1: 检查规则是否已下发
TagStateRouter router = (TagStateRouter) routerFactory.getRouter(...);
System.out.println("Rule: " + router.tagRouterRule);
System.out.println("Valid: " + router.tagRouterRule.isValid());
System.out.println("Enabled: " + router.tagRouterRule.isEnabled());

// step2: 检查规则的标签映射
TagRouterRule rule = router.tagRouterRule;
System.out.println("Tag names: " + rule.getTagNames());
System.out.println("Addresses: " + rule.getTagnameToAddresses());

// step3: 检查 TAG 值是否正确
RpcContext.getContext().getAttachment("tag");

// step4: 启用 DEBUG 日志
// 在 logback.xml 中添加
// <logger name="org.apache.dubbo.rpc.cluster.router.tag" level="DEBUG"/>
```

**常见原因**:
1. 规则未被初始化 (rule.isValid() = false)
2. 规则中的标签名与请求 TAG 不匹配
3. 标签对应的地址列表为空
4. force=true 但无匹配提供者

---

### 陷阱2: FAILOVER 降级不符合预期

**问题**:
```
标签无可用提供者时，降级到了意外的提供者
```

**解决方案**:

```java
// 确保 force 参数设置正确
String forceTag = invocation.getAttachment("force.tag");
System.out.println("Force tag: " + forceTag);

// 理解 3 层防护的逻辑
// layer1: 动态标签 (tagnameToAddresses)
// layer2: 静态标签 (url.parameter[TAG_KEY])
// layer3: failover (排除所有tagged地址)

// 若想完全禁用降级，设置:
rule.setForce(true);
// 此时若无匹配提供者，直接返回空，调用失败
```

---

### 陷阱3: 性能问题

**问题**:
```
调用延迟增加，profile 显示 doRoute() 耗时过长
```

**优化建议**:

```java
// 1. 使用 BitList 避免频繁创建 List
// ✗ 不好
List<Invoker> result = new ArrayList<>(invokers);

// ✓ 好
BitList<Invoker> result = invokers.clone();

// 2. 缓存地址匹配结果（若IP表达式复杂）
// 在 ParamMatch 或 Tag 中缓存已匹配的 invokers

// 3. 预构建标签树（若标签层级很深）
// 使用 Trie 树优化 selectAddressByTagLevel()

// 4. 减少规则扫描
// 使用索引而非逐个遍历 tags
```

---

## 四、调试技巧

### 技巧1: 启用详细日志

```xml
<!-- logback.xml -->
<configuration>
    <logger name="org.apache.dubbo.rpc.cluster.router.tag" level="DEBUG"/>
    
    <!-- 更详细的日志 -->
    <logger name="org.apache.dubbo.rpc.cluster.router" level="TRACE"/>
</configuration>
```

### 技巧2: 单元测试验证

```java
@Test
public void testTagRouterRule() {
    String yaml = "---\n" +
        "force: false\n" +
        "runtime: true\n" +
        "enabled: true\n" +
        "key: demo-provider\n" +
        "tags:\n" +
        "  - name: gray\n" +
        "    addresses: [\"192.168.1.10:20880\", \"192.168.1.11:20880\"]\n" +
        "  - name: prod\n" +
        "    addresses: [\"192.168.1.20:20880\"]\n";
    
    TagRouterRule rule = TagRuleParser.parse(yaml);
    
    // 验证规则
    Assert.assertTrue(rule.isValid());
    Assert.assertTrue(rule.isEnabled());
    
    // 验证标签映射
    Assert.assertEquals(2, rule.getTagNames().size());
    Assert.assertEquals(2, rule.getTagnameToAddresses().get("gray").size());
    
    // 验证地址映射
    Assert.assertTrue(rule.getTagnameToAddresses().containsKey("gray"));
    Assert.assertTrue(rule.getTagnameToAddresses().containsKey("prod"));
}

@Test
public void testSelectAddressByTagLevel() {
    Map<String, Set<String>> tagAddresses = new HashMap<>();
    tagAddresses.put("gray|bj|zone1", new HashSet<>(Arrays.asList("10.0.0.1", "10.0.0.2")));
    tagAddresses.put("gray|bj", new HashSet<>(Arrays.asList("10.0.0.3")));
    tagAddresses.put("gray", new HashSet<>(Arrays.asList("10.0.0.4")));
    
    // 测试精确匹配
    Set<String> result = TagStateRouter.selectAddressByTagLevel(
        tagAddresses, "gray|bj|zone1", false
    );
    Assert.assertEquals(2, result.size());
    
    // 测试降级
    result = TagStateRouter.selectAddressByTagLevel(
        tagAddresses, "gray|bj|zone2", false
    );
    Assert.assertEquals(1, result.size());
    Assert.assertTrue(result.contains("10.0.0.3"));
    
    // 测试强制匹配（无降级）
    result = TagStateRouter.selectAddressByTagLevel(
        tagAddresses, "gray|bj|zone2", true
    );
    Assert.assertNull(result);
}
```

### 技巧3: QoS 控制台查看

```
访问: http://localhost:22222/
查看路由信息
```

