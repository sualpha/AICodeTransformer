package cn.suso.aicodetransformer.util

/**
 * Token计数工具类
 * 用于估算文本内容的Token数量
 */
object TokenCounter {
    
    /**
     * 估算文本的Token数量
     * 使用简化的估算方法：
     * - 中文字符：1个字符 ≈ 1.5个Token
     * - 英文单词：1个单词 ≈ 1个Token
     * - 代码符号：根据复杂度估算
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        var tokenCount = 0
        var i = 0
        
        while (i < text.length) {
            val char = text[i]
            
            when {
                // 中文字符（包括中文标点）
                char.code in 0x4E00..0x9FFF || 
                char.code in 0x3000..0x303F ||
                char.code in 0xFF00..0xFFEF -> {
                    tokenCount += 2 // 中文字符通常占用更多Token
                    i++
                }
                
                // 英文字母和数字
                char.isLetterOrDigit() -> {
                    // 跳过整个单词
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) {
                        i++
                    }
                    tokenCount += 1 // 一个单词算作1个Token
                }
                
                // 代码特殊符号
                char in "{}[]().,;:\"'`" -> {
                    tokenCount += 1
                    i++
                }
                
                // 操作符
                char in "+-*/=<>!&|^%~" -> {
                    // 检查是否是多字符操作符
                    if (i + 1 < text.length) {
                        val nextChar = text[i + 1]
                        if ((char == '=' && nextChar == '=') ||
                            (char == '!' && nextChar == '=') ||
                            (char == '<' && nextChar == '=') ||
                            (char == '>' && nextChar == '=') ||
                            (char == '&' && nextChar == '&') ||
                            (char == '|' && nextChar == '|') ||
                            (char == '+' && nextChar == '+') ||
                            (char == '-' && nextChar == '-')) {
                            i += 2
                        } else {
                            i++
                        }
                    } else {
                        i++
                    }
                    tokenCount += 1
                }
                
                // 空白字符（通常不计入Token）
                char.isWhitespace() -> {
                    i++
                }
                
                // 其他字符
                else -> {
                    tokenCount += 1
                    i++
                }
            }
        }
        
        return tokenCount
    }
    
    /**
     * 估算文件差异内容的Token数量
     * 对于Git diff格式的内容，会过滤掉diff标记行
     */
    fun estimateTokensForDiff(diffContent: String): Int {
        val lines = diffContent.split('\n')
        var totalTokens = 0
        
        for (line in lines) {
            // 跳过diff标记行
            if (line.startsWith("diff --git") ||
                line.startsWith("index ") ||
                line.startsWith("--- ") ||
                line.startsWith("+++ ") ||
                line.startsWith("@@ ")) {
                continue
            }
            
            // 对于添加和删除的行，去掉前缀符号后计算Token
            val contentLine = when {
                line.startsWith("+") -> line.substring(1)
                line.startsWith("-") -> line.substring(1)
                else -> line
            }
            
            totalTokens += estimateTokens(contentLine)
        }
        
        return totalTokens
    }
    
    /**
     * 将文本按Token数量分割成多个段落
     * @param text 要分割的文本
     * @param maxTokensPerSegment 每段的最大Token数
     * @return 分割后的文本段落列表
     */
    fun splitTextByTokens(text: String, maxTokensPerSegment: Int): List<String> {
        if (estimateTokens(text) <= maxTokensPerSegment) {
            return listOf(text)
        }
        
        val lines = text.split('\n')
        val segments = mutableListOf<String>()
        val currentSegment = mutableListOf<String>()
        var currentTokens = 0
        
        for (line in lines) {
            val lineTokens = estimateTokens(line)
            
            // 如果单行就超过限制，需要进一步分割
            if (lineTokens > maxTokensPerSegment) {
                // 先保存当前段落
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment.joinToString("\n"))
                    currentSegment.clear()
                    currentTokens = 0
                }
                
                // 分割长行
                val splitLines = splitLongLine(line, maxTokensPerSegment)
                segments.addAll(splitLines)
                continue
            }
            
            // 如果添加这行会超过限制，先保存当前段落
            if (currentTokens + lineTokens > maxTokensPerSegment && currentSegment.isNotEmpty()) {
                segments.add(currentSegment.joinToString("\n"))
                currentSegment.clear()
                currentTokens = 0
            }
            
            currentSegment.add(line)
            currentTokens += lineTokens
        }
        
        // 保存最后一个段落
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.joinToString("\n"))
        }
        
        return segments
    }
    
    /**
     * 分割超长的单行文本
     */
    private fun splitLongLine(line: String, maxTokensPerSegment: Int): List<String> {
        val segments = mutableListOf<String>()
        var currentPos = 0
        
        while (currentPos < line.length) {
            var endPos = currentPos
            var tokens = 0
            
            // 逐字符增加，直到达到Token限制
            while (endPos < line.length && tokens < maxTokensPerSegment) {
                val char = line[endPos]
                val charTokens = when {
                    char.code in 0x4E00..0x9FFF -> 2
                    char.isLetterOrDigit() -> 1
                    else -> 1
                }
                
                if (tokens + charTokens > maxTokensPerSegment) break
                
                tokens += charTokens
                endPos++
            }
            
            // 确保至少前进一个字符，避免无限循环
            if (endPos == currentPos) {
                endPos = currentPos + 1
            }
            
            segments.add(line.substring(currentPos, endPos))
            currentPos = endPos
        }
        
        return segments
    }
}