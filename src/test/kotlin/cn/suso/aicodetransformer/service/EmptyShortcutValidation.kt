package cn.suso.aicodetransformer.service

import cn.suso.aicodetransformer.service.impl.ActionServiceImpl
import cn.suso.aicodetransformer.model.PromptTemplate

/**
 * 验证空快捷键处理的简单程序
 */
fun main() {
    val actionService = ActionServiceImpl()
    val templateService = TestPromptTemplateServiceImpl()
    
    println("=== 空快捷键处理验证 ===")
    
    // 测试1: 空字符串快捷键验证
    println("\n1. 测试空字符串快捷键验证:")
    val emptyValidation = actionService.validateShortcut("")
    println("   空字符串验证结果: $emptyValidation (应该为true)")
    
    // 测试2: 空白字符串快捷键验证
    println("\n2. 测试空白字符串快捷键验证:")
    val blankValidation = actionService.validateShortcut("   ")
    println("   空白字符串验证结果: $blankValidation (应该为true)")
    
    // 测试3: 空快捷键冲突检测
    println("\n3. 测试空快捷键冲突检测:")
    val emptyConflict = actionService.isShortcutInUse("")
    println("   空字符串冲突检测: $emptyConflict (应该为false)")
    
    val blankConflict = actionService.isShortcutInUse("   ")
    println("   空白字符串冲突检测: $blankConflict (应该为false)")
    
    // 测试4: 空快捷键解析
    println("\n4. 测试空快捷键解析:")
    val emptyParse = actionService.parseShortcut("")
    println("   空字符串解析结果: $emptyParse (应该为null)")
    
    val blankParse = actionService.parseShortcut("   ")
    println("   空白字符串解析结果: $blankParse (应该为null)")
    
    // 测试5: 模板空快捷键验证
    println("\n5. 测试模板空快捷键验证:")
    val emptyTemplate = PromptTemplate(
        id = "test-empty",
        name = "测试空快捷键",
        content = "测试内容",
        shortcutKey = "",
        category = "测试"
    )
    
    val emptyTemplateValidation = templateService.validateTemplate(emptyTemplate)
    println("   空快捷键模板验证: $emptyTemplateValidation (应该为null)")
    
    // 测试6: 模板空白快捷键验证
    println("\n6. 测试模板空白快捷键验证:")
    val blankTemplate = PromptTemplate(
        id = "test-blank",
        name = "测试空白快捷键",
        content = "测试内容",
        shortcutKey = "   ",
        category = "测试"
    )
    
    val blankTemplateValidation = templateService.validateTemplate(blankTemplate)
    println("   空白快捷键模板验证: $blankTemplateValidation (应该为null)")
    
    // 测试7: 模板保存和规范化
    println("\n7. 测试模板保存和快捷键规范化:")
    try {
        templateService.saveTemplate(blankTemplate)
        val savedTemplate = templateService.getTemplate("test-blank")
        println("   保存成功，快捷键规范化结果: '${savedTemplate?.shortcutKey}' (应该为null或空)")
    } catch (e: Exception) {
        println("   保存失败: ${e.message}")
    }
    
    println("\n=== 验证完成 ===")
}