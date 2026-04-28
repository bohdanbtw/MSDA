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
    echo Gradle wrapper jar not found - downloading 8.10.2 distribution ...
    set GRADLE_DIST_ZIP=%TEMP%\gradle-8.10.2-bin.zip
    if not exist "!GRADLE_DIST_ZIP!" (
        powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -OutFile '!GRADLE_DIST_ZIP!'"
    )
    if not exist "!GRADLE_DIST_ZIP!" (
        echo ERROR: Failed to download Gradle distribution.
        exit /b 1
    )

    set EXTRACT_DIR=%TEMP%\gradle-8.10.2-extract
    if exist "!EXTRACT_DIR!" rd /s /q "!EXTRACT_DIR!"
    mkdir "!EXTRACT_DIR!"
    powershell -Command "Expand-Archive -Path '!GRADLE_DIST_ZIP!' -DestinationPath '!EXTRACT_DIR!'"

    :: Search recursively for the wrapper jar
    for /r "!EXTRACT_DIR!" %%f in (gradle-wrapper*.jar) do (
        if not defined EXTRACTED_JAR set "EXTRACTED_JAR=%%f"
    )
    if not defined EXTRACTED_JAR (
        echo ERROR: Failed to locate gradle-wrapper.jar in downloaded distribution.
        exit /b 1
    )

    xcopy /y /q "!EXTRACTED_JAR!" "%WRAPPER_JAR%" >nul
    if not exist "%WRAPPER_JAR%" (
        echo ERROR: Failed to copy gradle-wrapper.jar into project.
        exit /b 1
    )
    echo Gradle wrapper jar downloaded and ready.
)

set CLASSPATH=%WRAPPER_JAR%

@rem -----------------------------------------------------------------
@rem 3. Execute Gradle
@rem -----------------------------------------------------------------
"%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
