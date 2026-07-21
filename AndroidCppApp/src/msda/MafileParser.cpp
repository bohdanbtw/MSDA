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

    // NebulaAuth / SDA v3 store session tokens inside a nested SessionData object.
    const auto sessionBlock = extractJsonObject(content, "SessionData")
        .value_or(extractJsonObject(content, "session_data").value_or(
            extractJsonObject(content, "sessiondata").value_or("")));

    auto sessionId = firstNonEmpty({
        extractStringValue(sessionBlock, "SessionID"),
        extractStringValue(sessionBlock, "sessionid"),
        extractStringValue(sessionBlock, "session_id"),
        extractStringValue(content, "SessionID"),
        extractStringValue(content, "sessionid")
    });

    auto steamLoginSecure = firstNonEmpty({
        extractStringValue(sessionBlock, "steamLoginSecure"),
        extractStringValue(content, "steamLoginSecure")
    });

    auto refreshToken = firstNonEmpty({
        extractStringValue(sessionBlock, "RefreshToken"),
        extractStringValue(sessionBlock, "refresh_token"),
        extractStringValue(sessionBlock, "refreshtoken"),
        extractStringValue(sessionBlock, "OAuthToken"),
        extractStringValue(sessionBlock, "refresh"),
        extractStringValue(content, "refresh_token"),
        extractStringValue(content, "refreshtoken"),
        extractStringValue(content, "OAuthToken"),
        extractStringValue(content, "refresh")
    });

    auto accessToken = firstNonEmpty({
        extractStringValue(sessionBlock, "AccessToken"),
        extractStringValue(sessionBlock, "access_token"),
        extractStringValue(sessionBlock, "accesstoken"),
        extractStringValue(sessionBlock, "access"),
        extractStringValue(content, "access_token"),
        extractStringValue(content, "accesstoken"),
        extractStringValue(content, "access")
    });
    if (accessToken.empty()) {
        accessToken = steamLoginSecure;
    }

    if (steamLoginSecure.empty() && !accessToken.empty() && steamId != "unknown") {
        if (accessToken.find("%7C%7C") != std::string::npos || accessToken.find("||") != std::string::npos) {
            steamLoginSecure = accessToken;
        } else if (accessToken.find('.') != std::string::npos) {
            steamLoginSecure = steamId + "%7C%7C" + accessToken;
        }
    }

    if (steamId.empty() && !sessionBlock.empty()) {
        steamId = firstNonEmpty({
            extractNumberOrString(sessionBlock, "SteamID"),
            extractNumberOrString(sessionBlock, "steamid"),
            extractNumberOrString(sessionBlock, "SteamId")
        });
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

std::optional<std::string> MafileParser::extractJsonObject(const std::string& json, const std::string& key) {
    const std::string search = "\"" + key + "\"";
    std::size_t pos = json.find(search);
    if (pos == std::string::npos) {
        return std::nullopt;
    }

    pos = json.find('{', pos + search.length());
    if (pos == std::string::npos) {
        return std::nullopt;
    }

    int depth = 0;
    bool inString = false;
    bool escaped = false;
    for (std::size_t i = pos; i < json.size(); ++i) {
        const char ch = json[i];
        if (inString) {
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                inString = false;
            }
            continue;
        }

        if (ch == '"') {
            inString = true;
            continue;
        }
        if (ch == '{') {
            ++depth;
        } else if (ch == '}') {
            --depth;
            if (depth == 0) {
                return json.substr(pos, i - pos + 1);
            }
        }
    }

    return std::nullopt;
}

std::string MafileParser::firstNonEmpty(std::initializer_list<std::string> values) {
    for (const auto& value : values) {
        if (!value.empty()) {
            return value;
        }
    }
    return {};
}

std::string MafileParser::fileNameFromPath(const std::string& filePath) {
    std::filesystem::path p(filePath);
    return p.stem().string();
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
