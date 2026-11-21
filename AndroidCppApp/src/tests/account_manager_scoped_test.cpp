#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

#include "msda/AccountManager.h"

namespace {

int g_failures = 0;

void check(bool condition, const std::string& message) {
    if (!condition) {
        ++g_failures;
        std::cerr << "FAIL: " << message << "\n";
    }
}

void writeMafile(const std::filesystem::path& path,
                 const std::string& accountName,
                 const std::string& steamId,
                 const std::string& sharedSecret) {
    std::ofstream out(path, std::ios::binary);
    out << "{\"account_name\":\"" << accountName
        << "\",\"steamid\":\"" << steamId
        << "\",\"shared_secret\":\"" << sharedSecret << "\"}";
}

} // namespace

int main() {
    namespace fs = std::filesystem;

    const auto suffix = std::chrono::steady_clock::now().time_since_epoch().count();
    const fs::path dir = fs::temp_directory_path() /
        ("msda_scoped_test_" + std::to_string(suffix));
    fs::create_directories(dir);

    const std::string steamA = "76561198000000001";
    const std::string steamB = "76561198000000002";
    // A valid Steam shared secret: 20 bytes once Base64-decoded (required by
    // generateSteamGuardCode's length validation).
    writeMafile(dir / "alpha.mafile", "alpha", steamA, "AQQBAgMEBQYHCAkKCwwNDg8QERI=");
    // Beta has no shared secret, so its code is always empty — a scoped read that
    // accidentally used the global active account would be caught immediately.
    writeMafile(dir / "beta.mafile", "beta", steamB, "");

    msda::AccountManager manager;
    check(manager.importFromFolder(dir.string()), "import succeeds");
    check(manager.accounts().size() == 2, "two accounts imported");

    // Make B active. Scoped reads for A must still return A's data.
    check(manager.setActiveIndex(1), "setActiveIndex(1) succeeds");
    check(manager.activeAccount() != nullptr && manager.activeAccount()->steamId == steamB,
          "beta is the active account before scoped reads");

    const auto codeAfirst = manager.codeForSteamId(steamA);
    const auto secondsAfirst = manager.secondsToNextCodeForSteamId(steamA);
    const auto codeB = manager.codeForSteamId(steamB);
    const auto secondsB = manager.secondsToNextCodeForSteamId(steamB);

    check(codeAfirst.size() == 5, "codeForSteamId(A) returns a 5-character code for A");
    check(codeB.empty(), "codeForSteamId(B) returns empty code for missing secret");
    check(secondsAfirst >= 1 && secondsAfirst <= 30, "seconds for A are in the 1..30 window");
    check(secondsB >= 1 && secondsB <= 30, "seconds for B are in the 1..30 window");

    // Out-of-order reads must be independent and must not touch the active index.
    const auto codeAsecond = manager.codeForSteamId(steamA);
    const auto secondsAsecond = manager.secondsToNextCodeForSteamId(steamA);
    // Only assert exact stability away from the 30s rollover boundary.
    if (secondsAfirst > 1 && secondsAfirst < 30) {
        check(codeAsecond == codeAfirst, "repeated scoped code read is stable within the code window");
        check(secondsAsecond == secondsAfirst, "repeated scoped seconds read is stable within the code window");
    }

    check(manager.secondsToNextCodeForSteamId("no-such-steam-id") == -1,
          "unknown Steam ID yields -1 seconds");
    check(manager.secondsToNextCodeForSteamId("") == -1,
          "blank Steam ID yields -1 seconds");

    check(manager.activeAccount() != nullptr && manager.activeAccount()->steamId == steamB,
          "scoped reads do not change the active account");
    check(manager.activeCode().empty(),
          "global active code still belongs to beta (empty secret)");

    fs::remove_all(dir);

    if (g_failures != 0) {
        std::cerr << g_failures << " check(s) failed\n";
        return 1;
    }
    std::cout << "account_manager_scoped_test: all checks passed\n";
    return 0;
}
