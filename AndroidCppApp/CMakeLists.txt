cmake_minimum_required(VERSION 3.22)
project(MSDAAndroidCpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(MSDA_BUILD_ANDROID_BRIDGE "Build Android JNI bridge" ON)

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
