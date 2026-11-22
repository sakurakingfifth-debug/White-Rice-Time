package com.panl.balance.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 的本地加密。
 *
 * 密钥由 **Android Keystore** 生成（AES-256-GCM，密钥永不离开 TEE/StrongBox），
 * SharedPreferences 里只落 `v1:` + Base64(iv ‖ ciphertext)。
 *
 * 边界说明：
 * - 卸载重装或换机后 Keystore 条目消失，旧密文无法解密 → 表现为「未配置密钥」，符合预期；
 * - root 设备仍可从运行中的进程内存拿到明文，这是任何本地方案的上限；
 * - 任何一步失败（个别 ROM 裁剪了 Keystore）就退回明文存储，保证功能不因加密而不可用。
 */
object SecretBox {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "pui_balance_api_key"
    private const val PREFIX = "v1:"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        val encrypted = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
        }.getOrNull()
        return encrypted ?: plain
    }

    fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (raw.size <= IV_LEN) return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_BITS, raw, 0, IV_LEN)
            )
            String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
