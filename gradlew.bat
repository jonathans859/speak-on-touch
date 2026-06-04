@rem Gradle startup script for Windows.
@rem Requires gradle-wrapper.jar in gradle/wrapper/ (run 'gradle wrapper' to generate).
@echo off

set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo ERROR: gradle-wrapper.jar not found. Run 'gradle wrapper --gradle-version 8.7' to generate it.
    exit /b 1
)

java -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
