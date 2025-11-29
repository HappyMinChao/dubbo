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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 自适应类代码生成器
 * Code generator for Adaptive class
 */
public class AdaptiveClassCodeGenerator {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveClassCodeGenerator.class);

    /** 调用类名 */
    private static final String CLASS_NAME_INVOCATION = "org.apache.dubbo.rpc.Invocation";

    /** 包代码模板 */
    private static final String CODE_PACKAGE = "package %s;\n";

    /** 导入代码模板 */
    private static final String CODE_IMPORTS = "import %s;\n";

    /** 类声明代码模板 */
    private static final String CODE_CLASS_DECLARATION = "public class %s$Adaptive implements %s {\n";

    /** 方法声明代码模板 */
    private static final String CODE_METHOD_DECLARATION = "public %s %s(%s) %s {\n%s}\n";

    /** 方法参数代码模板 */
    private static final String CODE_METHOD_ARGUMENT = "%s arg%d";

    /** 方法抛出异常代码模板 */
    private static final String CODE_METHOD_THROWS = "throws %s";

    /** 不支持操作代码模板 */
    private static final String CODE_UNSUPPORTED =
            "throw new UnsupportedOperationException(\"The method %s of interface %s is not adaptive method!\");\n";

    /** URL空值检查代码模板 */
    private static final String CODE_URL_NULL_CHECK =
            "if (arg%d == null) throw new IllegalArgumentException(\"url == null\");\n%s url = arg%d;\n";

    /** 扩展名称赋值代码模板 */
    private static final String CODE_EXT_NAME_ASSIGNMENT = "String extName = %s;\n";

    /** 扩展名称空值检查代码模板 */
    private static final String CODE_EXT_NAME_NULL_CHECK = "if(extName == null) "
            + "throw new IllegalStateException(\"Failed to get extension (%s) name from url (\" + url.toString() + \") use keys(%s)\");\n";

    /** 调用参数空值检查代码模板 */
    private static final String CODE_INVOCATION_ARGUMENT_NULL_CHECK =
            "if (arg%d == null) throw new IllegalArgumentException(\"invocation == null\"); "
                    + "String methodName = arg%d.getMethodName();\n";

    /** 作用域模型赋值代码模板 */
    private static final String CODE_SCOPE_MODEL_ASSIGNMENT =
            "ScopeModel scopeModel = ScopeModelUtil.getOrDefault(url.getScopeModel(), %s.class);\n";
    /** 扩展赋值代码模板 */
    private static final String CODE_EXTENSION_ASSIGNMENT =
            "%s extension = (%<s)scopeModel.getExtensionLoader(%s.class).getExtension(extName);\n";

    /** 扩展方法调用参数代码模板 */
    private static final String CODE_EXTENSION_METHOD_INVOKE_ARGUMENT = "arg%d";

    /** 类型 */
    private final Class<?> type;

    /** 默认扩展名称 */
    private final String defaultExtName;

    /**
     * 构造函数
     * 
     * @param type 类型
     * @param defaultExtName 默认扩展名称
     */
    public AdaptiveClassCodeGenerator(Class<?> type, String defaultExtName) {
        this.type = type;
        this.defaultExtName = defaultExtName;
    }

    /**
     * 测试给定类型是否至少有一个方法被<code>Adaptive</code>注解
     * 
     * @return 如果有自适应方法返回true，否则返回false
     * test if given type has at least one method annotated with <code>Adaptive</code>
     */
    private boolean hasAdaptiveMethod() {
        return Arrays.stream(type.getMethods()).anyMatch(m -> m.isAnnotationPresent(Adaptive.class));
    }

    /**
     * 生成并返回类代码
     * 
     * @return 类代码
     * generate and return class code
     */
    public String generate() {
        return this.generate(false);
    }

    /**
     * 生成并返回类代码
     * 
     * @param sort 是否对方法排序
     * @return 类代码
     * generate and return class code
     * @param sort - whether sort methods
     */
    public String generate(boolean sort) {
        // 没有自适应方法，无需生成自适应类
        if (!hasAdaptiveMethod()) {
            throw new IllegalStateException("No adaptive method exist on extension " + type.getName()
                    + ", refuse to create the adaptive class!");
        }

        StringBuilder code = new StringBuilder();
        code.append(generatePackageInfo());
        code.append(generateImports());
        code.append(generateClassDeclaration());

        Method[] methods = type.getMethods();
        if (sort) {
            Arrays.sort(methods, Comparator.comparing(Method::toString));
        }
        for (Method method : methods) {
            code.append(generateMethod(method));
        }
        code.append('}');

        if (logger.isDebugEnabled()) {
            logger.debug(code.toString());
        }
        return code.toString();
    }

    /**
     * 生成包信息
     * 
     * @return 包信息代码
     * generate package info
     */
    private String generatePackageInfo() {
        return String.format(CODE_PACKAGE, type.getPackage().getName());
    }

    /**
     * 生成导入语句
     * 
     * @return 导入语句代码
     * generate imports
     */
    private String generateImports() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(CODE_IMPORTS, ScopeModel.class.getName()));
        builder.append(String.format(CODE_IMPORTS, ScopeModelUtil.class.getName()));
        return builder.toString();
    }

    /**
     * 生成类声明
     * 
     * @return 类声明代码
     * generate class declaration
     */
    private String generateClassDeclaration() {
        return String.format(CODE_CLASS_DECLARATION, type.getSimpleName(), type.getCanonicalName());
    }

    /**
     * 生成未被Adaptive注解的方法，抛出不支持操作异常
     * 
     * @param method 方法
     * @return 不支持操作代码
     * generate method not annotated with Adaptive with throwing unsupported exception
     */
    private String generateUnsupported(Method method) {
        return String.format(CODE_UNSUPPORTED, method, type.getName());
    }

    /**
     * 获取URL类型参数的索引
     * 
     * @param method 方法
     * @return URL类型参数的索引，如果没有找到返回-1
     * get index of parameter with type URL
     */
    private int getUrlTypeIndex(Method method) {
        int urlTypeIndex = -1;
        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; ++i) {
            if (pts[i].equals(URL.class)) {
                urlTypeIndex = i;
                break;
            }
        }
        return urlTypeIndex;
    }

    /**
     * 生成方法声明
     * 
     * @param method 方法
     * @return 方法声明代码
     * generate method declaration
     */
    private String generateMethod(Method method) {
        String methodReturnType = method.getReturnType().getCanonicalName();
        String methodName = method.getName();
        String methodContent = generateMethodContent(method);
        String methodArgs = generateMethodArguments(method);
        String methodThrows = generateMethodThrows(method);
        return String.format(
                CODE_METHOD_DECLARATION, methodReturnType, methodName, methodArgs, methodThrows, methodContent);
    }

    /**
     * 生成方法参数
     * 
     * @param method 方法
     * @return 方法参数代码
     * generate method arguments
     */
    private String generateMethodArguments(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                .mapToObj(i -> String.format(CODE_METHOD_ARGUMENT, pts[i].getCanonicalName(), i))
                .collect(Collectors.joining(", "));
    }

    /**
     * 生成方法抛出异常声明
     * 
     * @param method 方法
     * @return 方法抛出异常代码
     * generate method throws
     */
    private String generateMethodThrows(Method method) {
        Class<?>[] ets = method.getExceptionTypes();
        if (ets.length > 0) {
            String list = Arrays.stream(ets).map(Class::getCanonicalName).collect(Collectors.joining(", "));
            return String.format(CODE_METHOD_THROWS, list);
        } else {
            return "";
        }
    }

    /**
     * 生成方法URL参数空值检查
     * 
     * @param index 参数索引
     * @return URL空值检查代码
     * generate method URL argument null check
     */
    private String generateUrlNullCheck(int index) {
        return String.format(CODE_URL_NULL_CHECK, index, URL.class.getName(), index);
    }

    /**
     * 生成方法内容
     * 
     * @param method 方法
     * @return 方法内容代码
     * generate method content
     */
    private String generateMethodContent(Method method) {
        Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
        if (adaptiveAnnotation == null) {
            return generateUnsupported(method);
        }
        StringBuilder code = new StringBuilder(512);
        int urlTypeIndex = getUrlTypeIndex(method);

        // 在URL类型中找到参数
        if (urlTypeIndex != -1) {
            // 空指针检查
            code.append(generateUrlNullCheck(urlTypeIndex));
        } else {
            // 在URL类型中未找到参数
            code.append(generateUrlAssignmentIndirectly(method));
        }

        String[] value = getMethodAdaptiveValue(adaptiveAnnotation);

        boolean hasInvocation = hasInvocationArgument(method);

        code.append(generateInvocationArgumentNullCheck(method));

        code.append(generateExtNameAssignment(value, hasInvocation));
        // 检查extName是否为null
        code.append(generateExtNameNullCheck(value));

        code.append(generateScopeModelAssignment());
        code.append(generateExtensionAssignment());

        // 返回语句
        code.append(generateReturnAndInvocation(method));

        return code.toString();
    }

    /**
     * 生成变量extName空值检查代码
     * 
     * @param value 值数组
     * @return 扩展名称空值检查代码
     * generate code for variable extName null check
     */
    private String generateExtNameNullCheck(String[] value) {
        return String.format(CODE_EXT_NAME_NULL_CHECK, type.getName(), Arrays.toString(value));
    }

    /**
     * 生成扩展名称赋值代码
     * 
     * @param value 值数组
     * @param hasInvocation 是否有调用参数
     * @return 扩展名称赋值代码
     * generate extName assignment code
     */
    private String generateExtNameAssignment(String[] value, boolean hasInvocation) {
        // TODO: 重构它
        String getNameCode = null;
        for (int i = value.length - 1; i >= 0; --i) {
            if (i == value.length - 1) {
                if (null != defaultExtName) {
                    if (!CommonConstants.PROTOCOL_KEY.equals(value[i])) {
                        if (hasInvocation) {
                            getNameCode = String.format(
                                    "url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                        }
                    } else {
                        getNameCode = String.format(
                                "( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                    }
                } else {
                    if (!CommonConstants.PROTOCOL_KEY.equals(value[i])) {
                        if (hasInvocation) {
                            getNameCode = String.format(
                                    "url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                        }
                    } else {
                        getNameCode = "url.getProtocol()";
                    }
                }
            } else {
                if (!CommonConstants.PROTOCOL_KEY.equals(value[i])) {
                    if (hasInvocation) {
                        getNameCode = String.format(
                                "url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                    } else {
                        getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                    }
                } else {
                    getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                }
            }
        }

        return String.format(CODE_EXT_NAME_ASSIGNMENT, getNameCode);
    }

    /**
     * 生成作用域模型赋值代码
     * 
     * @return 作用域模型赋值代码
     */
    private String generateScopeModelAssignment() {
        return String.format(CODE_SCOPE_MODEL_ASSIGNMENT, type.getName());
    }

    /**
     * 生成扩展赋值代码
     * 
     * @return 扩展赋值代码
     */
    private String generateExtensionAssignment() {
        return String.format(CODE_EXTENSION_ASSIGNMENT, type.getName(), type.getName());
    }

    /**
     * 生成方法调用语句并在必要时返回
     * 
     * @param method 方法
     * @return 方法调用和返回代码
     * generate method invocation statement and return it if necessary
     */
    private String generateReturnAndInvocation(Method method) {
        String returnStatement = method.getReturnType().equals(void.class) ? "" : "return ";

        String args = IntStream.range(0, method.getParameters().length)
                .mapToObj(i -> String.format(CODE_EXTENSION_METHOD_INVOKE_ARGUMENT, i))
                .collect(Collectors.joining(", "));

        return returnStatement + String.format("extension.%s(%s);\n", method.getName(), args);
    }

    /**
     * 测试方法是否有<code>Invocation</code>类型的参数
     * 
     * @param method 方法
     * @return 如果有Invocation类型的参数返回true，否则返回false
     * test if method has argument of type <code>Invocation</code>
     */
    private boolean hasInvocationArgument(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return Arrays.stream(pts).anyMatch(p -> CLASS_NAME_INVOCATION.equals(p.getName()));
    }

    /**
     * 生成代码以测试<code>Invocation</code>类型的参数是否为null
     * 
     * @param method 方法
     * @return 调用参数空值检查代码
     * generate code to test argument of type <code>Invocation</code> is null
     */
    private String generateInvocationArgumentNullCheck(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                .filter(i -> CLASS_NAME_INVOCATION.equals(pts[i].getName()))
                .mapToObj(i -> String.format(CODE_INVOCATION_ARGUMENT_NULL_CHECK, i, i))
                .findFirst()
                .orElse("");
    }

    /**
     * 获取自适应注解的值，如果为空则返回分割后的简单名称
     * 
     * @param adaptiveAnnotation 自适应注解
     * @return 自适应值数组
     * get value of adaptive annotation or if empty return splitted simple name
     */
    private String[] getMethodAdaptiveValue(Adaptive adaptiveAnnotation) {
        String[] value = adaptiveAnnotation.value();
        // 值未设置，使用从类名生成的值作为键
        if (value.length == 0) {
            String splitName = StringUtils.camelToSplitName(type.getSimpleName(), ".");
            value = new String[] {splitName};
        }
        return value;
    }

    /**
     * 从方法参数中获取<code>URL</code>类型的参数：
     * <p>
     * 测试参数是否有返回类型为<code>URL</code>的方法
     * <p>
     * 如果未找到，则抛出IllegalStateException
     * 
     * @param method 方法
     * @return URL赋值代码
     * get parameter with type <code>URL</code> from method parameter:
     * <p>
     * test if parameter has method which returns type <code>URL</code>
     * <p>
     * if not found, throws IllegalStateException
     */
    private String generateUrlAssignmentIndirectly(Method method) {
        Class<?>[] pts = method.getParameterTypes();

        Map<String, Integer> getterReturnUrl = new HashMap<>();
        // 查找URL getter方法
        for (int i = 0; i < pts.length; ++i) {
            for (Method m : pts[i].getMethods()) {
                String name = m.getName();
                if ((name.startsWith("get") || name.length() > 3)
                        && Modifier.isPublic(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes().length == 0
                        && m.getReturnType() == URL.class) {
                    getterReturnUrl.put(name, i);
                }
            }
        }

        if (getterReturnUrl.size() <= 0) {
            // 未找到getter方法，抛出异常
            throw new IllegalStateException("Failed to create adaptive class for interface " + type.getName()
                    + ": not found url parameter or url attribute in parameters of method " + method.getName());
        }

        Integer index = getterReturnUrl.get("getUrl");
        if (index != null) {
            return generateGetUrlNullCheck(index, pts[index], "getUrl");
        } else {
            Map.Entry<String, Integer> entry =
                    getterReturnUrl.entrySet().iterator().next();
            return generateGetUrlNullCheck(entry.getValue(), pts[entry.getValue()], entry.getKey());
        }
    }

    /**
     * 1. 测试argi是否为null
     * 2. 测试argi.getXX()是否返回null
     * 3. 将url赋值为argi.getXX()
     * 
     * @param index 参数索引
     * @param type 参数类型
     * @param method 方法名
     * @return URL空值检查代码
     */
    private String generateGetUrlNullCheck(int index, Class<?> type, String method) {
        // 空指针检查
        StringBuilder code = new StringBuilder();
        code.append(String.format(
                "if (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");\n",
                index, type.getName()));
        code.append(String.format(
                "if (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");\n",
                index, method, type.getName(), method));

        code.append(String.format("%s url = arg%d.%s();\n", URL.class.getName(), index, method));
        return code.toString();
    }
}
