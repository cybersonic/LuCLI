#!/bin/sh

##############################################################################
##                                                                          ##
##  LuCLI JVM Bootstrap for UN*X                                           ##
##                                                                          ##
##############################################################################

# Get the location of the running script
this_script=`which "$0" 2>/dev/null`
[ $? -gt 0 -a -f "$0" ] && this_script="$0"
case $this_script in
    /*) ;; # absolute path - good
    *) this_script=$(pwd)/$this_script ;; # relative path - fix it
esac

# Prepare Java arguments
java_args="$LUCLI_JAVA_ARGS -client"

##############################################################################
##  OS SPECIFIC CLEANUP + ARGS                                               ##
##############################################################################

# Cleanup paths for different OS
case "`uname`" in
Darwin)
    if [ -e /System/Library/Frameworks/JavaVM.framework ]
    then
        java_args="
            $LUCLI_JAVA_ARGS
            -client
            -Dcom.apple.mrj.application.apple.menu.about.name=LuCLI
            -Dcom.apple.mrj.application.growbox.intrudes=false
            -Dapple.laf.useScreenMenuBar=true
            -Xdock:name=LuCLI
            -Dfile.encoding=UTF-8
            -Djava.awt.headless=true
        "
    fi
    ;;
Linux)
    java_args="$LUCLI_JAVA_ARGS -client -Djava.awt.headless=true"
    ;;
esac

##############################################################################
##  JAVA DETERMINATION                                                       ##
##############################################################################

# The Embedded JRE takes precedence over a JAVA_HOME environment variable.

# Default the Java command to be global java call
java=java

# Check if JAVA_HOME is set, then use it
if [ -n "$JAVA_HOME" ]
then
    java="$JAVA_HOME/bin/java"
fi

# Verify if we have an embedded version, if we do use that instead.
JRE=$(dirname "$this_script")/jre
if [ -d "$JRE" ]
then
    java="$JRE/bin/java"
fi

##############################################################################
##  JAVA VERSION CHECK                                                       ##
##############################################################################

java_version_output=`"$java" -version 2>&1`
java_status=$?
if [ $java_status -ne 0 ]
then
    echo "❌ Unable to run Java command: $java" 1>&2
    echo "LuCLI requires Java 21 or newer." 1>&2
    echo "$java_version_output" | sed -n '1p' 1>&2
    exit 1
fi

java_version=`echo "$java_version_output" | awk -F'"' '/version/ {print $2; exit}'`
if [ -z "$java_version" ]
then
    echo "❌ Unable to detect Java version from command output." 1>&2
    echo "LuCLI requires Java 21 or newer." 1>&2
    echo "$java_version_output" | sed -n '1p' 1>&2
    exit 1
fi

java_major=`echo "$java_version" | awk -F. '{if ($1 == "1") print $2; else print $1}' | sed 's/[^0-9].*$//'`
case "$java_major" in
    ''|*[!0-9]*)
        echo "❌ Unable to parse Java major version from: $java_version" 1>&2
        echo "LuCLI requires Java 21 or newer." 1>&2
        exit 1
        ;;
esac

if [ "$java_major" -lt 21 ]
then
    echo "❌ Java 21 or newer is required. Detected: $java_version" 1>&2
    echo "Please install Java 21+ and set JAVA_HOME if needed." 1>&2
    exit 1
fi

##############################################################################
##  BINARY NAME DETECTION                                                    ##
##############################################################################

# Detect the name used to invoke this binary (e.g., "lucli", "wheels").
# When installed as a symlink (ln -s lucli wheels), the binary name tells
# LuCLI to auto-route commands to the module of that name.
binary_name=`basename "$0"`
# Strip common extensions (.sh, .exe) for cleaner detection
case "$binary_name" in
    *.sh)  binary_name=`echo "$binary_name" | sed 's/\.sh$//'` ;;
    *.exe) binary_name=`echo "$binary_name" | sed 's/\.exe$//'` ;;
esac

##############################################################################
##  EXECUTION                                                                ##
##############################################################################

# This script is concatenated with a JAR file
# The JAR starts after the __JAR_BOUNDARY__ marker
exec "$java" $java_args -Dlucli.binary.name="$binary_name" -jar "$this_script" "$@"
exit
__JAR_BOUNDARY__
