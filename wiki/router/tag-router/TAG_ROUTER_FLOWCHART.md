# Tag Router 流程图集合

## 1. 核心路由决策流程图

```mermaid
flowchart TD
    Start["🚀 消费端RPC调用<br/>TagStateRouter.doRoute"] -->|接收| Input["📥 输入<br/>- invokers列表<br/>- 请求URL<br/>- Invocation"]
    
    Input --> CheckInvokers{"✅ invokers<br/>是否为空?"}
    
    CheckInvokers -->|是| ReturnEmpty["📤 返回空列表"]
    CheckInvokers -->|否| GetRule["🔍 获取 tagRouterRule"]
    
    GetRule --> ValidRule{"✔ 规则是否<br/>有效&启用?"}
    
    ValidRule -->|否| UseStatic["使用静态标签<br/>filterUsingStaticTag"]
    ValidRule -->|是| GetTag["📋 获取请求TAG<br/>invocation.attachment[TAG_KEY]<br/>或 url.parameter[TAG_KEY]"]
    
    GetTag --> HasTag{"🏷 是否<br/>指定TAG?"}
    
    HasTag -->|否| NoTagPath["➡️ 无TAG分支"]
    HasTag -->|是| WithTagPath["➡️ 有TAG分支"]
    
    WithTagPath --> DynamicLookup["🔎 动态标签查询<br/>selectAddressByTagLevel"]
    DynamicLookup --> GetAddresses["📍 返回 Set&lt;String&gt; addresses"]
    GetAddresses --> AddrNotNull{"addresses<br/>是否为null?"}
    
    AddrNotNull -->|是| CheckStatic["检查静态标签<br/>url.getParameter[TAG_KEY]"]
    AddrNotNull -->|否| FilterByAddr["🎯 按addresses<br/>过滤invokers<br/>addressMatches"]
    FilterByAddr --> FilterResult{"过滤结果<br/>非空 OR<br/>Force=true?"}
    
    FilterResult -->|是| ReturnFiltered["✅ 返回过滤结果"]
    FilterResult -->|否| CheckStatic
    
    CheckStatic --> StaticExists{"存在静态TAG<br/>匹配的invoker?"}
    StaticExists -->|是| ReturnStatic["✅ 返回静态TAG结果"]
    StaticExists -->|否| CheckForce{"Force<br/>=true?"}
    CheckForce -->|是| ReturnEmpty2["📤 返回空"]
    CheckForce -->|否| Failover["⚠️ FAILOVER降级<br/>返回无任何标签的invokers"]
    
    NoTagPath --> GetAllAddr["获取所有dynamic<br/>tagged addresses"]
    GetAllAddr --> HasTaggedAddr{"有tagged<br/>地址?"}
    HasTaggedAddr -->|是| ExcludeTagged["排除tagged地址<br/>addressNotMatches"]
    HasTaggedAddr -->|否| ReturnAll["✅ 返回所有invokers"]
    ExcludeTagged --> HasRemain{"还有<br/>剩余?"}
    HasRemain -->|是| ExcludeStatic["再排除有static TAG<br/>的invokers"]
    HasRemain -->|否| ReturnAll
    ExcludeStatic --> ReturnFinal["✅ 返回结果"]
    
    UseStatic --> ReturnStatic
    Failover --> ReturnAll2["✅ 返回结果"]
    ReturnEmpty --> Output["📤 最终返回<br/>List&lt;Invoker&gt;"]
    ReturnFiltered --> Output
    ReturnStatic --> Output
    ReturnEmpty2 --> Output
    ReturnAll --> Output
    ReturnAll2 --> Output
    ReturnFinal --> Output
    
    Output --> LB["🎲 负载均衡<br/>选择一个invoker执行"]
    LB --> End["✨ RPC调用完成"]
    
    style Start fill:#ff9999
    style Output fill:#99ff99
    style End fill:#99ff99
    style DynamicLookup fill:#99ccff
    style FilterByAddr fill:#99ccff
    style Failover fill:#ffcc99
```

---

## 2. 标签级联查询算法流程

```mermaid
flowchart TD
    Start["🚀 selectAddressByTagLevel"] -->|输入| Input["<br/>tagAddresses: Map<String, Set<String>><br/>tagSelector: 'beta|team1|partner1'<br/>isForce: boolean<br/>"]
    
    Input --> CheckForce{"isForce OR<br/>无'|'分隔符?"}
    
    CheckForce -->|是| DirectGet["直接查询<br/>tagAddresses.get<br/>tagSelector"]
    DirectGet --> ReturnDirect["🎯 返回结果"]
    
    CheckForce -->|否| SplitTag["分割标签<br/>split by '|'<br/>selectors = ['beta','team1','partner1']"]
    
    SplitTag --> InitLoop["初始化循环<br/>i = selectors.length = 3"]
    
    InitLoop --> LoopBody{"i > 0?"}
    
    LoopBody -->|否| ReturnNull["返回 null<br/>全部失败"]
    LoopBody -->|是| BuildSelector["构建选择器<br/>join(selectors, '|', 0, i)<br/>"]
    
    BuildSelector --> Query["查询<br/>addresses = tagAddresses.get<br/>selectorTmp"]
    Query --> CheckResult{"addresses<br/>非空?"}
    
    CheckResult -->|是| ReturnAddr["🎯 返回 addresses"]
    CheckResult -->|否| Decrement["i--<br/>移除最右侧元素"]
    Decrement --> LoopBody
    
    ReturnDirect --> Output["📤 返回结果"]
    ReturnNull --> Output
    ReturnAddr --> Output
    
    Output --> UseCase["调用方: doRoute()<br/>按addresses过滤invokers"]
    
    style Start fill:#ff9999
    style Output fill:#99ff99
    style BuildSelector fill:#ffffcc
    style Query fill:#ffffcc
    style DirectGet fill:#ccffcc

    classDef example fill:#e1f5ff,stroke:#01579b,stroke-width:2px
    class UseCase example
```

**执行示例**:
```
输入: tagSelector="beta|team1|partner1", tagAddresses={
  "beta|team1|partner1": {"10.0.0.1", "10.0.0.2"},
  "beta|team1": {"10.0.0.3"},
  "beta": {"10.0.0.4"}
}

循环过程:
  i=3: 查 "beta|team1|partner1" → 命中 ✓ → 返回 {"10.0.0.1", "10.0.0.2"}
```

---

## 3. 规则初始化流程 (init方法)

```mermaid
flowchart TD
    Start["🚀 TagRouterRule.init<br/>规则初始化"] -->|输入| Input["router: TagStateRouter<br/>invokers: BitList"]
    
    Input --> CheckValid{"规则是否<br/>有效?"}
    CheckValid -->|否| Exit1["直接返回"]
    CheckValid -->|是| Step1["📌 Step1: 处理 addresses 字段<br/>所有版本支持"]
    
    Step1 --> Loop1["for each Tag in tags:<br/>if tag.addresses != null"]
    Loop1 --> DoStep1["<br/>1️⃣ tagnameToAddresses.put<br/>  tag.name, tag.addresses<br/>2️⃣ for each addr in addresses:<br/>  addressToTagnames[addr].add<br/>  tag.name<br/>"]
    
    DoStep1 --> Step2["📌 Step2: 处理 match 字段<br/>仅 v3.0+ 支持"]
    Step2 --> CheckVersion{"version<br/>是否为 v3.0+?"}
    CheckVersion -->|否| SkipMatch["跳过match处理"]
    CheckVersion -->|是| Loop2["for each Tag in tags:<br/>if tag.match != null AND<br/>tag.addresses == null"]
    
    Loop2 --> InitAddrs["addresses = new HashSet()"]
    InitAddrs --> Loop3["for each Invoker in invokers:"]
    Loop3 --> CheckMatch["检查所有 ParamMatch<br/>是否都匹配<br/>url参数?"]
    CheckMatch -->|是| AddAddr["addresses.add<br/>invoker.address"]
    CheckMatch -->|否| Skip["跳过该invoker"]
    Skip --> Loop3
    AddAddr --> Loop3End{"还有invoker?"}
    Loop3End -->|是| Loop3
    Loop3End -->|否| StoreMatched["tagnameToAddresses.put<br/>tag.name, addresses"]
    
    StoreMatched --> Loop2End{"还有Tag?"}
    Loop2End -->|是| Loop2
    Loop2End -->|否| Step3["📌 Step3: 完成<br/>两个Map都已构建"]
    
    SkipMatch --> Step3
    Step3 --> End["✨ 规则初始化完成"]
    Exit1 --> Stop["⛔ 初始化中止"]
    
    Output["📤 结果<br/>- tagnameToAddresses: 标签→地址映射<br/>- addressToTagnames: 地址→标签映射"]
    Step3 --> Output
    
    style Start fill:#ff9999
    style DoStep1 fill:#fff9c4
    style CheckMatch fill:#fff9c4
    style Output fill:#99ff99
    style End fill:#99ff99
```

---

## 4. 地址匹配检查流程

```mermaid
flowchart TD
    Start["🚀 checkAddressMatch"] -->|输入| Input["<br/>addresses: Set&lt;String&gt;<br/>host: String<br/>port: int<br/>"]
    
    Input --> InitLoop["初始化循环<br/>for each addr in addresses"]
    InitLoop --> LoopBody{"还有address<br/>要检查?"}
    
    LoopBody -->|是| TryMatch["尝试匹配<br/>address 与 host:port"]
    TryMatch --> CheckIP["NetUtils.matchIpExpression<br/>address, host, port"]
    CheckIP --> MatchIP{"IP表达式<br/>匹配?"}
    
    MatchIP -->|是| ReturnTrue["✅ 返回 true<br/>匹配成功"]
    MatchIP -->|否| CheckWildcard["检查 ANYHOST 匹配<br/>'*:port' == address?"]
    CheckWildcard --> MatchWildcard{"ANYHOST<br/>匹配?"}
    
    MatchWildcard -->|是| ReturnTrue
    MatchWildcard -->|否| NextLoop["继续下一个address"]
    NextLoop --> LoopBody
    
    LoopBody -->|否| ReturnFalse["❌ 返回 false<br/>全部失败"]
    
    ReturnTrue --> Output["📤 返回匹配结果"]
    ReturnFalse --> Output
    
    TryMatch -.->|异常| LogError["⚠️ 记录日志<br/>IP地址格式错误<br/>继续检查下一个"]
    LogError --> NextLoop
    
    style Start fill:#ff9999
    style ReturnTrue fill:#99ff99
    style ReturnFalse fill:#ffcccc
    style Output fill:#99ff99
    style CheckIP fill:#fff9c4
    style CheckWildcard fill:#fff9c4
```

**支持的地址格式**:
```
1. 精确: 192.168.1.10:20880
2. 通配符: 192.168.1.*:20880
3. 范围: 192.168.1.1-10:20880  
4. ANYHOST: *:20880
```

---

## 5. 配置变更监听和规则刷新流程

```mermaid
flowchart TD
    ConfigCenter["⚙️ 配置中心<br/>Nacos/Zookeeper"]
    ConfigCenter -->|规则变更| Event["ConfigChangedEvent<br/>- key: 'demo-provider.tag-router'<br/>- content: YAML文本<br/>- changeType: ADD/MODIFY/DELETE"]
    
    Event --> Process["🔄 TagStateRouter.process<br/>synchronized 方法"]
    
    Process --> CheckType{"变更类型<br/>是?"}
    
    CheckType -->|DELETED| SetNull["tagRouterRule = null<br/>清除规则"]
    CheckType -->|ADD/MODIFY| Parse["📝 解析规则<br/>TagRuleParser.parse"]
    
    Parse --> ParseYAML["YAML → Map<br/>SafeConstructor"]
    ParseYAML --> ParseMap["Map → TagRouterRule<br/>parseFromMap"]
    ParseMap --> CheckTags{"tags为空<br/>?"}
    CheckTags -->|是| SetInvalid["标记无效<br/>rule.valid = false"]
    CheckTags -->|否| DoInit["规则初始化<br/>rule.init(this)"]
    
    DoInit --> StoreRule["缓存规则<br/>this.tagRouterRule = rule"]
    SetInvalid --> StoreRule
    
    StoreRule --> LogSuccess["📊 记录日志<br/>规则已生效"]
    SetNull --> LogDelete["📊 记录日志<br/>规则已删除"]
    
    Parse -.->|异常| CatchError["⚠️ 捕获异常<br/>格式错误/无效规则"]
    CatchError --> LogError["📊 记录错误日志<br/>CLUSTER_TAG_ROUTE_INVALID"]
    LogError --> NoChange["不更新规则<br/>继续使用旧规则"]
    
    LogSuccess --> Apply["✨ 规则立即生效<br/>下次doRoute()使用"]
    LogDelete --> Apply
    NoChange --> Apply
    
    Apply --> End["调用链"]
    
    style ConfigCenter fill:#e8f5e9
    style Event fill:#fff9c4
    style Process fill:#bbdefb
    style Apply fill:#99ff99
    style CatchError fill:#ffcccc
    style End fill:#99ff99
```

---

## 6. 消费端调用完整时序图

```mermaid
sequenceDiagram
    participant Consumer as 消费端<br/>RPC调用
    participant Router as TagStateRouter<br/>路由器
    participant Rule as TagRouterRule<br/>规则
    participant ConfigCenter as 配置中心<br/>Nacos/ZK
    participant Provider as 服务提供者

    Consumer->>Router: 1. invoke(invokers, url, invocation)
    Router->>Router: 2. notify(invokers)<br/>获取providerApplication
    Router->>ConfigCenter: 3. addListener('demo-provider.tag-router')<br/>订阅规则变更
    ConfigCenter->>Router: 4. ConfigChangedEvent<br/>推送规则内容
    Router->>Rule: 5. process(event)<br/>解析和初始化规则
    Rule->>Rule: 6. init(router)<br/>构建 tagnameToAddresses 映射
    Router->>Router: 7. doRoute(invokers, url, invocation)<br/>执行路由逻辑
    Router->>Rule: 8. getTagnameToAddresses()<br/>查询标签映射
    Router->>Router: 9. selectAddressByTagLevel()<br/>级联查询地址
    Router->>Router: 10. filterInvoker()<br/>按地址过滤调用者
    Router->>Router: 11. addressMatches()<br/>IP匹配检验
    Router->>Consumer: 12. 返回 List<Invoker>
    Consumer->>Consumer: 13. 负载均衡选择一个invoker
    Consumer->>Provider: 14. 发起RPC调用
    Provider->>Provider: 15. 处理请求
    Provider-->>Consumer: 16. 返回响应

    note over Router,Rule: 首次初始化（消费者连接）
    note over Router,ConfigCenter: 规则变更（实时推送）
    note over Consumer,Provider: 每次调用（毫秒级）
```

---

## 7. 数据结构之间的关系图

```mermaid
graph TB
    TagStateRouter["🔶 TagStateRouter<T>"]
    
    TagStateRouter -->|holds| TagRule["🔷 TagRouterRule<br/>- force: boolean<br/>- runtime: boolean<br/>- enabled: boolean<br/>- tags: List<Tag>"]
    
    TagRule -->|contains| Tag["🔸 Tag<br/>- name: String<br/>- addresses: List<String><br/>- match: List<ParamMatch>"]
    
    Tag -->|references| ParamMatch["🔹 ParamMatch<br/>- key: String<br/>- value: StringMatch"]
    
    ParamMatch -->|delegates| StringMatch["StringMatch<br/>- exact: String<br/>- wildcard: String<br/>- prefix: String<br/>- regex: String"]
    
    TagRule -->|builds| TagToAddr["🟨 Map<String, Set<String>><br/>tagnameToAddresses<br/>标签 → 地址"]
    
    TagRule -->|builds| AddrToTag["🟩 Map<String, Set<String>><br/>addressToTagnames<br/>地址 → 标签"]
    
    TagStateRouter -->|caches| Invokers["🟦 BitList<Invoker<T>><br/>所有可用调用者"]
    
    TagToAddr -->|支持| Lookup["selectAddressByTagLevel<br/>级联查询<br/>O(n) n为分段数"]
    
    Invokers -->|支持| Filter["filterInvoker<br/>按谓词过滤<br/>O(n*m)"]
    
    Lookup -->|输出| Addresses["Set<String><br/>匹配的地址"]
    
    Addresses -->|输入| Filter
    
    Filter -->|输出| Result["List<Invoker<T>><br/>过滤后的调用者"]
    
    style TagStateRouter fill:#ffcccc
    style TagRule fill:#ccffcc
    style Tag fill:#ccccff
    style ParamMatch fill:#ffffcc
    style TagToAddr fill:#ffeecc
    style AddrToTag fill:#eeffcc
    style Result fill:#99ff99
```

---

## 8. 容错和降级策略流程

```mermaid
flowchart TD
    Start["🚀 处理无可用地址"] -->|场景| Scenario{"动态标签匹配<br/>返回空?"}
    
    Scenario -->|是| CheckForce1{"rule.force<br/>=true?"}
    
    CheckForce1 -->|是| ReturnEmpty1["❌ 返回空列表<br/>调用失败"]
    CheckForce1 -->|否| Layer2["📌 Layer2: 检查静态标签<br/>url.getParameter[TAG_KEY]"]
    
    Layer2 --> StaticMatch{"找到匹配<br/>的invoker?"}
    StaticMatch -->|是| ReturnStatic["✅ 返回静态标签结果"]
    StaticMatch -->|否| Layer3["📌 Layer3: FAILOVER 降级"]
    
    Layer3 --> Strategy{"降级策略<br/>">
    Strategy -->|Strategy A| FilterNoTag["A) 返回所有无任何标签的invokers<br/>StringUtils.isEmpty<br/>invoker.url.parameter[TAG_KEY]"]
    Strategy -->|Strategy B| FilterOutTagged["B) 返回排除tagged地址的invokers<br/>addressNotMatches<br/>rule.getAddresses"]
    
    FilterNoTag --> VerifyResult{"检查结果<br/>是否非空?"}
    FilterOutTagged --> VerifyResult
    
    VerifyResult -->|是| ReturnFallback["✅ 返回降级结果<br/>可用的提供者"]
    VerifyResult -->|否| ReturnEmpty2["❌ 返回空列表<br/>真正无可用提供者"]
    
    ReturnEmpty1 --> Output["📤 最终响应"]
    ReturnStatic --> Output
    ReturnFallback --> Output
    ReturnEmpty2 --> Output
    
    Output --> ClientCode{"消费端<br/>处理结果"}
    ClientCode -->|非空| Invoke["✨ 调用提供者"]
    ClientCode -->|为空| Error["💥 抛异常<br/>或使用Mock"]
    
    style Start fill:#ff9999
    style ReturnEmpty1 fill:#ffcccc
    style ReturnEmpty2 fill:#ffcccc
    style ReturnFallback fill:#ccffcc
    style ReturnStatic fill:#ccffcc
    style Error fill:#ffcccc
    style Invoke fill:#ccffcc
    
    classDef forcePath fill:#ffe0b2
    classDef fallbackPath fill:#f0f4c3
    class CheckForce1 forcePath
    class Layer2,Layer3 fallbackPath
```

---

## 9. 多级标签降级示例

```mermaid
graph TB
    Request["📍 请求<br/>tag='gray|beijing|zone2'"]
    
    Request --> Query1["查询 1️⃣<br/>gray|beijing|zone2"]
    Query1 -->|命中| Hit1["✅ 返回地址集合<br/>完美匹配"]
    Query1 -->|不命中| Query2["查询 2️⃣<br/>gray|beijing"]
    
    Query2 -->|命中| Hit2["✅ 返回地址集合<br/>降级一级"]
    Query2 -->|不命中| Query3["查询 3️⃣<br/>gray"]
    
    Query3 -->|命中| Hit3["✅ 返回地址集合<br/>降级到最高级"]
    Query3 -->|不命中| Fail["❌ 返回 null<br/>完全失败"]
    
    Hit1 --> Scenario1["📊 场景分析"]
    Hit2 --> Scenario2["📊 场景分析"]
    Hit3 --> Scenario3["📊 场景分析"]
    Fail --> Scenario4["📊 场景分析"]
    
    Scenario1 --> Desc1["zone2 灰度环境有专线<br/>路由到该服务器集群"]
    Scenario2 --> Desc2["zone2 无灰度环境<br/>降级到北京灰度通用集群"]
    Scenario3 --> Desc3["北京无灰度环境<br/>降级到全国灰度集群<br/>可能跨域访问"]
    Scenario4 --> Desc4["无灰度环境配置<br/>触发容错降级<br/>返回普通服务器"]
    
    Desc1 -.-> Example["<br/>❌ 分段丢弃原理<br/>gray|beijing|zone2<br/>→ gray|beijing (丢弃 zone2)<br/>→ gray (丢弃 beijing)<br/>"]
    
    style Request fill:#ff9999
    style Hit1 fill:#99ff99
    style Hit2 fill:#ccffcc
    style Hit3 fill:#f0f4c3
    style Fail fill:#ffcccc
    style Example fill:#e8eaf6
```

---

## 10. 数据流总结图

```mermaid
graph LR
    YamlRule["📋 YAML<br/>配置文件"]
    
    YamlRule -->|TagRuleParser| Map["🗺️ Map<br/>标签定义"]
    Map -->|parseFromMap| TagRule["TagRouterRule<br/>规则对象"]
    TagRule -->|init| Build["构建映射<br/>关系"]
    
    Build -->|生成| TMark["tagnameToAddresses<br/>标签→地址"]
    Build -->|生成| ATMark["addressToTagnames<br/>地址→标签"]
    
    TMark -->|支持| SelectAddr["selectAddressByTagLevel<br/>级联查询"]
    ATMark -->|备用| ReverseQuery["反向查询<br/>某地址的标签"]
    
    SelectAddr -->|查询结果| Addresses["Set<String><br/>目标地址"]
    
    Addresses -->|输入| MatchFilter["addressMatches<br/>地址匹配检查"]
    MatchFilter -->|过滤| FilterInvokers["BitList<Invoker><br/>过滤调用者"]
    
    FilterInvokers -->|输出| RouteResult["最终路由结果"]
    
    RpcRequest["🔴 RPC请求<br/>包含TAG_KEY"]
    RpcRequest -->|提供| Tag["tag 值"]
    Tag -->|输入| SelectAddr
    
    RouteResult -->|选择| LB["🎲 负载均衡<br/>选择一个invoker"]
    LB -->|返回| Invoker["单个Invoker"]
    Invoker -->|执行调用| RpcResult["✨ RPC结果"]
    
    style YamlRule fill:#c8e6c9
    style Map fill:#fff9c4
    style TagRule fill:#bbdefb
    style TMark fill:#ffccbc
    style ATMark fill:#ffccbc
    style RouteResult fill:#99ff99
    style RpcRequest fill:#ff9999
    style RpcResult fill:#99ff99
```

