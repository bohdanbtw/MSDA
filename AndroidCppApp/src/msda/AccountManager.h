#pragma once

#include <string>
#include <vector>
#include "MafileModels.h"

namespace msda {

class AccountManager {
public:
    bool importFromFolder(const std::string& folderPath);
    bool setActiveIndex(std::size_t index);

    const std::vector<MafileAccount>& accounts() const;
    const MafileAccount* activeAccount() const;
    std::string activeCode() const;
    int secondsToNextCode() const;
    std::string activeConfirmationAuthPayload() const;

    void updateSessionTokens(const std::string& steamId,
                             const std::string& sessionId,
                             const std::string& steamLoginSecure,
                             const std::string& refreshToken,
                             const std::string& accessToken,
                             const std::string& deviceId = "",
                             const std::string& permanentDeviceId = "");

private:
    std::vector<MafileAccount> _accounts;
    std::size_t _activeIndex = static_cast<std::size_t>(-1);

    static bool isMafilePath(const std::string& path);
};

} // namespace msda
