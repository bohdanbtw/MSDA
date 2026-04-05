#include "AccountManager.h"

#include <array>
#include <cctype>
#include <cstdint>
#include <ctime>
#include <filesystem>
#include <set>
#include <sstream>
#include <vector>
#include "MafileParser.h"

namespace {

std::uint32_t rotateLeft(std::uint32_t value, std::uint32_t bits) {
    return (value << bits) | (value >> (32 - bits));
}

std::vector<std::uint8_t> sha1(const std::vector<std::uint8_t>& input) {
    std::uint32_t h0 = 0x67452301;
    std::uint32_t h1 = 0xEFCDAB89;
    std::uint32_t h2 = 0x98BADCFE;
    std::uint32_t h3 = 0x10325476;
    std::uint32_t h4 = 0xC3D2E1F0;

    std::vector<std::uint8_t> data = input;
    const std::uint64_t bitLength = static_cast<std::uint64_t>(data.size()) * 8ULL;

    data.push_back(0x80);
    while ((data.size() % 64) != 56) {
        data.push_back(0x00);
    }

    for (int i = 7; i >= 0; --i) {
        data.push_back(static_cast<std::uint8_t>((bitLength >> (i * 8)) & 0xFF));
    }

    for (std::size_t chunk = 0; chunk < data.size(); chunk += 64) {
        std::uint32_t w[80] = {};

        for (int i = 0; i < 16; ++i) {
            const std::size_t idx = chunk + static_cast<std::size_t>(i * 4);
            w[i] = (static_cast<std::uint32_t>(data[idx]) << 24) |
                   (static_cast<std::uint32_t>(data[idx + 1]) << 16) |
                   (static_cast<std::uint32_t>(data[idx + 2]) << 8) |
                   static_cast<std::uint32_t>(data[idx + 3]);
        }

        for (int i = 16; i < 80; ++i) {
            w[i] = rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        }

        std::uint32_t a = h0;
        std::uint32_t b = h1;
        std::uint32_t c = h2;
        std::uint32_t d = h3;
        std::uint32_t e = h4;

        for (int i = 0; i < 80; ++i) {
            std::uint32_t f = 0;
            std::uint32_t k = 0;

            if (i < 20) {
                f = (b & c) | ((~b) & d);
                k = 0x5A827999;
            } else if (i < 40) {
                f = b ^ c ^ d;
                k = 0x6ED9EBA1;
            } else if (i < 60) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8F1BBCDC;
            } else {
                f = b ^ c ^ d;
                k = 0xCA62C1D6;
            }

            const std::uint32_t temp = rotateLeft(a, 5) + f + e + k + w[i];
            e = d;
            d = c;
            c = rotateLeft(b, 30);
            b = a;
            a = temp;
        }

        h0 += a;
        h1 += b;
        h2 += c;
        h3 += d;
        h4 += e;
    }

    std::vector<std::uint8_t> digest(20);
    const std::uint32_t h[5] = {h0, h1, h2, h3, h4};
    for (int i = 0; i < 5; ++i) {
        digest[static_cast<std::size_t>(i * 4)] = static_cast<std::uint8_t>((h[i] >> 24) & 0xFF);
        digest[static_cast<std::size_t>(i * 4 + 1)] = static_cast<std::uint8_t>((h[i] >> 16) & 0xFF);
        digest[static_cast<std::size_t>(i * 4 + 2)] = static_cast<std::uint8_t>((h[i] >> 8) & 0xFF);
        digest[static_cast<std::size_t>(i * 4 + 3)] = static_cast<std::uint8_t>(h[i] & 0xFF);
    }

    return digest;
}

std::vector<std::uint8_t> hmacSha1(const std::vector<std::uint8_t>& key, const std::array<std::uint8_t, 8>& message) {
    constexpr std::size_t blockSize = 64;
    std::vector<std::uint8_t> normalizedKey = key;

    if (normalizedKey.size() > blockSize) {
        normalizedKey = sha1(normalizedKey);
    }

    normalizedKey.resize(blockSize, 0x00);

    std::vector<std::uint8_t> oKeyPad(blockSize);
    std::vector<std::uint8_t> iKeyPad(blockSize);

    for (std::size_t i = 0; i < blockSize; ++i) {
        oKeyPad[i] = static_cast<std::uint8_t>(normalizedKey[i] ^ 0x5c);
        iKeyPad[i] = static_cast<std::uint8_t>(normalizedKey[i] ^ 0x36);
    }

    std::vector<std::uint8_t> inner = iKeyPad;
    inner.insert(inner.end(), message.begin(), message.end());
    const auto innerHash = sha1(inner);

    std::vector<std::uint8_t> outer = oKeyPad;
    outer.insert(outer.end(), innerHash.begin(), innerHash.end());
    return sha1(outer);
}

std::vector<std::uint8_t> decodeBase64(const std::string& value) {
    static const std::string alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::array<int, 256> map{};
    map.fill(-1);

    for (std::size_t i = 0; i < alphabet.size(); ++i) {
        map[static_cast<unsigned char>(alphabet[i])] = static_cast<int>(i);
    }

    std::vector<std::uint8_t> out;
    int buffer = 0;
    int bits = 0;

    for (char ch : value) {
        if (ch == '=') {
            break;
        }

        if (std::isspace(static_cast<unsigned char>(ch)) != 0) {
            continue;
        }

        const int v = map[static_cast<unsigned char>(ch)];
        if (v < 0) {
            return {};
        }

        buffer = (buffer << 6) | v;
        bits += 6;

        if (bits >= 8) {
            bits -= 8;
            out.push_back(static_cast<std::uint8_t>((buffer >> bits) & 0xFF));
        }
    }

    return out;
}

std::string generateSteamGuardCode(const std::string& sharedSecret, std::uint64_t unixTime) {
    if (sharedSecret.empty()) {
        return {};
    }

    static const std::string codeChars = "23456789BCDFGHJKMNPQRTVWXY";

    const auto key = decodeBase64(sharedSecret);
    if (key.empty()) {
        return {};
    }

    const std::uint64_t timeSlice = unixTime / 30ULL;
    std::array<std::uint8_t, 8> message{};
    for (int i = 7; i >= 0; --i) {
        message[static_cast<std::size_t>(i)] = static_cast<std::uint8_t>(timeSlice >> ((7 - i) * 8));
    }

    const auto hash = hmacSha1(key, message);
    if (hash.size() < 20) {
        return {};
    }

    const int offset = hash[19] & 0x0F;
    std::uint32_t codePoint = (static_cast<std::uint32_t>(hash[static_cast<std::size_t>(offset)] & 0x7F) << 24) |
                              (static_cast<std::uint32_t>(hash[static_cast<std::size_t>(offset + 1)]) << 16) |
                              (static_cast<std::uint32_t>(hash[static_cast<std::size_t>(offset + 2)]) << 8) |
                              static_cast<std::uint32_t>(hash[static_cast<std::size_t>(offset + 3)]);

    std::string code;
    code.reserve(5);
    for (int i = 0; i < 5; ++i) {
        code.push_back(codeChars[static_cast<std::size_t>(codePoint % codeChars.size())]);
        codePoint /= static_cast<std::uint32_t>(codeChars.size());
    }

    return code;
}

} // namespace

namespace msda {

bool AccountManager::importFromFolder(const std::string& folderPath) {
    namespace fs = std::filesystem;

    _accounts.clear();
    _activeIndex = static_cast<std::size_t>(-1);

    std::error_code ec;
    if (!fs::exists(folderPath, ec) || !fs::is_directory(folderPath, ec)) {
        return false;
    }

    std::set<std::string> unique;

    for (const auto& entry : fs::recursive_directory_iterator(folderPath, ec)) {
        if (ec) {
            break;
        }

        if (!entry.is_regular_file(ec)) {
            continue;
        }

        const auto path = entry.path().string();
        if (!isMafilePath(path)) {
            continue;
        }

        auto parsed = MafileParser::parseFile(path);
        if (!parsed.has_value()) {
            continue;
        }

        const auto key = parsed->accountName + "#" + parsed->steamId;
        if (unique.insert(key).second) {
            _accounts.push_back(std::move(parsed.value()));
        }
    }

    if (!_accounts.empty()) {
        _activeIndex = 0;
    }

    return true;
}

bool AccountManager::setActiveIndex(std::size_t index) {
    if (index >= _accounts.size()) {
        return false;
    }

    _activeIndex = index;
    return true;
}

const std::vector<MafileAccount>& AccountManager::accounts() const {
    return _accounts;
}

const MafileAccount* AccountManager::activeAccount() const {
    if (_activeIndex >= _accounts.size()) {
        return nullptr;
    }

    return &_accounts[_activeIndex];
}

std::string AccountManager::activeCode() const {
    const auto* active = activeAccount();
    if (active == nullptr) {
        return {};
    }

    const auto now = static_cast<std::uint64_t>(std::time(nullptr));
    return generateSteamGuardCode(active->sharedSecret, now);
}

int AccountManager::secondsToNextCode() const {
    const auto now = static_cast<std::uint64_t>(std::time(nullptr));
    const int remaining = static_cast<int>(30ULL - (now % 30ULL));
    return remaining <= 0 ? 30 : remaining;
}

bool AccountManager::isMafilePath(const std::string& path) {
    namespace fs = std::filesystem;
    const auto ext = fs::path(path).extension().string();

    if (ext.empty()) {
        return false;
    }

    std::string lower = ext;
    for (auto& ch : lower) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }

    return lower == ".mafile";
}

std::string AccountManager::activeConfirmationAuthPayload() const {
    const auto* active = activeAccount();
    if (active == nullptr) {
        return {};
    }

    std::ostringstream out;
    out << active->steamId << "|"
        << active->identitySecret << "|"
        << active->deviceId << "|"
        << active->sessionId << "|"
        << active->steamLoginSecure << "|"
        << active->accountName << "|"
        << active->sharedSecret << "|"
        << active->refreshToken << "|"
        << active->accessToken;
    return out.str();
}

} // namespace msda
