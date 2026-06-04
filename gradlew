#!/bin/sh
#
# Gradle start up script for POSIX compatible shells.
# Requires gradle-wrapper.jar in gradle/wrapper/ (run 'gradle wrapper' to generate).
#

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPERTIES="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "ERROR: gradle-wrapper.jar not found. Run 'gradle wrapper --gradle-version 8.7' to generate it."
    exit 1
fi

exec java -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
