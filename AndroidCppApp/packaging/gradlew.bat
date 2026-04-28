@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope with delayed expansion to allow variable changes inside blocks
if "%OS%"=="Windows_NT" setlocal enabledelayedexpansion

:: ------------------------------------------------
:: 1. Locate a JDK 17 if JAVA_HOME is not already set
:: ------------------------------------------------
if "%JAVA_HOME%"=="" (
    :: first try to find java on PATH
    for /f "usebackq delims=" %%a in (`where java 2^>nul`) do (
        if not defined JAVA_HOME (
            for %%b in ("%%a") do set "JAVA_HOME=%%~dpb.."
        )
    )
    :: if still empty, search common install locations
    if not defined JAVA_HOME (
        for %%d in (
            "C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
            "C:\Program Files\Java\jdk-17.0.11"
            "C:\Program Files\Microsoft\jdk-17.0.12.7-hotspot"
            "C:\Program Files\OpenJDK\jdk-17.0.11.9-hotspot"
        ) do if exist "%%~d\bin\java.exe" (
            if not defined JAVA_HOME (
                set "JAVA_HOME=%%~d"
            )
        )
    )
)

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem -----------------------------------------------------------------
@rem 2. If the wrapper jar is missing, download it automatically
@rem -----------------------------------------------------------------
if not exist "%WRAPPER_JAR%" (
    echo Gradle wrapper jar not found.
    echo Attempting to download directly from Gradle GitHub repository...
    set WRAPPER_JAR_URL=https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
    powershell -Command "Invoke-WebRequest -Uri '!WRAPPER_JAR_URL!' -OutFile '%WRAPPER_JAR%'"
    if not exist "%WRAPPER_JAR%" (
        echo ERROR: Failed to download gradle-wrapper.jar.
        echo Please generate the wrapper manually by running:
        echo     gradle wrapper --gradle-version 8.10.2
        echo inside the packaging directory.
        exit /b 1
    )
    echo Gradle wrapper jar downloaded and ready.
)

set CLASSPATH=%WRAPPER_JAR%

@rem -----------------------------------------------------------------
@rem 3. Execute Gradle
@rem -----------------------------------------------------------------
"%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
