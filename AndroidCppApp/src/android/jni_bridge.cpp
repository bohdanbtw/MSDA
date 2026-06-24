#include <jni.h>
#include <mutex>
#include <sstream>
#include <cstdio>
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

std::string escapeJson(const std::string& value) {
    std::string out;
    out.reserve(value.size() * 2);
    for (char ch : value) {
        switch (ch) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (static_cast<unsigned char>(ch) < 0x20) {
                    char buf[7];
                    std::snprintf(buf, sizeof(buf), "\\u%04x",
                                  static_cast<unsigned char>(ch));
                    out += buf;
                } else {
                    out += ch;
                }
                break;
        }
    }
    return out;
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

    // Build valid JSON with proper escaping.
    std::ostringstream out;
    out << "{\"accountName\":\""        << escapeJson(active->accountName)       << "\","
        << "\"steamId\":\""             << escapeJson(active->steamId)            << "\","
        << "\"sharedSecret\":\""        << escapeJson(active->sharedSecret)       << "\","
        << "\"identitySecret\":\""      << escapeJson(active->identitySecret)     << "\","
        << "\"deviceId\":\""            << escapeJson(active->deviceId)           << "\","
        << "\"sessionId\":\""           << escapeJson(active->sessionId)          << "\","
        << "\"steamLoginSecure\":\""    << escapeJson(active->steamLoginSecure)   << "\","
        << "\"refreshToken\":\""        << escapeJson(active->refreshToken)       << "\","
        << "\"accessToken\":\""         << escapeJson(active->accessToken)        << "\"}";
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
Java_com_msda_android_NativeBridge_updateSessionTokens(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId,
    jstring sessionId,
    jstring steamLoginSecure,
    jstring refreshToken,
    jstring accessToken) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (steamId == nullptr) return JNI_FALSE;

    g_manager.updateSessionTokens(
        jstringToStd(env, steamId),
        sessionId != nullptr ? jstringToStd(env, sessionId) : "",
        steamLoginSecure != nullptr ? jstringToStd(env, steamLoginSecure) : "",
        refreshToken != nullptr ? jstringToStd(env, refreshToken) : "",
        accessToken != nullptr ? jstringToStd(env, accessToken) : "",
        "",
        ""
    );
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msda_android_NativeBridge_updateMafileSessionTokens(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId,
    jstring sessionId,
    jstring steamLoginSecure,
    jstring refreshToken,
    jstring accessToken) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (steamId == nullptr) return JNI_FALSE;

    return g_manager.updateMafileSessionTokens(
        jstringToStd(env, steamId),
        sessionId != nullptr ? jstringToStd(env, sessionId) : "",
        steamLoginSecure != nullptr ? jstringToStd(env, steamLoginSecure) : "",
        refreshToken != nullptr ? jstringToStd(env, refreshToken) : "",
        accessToken != nullptr ? jstringToStd(env, accessToken) : ""
    ) ? JNI_TRUE : JNI_FALSE;
}
