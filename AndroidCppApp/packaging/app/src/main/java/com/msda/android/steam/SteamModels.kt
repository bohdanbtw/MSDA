package com.msda.android.steam

data class DeviceDetails(
    val deviceFriendlyName: String,
    val platformType: Int,
    val osType: Int,
    val gamingDeviceType: Int
)

data class SteamSessionData(
    val steamId: String,
    val accountName: String,
    val sessionId: String,
    val steamLoginSecure: String,
    val refreshToken: String,
    val accessToken: String
)

data class TransferInfo(
    val url: String,
    val nonce: String,
    val auth: String
)

data class LoginV2Result(
    val steamId: String,
    val accountName: String,
    val sessionId: String,
    val steamLoginSecure: String,
    val refreshToken: String,
    val accessToken: String,
    val expiresAtMs: Long
)

class SessionInvalidException(message: String) : IllegalStateException(message)
