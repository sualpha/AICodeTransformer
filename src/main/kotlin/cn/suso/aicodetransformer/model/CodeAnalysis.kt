package cn.suso.aicodetransformer.model

/**
 * 字段信息数据类
 */
data class FieldInfo(
    val name: String,
    val type: String,
    val annotations: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val hasGetter: Boolean = false,
    val hasSetter: Boolean = false
)

/**
 * 类信息数据类
 */
data class ClassInfo(
    val name: String,
    val packageName: String,
    val fields: List<FieldInfo>
)

/**
 * 方法信息数据类
 */
data class MethodInfo(
    val name: String,
    val returnType: String,
    val parameters: List<ParameterInfo>,
    val isPublic: Boolean = true,
    val isStatic: Boolean = false,
    val annotations: List<String> = emptyList()
)

/**
 * 参数信息数据类
 */
data class ParameterInfo(
    val name: String,
    val type: String,
    val annotations: List<String> = emptyList()
)
