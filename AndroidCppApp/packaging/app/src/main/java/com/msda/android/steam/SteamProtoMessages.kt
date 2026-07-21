package com.msda.android.steam

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

object SteamProtoMessages {
    data class GetPasswordRsaPublicKeyRequest(val accountName: String)
    data class GetPasswordRsaPublicKeyResponse(
        val publicKeyMod: String,
        val publicKeyExp: String,
        val timestamp: Long
    )

    data class BeginAuthSessionViaCredentialsRequest(
        val deviceFriendlyName: String,
        val accountName: String,
        val encryptedPassword: String,
        val encryptionTimestamp: Long,
        val rememberLogin: Boolean,
        val platformType: Int,
        val persistence: Int,
        val websiteId: String,
        val deviceDetails: DeviceDetails
    )

    data class BeginAuthSessionViaCredentialsResponse(
        val clientId: Long,
        val requestId: ByteArray,
        val interval: Float,
        val allowedConfirmations: List<AllowedConfirmation>,
        val steamId: Long,
        val extendedErrorMessage: String
    )

    data class AllowedConfirmation(
        val confirmationType: Int,
        val associatedMessage: String = ""
    )

    data class UpdateAuthSessionWithSteamGuardCodeRequest(
        val clientId: Long,
        val steamId: Long,
        val code: String,
        val codeType: Int
    )

    data class PollAuthSessionStatusRequest(
        val clientId: Long,
        val requestId: ByteArray
    )

    data class PollAuthSessionStatusResponse(
        val refreshToken: String,
        val accessToken: String
    )

    data class GenerateAccessTokenForAppRequest(
        val refreshToken: String,
        val steamId: Long,
        val tokenRenewalType: Boolean = true
    )

    data class GenerateAccessTokenForAppResponse(
        val accessToken: String
    )

    data class GetAuthSessionsForAccountResponse(val clientIds: List<Long>)

    data class GetAuthSessionInfoRequest(val clientId: Long)

    data class UpdateAuthSessionWithMobileConfirmationRequest(
        val version: Int,
        val clientId: Long,
        val steamId: Long,
        val signature: ByteArray,
        val confirm: Boolean,
        val persistence: Int
    )

    private fun build(block: (CodedOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        val coded = CodedOutputStream.newInstance(out)
        block(coded)
        coded.flush()
        return out.toByteArray()
    }

    fun encodeGetPasswordRsaPublicKeyRequest(request: GetPasswordRsaPublicKeyRequest): ByteArray =
        build { out ->
            out.writeString(1, request.accountName)
        }

    fun decodeGetPasswordRsaPublicKeyResponse(bytes: ByteArray): GetPasswordRsaPublicKeyResponse {
        var mod = ""
        var exp = ""
        var timestamp = 0L
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            when (input.readTag()) {
                0 -> break
                10 -> mod = input.readString()
                18 -> exp = input.readString()
                24 -> timestamp = input.readUInt64().toLong()
                else -> input.skipField(input.lastTag)
            }
        }
        return GetPasswordRsaPublicKeyResponse(mod, exp, timestamp)
    }

    fun encodeBeginAuthSessionViaCredentialsRequest(request: BeginAuthSessionViaCredentialsRequest): ByteArray =
        build { out ->
            out.writeString(1, request.deviceFriendlyName)
            out.writeString(2, request.accountName)
            out.writeString(3, request.encryptedPassword)
            out.writeUInt64(4, request.encryptionTimestamp)
            out.writeBool(5, request.rememberLogin)
            out.writeInt32(6, request.platformType)
            out.writeInt32(7, request.persistence)
            out.writeString(8, request.websiteId)
            out.writeTag(9, 2)
            val deviceBytes = build { d ->
                d.writeString(1, request.deviceDetails.deviceFriendlyName)
                d.writeInt32(2, request.deviceDetails.platformType)
                d.writeInt32(3, request.deviceDetails.osType)
                d.writeUInt32(4, request.deviceDetails.gamingDeviceType)
            }
            out.writeUInt32NoTag(deviceBytes.size)
            out.writeRawBytes(deviceBytes)
        }

    fun decodeBeginAuthSessionViaCredentialsResponse(bytes: ByteArray): BeginAuthSessionViaCredentialsResponse {
        var clientId = 0L
        var requestId = byteArrayOf()
        var interval = 1f
        val allowed = mutableListOf<AllowedConfirmation>()
        var steamId = 0L
        var error = ""
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            when (input.readTag()) {
                0 -> break
                8 -> clientId = input.readUInt64().toLong()
                18 -> requestId = input.readByteArray()
                29 -> interval = input.readFloat()
                34 -> allowed += decodeAllowedConfirmation(input.readByteArray())
                40 -> steamId = input.readUInt64().toLong()
                66 -> error = input.readString()
                else -> input.skipField(input.lastTag)
            }
        }
        return BeginAuthSessionViaCredentialsResponse(clientId, requestId, interval, allowed, steamId, error)
    }

    private fun decodeAllowedConfirmation(bytes: ByteArray): AllowedConfirmation {
        var confirmationType = 0
        var associatedMessage = ""
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            when (input.readTag()) {
                0 -> break
                8 -> confirmationType = input.readInt32()
                18 -> associatedMessage = input.readString()
                else -> input.skipField(input.lastTag)
            }
        }
        return AllowedConfirmation(confirmationType, associatedMessage)
    }

    fun encodeUpdateAuthSessionWithSteamGuardCodeRequest(request: UpdateAuthSessionWithSteamGuardCodeRequest): ByteArray =
        build { out ->
            out.writeUInt64(1, request.clientId)
            out.writeFixed64(2, request.steamId)
            out.writeString(3, request.code)
            out.writeInt32(4, request.codeType)
        }

    fun encodePollAuthSessionStatusRequest(request: PollAuthSessionStatusRequest): ByteArray =
        build { out ->
            out.writeUInt64(1, request.clientId)
            out.writeByteArray(2, request.requestId)
        }

    fun decodePollAuthSessionStatusResponse(bytes: ByteArray): PollAuthSessionStatusResponse {
        var refreshToken = ""
        var accessToken = ""
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            when (input.readTag()) {
                0 -> break
                26 -> refreshToken = input.readString()
                34 -> accessToken = input.readString()
                else -> input.skipField(input.lastTag)
            }
        }
        return PollAuthSessionStatusResponse(refreshToken, accessToken)
    }

    fun encodeGenerateAccessTokenForAppRequest(request: GenerateAccessTokenForAppRequest): ByteArray =
        build { out ->
            out.writeString(1, request.refreshToken)
            out.writeFixed64(2, request.steamId)
            out.writeBool(3, request.tokenRenewalType)
        }

    fun decodeGenerateAccessTokenForAppResponse(bytes: ByteArray): GenerateAccessTokenForAppResponse {
        var accessToken = ""
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            when (input.readTag()) {
                0 -> break
                10 -> accessToken = input.readString()
                else -> input.skipField(input.lastTag)
            }
        }
        return GenerateAccessTokenForAppResponse(accessToken)
    }

    fun decodeGetAuthSessionsForAccountResponse(bytes: ByteArray): GetAuthSessionsForAccountResponse {
        val ids = mutableListOf<Long>()
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            when (input.readTag()) {
                0 -> break
                8 -> ids += input.readUInt64().toLong()
                10 -> {
                    val length = input.readRawVarint32()
                    val old = input.pushLimit(length)
                    while (!input.isAtEnd) {
                        ids += input.readUInt64().toLong()
                    }
                    input.popLimit(old)
                }
                else -> input.skipField(input.lastTag)
            }
        }
        return GetAuthSessionsForAccountResponse(ids)
    }

    fun encodeGetAuthSessionInfoRequest(request: GetAuthSessionInfoRequest): ByteArray =
        build { out ->
            out.writeUInt64(1, request.clientId)
        }

    fun encodeUpdateAuthSessionWithMobileConfirmationRequest(
        request: UpdateAuthSessionWithMobileConfirmationRequest
    ): ByteArray = build { out ->
        out.writeInt32(1, request.version)
        out.writeUInt64(2, request.clientId)
        out.writeFixed64(3, request.steamId)
        out.writeByteArray(4, request.signature)
        out.writeBool(5, request.confirm)
        out.writeInt32(6, request.persistence)
    }
}
