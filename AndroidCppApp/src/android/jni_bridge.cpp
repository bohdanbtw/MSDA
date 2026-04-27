#include <jni.h>
#include <mutex>
#include <sstream>
#include "msda/AccountManager.h"

namespace {
std::mutex g_mutex;
msda::AccountManager g_manager;

std::string jstringToStd(JNIEnv* env, jstring input) {
    if (input == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(input, nullptr);
    std::string out = chars ? chars : "";
    if (chars) {
        env->ReleaseStringUTFChars(input, chars);
    }

    return out;
}

jstring stdToJstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

// Quick JSON value extractor used by tryRefreshSession / reauthWithPassword.
// Returns the value for a key like "key":"value".
std::string extractJsonValue(const std::string& json, const std::string& key) {
    const std::string pattern = "\"" + key + "\":\"";
    std::size_t pos = json.find(pattern);
    if (pos == std::string::npos) {
        return {};
    }
    pos += pattern.size();
    std::size_t end = json.find('"', pos);
    if (end == std::string::npos) {
        return {};
    }
    return json.substr(pos, end - pos);
}

// Cached Java class and method IDs for the HTTP helper (filled on first use).
static jclass g_helperClass = nullptr;
static jmethodID g_performRefreshID = nullptr;
static jmethodID g_performReauthID = nullptr;

bool ensureHelperMethods(JNIEnv* env) {
    if (g_helperClass != nullptr) return true;

    jclass localClass = env->FindClass("com/msda/android/HttpHelper");
    if (localClass == nullptr) return false;

    g_helperClass = static_cast<jclass>(env->NewGlobalRef(localClass));
    env->DeleteLocalRef(localClass);

    if (g_helperClass == nullptr) return false;

    g_performRefreshID = env->GetStaticMethodID(
        g_helperClass,
        "performRefresh",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (g_performRefreshID == nullptr) return false;

    g_performReauthID = env->GetStaticMethodID(
        g_helperClass,
        "performReauthWithPassword",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (g_performReauthID == nullptr) return false;

    return true;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msda_android_NativeBridge_importMafilesFromFolder(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring folderPath) {

    std::lock_guard<std::mutex> lock(g_mutex);
    const auto folder = jstringToStd(env, folderPath);
    return g_manager.importFromFolder(folder) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getAccounts(
    JNIEnv* env,
    jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    const auto& accounts = g_manager.accounts();

    std::ostringstream out;
    for (std::size_t i = 0; i < accounts.size(); ++i) {
        out << i << "|" << accounts[i].accountName << "|" << accounts[i].steamId;
        if (i + 1 < accounts.size()) {
            out << "\n";
        }
    }

    return stdToJstring(env, out.str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msda_android_NativeBridge_setActiveAccount(
    JNIEnv* env,
    jobject /*thiz*/,
    jint index) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (index < 0) {
        return JNI_FALSE;
    }

    return g_manager.setActiveIndex(static_cast<std::size_t>(index)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getActiveAccount(
    JNIEnv* env,
    jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    const auto* active = g_manager.activeAccount();
    if (active == nullptr) {
        return stdToJstring(env, "{}");
    }

    std::ostringstream out;
    out << "{\"accountName\":\"" << active->accountName << "\","
        << "\"steamId\":\"" << active->steamId << "\","
        << "\"sharedSecret\":\"" << active->sharedSecret << "\","
        << "\"identitySecret\":\"" << active->identitySecret << "\","
        << "\"deviceId\":\"" << active->deviceId << "\","
        << "\"sessionId\":\"" << active->sessionId << "\","
        << "\"steamLoginSecure\":\"" << active->steamLoginSecure << "\","
        << "\"refreshToken\":\"" << active->refreshToken << "\","
        << "\"accessToken\":\"" << active->accessToken << "\"}";
    return stdToJstring(env, out.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getActiveCode(
    JNIEnv* env,
    jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    return stdToJstring(env, g_manager.activeCode());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_msda_android_NativeBridge_getSecondsToNextCode(
    JNIEnv* env,
    jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<jint>(g_manager.secondsToNextCode());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getActiveConfirmationAuthPayload(
    JNIEnv* env,
    jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    return stdToJstring(env, g_manager.activeConfirmationAuthPayload());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msda_android_NativeBridge_tryRefreshSession(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId,
    jstring deviceId) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (steamId == nullptr || deviceId == nullptr) return JNI_FALSE;
    if (!ensureHelperMethods(env)) return JNI_FALSE;

    jstring result = static_cast<jstring>(
        env->CallStaticObjectMethod(g_helperClass, g_performRefreshID, steamId, deviceId));
    if (result == nullptr) return JNI_FALSE;

    const std::string json = jstringToStd(env, result);
    env->DeleteLocalRef(result);
    if (json.empty()) return JNI_FALSE;

    const auto newSessionId          = extractJsonValue(json, "sessionid");
    const auto newSteamLoginSecure   = extractJsonValue(json, "steamLoginSecure");
    const auto newRefreshToken       = extractJsonValue(json, "refresh_token");
    const auto newAccessToken        = extractJsonValue(json, "access_token");
    const auto newDeviceId           = extractJsonValue(json, "device_id");
    const auto sId                   = jstringToStd(env, steamId);

    g_manager.updateSessionTokens(sId, newSessionId, newSteamLoginSecure,
                                  newRefreshToken, newAccessToken, newDeviceId,
                                  jstringToStd(env, deviceId));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msda_android_NativeBridge_reauthWithPassword(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId,
    jstring password,
    jstring deviceId) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (steamId == nullptr || password == nullptr || deviceId == nullptr) return JNI_FALSE;
    if (!ensureHelperMethods(env)) return JNI_FALSE;

    jstring result = static_cast<jstring>(
        env->CallStaticObjectMethod(g_helperClass, g_performReauthID,
                                    steamId, password, deviceId));
    if (result == nullptr) return JNI_FALSE;

    const std::string json = jstringToStd(env, result);
    env->DeleteLocalRef(result);
    if (json.empty()) return JNI_FALSE;

    const auto newSessionId          = extractJsonValue(json, "sessionid");
    const auto newSteamLoginSecure   = extractJsonValue(json, "steamLoginSecure");
    const auto newRefreshToken       = extractJsonValue(json, "refresh_token");
    const auto newAccessToken        = extractJsonValue(json, "access_token");
    const auto newDeviceId           = extractJsonValue(json, "device_id");
    const auto sId                   = jstringToStd(env, steamId);

    g_manager.updateSessionTokens(sId, newSessionId, newSteamLoginSecure,
                                  newRefreshToken, newAccessToken, newDeviceId,
                                  jstringToStd(env, deviceId));
    return JNI_TRUE;
}
