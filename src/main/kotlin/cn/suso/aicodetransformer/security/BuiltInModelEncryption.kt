package cn.suso.aicodetransformer.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 */
object BuiltInModelEncryption {
    

    private val _a = byteArrayOf(0x41, 0x45, 0x53)
    private val _b = charArrayOf('A','E','S','/','C','B','C','/','P','K','C','S','5','P','a','d','d','i','n','g')
    

    private val _k1 = byteArrayOf(
        0x60, 0x78, 0x52, 0x7e, 0x73, 0x72, 0x61, 0x69, 0x61, 0x7d, 0x70, 0x63, 0x7e, 0x69, 0x7d,
        0x33, 0x31, 0x33, 0x36, 0x51, 0x7e, 0x7d, 0x28, 0x71, 0x71, 0x7e, 0x72, 0x70, 0x71, 0x7f, 0x72, 0x20
    )
    private val _k2 = byteArrayOf(
        0x60, 0x78, 0x52, 0x7e, 0x73, 0x72, 0x61, 0x69, 0x61, 0x7d, 0x70, 0x63, 0x78, 0x61, 0x33,
        0x37, 0x2e, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20
    )
    private const val _x = 0x11
    

    private fun _d(data: ByteArray): ByteArray = data.map { (it.toInt() xor _x).toByte() }.toByteArray()
    private fun _s(): String = String(_a, Charsets.UTF_8)
    private fun _t(): String = String(_b)
    
    /**
     */
    fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance(_t())
            val keySpec = SecretKeySpec(_d(_k1).copyOf(32), _s())
            val ivSpec = IvParameterSpec(_d(_k2).copyOf(16))
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.getDecoder().decode(encryptedText)
            val decrypted = cipher.doFinal(decoded)
            
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw EncryptionException("解密失败: ${e.message}", e)
        }
    }
    
    /**
     */
    fun verify(): Boolean {
        return try {
            // 简单验证解密功能是否可用
            val cipher = Cipher.getInstance(_t())
            val keySpec = SecretKeySpec(_d(_k1).copyOf(32), _s())
            val ivSpec = IvParameterSpec(_d(_k2).copyOf(16))
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 加密异常
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

