cmake_minimum_required(VERSION 3.22)
project(MSDAAndroidCpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(MSDA_BUILD_ANDROID_BRIDGE "Build Android JNI bridge" ON)
option(MSDA_BUILD_TESTS "Build native unit tests and known-answer tests" OFF)

add_library(msda_core STATIC
    src/msda/MafileParser.cpp
    src/msda/AccountManager.cpp
)

target_include_directories(msda_core PUBLIC src)

add_executable(msda_cli
    src/cli/main.cpp
)

target_link_libraries(msda_cli PRIVATE msda_core)

if(ANDROID AND MSDA_BUILD_ANDROID_BRIDGE)
    add_library(msda_android SHARED
        src/android/jni_bridge.cpp
    )

    target_link_libraries(msda_android PRIVATE msda_core log)
endif()

if(MSDA_BUILD_TESTS AND NOT ANDROID)
    enable_testing()

    add_executable(msda_tests
        tests/ImportTraversalTest.cpp
    )
    target_link_libraries(msda_tests PRIVATE msda_core)
    add_test(NAME import_traversal COMMAND msda_tests)

    # Known-answer tests for the file-local SHA-1/HMAC/Base64/TOTP helpers and the
    # JSON parser. The source includes the implementation files directly, so do
    # NOT link msda_core here (that would duplicate symbols).
    add_executable(msda_core_tests
        tests/msda_core_tests.cpp
    )
    add_test(NAME msda_core_tests COMMAND msda_core_tests)

    # Scoped (by-steamId) account access regression tests; links the library.
    add_executable(msda_account_manager_test
        src/tests/account_manager_scoped_test.cpp
    )
    target_link_libraries(msda_account_manager_test PRIVATE msda_core)
    add_test(NAME account_manager_scoped COMMAND msda_account_manager_test)
endif()
