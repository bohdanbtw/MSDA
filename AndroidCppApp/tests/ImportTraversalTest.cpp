#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include "msda/AccountManager.h"

namespace {
namespace fs = std::filesystem;

int check(bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << "\n";
        return 1;
    }
    return 0;
}

void writeMafile(const fs::path& path, const std::string& accountName) {
    std::ofstream out(path, std::ios::binary);
    out << "{\n"
        << "  \"account_name\": \"" << accountName << "\",\n"
        << "  \"steamid\": 76561198000000001,\n"
        << "  \"shared_secret\": \"shared\",\n"
        << "  \"identity_secret\": \"identity\"\n"
        << "}\n";
}

} // namespace

int main() {
    int failures = 0;
    const auto suffix = std::chrono::steady_clock::now().time_since_epoch().count();
    const fs::path root = fs::temp_directory_path() /
                          ("msda_import_traversal_test_" + std::to_string(suffix));
    std::error_code ec;

    // Baseline: a normal import still finds accounts.
    {
        const fs::path folder = root / "normal";
        fs::create_directories(folder, ec);
        writeMafile(folder / "top.mafile", "top");

        msda::AccountManager manager;
        const bool ok = manager.importFromFolder(folder.string());
        failures += check(ok, "normal folder: importFromFolder returned false");
        failures += check(manager.accounts().size() == 1,
                          "normal folder: expected exactly one account");
        if (!manager.accounts().empty()) {
            failures += check(manager.accounts()[0].accountName == "top",
                              "normal folder: wrong account name");
            failures += check(manager.accounts()[0].steamId == "76561198000000001",
                              "normal folder: wrong steam id");
        }
    }

    // Regression: a subdirectory that cannot be traversed used to throw
    // std::filesystem_error from range-for's operator++() and terminate the
    // process. It must now be reported through error_code and stop the walk.
    {
        const fs::path folder = root / "locked";
        const fs::path child = folder / "restricted";
        fs::create_directories(child, ec);
        writeMafile(child / "nested.mafile", "nested");
        fs::permissions(child, fs::perms::none, ec);

        msda::AccountManager manager;
        const bool ok = manager.importFromFolder(folder.string());
        failures += check(ok, "locked folder: importFromFolder returned false");
        failures += check(manager.accounts().empty(),
                          "locked folder: no account should be imported");

        fs::permissions(child, fs::perms::owner_all, ec);
    }

    fs::remove_all(root, ec);

    if (failures == 0) {
        std::cout << "ImportTraversalTest: all checks passed\n";
    }
    return failures == 0 ? 0 : 1;
}
