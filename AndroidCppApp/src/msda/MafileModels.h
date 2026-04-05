#pragma once

#include <string>

namespace msda {

struct MafileAccount {
    std::string accountName;
    std::string steamId;
    std::string sharedSecret;
    std::string identitySecret;
    std::string deviceId;
    std::string sessionId;
    std::string steamLoginSecure;
    std::string refreshToken;
    std::string accessToken;
    std::string sourcePath;
};

} // namespace msda
