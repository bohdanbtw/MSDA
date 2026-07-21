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
    const MafileAccount* accountForSteamId(const std::string& steamId) const;
    std::string activeCode() const;
    int secondsToNextCode() const;
    std::string activeConfirmationAuthPayload() const;

    // By-steamId accessors that do NOT mutate the active index. Used by background
    // workers so they never race with the foreground UI over the active account.
    std::string confirmationAuthPayloadForSteamId(const std::string& steamId) const;
    std::string codeForSteamId(const std::string& steamId) const;

    void updateSessionTokens(const std::string& steamId,
                             const std::string& sessionId,
                             const std::string& steamLoginSecure,
                             const std::string& refreshToken,
                             const std::string& accessToken,
                             const std::string& deviceId = "",
                             const std::string& permanentDeviceId = "");

    bool updateMafileSessionTokens(const std::string& steamId,
                                   const std::string& sessionId,
                                   const std::string& steamLoginSecure,
                                   const std::string& refreshToken,
                                   const std::string& accessToken);

private:
    std::vector<MafileAccount> _accounts;
    std::size_t _activeIndex = static_cast<std::size_t>(-1);

    static bool isMafilePath(const std::string& path);
};

} // namespace msda
