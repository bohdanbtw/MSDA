#include <jni.h>
#include <mutex>
#include <sstream>
#include <cstdio>
#include <exception>
#include <android/log.h>
#include "msda/AccountManager.h"

namespace {
std::mutex g_mutex;
msda::AccountManager g_manager;

void logJniException(const char* function, const char* what) {
    __android_log_print(ANDROID_LOG_ERROR, "msda",
                        "%s: C++ exception contained at JNI boundary: %s", function, what);
}

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

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        const auto folder = jstringToStd(env, folderPath);
        return g_manager.importFromFolder(folder) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        logJniException("importMafilesFromFolder", e.what());
        return JNI_FALSE;
    } catch (...) {
        logJniException("importMafilesFromFolder", "unknown exception");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getAccounts(
    JNIEnv* env,
    jobject /*thiz*/) {

    try {
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
    } catch (const std::exception& e) {
        logJniException("getAccounts", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        logJniException("getAccounts", "unknown exception");
        return env->NewStringUTF("");
    }
}

// NOTE: setActiveAccount mutates process-global state and must only be called from
// explicit UI-thread user selection. Background receivers/workers and widget ticks must
// use the by-steamId accessors below instead.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_msda_android_NativeBridge_setActiveAccount(
    JNIEnv* env,
    jobject /*thiz*/,
    jint index) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (index < 0) {
            return JNI_FALSE;
        }

        return g_manager.setActiveIndex(static_cast<std::size_t>(index)) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        logJniException("setActiveAccount", e.what());
        return JNI_FALSE;
    } catch (...) {
        logJniException("setActiveAccount", "unknown exception");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getActiveAccount(
    JNIEnv* env,
    jobject /*thiz*/) {

    try {
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
    } catch (const std::exception& e) {
        logJniException("getActiveAccount", e.what());
        return env->NewStringUTF("{}");
    } catch (...) {
        logJniException("getActiveAccount", "unknown exception");
        return env->NewStringUTF("{}");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getActiveCode(
    JNIEnv* env,
    jobject /*thiz*/) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        return stdToJstring(env, g_manager.activeCode());
    } catch (const std::exception& e) {
        logJniException("getActiveCode", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        logJniException("getActiveCode", "unknown exception");
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_msda_android_NativeBridge_getSecondsToNextCode(
    JNIEnv* env,
    jobject /*thiz*/) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        return static_cast<jint>(g_manager.secondsToNextCode());
    } catch (const std::exception& e) {
        logJniException("getSecondsToNextCode", e.what());
        return 0;
    } catch (...) {
        logJniException("getSecondsToNextCode", "unknown exception");
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getActiveConfirmationAuthPayload(
    JNIEnv* env,
    jobject /*thiz*/) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        return stdToJstring(env, g_manager.activeConfirmationAuthPayload());
    } catch (const std::exception& e) {
        logJniException("getActiveConfirmationAuthPayload", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        logJniException("getActiveConfirmationAuthPayload", "unknown exception");
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getConfirmationAuthPayloadForSteamId(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (steamId == nullptr) return stdToJstring(env, "");
        return stdToJstring(env, g_manager.confirmationAuthPayloadForSteamId(jstringToStd(env, steamId)));
    } catch (const std::exception& e) {
        logJniException("getConfirmationAuthPayloadForSteamId", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        logJniException("getConfirmationAuthPayloadForSteamId", "unknown exception");
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msda_android_NativeBridge_getCodeForSteamId(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (steamId == nullptr) return stdToJstring(env, "");
        return stdToJstring(env, g_manager.codeForSteamId(jstringToStd(env, steamId)));
    } catch (const std::exception& e) {
        logJniException("getCodeForSteamId", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        logJniException("getCodeForSteamId", "unknown exception");
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_msda_android_NativeBridge_getSecondsToNextCodeForSteamId(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring steamId) {

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (steamId == nullptr) return static_cast<jint>(-1);
        return static_cast<jint>(g_manager.secondsToNextCodeForSteamId(jstringToStd(env, steamId)));
    } catch (const std::exception& e) {
        logJniException("getSecondsToNextCodeForSteamId", e.what());
        return static_cast<jint>(-1);
    } catch (...) {
        logJniException("getSecondsToNextCodeForSteamId", "unknown exception");
        return static_cast<jint>(-1);
    }
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

    try {
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
    } catch (const std::exception& e) {
        logJniException("updateSessionTokens", e.what());
        return JNI_FALSE;
    } catch (...) {
        logJniException("updateSessionTokens", "unknown exception");
        return JNI_FALSE;
    }
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

    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (steamId == nullptr) return JNI_FALSE;

        return g_manager.updateMafileSessionTokens(
            jstringToStd(env, steamId),
            sessionId != nullptr ? jstringToStd(env, sessionId) : "",
            steamLoginSecure != nullptr ? jstringToStd(env, steamLoginSecure) : "",
            refreshToken != nullptr ? jstringToStd(env, refreshToken) : "",
            accessToken != nullptr ? jstringToStd(env, accessToken) : ""
        ) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        logJniException("updateMafileSessionTokens", e.what());
        return JNI_FALSE;
    } catch (...) {
        logJniException("updateMafileSessionTokens", "unknown exception");
        return JNI_FALSE;
    }
}
