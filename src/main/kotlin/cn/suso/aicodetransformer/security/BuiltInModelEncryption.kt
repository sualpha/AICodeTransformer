package cn.suso.aicodetransformer.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * 内置模型加密服务
 * 用于加密和解密内置模型的敏感信息(如API密钥)
 * 
 * 注意: 此加密方案用于基本保护,密钥硬编码在代码中。
 * 虽然提供了一定程度的保护,但仍存在逆向工程风险。
 * 建议为内置模型使用专用的、权限受限的API密钥。
 */
object BuiltInModelEncryption {
    
    // 加密算法
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    
    // 密钥和IV (在实际使用中,这些应该使用混淆技术保护)
    // 注意: 这是一个示例密钥,在生产环境中应该使用更安全的密钥管理方案
    private val SECRET_KEY = "AICodeTransform2024SecretKey!".toByteArray(Charsets.UTF_8).copyOf(32)
    private val IV = "AICodeTransIV16!".toByteArray(Charsets.UTF_8).copyOf(16)
    
    /**
     * 加密文本
     * @param plainText 明文
     * @return Base64编码的密文
     */
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(SECRET_KEY, ALGORITHM)
            val ivSpec = IvParameterSpec(IV)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            Base64.getEncoder().encodeToString(encrypted)
        } catch (e: Exception) {
            throw EncryptionException("加密失败: ${e.message}", e)
        }
    }
    
    
    /**
     * 解密文本
     * @param encryptedText Base64编码的密文
     * @return 明文
     */
    fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(SECRET_KEY, ALGORITHM)
            val ivSpec = IvParameterSpec(IV)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.getDecoder().decode(encryptedText)
            val decrypted = cipher.doFinal(decoded)
            
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw EncryptionException("解密失败: ${e.message}", e)
        }
    }
    
    /**
     * 验证加密/解密是否正常工作
     * @return 如果加密解密功能正常返回true
     */
    fun verify(): Boolean {
        return try {
            val testText = "test-encryption-verification"
            val encrypted = encrypt(testText)
            val decrypted = decrypt(encrypted)
            testText == decrypted
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 测试方法: 加密API密钥
     * 使用方法: 运行此main函数,输入你的API密钥,获取加密后的文本
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== API密钥加密工具 ===")
        println("请输入要加密的API密钥:")
        
        val apiKey = readLine() ?: ""
        
        if (apiKey.isBlank()) {
            println("错误: API密钥不能为空")
            return
        }
        
        try {
            val encrypted = encrypt(apiKey)
            println("\n加密成功!")
            println("原始密钥: $apiKey")
            println("加密后的文本:")
            println(encrypted)
            println("\n请将上面的加密文本复制到 BuiltInModelProvider.kt 中使用")
            
            // 验证解密
            val decrypted = decrypt(encrypted)
            if (decrypted == apiKey) {
                println("\n✓ 验证成功: 加密/解密正常工作")
            } else {
                println("\n✗ 警告: 解密后的内容与原始内容不匹配")
            }
        } catch (e: Exception) {
            println("加密失败: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * 加密异常
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
