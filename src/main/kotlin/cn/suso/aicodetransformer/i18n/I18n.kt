package cn.suso.aicodetransformer.i18n

import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 轻量级国际化支持：通过 ResourceBundle 加载 messages.* 资源文件。
 * LanguageManager 负责语言代码与 Locale 的切换，并通知监听者刷新文本。
 */
object LanguageManager {
    @Volatile private var languageCode: String = "zh_CN"
    @Volatile private var locale: Locale = Locale.SIMPLIFIED_CHINESE
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun getLanguageCode(): String = languageCode
    fun getLocale(): Locale = locale

    fun setLanguage(code: String) {
        val newLocale = when (code) {
            "system" -> Locale.getDefault()
            "en_US", "en" -> Locale("en", "US")
            "zh_CN", "zh" -> Locale("zh", "CN")
            else -> Locale("zh", "CN")
        }
        languageCode = code
        locale = newLocale
        ResourceBundle.clearCache()
        listeners.forEach {
            kotlin.runCatching { 
                it() 
            }.onFailure { e ->
            }
        }
    }

    fun addChangeListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeChangeListener(listener: () -> Unit) { listeners.remove(listener) }
}

object I18n {
    @Volatile private var bundle: ResourceBundle = ResourceBundle.getBundle("i18n.messages", LanguageManager.getLocale())

    init {
        LanguageManager.addChangeListener {
            bundle = ResourceBundle.getBundle("i18n.messages", LanguageManager.getLocale())
        }
    }

    fun t(key: String): String {
        return try { bundle.getString(key) } catch (_: Exception) { key }
    }

    fun t(key: String, vararg args: Any): String {
        return try {
            val pattern = bundle.getString(key)
            java.text.MessageFormat(pattern).format(args)
        } catch (_: Exception) {
            key
        }
    }
}