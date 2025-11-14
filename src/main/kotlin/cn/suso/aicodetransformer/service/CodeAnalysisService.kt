package cn.suso.aicodetransformer.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import cn.suso.aicodetransformer.model.FieldInfo
import cn.suso.aicodetransformer.model.ClassInfo
import cn.suso.aicodetransformer.model.MethodInfo
import cn.suso.aicodetransformer.model.ParameterInfo

/**
 * 代码分析服务，用于解析Java类的字段信息
 */
@Service
class CodeAnalysisService {
    

    

    
    /**
     * 从代码中提取方法信息
     */
    private fun extractMethodInfo(code: String): MethodInfo? {
        try {
            // 预处理代码：移除多余的空白字符，将多行压缩为单行以便正则匹配
            val normalizedCode = code.replace("""\s+""".toRegex(), " ").trim()
            
            // 支持多种方法声明格式的正则表达式，改进泛型匹配
            val methodPatterns = listOf(
                // Java/Kotlin 标准方法格式 - 支持嵌套泛型（带大括号）
                Regex("""(?:public|private|protected)?\s*(?:static\s+)?(\w+(?:<[^{}]*>)?)\s+(\w+)\s*\(([^)]*)\)\s*\{"""),
                // Java/Kotlin 标准方法格式 - 支持嵌套泛型（不带大括号）
                Regex("""(?:public|private|protected)?\s*(?:static\s+)?(\w+(?:<[^{}]*>)?)\s+(\w+)\s*\(([^)]*)\)\s*;?\s*$"""),
                // 简化格式（没有访问修饰符）- 支持嵌套泛型（带大括号）
                Regex("""(\w+(?:<[^{}]*>)?)\s+(\w+)\s*\(([^)]*)\)\s*\{"""),
                // 简化格式（没有访问修饰符）- 支持嵌套泛型（不带大括号）
                Regex("""(\w+(?:<[^{}]*>)?)\s+(\w+)\s*\(([^)]*)\)\s*;?\s*$"""),
                // Kotlin 函数格式（带大括号）
                Regex("""fun\s+(\w+)\s*\(([^)]*)\)\s*:\s*(\w+(?:<[^{}]*>)?)\s*\{"""),
                // Kotlin 函数格式（不带大括号）
                Regex("""fun\s+(\w+)\s*\(([^)]*)\)\s*:\s*(\w+(?:<[^{}]*>)?)\s*$"""),
                // Kotlin 无返回值函数（带大括号）
                Regex("""fun\s+(\w+)\s*\(([^)]*)\)\s*\{"""),
                // Kotlin 无返回值函数（不带大括号）
                Regex("""fun\s+(\w+)\s*\(([^)]*)\)\s*$""")
            )
            
            for (pattern in methodPatterns) {
                val match = pattern.find(normalizedCode)
                if (match != null) {
                    return when (pattern) {
                        methodPatterns[0], methodPatterns[1], methodPatterns[2], methodPatterns[3] -> {
                            // Java 格式（带或不带大括号）
                            val returnType = match.groupValues[1]
                            val methodName = match.groupValues[2]
                            val parametersStr = match.groupValues[3]
                            
                            MethodInfo(
                                name = methodName,
                                returnType = returnType,
                                parameters = parseParameters(parametersStr),
                                isPublic = code.contains("public"),
                                isStatic = code.contains("static")
                            )
                        }
                        methodPatterns[4], methodPatterns[5] -> {
                            // Kotlin 有返回值函数（带或不带大括号）
                            val methodName = match.groupValues[1]
                            val parametersStr = match.groupValues[2]
                            val returnType = match.groupValues[3]
                            
                            MethodInfo(
                                name = methodName,
                                returnType = returnType,
                                parameters = parseParameters(parametersStr),
                                isPublic = !code.contains("private")
                            )
                        }
                        methodPatterns[6], methodPatterns[7] -> {
                            // Kotlin 无返回值函数（带或不带大括号）
                            val methodName = match.groupValues[1]
                            val parametersStr = match.groupValues[2]
                            
                            MethodInfo(
                                name = methodName,
                                returnType = "Unit",
                                parameters = parseParameters(parametersStr),
                                isPublic = !code.contains("private")
                            )
                        }
                        else -> null
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 解析方法参数
     */
    private fun parseParameters(parametersStr: String): List<ParameterInfo> {
        if (parametersStr.isBlank()) {
            return emptyList()
        }
        
        val parameters = mutableListOf<ParameterInfo>()
        
        try {
            // 分割参数，处理泛型中的逗号
            val paramList = splitParameters(parametersStr)
            
            for (param in paramList) {
                val trimmedParam = param.trim()
                if (trimmedParam.isBlank()) continue
                
                // 解析参数格式：[注解] 类型 名称
                val paramPattern = Regex("""(?:@\w+\s+)?(\w+(?:<[^>]*>)?)\s+(\w+)""")
                val match = paramPattern.find(trimmedParam)
                
                if (match != null) {
                    val type = match.groupValues[1]
                    val name = match.groupValues[2]
                    
                    // 提取注解
                    val annotations = mutableListOf<String>()
                    val annotationPattern = Regex("""@(\w+)""")
                    annotationPattern.findAll(trimmedParam).forEach { annotationMatch ->
                        annotations.add("@${annotationMatch.groupValues[1]}")
                    }
                    
                    parameters.add(ParameterInfo(
                        name = name,
                        type = type,
                        annotations = annotations
                    ))
                }
            }
        } catch (e: Exception) {
            // 如果解析失败，返回空列表
        }
        
        return parameters
    }
    
    /**
     * 智能分割参数字符串，处理泛型中的逗号
     */
    private fun splitParameters(parametersStr: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        
        for (char in parametersStr) {
            when (char) {
                '<' -> {
                    depth++
                    current.append(char)
                }
                '>' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        
        return result
    }

    /**
     * 从选中的代码中提取类信息
     */
    fun extractClassInfoFromCode(selectedCode: String, project: Project): List<ClassInfo> {
        val classes = mutableListOf<ClassInfo>()
        
        try {
            // 增强的正则表达式解析，支持更复杂的Java语法
            val classPattern = Regex("""(?:public\s+|private\s+|protected\s+)?(?:static\s+)?(?:final\s+)?class\s+(\w+)(?:<[^>]*>)?(?:\s+extends\s+\w+)?(?:\s+implements\s+[\w,\s]+)?\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
            
            // 改进的字段匹配模式，支持注解、修饰符、泛型等
            val fieldPattern = Regex("""(?:@\w+(?:\([^)]*\))?\s*)*(?:private|public|protected|static|final|transient|volatile)\s+(?:private|public|protected|static|final|transient|volatile\s+)*([\w<>\[\],\s?]+)\s+(\w+)(?:\s*=\s*[^;]+)?\s*;""")
            val methodPattern = Regex("""(?:private|public|protected)?\s*(?:static\s+)?(\w+(?:<.*?>)?)\s+(\w+)\s*\(([^)]*)\)""")
            
            classPattern.findAll(selectedCode).forEach { classMatch ->
                val className = classMatch.groupValues[1]
                val classBody = classMatch.groupValues[2]
                
                val fields = mutableListOf<FieldInfo>()
                
                // 解析字段
                fieldPattern.findAll(classBody).forEach { fieldMatch ->
                    val fieldType = fieldMatch.groupValues[1].trim()
                    val fieldName = fieldMatch.groupValues[2].trim()
                    
                    // 提取注解信息
                    val annotations = mutableListOf<String>()
                    val annotationPattern = Regex("""@(\w+)(?:\([^)]*\))?""")
                    val fieldDeclaration = fieldMatch.value
                    annotationPattern.findAll(fieldDeclaration).forEach { annotationMatch ->
                        annotations.add("@${annotationMatch.groupValues[1]}")
                    }
                    
                    // 检查访问修饰符和其他修饰符
                    val isPrivate = fieldDeclaration.contains("private")
                    
                    // 检查是否有getter和setter方法
                    val getterPattern = Regex("""(?:public\s+)?${Regex.escape(fieldType)}\s+get${fieldName.replaceFirstChar { it.uppercase() }}\s*\(\s*\)""")
                    val setterPattern = Regex("""(?:public\s+)?void\s+set${fieldName.replaceFirstChar { it.uppercase() }}\s*\(\s*${Regex.escape(fieldType)}\s+\w+\s*\)""")
                    
                    val hasGetter = getterPattern.containsMatchIn(classBody)
                    val hasSetter = setterPattern.containsMatchIn(classBody)
                    
                    fields.add(FieldInfo(
                        name = fieldName,
                        type = fieldType,
                        annotations = annotations,
                        isPrivate = isPrivate,
                        hasGetter = hasGetter,
                        hasSetter = hasSetter
                    ))
                }
                
                // 解析方法参数和返回类型
                methodPattern.findAll(classBody).forEach { methodMatch ->
                    val returnType = methodMatch.groupValues[1]
                    val methodName = methodMatch.groupValues[2]
                    val parameters = methodMatch.groupValues[3]
                    
                    // 添加返回类型作为字段（如果不是void且不是构造函数）
                    if (returnType != "void" && methodName != className && !returnType.equals(className, ignoreCase = true)) {
                        fields.add(FieldInfo(
                            name = "${methodName}Result",
                            type = returnType,
                            annotations = listOf("@MethodReturn"),
                            isPrivate = false,
                            hasGetter = true,
                            hasSetter = false
                        ))
                    }
                    
                    // 解析方法参数
                    if (parameters.isNotBlank()) {
                        val paramPattern = Regex("""(\w+(?:<.*?>)?)\s+(\w+)""")
                        paramPattern.findAll(parameters).forEach { paramMatch ->
                            val paramType = paramMatch.groupValues[1]
                            val paramName = paramMatch.groupValues[2]
                            
                            fields.add(FieldInfo(
                                name = paramName,
                                type = paramType,
                                annotations = listOf("@Parameter"),
                                isPrivate = false,
                                hasGetter = true,
                                hasSetter = true
                            ))
                        }
                    }
                }
                
                classes.add(ClassInfo(
                    name = className,
                    packageName = "",
                    fields = fields
                ))
            }
            
            // 如果没有找到完整的类定义，尝试从方法签名中提取类型信息
            if (classes.isEmpty()) {
                val methodClasses = extractClassInfoFromMethodSignature(selectedCode, project)
                classes.addAll(methodClasses)
            }
            
        } catch (e: Exception) {
            // 如果解析失败，返回空列表
        }
        
        return classes
    }
    
    /**
     * 从方法签名中提取类信息（使用PSI API获取真实类信息）
     */
    private fun extractClassInfoFromMethodSignature(selectedCode: String, project: Project): List<ClassInfo> {
        val classes = mutableListOf<ClassInfo>()
        
        try {
            // 解析方法签名，提取参数类型和返回类型
            val methodInfo = extractMethodInfo(selectedCode)
            if (methodInfo != null) {
                // 创建源类（基于方法参数）
                if (methodInfo.parameters.isNotEmpty()) {
                    val sourceClassName = methodInfo.parameters.first().type
                    // 使用PSI API获取真实的类信息
                    val sourceClassInfo = getRealClassInfo(sourceClassName, project)
                    if (sourceClassInfo != null) {
                        classes.add(sourceClassInfo)
                    }
                }
                
                // 创建目标类（基于返回类型）
                if (methodInfo.returnType != "void" && methodInfo.returnType != "Unit" && methodInfo.returnType.isNotBlank()) {
                    val targetClassName = methodInfo.returnType
                    // 使用PSI API获取真实的类信息
                    val targetClassInfo = getRealClassInfo(targetClassName, project)
                    if (targetClassInfo != null) {
                        classes.add(targetClassInfo)
                    }
                }
            }
        } catch (e: Exception) {
            // 如果解析失败，返回空列表
        }
        
        return classes
    }
    

    

    
    /**
     * 通过真实的类名获取真实的字段信息
     * @param className 完全限定的类名或短类名
     * @param project 当前项目
     * @return 真实的类信息，如果找不到类则返回null
     */
    fun getRealClassInfo(className: String, project: Project): ClassInfo? {
        return try {
            ReadAction.compute<ClassInfo?, Exception> {
                try {
                    val javaPsiFacade = JavaPsiFacade.getInstance(project)
                    val searchScope = GlobalSearchScope.allScope(project)
                    
                    // 首先尝试通过完全限定名查找
                    var psiClass = javaPsiFacade.findClass(className, searchScope)
                    
                    // 如果找不到，尝试通过短类名查找
                    if (psiClass == null) {
                        val shortNamesCache = PsiShortNamesCache.getInstance(project)
                        val classes = shortNamesCache.getClassesByName(className, searchScope)
                        psiClass = classes.firstOrNull()
                    }
                    
                    if (psiClass == null) {
                        return@compute null
                    }
                    
                    // 提取真实的字段信息
                    val fields = mutableListOf<FieldInfo>()
            
            // 获取所有字段
            for (psiField in psiClass.allFields) {
                // 跳过静态字段和常量
                if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
                    val fieldType = psiField.type.presentableText
                    val fieldName = psiField.name
                    val annotations = psiField.annotations.map { it.qualifiedName ?: "" }
                    val isPrivate = psiField.hasModifierProperty(PsiModifier.PRIVATE)
                    
                    // 检查是否有getter和setter方法
                    val hasGetter = psiClass.findMethodsByName("get${fieldName.replaceFirstChar { it.uppercase() }}", false).isNotEmpty() ||
                                   psiClass.findMethodsByName("is${fieldName.replaceFirstChar { it.uppercase() }}", false).isNotEmpty()
                    val hasSetter = psiClass.findMethodsByName("set${fieldName.replaceFirstChar { it.uppercase() }}", false).isNotEmpty()
                    
                    fields.add(FieldInfo(
                        name = fieldName,
                        type = fieldType,
                        annotations = annotations,
                        isPrivate = isPrivate,
                        hasGetter = hasGetter,
                        hasSetter = hasSetter
                    ))
                }
            }
            
                // 获取包名
                val packageName = psiClass.qualifiedName?.substringBeforeLast('.') ?: ""
                val simpleClassName = psiClass.name ?: className
                
                return@compute ClassInfo(
                    name = simpleClassName,
                    packageName = packageName,
                    fields = fields
                )
                
            } catch (e: Exception) {
                // 如果发生异常，返回null
                return@compute null
            }
        }
        } catch (e: Exception) {
            // 如果 ReadAction 本身失败（比如在测试环境中），返回null
            return null
        }
    }
}

