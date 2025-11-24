package cn.suso.aicodetransformer.service.java

import cn.suso.aicodetransformer.model.ClassInfo
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * 访问 Java PSI 能力的抽象。PyCharm 等 IDE 若缺少 Java 插件则返回 null，避免加载失败。
 */
data class JavaContextInfo(
    val packageName: String?,
    val className: String?,
    val methodName: String?
)

interface JavaPsiHelper {
    fun resolvePackageName(psiFile: PsiFile): String?

    fun extractContextInfo(psiFile: PsiFile, offset: Int): JavaContextInfo?

    fun getRealClassInfo(className: String, project: Project): ClassInfo?
}

/**
 * 通过反射按需加载 Java PSI 实现，防止在无 Java 模块的 IDE 上触发类加载异常。
 */
object JavaPsiHelperLoader {
    private val pluginId: PluginId = PluginId.getId("com.intellij.java")
    @Volatile private var cached: JavaPsiHelper? = null
    @Volatile private var attempted = false

    fun helper(): JavaPsiHelper? {
        val plugin = PluginManagerCore.getPlugin(pluginId)
        if (plugin == null || !plugin.isEnabled) {
            return null
        }
        if (cached != null || attempted) {
            return cached
        }
        synchronized(this) {
            if (!attempted) {
                cached = instantiateHelper()
                attempted = true
            }
        }
        return cached
    }

    private fun instantiateHelper(): JavaPsiHelper? {
        return try {
            val clazz = Class.forName("cn.suso.aicodetransformer.service.java.JavaPsiHelperImpl")
            clazz.getDeclaredConstructor().newInstance() as? JavaPsiHelper
        } catch (_: Throwable) {
            null
        }
    }
}
