#!/usr/bin/env bash

JAVA_HOME_DEFAULT="/usr/lib/jvm/java-17-openjdk-amd64"

if [ -z "${JAVA_HOME:-}" ] && command -v javac >/dev/null 2>&1; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"

MAVEN_SETTINGS_ARGS=()
if [ -n "${MAVEN_SETTINGS_FILE:-}" ]; then
  if [ ! -r "$MAVEN_SETTINGS_FILE" ]; then
    echo "Maven settings file is not readable: $MAVEN_SETTINGS_FILE" >&2
    exit 1
  fi
  MAVEN_SETTINGS_ARGS=(-s "$MAVEN_SETTINGS_FILE")
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Java runtime not found at $JAVA_HOME/bin/java" >&2
  exit 1
fi

if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "Java compiler not found at $JAVA_HOME/bin/javac" >&2
  exit 1
fi
