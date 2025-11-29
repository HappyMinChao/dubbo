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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.Experimental;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.InternalThreadLocal;
import org.apache.dubbo.common.utils.StringUtils;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Thread local context. (API, ThreadLocal, ThreadSafe)
 * <p>
 * Note: RpcContext is a temporary state holder. States in RpcContext changes every time when request is sent or received.
 * <p/>
 * There are four kinds of RpcContext, which are ServerContext, ClientAttachment, ServerAttachment and ServiceContext.
 * <p/>
 * ServiceContext: Using to pass environment parameters in the whole invocation. For example, `remotingApplicationName`,
 * `remoteAddress`, etc. {@link RpcServiceContext}
 * ClientAttachment, ServerAttachment and ServiceContext are using to transfer attachments.
 * Imaging a situation like this, A is calling B, and B will call C, after that, B wants to return some attachments back to A.
 * ClientAttachment is using to pass attachments to next hop as a consumer. ( A --> B , in A side)
 * ServerAttachment is using to fetch attachments from previous hop as a provider. ( A --> B , in B side)
 * ServerContext is using to return some attachments back to client as a provider. ( A <-- B , in B side)
 * The reason why using `ServiceContext` is to make API compatible with previous.
 *
 * @export
 * @see org.apache.dubbo.rpc.filter.ContextFilter
 */
/**
 * RPC上下文类
 * 线程本地上下文，用于在RPC调用过程中传递上下文信息
 *
 * @author dubbo
 */
public class RpcContext {

    // RPC上下文代理实例
    private static final RpcContext AGENT = new RpcContext();

    /**
     * 使用内部线程本地变量以提高性能
     */
    private static final InternalThreadLocal<RpcContextAttachment> CLIENT_RESPONSE_LOCAL =
            new InternalThreadLocal<RpcContextAttachment>() {
                @Override
                protected RpcContextAttachment initialValue() {
                    return new RpcContextAttachment();
                }
            };

    // 服务器响应上下文的线程本地变量
    private static final InternalThreadLocal<RpcContextAttachment> SERVER_RESPONSE_LOCAL =
            new InternalThreadLocal<RpcContextAttachment>() {
                @Override
                protected RpcContextAttachment initialValue() {
                    return new RpcContextAttachment();
                }
            };

    // 客户端附件的线程本地变量
    private static final InternalThreadLocal<RpcContextAttachment> CLIENT_ATTACHMENT =
            new InternalThreadLocal<RpcContextAttachment>() {
                @Override
                protected RpcContextAttachment initialValue() {
                    return new RpcContextAttachment();
                }
            };

    // 服务器附件的线程本地变量
    private static final InternalThreadLocal<RpcContextAttachment> SERVER_ATTACHMENT =
            new InternalThreadLocal<RpcContextAttachment>() {
                @Override
                protected RpcContextAttachment initialValue() {
                    return new RpcContextAttachment();
                }
            };

    // 服务上下文的线程本地变量
    private static final InternalThreadLocal<RpcServiceContext> SERVICE_CONTEXT =
            new InternalThreadLocal<RpcServiceContext>() {
                @Override
                protected RpcServiceContext initialValue() {
                    return new RpcServiceContext();
                }
            };

    /**
     * 用于取消调用的线程本地变量
     */
    private static final InternalThreadLocal<CancellationContext> CANCELLATION_CONTEXT =
            new InternalThreadLocal<CancellationContext>() {
                @Override
                protected CancellationContext initialValue() {
                    return new CancellationContext();
                }
            };

    /**
     * 获取取消上下文
     * 
     * @return 取消上下文
     */
    public static CancellationContext getCancellationContext() {
        return CANCELLATION_CONTEXT.get();
    }

    /**
     * 移除取消上下文
     */
    public static void removeCancellationContext() {
        CANCELLATION_CONTEXT.remove();
    }

    /**
     * 恢复取消上下文
     * 
     * @param oldContext 旧的取消上下文
     */
    public static void restoreCancellationContext(CancellationContext oldContext) {
        CANCELLATION_CONTEXT.set(oldContext);
    }

    // 是否移除标志
    private boolean remove = true;

    /**
     * 受保护的构造函数
     */
    protected RpcContext() {}

    /**
     * 获取服务器端上下文 ( A <-- B , 在B端)
     *
     * @return 服务器上下文
     */
    public static RpcContextAttachment getServerContext() {
        return new RpcServerContextAttachment();
    }

    /**
     * 移除服务器端上下文
     *
     * @see org.apache.dubbo.rpc.filter.ContextFilter
     * @return 客户端响应上下文
     */
    public static RpcContextAttachment getClientResponseContext() {
        return CLIENT_RESPONSE_LOCAL.get();
    }

    /**
     * 获取服务器响应上下文
     * 
     * @return 服务器响应上下文
     */
    public static RpcContextAttachment getServerResponseContext() {
        return SERVER_RESPONSE_LOCAL.get();
    }

    /**
     * 移除客户端响应上下文
     */
    public static void removeClientResponseContext() {
        CLIENT_RESPONSE_LOCAL.remove();
    }

    /**
     * 移除服务器响应上下文
     */
    public static void removeServerResponseContext() {
        SERVER_RESPONSE_LOCAL.remove();
    }

    /**
     * 获取上下文（已废弃）
     *
     * @return 上下文
     */
    @Deprecated
    public static RpcContext getContext() {
        return AGENT;
    }

    /**
     * 获取消费者端附件 ( A --> B , 在A端)
     *
     * @return 客户端附件上下文
     */
    public static RpcContextAttachment getClientAttachment() {
        return CLIENT_ATTACHMENT.get();
    }

    /**
     * 获取来自消费者的提供者端附件 ( A --> B , 在B端)
     *
     * @return 服务器附件上下文
     */
    public static RpcContextAttachment getServerAttachment() {
        return SERVER_ATTACHMENT.get();
    }

    /**
     * 移除服务器上下文
     */
    public static void removeServerContext() {
        RpcContextAttachment rpcContextAttachment = RpcContext.getServerContext();
        for (String key : rpcContextAttachment.attachments.keySet()) {
            rpcContextAttachment.remove(key);
        }
    }

    /**
     * 判断是否可以移除
     * 
     * @return 如果可以移除返回true，否则返回false
     */
    public boolean canRemove() {
        return remove;
    }

    /**
     * 设置每次调用后是否清除
     * 
     * @param remove 是否移除
     */
    public void clearAfterEachInvoke(boolean remove) {
        this.remove = remove;
    }

    /**
     * 用于在整个调用过程中传递环境参数。例如，`remotingApplicationName`、`remoteAddress`等。
     * {@link RpcServiceContext}
     *
     * @return 服务上下文
     */
    public static RpcServiceContext getServiceContext() {
        return SERVICE_CONTEXT.get();
    }

    /**
     * 获取当前服务上下文
     * 
     * @return 当前服务上下文
     */
    public static RpcServiceContext getCurrentServiceContext() {
        return SERVICE_CONTEXT.getWithoutInitialize();
    }

    /**
     * 移除服务上下文
     */
    public static void removeServiceContext() {
        SERVICE_CONTEXT.remove();
    }

    /**
     * 移除客户端附件
     */
    public static void removeClientAttachment() {
        if (CLIENT_ATTACHMENT.get().canRemove()) {
            CLIENT_ATTACHMENT.remove();
        }
    }

    /**
     * 移除服务器附件
     */
    public static void removeServerAttachment() {
        if (SERVER_ATTACHMENT.get().canRemove()) {
            SERVER_ATTACHMENT.remove();
        }
    }

    /**
     * 为内部使用定制的方法
     */
    public static void removeContext() {
        if (CLIENT_ATTACHMENT.get().canRemove()) {
            CLIENT_ATTACHMENT.remove();
        }
        if (SERVER_ATTACHMENT.get().canRemove()) {
            SERVER_ATTACHMENT.remove();
        }
        CLIENT_RESPONSE_LOCAL.remove();
        SERVER_RESPONSE_LOCAL.remove();
        SERVICE_CONTEXT.remove();
        CANCELLATION_CONTEXT.remove();
    }

    /**
     * 获取底层RPC协议的请求对象，例如HttpServletRequest
     *
     * @return 如果底层协议不支持获取请求则返回null
     */
    public Object getRequest() {
        return SERVICE_CONTEXT.get().getRequest();
    }

    /**
     * 设置底层RPC协议的请求对象
     * 
     * @param request 请求对象
     */
    public void setRequest(Object request) {
        SERVICE_CONTEXT.get().setRequest(request);
    }

    /**
     * 获取指定类型的底层RPC协议请求对象，例如HttpServletRequest
     *
     * @param clazz 请求对象类型
     * @return 如果底层协议不支持获取请求或请求不是指定类型则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequest(Class<T> clazz) {
        return SERVICE_CONTEXT.get().getRequest(clazz);
    }

    /**
     * 获取底层RPC协议的响应对象，例如HttpServletResponse
     *
     * @return 如果底层协议不支持获取响应则返回null
     */
    public Object getResponse() {
        return SERVICE_CONTEXT.get().getResponse();
    }

    /**
     * 设置底层RPC协议的响应对象
     * 
     * @param response 响应对象
     */
    public void setResponse(Object response) {
        SERVICE_CONTEXT.get().setResponse(response);
    }

    /**
     * 获取指定类型的底层RPC协议响应对象，例如HttpServletResponse
     *
     * @param clazz 响应对象类型
     * @return 如果底层协议不支持获取响应或响应不是指定类型则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getResponse(Class<T> clazz) {
        return SERVICE_CONTEXT.get().getResponse(clazz);
    }

    /**
     * 是否为提供者端
     *
     * @return 如果是提供者端返回true，否则返回false
     */
    public boolean isProviderSide() {
        return SERVICE_CONTEXT.get().isProviderSide();
    }

    /**
     * 是否为消费者端
     *
     * @return 如果是消费者端返回true，否则返回false
     */
    public boolean isConsumerSide() {
        return SERVICE_CONTEXT.get().isConsumerSide();
    }

    /**
     * 获取CompletableFuture
     *
     * @param <T> 泛型类型
     * @return CompletableFuture对象
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getCompletableFuture() {
        return SERVICE_CONTEXT.get().getCompletableFuture();
    }

    /**
     * 获取Future
     *
     * @param <T> 泛型类型
     * @return Future对象
     */
    @SuppressWarnings("unchecked")
    public <T> Future<T> getFuture() {
        return SERVICE_CONTEXT.get().getFuture();
    }

    /**
     * 设置Future
     *
     * @param future CompletableFuture对象
     */
    public void setFuture(CompletableFuture<?> future) {
        SERVICE_CONTEXT.get().setFuture(future);
    }

    /**
     * 获取URL列表
     * 
     * @return URL列表
     */
    public List<URL> getUrls() {
        return SERVICE_CONTEXT.get().getUrls();
    }

    /**
     * 设置URL列表
     * 
     * @param urls URL列表
     */
    public void setUrls(List<URL> urls) {
        SERVICE_CONTEXT.get().setUrls(urls);
    }

    /**
     * 获取URL
     * 
     * @return URL对象
     */
    public URL getUrl() {
        return SERVICE_CONTEXT.get().getUrl();
    }

    /**
     * 设置URL
     * 
     * @param url URL对象
     */
    public void setUrl(URL url) {
        SERVICE_CONTEXT.get().setUrl(url);
    }

    /**
     * 获取方法名
     *
     * @return 方法名
     */
    public String getMethodName() {
        return SERVICE_CONTEXT.get().getMethodName();
    }

    /**
     * 设置方法名
     * 
     * @param methodName 方法名
     */
    public void setMethodName(String methodName) {
        SERVICE_CONTEXT.get().setMethodName(methodName);
    }

    /**
     * 获取参数类型数组
     *
     * @serial
     * @return 参数类型数组
     */
    public Class<?>[] getParameterTypes() {
        return SERVICE_CONTEXT.get().getParameterTypes();
    }

    /**
     * 设置参数类型数组
     * 
     * @param parameterTypes 参数类型数组
     */
    public void setParameterTypes(Class<?>[] parameterTypes) {
        SERVICE_CONTEXT.get().setParameterTypes(parameterTypes);
    }

    /**
     * 获取参数数组
     *
     * @return 参数数组
     */
    public Object[] getArguments() {
        return SERVICE_CONTEXT.get().getArguments();
    }

    /**
     * 设置参数数组
     * 
     * @param arguments 参数数组
     */
    public void setArguments(Object[] arguments) {
        SERVICE_CONTEXT.get().setArguments(arguments);
    }

    /**
     * 设置本地地址
     *
     * @param host 主机名
     * @param port 端口号
     * @return RPC上下文
     */
    public RpcContext setLocalAddress(String host, int port) {
        return SERVICE_CONTEXT.get().setLocalAddress(host, port);
    }

    /**
     * 获取本地地址
     *
     * @return 本地地址
     */
    public InetSocketAddress getLocalAddress() {
        return SERVICE_CONTEXT.get().getLocalAddress();
    }

    /**
     * 设置本地地址
     *
     * @param address InetSocketAddress地址
     * @return RPC上下文
     */
    public RpcContext setLocalAddress(InetSocketAddress address) {
        return SERVICE_CONTEXT.get().setLocalAddress(address);
    }

    /**
     * 获取本地地址字符串
     * 
     * @return 本地地址字符串
     */
    public String getLocalAddressString() {
        return SERVICE_CONTEXT.get().getLocalAddressString();
    }

    /**
     * 获取本地主机名
     *
     * @return 本地主机名
     */
    public String getLocalHostName() {
        return SERVICE_CONTEXT.get().getLocalHostName();
    }

    /**
     * 设置远程地址
     *
     * @param host 主机名
     * @param port 端口号
     * @return RPC上下文
     */
    public RpcContext setRemoteAddress(String host, int port) {
        return SERVICE_CONTEXT.get().setRemoteAddress(host, port);
    }

    /**
     * 获取远程地址
     *
     * @return 远程地址
     */
    public InetSocketAddress getRemoteAddress() {
        return SERVICE_CONTEXT.get().getRemoteAddress();
    }

    /**
     * 设置远程地址
     *
     * @param address InetSocketAddress地址
     * @return RPC上下文
     */
    public RpcContext setRemoteAddress(InetSocketAddress address) {
        return SERVICE_CONTEXT.get().setRemoteAddress(address);
    }

    /**
     * 获取远程应用名称
     * 
     * @return 远程应用名称
     */
    public String getRemoteApplicationName() {
        return SERVICE_CONTEXT.get().getRemoteApplicationName();
    }

    /**
     * 设置远程应用名称
     * 
     * @param remoteApplicationName 远程应用名称
     * @return RPC上下文
     */
    public RpcContext setRemoteApplicationName(String remoteApplicationName) {
        return SERVICE_CONTEXT.get().setRemoteApplicationName(remoteApplicationName);
    }

    /**
     * 获取远程地址字符串
     *
     * @return 远程地址字符串
     */
    public String getRemoteAddressString() {
        return SERVICE_CONTEXT.get().getRemoteAddressString();
    }

    /**
     * 获取远程主机名
     *
     * @return 远程主机名
     */
    public String getRemoteHostName() {
        return SERVICE_CONTEXT.get().getRemoteHostName();
    }

    /**
     * 获取本地主机
     *
     * @return 本地主机
     */
    public String getLocalHost() {
        return SERVICE_CONTEXT.get().getLocalHost();
    }

    /**
     * 获取本地端口
     *
     * @return 本地端口
     */
    public int getLocalPort() {
        return SERVICE_CONTEXT.get().getLocalPort();
    }

    /**
     * 获取远程主机
     *
     * @return 远程主机
     */
    public String getRemoteHost() {
        return SERVICE_CONTEXT.get().getRemoteHost();
    }

    /**
     * 获取远程端口
     *
     * @return 远程端口
     */
    public int getRemotePort() {
        return SERVICE_CONTEXT.get().getRemotePort();
    }

    /**
     * 获取附件，也请参见 {@link #getObjectAttachment(String)}
     *
     * @param key 键
     * @return 附件值
     */
    public String getAttachment(String key) {
        String client = CLIENT_ATTACHMENT.get().getAttachment(key);
        if (StringUtils.isEmpty(client)) {
            return SERVER_ATTACHMENT.get().getAttachment(key);
        }
        return client;
    }

    /**
     * 获取附件
     *
     * @param key 键
     * @return 附件值
     */
    @Experimental("实验性API，用于支持对象传输")
    public Object getObjectAttachment(String key) {
        Object client = CLIENT_ATTACHMENT.get().getObjectAttachment(key);
        if (client == null) {
            return SERVER_ATTACHMENT.get().getObjectAttachment(key);
        }
        return client;
    }

    /**
     * 设置附件
     *
     * @param key 键
     * @param value 值
     * @return RPC上下文
     */
    public RpcContext setAttachment(String key, String value) {
        return setObjectAttachment(key, value);
    }

    /**
     * 设置附件
     * 
     * @param key 键
     * @param value 值
     * @return RPC上下文
     */
    public RpcContext setAttachment(String key, Object value) {
        return setObjectAttachment(key, value);
    }

    /**
     * 设置对象附件
     * 
     * @param key 键
     * @param value 值
     * @return RPC上下文
     */
    @Experimental("实验性API，用于支持对象传输")
    public RpcContext setObjectAttachment(String key, Object value) {
        // TODO 兼容以前的版本
        CLIENT_ATTACHMENT.get().setObjectAttachment(key, value);
        return this;
    }

    /**
     * 移除附件
     *
     * @param key 键
     * @return RPC上下文
     */
    public RpcContext removeAttachment(String key) {
        CLIENT_ATTACHMENT.get().removeAttachment(key);
        return this;
    }

    /**
     * 获取附件映射
     *
     * @return 附件映射
     */
    @Deprecated
    public Map<String, String> getAttachments() {
        return new AttachmentsAdapter.ObjectToStringMap(this.getObjectAttachments());
    }

    /**
     * 获取对象附件映射
     *
     * @return 对象附件映射
     */
    @Experimental("实验性API，用于支持对象传输")
    public Map<String, Object> getObjectAttachments() {
        Map<String, Object> result =
                new HashMap<>((int) ((CLIENT_ATTACHMENT.get().attachments.size()
                                        + SERVER_ATTACHMENT.get().attachments.size())
                                / .75)
                        + 1);
        result.putAll(SERVER_ATTACHMENT.get().attachments);
        result.putAll(CLIENT_ATTACHMENT.get().attachments);
        return result;
    }

    /**
     * 设置附件映射
     *
     * @param attachment 附件映射
     * @return RPC上下文
     */
    public RpcContext setAttachments(Map<String, String> attachment) {
        CLIENT_ATTACHMENT.get().attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            CLIENT_ATTACHMENT.get().attachments.putAll(attachment);
        }
        return this;
    }

    /**
     * 设置对象附件映射
     *
     * @param attachment 对象附件映射
     * @return RPC上下文
     */
    @Experimental("实验性API，用于支持对象传输")
    public RpcContext setObjectAttachments(Map<String, Object> attachment) {
        CLIENT_ATTACHMENT.get().attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            CLIENT_ATTACHMENT.get().attachments.putAll(attachment);
        }
        return this;
    }

    /**
     * 清除附件
     */
    public void clearAttachments() {
        CLIENT_ATTACHMENT.get().attachments.clear();
    }

    /**
     * 获取值映射
     *
     * @return 值映射
     */
    @Deprecated
    public Map<String, Object> get() {
        return CLIENT_ATTACHMENT.get().get();
    }

    /**
     * 设置值
     *
     * @param key 键
     * @param value 值
     * @return RPC上下文
     */
    @Deprecated
    public RpcContext set(String key, Object value) {
        CLIENT_ATTACHMENT.get().set(key, value);
        return this;
    }

    /**
     * 移除值
     *
     * @param key 键
     * @return RPC上下文
     */
    @Deprecated
    public RpcContext remove(String key) {
        CLIENT_ATTACHMENT.get().remove(key);
        return this;
    }

    /**
     * 获取值
     *
     * @param key 键
     * @return 值
     */
    @Deprecated
    public Object get(String key) {
        return CLIENT_ATTACHMENT.get().get(key);
    }

    /**
     * 是否为服务器端（已废弃，请使用isProviderSide()）
     */
    @Deprecated
    public boolean isServerSide() {
        return SERVICE_CONTEXT.get().isServerSide();
    }

    /**
     * 是否为客户端端（已废弃，请使用isConsumerSide()）
     */
    @Deprecated
    public boolean isClientSide() {
        return SERVICE_CONTEXT.get().isClientSide();
    }

    /**
     * 获取调用者列表（已废弃，请使用getUrls()）
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Invoker<?>> getInvokers() {
        return SERVICE_CONTEXT.get().getInvokers();
    }

    /**
     * 设置调用者列表
     * 
     * @param invokers 调用者列表
     * @return RPC上下文
     */
    public RpcContext setInvokers(List<Invoker<?>> invokers) {
        return SERVICE_CONTEXT.get().setInvokers(invokers);
    }

    /**
     * 获取调用者（已废弃，请使用getUrl()）
     */
    @Deprecated
    public Invoker<?> getInvoker() {
        return SERVICE_CONTEXT.get().getInvoker();
    }

    /**
     * 设置调用者
     * 
     * @param invoker 调用者
     * @return RPC上下文
     */
    public RpcContext setInvoker(Invoker<?> invoker) {
        return SERVICE_CONTEXT.get().setInvoker(invoker);
    }

    /**
     * 获取调用（已废弃，请使用getMethodName(), getParameterTypes(), getArguments()）
     */
    @Deprecated
    public Invocation getInvocation() {
        return SERVICE_CONTEXT.get().getInvocation();
    }

    /**
     * 设置调用
     * 
     * @param invocation 调用
     * @return RPC上下文
     */
    public RpcContext setInvocation(Invocation invocation) {
        return SERVICE_CONTEXT.get().setInvocation(invocation);
    }

    /**
     * 异步调用。即使不调用<code>Future.get()</code>也会处理超时。
     *
     * @param callable 可调用对象
     * @return 从<code>future.get()</code>获取返回结果
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> asyncCall(Callable<T> callable) {
        return SERVICE_CONTEXT.get().asyncCall(callable);
    }

    /**
     * 单向异步调用，仅发送请求，不需要结果
     *
     * @param runnable 可运行对象
     */
    public void asyncCall(Runnable runnable) {
        SERVICE_CONTEXT.get().asyncCall(runnable);
    }

    /**
     * 启动异步上下文
     *
     * @return 异步上下文
     * @throws IllegalStateException 非法状态异常
     */
    @SuppressWarnings("unchecked")
    public static AsyncContext startAsync() throws IllegalStateException {
        return RpcContextAttachment.startAsync();
    }

    /**
     * 设置异步上下文
     * 
     * @param asyncContext 异步上下文
     */
    protected void setAsyncContext(AsyncContext asyncContext) {
        SERVER_ATTACHMENT.get().setAsyncContext(asyncContext);
    }

    /**
     * 是否已启动异步
     * 
     * @return 如果已启动异步返回true，否则返回false
     */
    public boolean isAsyncStarted() {
        return SERVER_ATTACHMENT.get().isAsyncStarted();
    }

    /**
     * 停止异步
     * 
     * @return 如果停止成功返回true，否则返回false
     */
    public boolean stopAsync() {
        return SERVER_ATTACHMENT.get().stopAsync();
    }

    /**
     * 获取异步上下文
     * 
     * @return 异步上下文
     */
    public AsyncContext getAsyncContext() {
        return SERVER_ATTACHMENT.get().getAsyncContext();
    }

    /**
     * 获取分组
     * 
     * @return 分组
     */
    public String getGroup() {
        return SERVICE_CONTEXT.get().getGroup();
    }

    /**
     * 获取版本
     * 
     * @return 版本
     */
    public String getVersion() {
        return SERVICE_CONTEXT.get().getVersion();
    }

    /**
     * 获取接口名称
     * 
     * @return 接口名称
     */
    public String getInterfaceName() {
        return SERVICE_CONTEXT.get().getInterfaceName();
    }

    /**
     * 获取协议
     * 
     * @return 协议
     */
    public String getProtocol() {
        return SERVICE_CONTEXT.get().getProtocol();
    }

    /**
     * 获取服务键
     * 
     * @return 服务键
     */
    public String getServiceKey() {
        return SERVICE_CONTEXT.get().getServiceKey();
    }

    /**
     * 获取协议服务键
     * 
     * @return 协议服务键
     */
    public String getProtocolServiceKey() {
        return SERVICE_CONTEXT.get().getProtocolServiceKey();
    }

    /**
     * 获取消费者URL
     * 
     * @return 消费者URL
     */
    public URL getConsumerUrl() {
        return SERVICE_CONTEXT.get().getConsumerUrl();
    }

    /**
     * 设置消费者URL
     * 
     * @param consumerUrl 消费者URL
     */
    public void setConsumerUrl(URL consumerUrl) {
        SERVICE_CONTEXT.get().setConsumerUrl(consumerUrl);
    }

    /**
     * 设置RPC上下文（已废弃）
     * 
     * @param url URL
     */
    @Deprecated
    public static void setRpcContext(URL url) {
        RpcServiceContext.getServiceContext().setConsumerUrl(url);
    }

    /**
     * 清除并存储上下文
     * 
     * @return 恢复上下文
     */
    protected static RestoreContext clearAndStoreContext() {
        RestoreContext restoreContext = new RestoreContext();
        RpcContext.removeContext();
        return restoreContext;
    }

    /**
     * 存储上下文
     * 
     * @return 恢复上下文
     */
    protected static RestoreContext storeContext() {
        return new RestoreContext();
    }

    /**
     * 存储服务上下文
     * 
     * @return 恢复服务上下文
     */
    public static RestoreServiceContext storeServiceContext() {
        return new RestoreServiceContext();
    }

    /**
     * 恢复服务上下文
     * 
     * @param restoreServiceContext 恢复服务上下文
     */
    public static void restoreServiceContext(RestoreServiceContext restoreServiceContext) {
        if (restoreServiceContext != null) {
            restoreServiceContext.restore();
        }
    }

    /**
     * 恢复上下文
     * 
     * @param restoreContext 恢复上下文
     */
    protected static void restoreContext(RestoreContext restoreContext) {
        if (restoreContext != null) {
            restoreContext.restore();
        }
    }

    /**
     * 用于临时存储和恢复当前线程的各种上下文
     */
    public static class RestoreContext {
        // 服务上下文
        private final RpcServiceContext serviceContext;
        // 客户端附件
        private final RpcContextAttachment clientAttachment;
        // 服务器附件
        private final RpcContextAttachment serverAttachment;
        // 客户端响应本地变量
        private final RpcContextAttachment clientResponseLocal;
        // 服务器响应本地变量
        private final RpcContextAttachment serverResponseLocal;

        /**
         * 构造函数
         */
        public RestoreContext() {
            serviceContext = getServiceContext().copyOf(false);
            clientAttachment = getClientAttachment().copyOf(false);
            serverAttachment = getServerAttachment().copyOf(false);
            clientResponseLocal = getClientResponseContext().copyOf(false);
            serverResponseLocal = getServerResponseContext().copyOf(false);
        }

        /**
         * 恢复上下文
         */
        public void restore() {
            if (serviceContext != null) {
                SERVICE_CONTEXT.set(serviceContext);
            } else {
                removeServiceContext();
            }
            if (clientAttachment != null) {
                CLIENT_ATTACHMENT.set(clientAttachment);
            } else {
                removeClientAttachment();
            }
            if (serverAttachment != null) {
                SERVER_ATTACHMENT.set(serverAttachment);
            } else {
                removeServerAttachment();
            }
            if (clientResponseLocal != null) {
                CLIENT_RESPONSE_LOCAL.set(clientResponseLocal);
            } else {
                removeClientResponseContext();
            }
            if (serverResponseLocal != null) {
                SERVER_RESPONSE_LOCAL.set(serverResponseLocal);
            } else {
                removeServerResponseContext();
            }
        }
    }

    /**
     * 恢复服务上下文类
     */
    public static class RestoreServiceContext {
        // 服务上下文
        private final RpcServiceContext serviceContext;

        /**
         * 构造函数
         */
        public RestoreServiceContext() {
            RpcServiceContext originContext = getCurrentServiceContext();
            if (originContext == null) {
                this.serviceContext = null;
            } else {
                this.serviceContext = originContext.copyOf(true);
            }
        }

        /**
         * 恢复服务上下文
         */
        protected void restore() {
            if (serviceContext != null) {
                SERVICE_CONTEXT.set(serviceContext);
            } else {
                removeServiceContext();
            }
        }
    }
}
