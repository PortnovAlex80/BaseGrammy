#!/usr/bin/env sh

# Gradle startup script for POSIX systems

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

DEFAULT_JVM_OPTS=""

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
win=false

case "`uname`" in
  CYGWIN*) cygwin=true ;;
  MINGW*) msys=true ;; 
  MSYS*) msys=true ;; 
  Darwin*) ;; 
  Linux*) ;; 
  *) ;; 
 esac

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit 1
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar:$APP_HOME/gradle/wrapper/gradle-wrapper-shared.jar:$APP_HOME/gradle/wrapper/gradle-cli.jar"

# A Windows JVM expects native paths and semicolons, including under Git Bash.
if $cygwin || $msys; then
    CLASSPATH=$(cygpath --path --windows "$CLASSPATH") || exit 1
fi

if [ -n "$JAVA_HOME" ] ; then
    JAVA_EXE="$JAVA_HOME/bin/java"
    if $cygwin || $msys; then
        JAVA_EXE="$(cygpath --unix "$JAVA_HOME")/bin/java.exe"
    fi
else
    JAVA_EXE=$(command -v java) || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ ! -x "$JAVA_EXE" ] ; then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

exec "$JAVA_EXE" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
