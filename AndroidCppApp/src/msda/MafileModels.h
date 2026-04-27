#pragma once

#include <string>

namespace msda {

struct MafileAccount {
    std::string accountName;
    std::string steamId;
    std::string sharedSecret;
    std::string identitySecret;

    // Stable, non‑changing device identifier that persists across reinstalls.
    // Replaces the original mutable maFile device fingerprint.
    std::string deviceId;

    std::string sessionId;
    std::string steamLoginSecure;
    std::string refreshToken;
    std::string accessToken;
    std::string sourcePath;
};

} // namespace msda
