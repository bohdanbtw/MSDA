#include "MafileParser.h"

#include <cctype>
#include <filesystem>
#include <fstream>
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

    auto refreshToken = extractStringValue(content, "refresh_token");
    if (refreshToken.empty()) {
        refreshToken = extractStringValue(content, "refreshtoken");
    }
    if (refreshToken.empty()) {
        refreshToken = extractStringValue(content, "OAuthToken");
    }
    if (refreshToken.empty()) {
        refreshToken = extractStringValue(content, "refresh");
    }

    auto accessToken = extractStringValue(content, "access_token");
    if (accessToken.empty()) {
        accessToken = extractStringValue(content, "accesstoken");
    }
    if (accessToken.empty()) {
        accessToken = extractStringValue(content, "access");
    }
    if (accessToken.empty()) {
        accessToken = steamLoginSecure;
    }

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
    item.refreshToken = std::move(refreshToken);
    item.accessToken = std::move(accessToken);
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
    const std::string search = "\"" + key + "\":";
    std::size_t pos = json.find(search);
    if (pos == std::string::npos) {
        return {};
    }
    pos += search.length();
    // skip whitespace
    while (pos < json.size() && (json[pos] == ' ' || json[pos] == '\t' || json[pos] == '\r' || json[pos] == '\n')) {
        ++pos;
    }
    if (pos >= json.size() || json[pos] != '\"') {
        return {};
    }
    ++pos; // skip opening quote
    const std::size_t start = pos;
    while (pos < json.size()) {
        if (json[pos] == '\\' && pos + 1 < json.size()) {
            pos += 2; // skip escaped character
            continue;
        }
        if (json[pos] == '\"') {
            return unescapeJsonString(json.substr(start, pos - start));
        }
        ++pos;
    }
    return {};
}

std::string MafileParser::extractNumberOrString(const std::string& json, const std::string& key) {
    // try a quoted string first
    auto fromString = extractStringValue(json, key);
    if (!fromString.empty()) {
        return fromString;
    }

    const std::string search = "\"" + key + "\":";
    std::size_t pos = json.find(search);
    if (pos == std::string::npos) {
        return {};
    }
    pos += search.length();
    // skip whitespace
    while (pos < json.size() && (json[pos] == ' ' || json[pos] == '\t' || json[pos] == '\r' || json[pos] == '\n')) {
        ++pos;
    }
    if (pos >= json.size() || !std::isdigit(static_cast<unsigned char>(json[pos]))) {
        return {};
    }
    const std::size_t start = pos;
    while (pos < json.size() && std::isdigit(static_cast<unsigned char>(json[pos]))) {
        ++pos;
    }
    return json.substr(start, pos - start);
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
