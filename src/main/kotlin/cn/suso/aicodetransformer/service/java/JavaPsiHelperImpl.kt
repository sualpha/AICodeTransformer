package cn.suso.aicodetransformer.service.java

import cn.suso.aicodetransformer.model.ClassInfo
import cn.suso.aicodetransformer.model.FieldInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil

/**
 * 仅在 Java 插件存在时可被反射加载的实现，封装所有对 Java PSI 的直接调用。
 */
internal class JavaPsiHelperImpl : JavaPsiHelper {
    override fun resolvePackageName(psiFile: PsiFile): String? {
        return (psiFile as? PsiJavaFile)?.packageName
    }

    override fun extractContextInfo(psiFile: PsiFile, offset: Int): JavaContextInfo? {
        val element = psiFile.findElementAt(offset) ?: return null
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        val psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        val packageName = (psiFile as? PsiJavaFile)?.packageName
        return JavaContextInfo(
            packageName = packageName,
            className = psiClass?.name,
            methodName = psiMethod?.name
        )
    }

    override fun getRealClassInfo(className: String, project: Project): ClassInfo? {
        return try {
            ReadAction.compute<ClassInfo?, Exception> {
                val javaPsiFacade = JavaPsiFacade.getInstance(project)
                val searchScope = GlobalSearchScope.allScope(project)

                var psiClass = javaPsiFacade.findClass(className, searchScope)
                if (psiClass == null) {
                    val shortNamesCache = PsiShortNamesCache.getInstance(project)
                    val classes = shortNamesCache.getClassesByName(className, searchScope)
                    psiClass = classes.firstOrNull()
                }
                if (psiClass == null) {
                    return@compute null
                }

                val fields = mutableListOf<FieldInfo>()
                for (psiField in psiClass.allFields) {
                    if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
                        val fieldType = psiField.type.presentableText
                        val fieldName = psiField.name
                        val annotations = psiField.annotations.map { it.qualifiedName ?: "" }
                        val isPrivate = psiField.hasModifierProperty(PsiModifier.PRIVATE)
                        val hasGetter = psiClass.findMethodsByName("get${fieldName.replaceFirstChar { it.uppercase() }}", false).isNotEmpty() ||
                            psiClass.findMethodsByName("is${fieldName.replaceFirstChar { it.uppercase() }}", false).isNotEmpty()
                        val hasSetter = psiClass.findMethodsByName("set${fieldName.replaceFirstChar { it.uppercase() }}", false).isNotEmpty()

                        fields.add(
                            FieldInfo(
                                name = fieldName,
                                type = fieldType,
                                annotations = annotations,
                                isPrivate = isPrivate,
                                hasGetter = hasGetter,
                                hasSetter = hasSetter
                            )
                        )
                    }
                }

                val packageName = psiClass.qualifiedName?.substringBeforeLast('.') ?: ""
                val simpleClassName = psiClass.name ?: className
                ClassInfo(
                    name = simpleClassName,
                    packageName = packageName,
                    fields = fields
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
