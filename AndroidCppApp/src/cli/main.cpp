#include <iostream>
#include <string>
#include "msda/AccountManager.h"

int main() {
    msda::AccountManager manager;

    std::cout << "MSDA C++ importer demo\n";
    std::cout << "Enter folder path with .mafile files: ";

    std::string folder;
    std::getline(std::cin, folder);

    if (!manager.importFromFolder(folder)) {
        std::cout << "Failed to read folder.\n";
        return 1;
    }

    const auto& accounts = manager.accounts();
    if (accounts.empty()) {
        std::cout << "No .mafile accounts found.\n";
        return 0;
    }

    std::cout << "Imported accounts: " << accounts.size() << "\n";
    for (std::size_t i = 0; i < accounts.size(); ++i) {
        std::cout << i << ": " << accounts[i].accountName << " (" << accounts[i].steamId << ")\n";
    }

    std::cout << "Choose account index: ";
    std::string indexText;
    std::getline(std::cin, indexText);

    std::size_t index = 0;
    try {
        index = static_cast<std::size_t>(std::stoull(indexText));
    } catch (...) {
        std::cout << "Invalid index input.\n";
        return 1;
    }

    if (!manager.setActiveIndex(index)) {
        std::cout << "Index out of range.\n";
        return 1;
    }

    const auto* active = manager.activeAccount();
    if (active == nullptr) {
        std::cout << "No active account.\n";
        return 1;
    }

    std::cout << "Active account switched to: " << active->accountName << " (" << active->steamId << ")\n";
    return 0;
}
