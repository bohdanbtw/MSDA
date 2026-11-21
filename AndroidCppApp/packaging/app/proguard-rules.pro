# R8/ProGuard rules for MSDA release builds (minifyEnabled true).
# Do not remove these: the bundled native library resolves symbols by name,
# and R8 would otherwise rename or strip the classes below.

## ---------------------------------------------------------------------------
## JNI bridge
## ---------------------------------------------------------------------------
## libmsda_android.so exports Java_com_msda_android_NativeBridge_* entry points,
## which are resolved by fully-qualified name at runtime. R8 must preserve the
## NativeBridge class name and every `external fun`'s name + descriptor,
## otherwise calls into native code fail with UnsatisfiedLinkError.
-keep,includedescriptorclasses class com.msda.android.NativeBridge {
    native <methods>;
}
-keepclasseswithmembernames,includedescriptorclasses class com.msda.android.NativeBridge {
    native <methods>;
}

## ---------------------------------------------------------------------------
## Data / model classes
## ---------------------------------------------------------------------------
## DTOs exchanged with the native core and parsed from JSON. Keeping them
## prevents R8 from renaming/removing fields used across the JNI boundary or by
## reflection-based serializers, and keeps release smoke tests (mafile import,
## 2FA code generation, confirmation parsing) predictable.
-keep class com.msda.android.ConfirmationAuthContext { *; }
-keep class com.msda.android.TradePartnerSummary { *; }
-keep class com.msda.android.ConfirmationItem { *; }
-keep class com.msda.android.ConfirmationBundle { *; }
-keep class com.msda.android.AccountProxyConfig { *; }
-keep class com.msda.android.StoredSteamSession { *; }
-keep class com.msda.android.AppReleaseInfo { *; }
-keep class com.msda.android.SteamAuthResult { *; }
-keep class com.msda.android.ProxyCheckResult { *; }
-keep class com.msda.android.QrApprovalResult { *; }
-keep class com.msda.android.steam.DeviceDetails { *; }
-keep class com.msda.android.steam.SteamSessionData { *; }
-keep class com.msda.android.steam.TransferInfo { *; }
-keep class com.msda.android.steam.LoginV2Result { *; }
-keep class com.msda.android.steam.ParsedSteamToken { *; }
-keep class com.msda.android.steam.SteamProtoMessages$** { *; }
-keep class com.msda.android.steam.MafileSecretsReader$MafileSecrets { *; }
