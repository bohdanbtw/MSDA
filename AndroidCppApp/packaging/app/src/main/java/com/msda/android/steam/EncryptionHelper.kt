package com.msda.android.steam

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    fun toBase64EncryptedPassword(keyExp: String, keyMod: String, password: String): String {
        val modulus = BigInteger(keyMod, 16)
        val exponent = BigInteger(keyExp, 16)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.US_ASCII)))
    }

    fun generateConfirmationHash(time: Long, identitySecret: String, tag: String = "conf"): String {
        return Base64.getEncoder().encodeToString(generateConfirmationHashBytes(time, identitySecret, tag))
    }

    fun generateConfirmationHashBytes(time: Long, identitySecret: String, tag: String = "conf"): ByteArray {
        val secret = Base64.getDecoder().decode(identitySecret)
        val size = 8 + minOf(tag.length, 32)
        val payload = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).putLong(time).array()
        val result = ByteArray(size)
        System.arraycopy(payload, 0, result, 0, 8)
        val tagBytes = tag.toByteArray(Charsets.UTF_8)
        System.arraycopy(tagBytes, 0, result, 8, size - 8)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        return mac.doFinal(result)
    }

    fun computeQrSignature(version: Int, clientId: Long, steamId: Long, sharedSecret: String): ByteArray {
        val secret = Base64.getDecoder().decode(sharedSecret)
        val payload = ByteArray(18)
        ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(version.toShort())
        ByteBuffer.wrap(payload, 2, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(clientId)
        ByteBuffer.wrap(payload, 10, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(steamId)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(payload)
    }
}
