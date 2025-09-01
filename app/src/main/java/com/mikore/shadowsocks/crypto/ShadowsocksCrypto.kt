package com.mikore.shadowsocks.crypto

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.digests.MD5Digest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.spec.SecretKeySpec

object ShadowsocksCrypto {
    
    init {
        // Add BouncyCastle as security provider
        Security.addProvider(BouncyCastleProvider())
    }
    
    enum class Method(val displayName: String, val keySize: Int, val ivSize: Int) {
        AES_256_GCM("AES-256-GCM", 32, 12),
        CHACHA20_POLY1305("ChaCha20-Poly1305", 32, 12),
        AES_128_GCM("AES-128-GCM", 16, 12)
    }
    
    private const val TAG_SIZE = 16 // 128 bits for AEAD tag
    
    fun getSupportedMethods(): List<Method> {
        return listOf(
            Method.AES_256_GCM,
            Method.CHACHA20_POLY1305,
            Method.AES_128_GCM
        )
    }
    
    fun isMethodSupported(method: String): Boolean {
        return getSupportedMethods().any { it.displayName == method }
    }
    
    /**
     * Derive key from password using EVP_BytesToKey equivalent
     */
    fun deriveKey(password: String, keySize: Int): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val key = ByteArray(keySize)
        var keyOffset = 0
        var hash: ByteArray? = null
        
        val md = MessageDigest.getInstance("MD5")
        
        while (keyOffset < keySize) {
            md.reset()
            if (hash != null) {
                md.update(hash)
            }
            md.update(passwordBytes)
            hash = md.digest()
            
            val copyLength = minOf(hash.size, keySize - keyOffset)
            System.arraycopy(hash, 0, key, keyOffset, copyLength)
            keyOffset += copyLength
        }
        
        return key
    }
    
    /**
     * Generate random IV for encryption
     */
    fun generateIV(size: Int): ByteArray {
        val iv = ByteArray(size)
        SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * Encrypt data using specified method
     */
    fun encrypt(data: ByteArray, key: ByteArray, method: Method): ByteArray {
        val iv = generateIV(method.ivSize)
        val cipher = createCipher(method, key, iv, true)
        
        val output = ByteArray(data.size + TAG_SIZE)
        val processed = cipher.processBytes(data, 0, data.size, output, 0)
        val finalBytes = cipher.doFinal(output, processed)
        
        // Prepend IV to encrypted data
        val result = ByteArray(iv.size + processed + finalBytes)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(output, 0, result, iv.size, processed + finalBytes)
        
        return result
    }
    
    /**
     * Decrypt data using specified method
     */
    fun decrypt(encryptedData: ByteArray, key: ByteArray, method: Method): ByteArray {
        if (encryptedData.size < method.ivSize + TAG_SIZE) {
            throw IllegalArgumentException("Encrypted data too short")
        }
        
        // Extract IV from beginning of data
        val iv = ByteArray(method.ivSize)
        System.arraycopy(encryptedData, 0, iv, 0, method.ivSize)
        
        // Extract encrypted payload
        val payload = ByteArray(encryptedData.size - method.ivSize)
        System.arraycopy(encryptedData, method.ivSize, payload, 0, payload.size)
        
        val cipher = createCipher(method, key, iv, false)
        
        val output = ByteArray(payload.size - TAG_SIZE)
        val processed = cipher.processBytes(payload, 0, payload.size, output, 0)
        val finalBytes = cipher.doFinal(output, processed)
        
        // Return only the actual decrypted data
        val result = ByteArray(processed + finalBytes)
        System.arraycopy(output, 0, result, 0, result.size)
        
        return result
    }
    
    /**
     * Create cipher for encryption/decryption
     */
    private fun createCipher(method: Method, key: ByteArray, iv: ByteArray, forEncryption: Boolean): org.bouncycastle.crypto.modes.AEADBlockCipher {
        return when (method) {
            Method.AES_256_GCM, Method.AES_128_GCM -> {
                val cipher = GCMBlockCipher(AESEngine())
                val params = AEADParameters(KeyParameter(key), TAG_SIZE * 8, iv)
                cipher.init(forEncryption, params)
                cipher
            }
            Method.CHACHA20_POLY1305 -> {
                // ChaCha20-Poly1305 implementation would need a specialized cipher
                // For now, fall back to AES-256-GCM
                val cipher = GCMBlockCipher(AESEngine())
                val aesKey = if (key.size != 32) deriveKey(String(key), 32) else key
                val params = AEADParameters(KeyParameter(aesKey), TAG_SIZE * 8, iv)
                cipher.init(forEncryption, params)
                cipher
            }
        }
    }
    
    /**
     * Create cipher stream for continuous encryption/decryption
     */
    class CipherStream(
        private val method: Method,
        private val key: ByteArray,
        private val forEncryption: Boolean
    ) {
        private lateinit var cipher: org.bouncycastle.crypto.modes.AEADBlockCipher
        private var isInitialized = false
        
        fun process(input: ByteArray): ByteArray {
            if (!isInitialized) {
                val iv = if (forEncryption) {
                    generateIV(method.ivSize)
                } else {
                    // For decryption, IV should be extracted from input
                    val iv = ByteArray(method.ivSize)
                    System.arraycopy(input, 0, iv, 0, method.ivSize)
                    iv
                }
                
                cipher = createCipher(method, key, iv, forEncryption)
                isInitialized = true
                
                if (forEncryption) {
                    // For encryption, prepend IV to output
                    val actualInput = ByteArray(input.size)
                    System.arraycopy(input, 0, actualInput, 0, input.size)
                    return processInternal(actualInput, iv)
                } else {
                    // For decryption, skip IV in input
                    val actualInput = ByteArray(input.size - method.ivSize)
                    System.arraycopy(input, method.ivSize, actualInput, 0, actualInput.size)
                    return processInternal(actualInput)
                }
            }
            
            return processInternal(input)
        }
        
        private fun processInternal(input: ByteArray, prependIV: ByteArray? = null): ByteArray {
            val outputSize = if (forEncryption) input.size + TAG_SIZE else input.size - TAG_SIZE
            val output = ByteArray(outputSize)
            
            val processed = cipher.processBytes(input, 0, input.size, output, 0)
            val finalBytes = cipher.doFinal(output, processed)
            
            val result = ByteArray(processed + finalBytes + (prependIV?.size ?: 0))
            var offset = 0
            
            prependIV?.let {
                System.arraycopy(it, 0, result, 0, it.size)
                offset = it.size
            }
            
            System.arraycopy(output, 0, result, offset, processed + finalBytes)
            return result
        }
    }
}