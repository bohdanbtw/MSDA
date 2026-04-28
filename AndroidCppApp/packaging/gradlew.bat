@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    set WRAPPER_JAR=C:\Tools\Gradle\gradle-8.10.2\lib\gradle-wrapper-8.10.2.jar
)

if not exist "%WRAPPER_JAR%" (
    echo ERROR: Gradle wrapper jar not found.^
 Expected at either %APP_HOME%\gradle\wrapper\gradle-wrapper.jar or C:\Tools\Gradle\gradle-8.10.2\lib\gradle-wrapper-8.10.2.jar
    exit /b 1
)

set CLASSPATH=%WRAPPER_JAR%

@rem Execute Gradle
"%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
