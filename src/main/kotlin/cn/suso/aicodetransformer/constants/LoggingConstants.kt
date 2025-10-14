package cn.suso.aicodetransformer.constants

import kotlinx.serialization.Serializable

/**
 * 日志级别
 */
@Serializable
enum class LogLevel(val priority: Int) {
    DEBUG(0),
    INFO(1),
    WARNING(2),
    ERROR(3)
}

/**
 * 日志类型
 */
@Serializable
enum class LogType {
    SYSTEM,         // 系统日志
    API_CALL,       // API调用日志
    USER_ACTION,    // 用户操作日志
    PERFORMANCE,    // 性能日志
    SECURITY,       // 安全日志
    ERROR           // 错误日志
}

/**
 * 用户操作类型
 */
@Serializable
enum class UserAction {
    LOGIN,              // 登录
    LOGOUT,             // 登出
    CODE_TRANSFORM,     // 代码转换
    TEMPLATE_CREATE,    // 创建模板
    TEMPLATE_UPDATE,    // 更新模板
    TEMPLATE_DELETE,    // 删除模板
    CONFIG_UPDATE,      // 更新配置
    FILE_UPLOAD,        // 文件上传
    FILE_DOWNLOAD,      // 文件下载
    EXPORT_DATA,        // 导出数据
    IMPORT_DATA         // 导入数据
}

/**
 * 性能指标类型
 */
@Serializable
enum class PerformanceMetric {
    API_RESPONSE_TIME,      // API响应时间
    MEMORY_USAGE,           // 内存使用量
    CPU_USAGE,              // CPU使用率
    CACHE_HIT_RATE,         // 缓存命中率
    THROUGHPUT,             // 吞吐量
    ERROR_RATE,             // 错误率
    CONCURRENT_USERS        // 并发用户数
}

/**
 * 安全事件类型
 */
@Serializable
enum class SecurityEvent {
    AUTHENTICATION_FAILURE,     // 认证失败
    AUTHORIZATION_FAILURE,      // 授权失败
    SUSPICIOUS_ACTIVITY,        // 可疑活动
    DATA_ACCESS_VIOLATION,      // 数据访问违规
    API_ABUSE,                  // API滥用
    RATE_LIMIT_EXCEEDED,        // 超出限流
    INVALID_INPUT,              // 无效输入
    SECURITY_SCAN_DETECTED      // 检测到安全扫描
}

/**
 * 日志排序字段
 */
@Serializable
enum class LogSortBy {
    TIMESTAMP,      // 时间戳
    LEVEL,          // 日志级别
    TYPE,           // 日志类型
    USER_ID         // 用户ID
}

/**
 * 日志导出格式
 */
@Serializable
enum class LogExportFormat {
    JSON,       // JSON格式
    CSV,        // CSV格式
    XML,        // XML格式
    TXT         // 纯文本格式
}

/**
 * 日志格式
 */
@Serializable
enum class LogFormat {
    JSON,       // JSON格式
    PLAIN,      // 纯文本格式
    STRUCTURED  // 结构化格式
}

/**
 * 排序方向
 */
@Serializable
enum class SortDirection {
    ASC,    // 升序
    DESC    // 降序
}