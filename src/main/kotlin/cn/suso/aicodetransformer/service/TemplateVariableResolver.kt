package cn.suso.aicodetransformer.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import cn.suso.aicodetransformer.model.FieldInfo

/**
 * 模板变量解析器，用于解析和替换模板中的内置变量
 */
class TemplateVariableResolver(private val project: Project) {
    
    private val codeAnalysisService = project.service<CodeAnalysisService>()
    
    /**
     * 解析模板中的变量
     */
    fun resolveVariables(template: String, editor: Editor): String {
        var resolvedTemplate = template
        
        // 在ReadAction中获取所有需要的信息
        val variableValues = ReadAction.compute<Map<String, String>, RuntimeException> {
            extractAllVariables(editor)
        }
        
        // 替换所有内置变量
        variableValues.forEach { (variable, value) ->
            resolvedTemplate = resolvedTemplate.replace(variable, value)
        }
        
        return resolvedTemplate
    }
    

    

    

    
    /**
     * 提取所有内置变量的值
     */
    private fun extractAllVariables(editor: Editor): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        
        // 获取选中的代码
        val selectedText = getSelectedText(editor)
        variables["{{selectedCode}}"] = selectedText
        
        // 获取当前文件信息
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        
        if (virtualFile != null) {
            variables["{{fileName}}"] = virtualFile.name
            variables["{{filePath}}"] = virtualFile.path
            variables["{{language}}"] = getLanguageFromFile(virtualFile)
            
            // 获取PSI信息
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile != null) {
                val offset = editor.caretModel.offset
                
                // 获取当前类名
                val psiClass = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiClass::class.java)
                variables["{{className}}"] = psiClass?.name ?: ""
                
                // 获取当前方法名
                val psiMethod = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiMethod::class.java)
                variables["{{methodName}}"] = psiMethod?.name ?: ""
                
                // 获取包名
                val packageName = when (psiFile) {
                    is PsiJavaFile -> psiFile.packageName
                    else -> ""
                }
                variables["{{packageName}}"] = packageName
            }
        }
        
        // 获取项目名
        variables["{{projectName}}"] = project.name
        
        // 获取请求参数和返回参数信息
        try {
            val classInfoList = codeAnalysisService.extractClassInfoFromCode(selectedText, project)
            
            val requestParams = StringBuilder()
            val responseParams = StringBuilder()
            
            classInfoList.forEach { classInfo ->
                val paramInfo = StringBuilder()
                paramInfo.append("类名: ${classInfo.name}\n")
                if (classInfo.packageName.isNotEmpty()) {
                    paramInfo.append("包名: ${classInfo.packageName}\n")
                }
                paramInfo.append("字段:\n")
                classInfo.fields.forEach { field ->
                    paramInfo.append("  - ${field.name}: ${field.type}")
                    if (field.annotations.isNotEmpty()) {
                        paramInfo.append(" ${field.annotations.joinToString(" ")}")
                    }
                    paramInfo.append("\n")
                }
                paramInfo.append("\n")
                
                // 简单判断：如果类名包含Request或Req，认为是请求参数
                if (classInfo.name.contains("Request", ignoreCase = true) || 
                    classInfo.name.contains("Req", ignoreCase = true)) {
                    requestParams.append(paramInfo)
                } else {
                    responseParams.append(paramInfo)
                }
            }
            
            variables["{{requestParams}}"] = if (requestParams.isNotEmpty()) requestParams.toString().trim() else "无请求参数信息"
            variables["{{responseParams}}"] = if (responseParams.isNotEmpty()) responseParams.toString().trim() else "无返回参数信息"
            
            // 获取第一个请求参数信息
            val firstRequestParam = classInfoList.firstOrNull { classInfo ->
                classInfo.name.contains("Request", ignoreCase = true) || 
                classInfo.name.contains("Req", ignoreCase = true)
            }
            
            if (firstRequestParam != null) {
                val firstParamInfo = StringBuilder()
                firstParamInfo.append("类名: ${firstRequestParam.name}\n")
                if (firstRequestParam.packageName.isNotEmpty()) {
                    firstParamInfo.append("包名: ${firstRequestParam.packageName}\n")
                }
                firstParamInfo.append("字段:\n")
                firstRequestParam.fields.forEach { field ->
                    firstParamInfo.append("  - ${field.name}: ${field.type}")
                    if (field.annotations.isNotEmpty()) {
                        firstParamInfo.append(" ${field.annotations.joinToString(" ")}")
                    }
                    firstParamInfo.append("\n")
                }
                variables["{{firstRequestParam}}"] = firstParamInfo.toString().trim()
            } else {
                variables["{{firstRequestParam}}"] = "无第一个请求参数信息"
            }
        } catch (e: Exception) {
            variables["{{requestParams}}"] = "变量替换失败"
            variables["{{responseParams}}"] = "变量替换失败"
            variables["{{firstRequestParam}}"] = "变量替换失败"
        }
        
        return variables
    }
    
    /**
     * 根据文件扩展名获取编程语言
     */
    private fun getLanguageFromFile(virtualFile: VirtualFile): String {
        return when (virtualFile.extension?.lowercase()) {
            "java" -> "Java"
            "kt" -> "Kotlin"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "py" -> "Python"
            "cpp", "cc", "cxx" -> "C++"
            "c" -> "C"
            "cs" -> "C#"
            "go" -> "Go"
            "rs" -> "Rust"
            "php" -> "PHP"
            "rb" -> "Ruby"
            "swift" -> "Swift"
            "scala" -> "Scala"
            "groovy" -> "Groovy"
            "xml" -> "XML"
            "html" -> "HTML"
            "css" -> "CSS"
            "json" -> "JSON"
            "yaml", "yml" -> "YAML"
            "sql" -> "SQL"
            "sh" -> "Shell"
            "md" -> "Markdown"
            else -> "Unknown"
        }
    }

    /**
     * 获取选中的文本或当前行文本
     * 注意：已在ReadAction中被调用，可以安全访问Editor组件
     */
    private fun getSelectedText(editor: Editor): String {
        return if (editor.selectionModel.hasSelection()) {
            editor.selectionModel.selectedText ?: ""
        } else {
            // 如果没有选中文本，获取当前行
            val document = editor.document
            val caretModel = editor.caretModel
            val lineNumber = document.getLineNumber(caretModel.offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            val lineEndOffset = document.getLineEndOffset(lineNumber)
            document.getText(TextRange(lineStartOffset, lineEndOffset))
        }
    }
}