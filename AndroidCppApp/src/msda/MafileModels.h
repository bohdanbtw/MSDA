#pragma once

#include <string>

namespace msda {

struct MafileAccount {
    std::string accountName;
    std::string steamId;
    std::string sharedSecret;
    std::string identitySecret;
    std::string deviceId;             // original device fingerprint from maFile
    std::string sessionId;
    std::string steamLoginSecure;
    std::string refreshToken;
    std::string accessToken;
    std::string sourcePath;

    // Stable device identifier that persists across reinstalls
    std::string permanentDeviceId;
};

} // namespace msda
