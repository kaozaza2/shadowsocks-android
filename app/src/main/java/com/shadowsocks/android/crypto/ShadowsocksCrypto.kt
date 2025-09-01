package com.shadowsocks.android.crypto

object ShadowsocksCrypto {
    
    enum class Method(val displayName: String, val keySize: Int) {
        AES_256_GCM("AES-256-GCM", 32),
        CHACHA20_POLY1305("ChaCha20-Poly1305", 32),
        AES_128_GCM("AES-128-GCM", 16)
    }
    
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
    
    // TODO: Implement actual crypto operations using BouncyCastle
    // This is a placeholder for the actual encryption/decryption logic
}