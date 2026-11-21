// Known-answer tests for the hand-written crypto and parsing code in msda_core.
//
// The SHA-1 / HMAC / Base64 / Steam Guard helpers deliberately live in an
// anonymous namespace in AccountManager.cpp. Including the implementation here
// (and NOT linking msda_core) lets these tests exercise the exact shipped code
// without changing any production signatures solely for testability.

#include <array>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <optional>
#include <string>
#include <vector>

#include "../src/msda/AccountManager.cpp"
#include "../src/msda/MafileParser.cpp"

namespace {

int failures = 0;

std::string toHex(const std::vector<std::uint8_t>& bytes) {
    static const char* digits = "0123456789abcdef";
    std::string out;
    out.reserve(bytes.size() * 2);
    for (const auto byte : bytes) {
        out.push_back(digits[(byte >> 4) & 0x0F]);
        out.push_back(digits[byte & 0x0F]);
    }
    return out;
}

std::vector<std::uint8_t> fromHex(const std::string& hex) {
    std::vector<std::uint8_t> out;
    for (std::size_t i = 0; i + 1 < hex.size(); i += 2) {
        const auto nibble = [](char ch) -> std::uint8_t {
            if (ch >= '0' && ch <= '9') return static_cast<std::uint8_t>(ch - '0');
            if (ch >= 'a' && ch <= 'f') return static_cast<std::uint8_t>(ch - 'a' + 10);
            if (ch >= 'A' && ch <= 'F') return static_cast<std::uint8_t>(ch - 'A' + 10);
            return 0;
        };
        out.push_back(static_cast<std::uint8_t>((nibble(hex[i]) << 4) | nibble(hex[i + 1])));
    }
    return out;
}

void expectEq(const std::string& actual, const std::string& expected, const std::string& what) {
    if (actual == expected) {
        return;
    }
    ++failures;
    std::cerr << "FAIL [" << what << "]\n  expected: " << expected << "\n  actual:   " << actual << "\n";
}

void expectTrue(bool condition, const std::string& what) {
    if (!condition) {
        ++failures;
        std::cerr << "FAIL [" << what << "]\n";
    }
}

std::vector<std::uint8_t> bytesOf(const std::string& text) {
    return std::vector<std::uint8_t>(text.begin(), text.end());
}

std::array<std::uint8_t, 8> bigEndianMessage(const std::string& text) {
    std::array<std::uint8_t, 8> message{};
    for (std::size_t i = 0; i < text.size() && i < message.size(); ++i) {
        message[i] = static_cast<std::uint8_t>(text[i]);
    }
    return message;
}

std::array<std::uint8_t, 8> bigEndianTime(std::uint64_t unixTime) {
    const std::uint64_t slice = unixTime / 30ULL;
    std::array<std::uint8_t, 8> message{};
    for (int i = 7; i >= 0; --i) {
        message[static_cast<std::size_t>(i)] = static_cast<std::uint8_t>(slice >> ((7 - i) * 8));
    }
    return message;
}

std::string writeTempMafile(const std::string& suffix, const std::string& content) {
    static int counter = 0;
    const auto stamp = std::chrono::steady_clock::now().time_since_epoch().count();
    const auto path = std::filesystem::temp_directory_path() /
        ("msda_kat_" + std::to_string(stamp) + "_" + std::to_string(++counter) + "_" + suffix + ".mafile");
    std::ofstream out(path, std::ios::binary);
    out << content;
    out.close();
    return path.string();
}

void removeTempMafile(const std::string& path) {
    std::error_code ec;
    std::filesystem::remove(path, ec);
}

void testSha1() {
    expectEq(toHex(sha1({})), "da39a3ee5e6b4b0d3255bfef95601890afd80709", "SHA-1 of empty input");
    expectEq(toHex(sha1(bytesOf("abc"))), "a9993e364706816aba3e25717850c26c9cd0d89d", "SHA-1 of abc");
    expectEq(
        toHex(sha1(bytesOf("The quick brown fox jumps over the lazy dog"))),
        "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
        "SHA-1 of quick brown fox"
    );
}

void testHmacSha1() {
    // RFC 2202, test case 1: 20-byte 0x0b key over the 8-byte message "Hi There".
    const std::vector<std::uint8_t> rfcKey(20, 0x0b);
    expectEq(
        toHex(hmacSha1(rfcKey, bigEndianMessage("Hi There"))),
        "b617318655057264e28bc0b6fb378c8ef146be00",
        "HMAC-SHA1 RFC 2202 vector 1"
    );

    // Steam: HMAC over the big-endian 30-second time slice using the decoded
    // 20-byte secret (fixed-length Steam shared secret).
    const auto steamKey = decodeBase64("AQQBAgMEBQYHCAkKCwwNDg8QERI=");
    expectEq(toHex(hmacSha1(steamKey, bigEndianTime(1'700'000'000))),
             "9be0afd45a6b2bf973c9d7bb43aeb8413a4ee9d5", "HMAC-SHA1 Steam time slice");
}

void testBase64() {
    expectEq(
        toHex(decodeBase64("AQQBAgMEBQYHCAkKCwwNDg8QERI=")),
        "01040102030405060708090a0b0c0d0e0f101112",
        "Base64 Steam shared secret (20 bytes)"
    );
    expectEq(toHex(decodeBase64("TWFu")), "4d616e", "Base64 'Man'");
    expectEq(toHex(decodeBase64("TQ==")), "4d", "Base64 with padding");
    expectEq(toHex(decodeBase64("T Q==\n")), "4d", "Base64 ignores whitespace");
    expectEq(toHex(decodeBase64("////")), "ffffff", "Base64 all-set sextets");
    expectTrue(decodeBase64("not-base64!").empty(), "Base64 rejects invalid characters");
    expectTrue(decodeBase64("").empty(), "Base64 of empty input");
}

void testSteamGuardCode() {
    // Steam shared secrets are exactly 20 bytes once decoded. These codes were
    // computed for this 20-byte key with the same RFC 6238-style TOTP algorithm
    // used by the Kotlin generator (see SteamGuardCodeGenerator.kt).
    const std::string secret = "AQQBAgMEBQYHCAkKCwwNDg8QERI=";
    expectEq(generateSteamGuardCode(secret, 1'700'000'000), "7F4NB", "Steam Guard at 1700000000");
    expectEq(generateSteamGuardCode(secret, 1'700'000'030), "27R6B", "Steam Guard at 1700000030");

    // Negative cases: empty, malformed Base64, wrong decoded length.
    expectTrue(generateSteamGuardCode("", 1'700'000'000).empty(), "Steam Guard rejects empty secret");
    expectTrue(generateSteamGuardCode("!!!", 1'700'000'000).empty(), "Steam Guard rejects invalid secret");
    // The old Kotlin KAT vector decodes to 24 bytes and must be rejected by the
    // native implementation now that the decoded-key length is validated.
    expectTrue(generateSteamGuardCode("AQQBAgMEBQYHCAkKCwwNDg8QERITFBUW", 1'700'000'000).empty(),
               "Steam Guard rejects a 24-byte decoded secret");
}

void testMafileParsing() {
    {
        const std::string path = writeTempMafile("nested", R"json({
          "account_name": "nested_user",
          "steamid": 76561198000000000,
          "shared_secret": "shared",
          "identity_secret": "identity",
          "device_id": "device",
          "SessionData": {
            "SessionID": "nested-session",
            "steamLoginSecure": "76561198000000000%7C%7Cjwt",
            "RefreshToken": "nested-refresh",
            "AccessToken": "nested-access"
          }
        })json");
        const auto account = msda::MafileParser::parseFile(path);
        expectTrue(account.has_value(), "parse nested SessionData mafile");
        if (account) {
            expectEq(account->accountName, "nested_user", "nested account name");
            expectEq(account->steamId, "76561198000000000", "numeric steam id");
            expectEq(account->sharedSecret, "shared", "nested shared secret");
            expectEq(account->identitySecret, "identity", "nested identity secret");
            expectEq(account->deviceId, "device", "nested device id");
            expectEq(account->sessionId, "nested-session", "nested session id");
            expectEq(account->steamLoginSecure, "76561198000000000%7C%7Cjwt", "nested login cookie");
            expectEq(account->refreshToken, "nested-refresh", "nested refresh token");
            expectEq(account->accessToken, "nested-access", "nested access token");
        }
        removeTempMafile(path);
    }
    {
        const std::string path = writeTempMafile("escaped", R"json({
          "account_name": "quote\"and\\slash\nnewline",
          "steamid": "123",
          "shared_secret": "s"
        })json");
        const auto account = msda::MafileParser::parseFile(path);
        expectTrue(account.has_value(), "parse escaped account name");
        if (account) {
            expectEq(account->accountName, "quote\"and\\slash\nnewline", "JSON escape sequences");
        }
        removeTempMafile(path);
    }
    {
        const std::string path = writeTempMafile("legacy", R"json({
          "AccountName": "legacy_user",
          "SteamID": 76561198123456789,
          "shared": "sh",
          "identity": "id",
          "deviceid": "dev",
          "sessionid": "legacy-session",
          "OAuthToken": "legacy-refresh",
          "access_token": "e30.token"
        })json");
        const auto account = msda::MafileParser::parseFile(path);
        expectTrue(account.has_value(), "parse legacy SDA field names");
        if (account) {
            expectEq(account->accountName, "legacy_user", "legacy account name");
            expectEq(account->steamId, "76561198123456789", "legacy steam id");
            expectEq(account->sharedSecret, "sh", "legacy shared secret");
            expectEq(account->identitySecret, "id", "legacy identity secret");
            expectEq(account->deviceId, "dev", "legacy device id");
            expectEq(account->sessionId, "legacy-session", "legacy session id");
            expectEq(account->refreshToken, "legacy-refresh", "legacy refresh token");
            expectEq(account->accessToken, "e30.token", "legacy access token");
            expectEq(account->steamLoginSecure, "76561198123456789%7C%7Ce30.token", "legacy login cookie composed from access token");
        }
        removeTempMafile(path);
    }
    {
        const std::string path = writeTempMafile("precedence", R"json({
          "sessionid": "legacy-session",
          "SessionData": { "SessionID": "nebula-session" }
        })json");
        const auto account = msda::MafileParser::parseFile(path);
        expectTrue(account.has_value(), "parse precedence mafile");
        if (account) {
            expectEq(account->sessionId, "nebula-session", "SessionData overrides legacy session id");
        }
        removeTempMafile(path);
    }
    expectTrue(
        !msda::MafileParser::parseFile("/nonexistent/msda_kat_does_not_exist.mafile").has_value(),
        "missing mafile returns nullopt"
    );
}

} // namespace

int main() {
    testSha1();
    testHmacSha1();
    testBase64();
    testSteamGuardCode();
    testMafileParsing();

    if (failures == 0) {
        std::cout << "msda_core_tests: all known-answer tests passed\n";
        return 0;
    }
    std::cerr << "msda_core_tests: " << failures << " failure(s)\n";
    return 1;
}
