#pragma once

#include <optional>
#include <string>
#include "MafileModels.h"

namespace msda {

class MafileParser {
public:
    static std::optional<MafileAccount> parseFile(const std::string& filePath);

private:
    static std::optional<MafileAccount> parseContent(const std::string& content, const std::string& filePath);
    static std::optional<std::string> readAllText(const std::string& filePath);
    static std::string extractStringValue(const std::string& json, const std::string& key);
    static std::string extractNumberOrString(const std::string& json, const std::string& key);
    static std::string fileNameFromPath(const std::string& filePath);
    static std::string unescapeJsonString(const std::string& value);
};

} // namespace msda
