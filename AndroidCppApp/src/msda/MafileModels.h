#pragma once

#include <string>

namespace msda {

struct MafileAccount {
    std::string accountName{};
    std::string steamId{};
    std::string sharedSecret{};
    std::string identitySecret{};

    // Stable device identifier that persists across OS reinstalls.
    // Used for data linking and recovery, independent of the maFile fingerprint.
    std::string permanentDeviceId{};

    // Original device fingerprint from the maFile (required for 2‑FA binding).
    std::string deviceId{};

    std::string sessionId{};
    std::string steamLoginSecure{};
    std::string refreshToken{};
    std::string accessToken{};
    std::string sourcePath{};
};

} // namespace msda
