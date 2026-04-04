#include "MafileParser.h"

#include <filesystem>
#include <fstream>
#include <regex>
#include <sstream>

namespace msda {

std::optional<MafileAccount> MafileParser::parseFile(const std::string& filePath) {
    auto content = readAllText(filePath);
    if (!content.has_value()) {
        return std::nullopt;
    }

    return parseContent(content.value(), filePath);
}

std::optional<MafileAccount> MafileParser::parseContent(const std::string& content, const std::string& filePath) {
    if (content.empty()) {
        return std::nullopt;
    }

    auto accountName = extractStringValue(content, "account_name");
    if (accountName.empty()) {
        accountName = extractStringValue(content, "AccountName");
    }

    auto steamId = extractNumberOrString(content, "steamid");
    if (steamId.empty()) {
        steamId = extractNumberOrString(content, "SteamID");
    }
    if (steamId.empty()) {
        steamId = extractNumberOrString(content, "SteamId");
    }

    auto sharedSecret = extractStringValue(content, "shared_secret");
    if (sharedSecret.empty()) {
        sharedSecret = extractStringValue(content, "SharedSecret");
    }
    if (sharedSecret.empty()) {
        sharedSecret = extractStringValue(content, "shared");
    }

    auto identitySecret = extractStringValue(content, "identity_secret");
    if (identitySecret.empty()) {
        identitySecret = extractStringValue(content, "IdentitySecret");
    }
    if (identitySecret.empty()) {
        identitySecret = extractStringValue(content, "identity");
    }

    auto deviceId = extractStringValue(content, "device_id");
    if (deviceId.empty()) {
        deviceId = extractStringValue(content, "deviceid");
    }

    auto sessionId = extractStringValue(content, "SessionID");
    if (sessionId.empty()) {
        sessionId = extractStringValue(content, "sessionid");
    }

    auto steamLoginSecure = extractStringValue(content, "steamLoginSecure");

    if (accountName.empty()) {
        accountName = fileNameFromPath(filePath);
    }
    if (steamId.empty()) {
        steamId = "unknown";
    }

    MafileAccount item;
    item.accountName = std::move(accountName);
    item.steamId = std::move(steamId);
    item.sharedSecret = std::move(sharedSecret);
    item.identitySecret = std::move(identitySecret);
    item.deviceId = std::move(deviceId);
    item.sessionId = std::move(sessionId);
    item.steamLoginSecure = std::move(steamLoginSecure);
    item.sourcePath = filePath;
    return item;
}

std::optional<std::string> MafileParser::readAllText(const std::string& filePath) {
    std::ifstream input(filePath, std::ios::binary);
    if (!input.is_open()) {
        return std::nullopt;
    }

    std::ostringstream buffer;
    buffer << input.rdbuf();
    return buffer.str();
}

std::string MafileParser::extractStringValue(const std::string& json, const std::string& key) {
    const std::regex re("\\\"" + key + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    std::smatch match;
    if (std::regex_search(json, match, re) && match.size() > 1) {
        return unescapeJsonString(match[1].str());
    }

    return {};
}

std::string MafileParser::extractNumberOrString(const std::string& json, const std::string& key) {
    auto fromString = extractStringValue(json, key);
    if (!fromString.empty()) {
        return fromString;
    }

    const std::regex re("\\\"" + key + "\\\"\\s*:\\s*([0-9]+)");
    std::smatch match;
    if (std::regex_search(json, match, re) && match.size() > 1) {
        return match[1].str();
    }

    return {};
}

std::string MafileParser::fileNameFromPath(const std::string& filePath) {
    std::filesystem::path p(filePath);
    return p.filename().string();
}

std::string MafileParser::unescapeJsonString(const std::string& value) {
    std::string out;
    out.reserve(value.size());

    for (std::size_t i = 0; i < value.size(); ++i) {
        const char ch = value[i];
        if (ch == '\\' && i + 1 < value.size()) {
            const char next = value[++i];
            switch (next) {
                case '"': out.push_back('"'); break;
                case '\\': out.push_back('\\'); break;
                case '/': out.push_back('/'); break;
                case 'b': out.push_back('\b'); break;
                case 'f': out.push_back('\f'); break;
                case 'n': out.push_back('\n'); break;
                case 'r': out.push_back('\r'); break;
                case 't': out.push_back('\t'); break;
                default:
                    out.push_back(next);
                    break;
            }
            continue;
        }

        out.push_back(ch);
    }

    return out;
}

} // namespace msda
