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
##  EXECUTION                                                                ##
##############################################################################

# This script is concatenated with a JAR file
# The JAR starts after the __JAR_BOUNDARY__ marker
exec "$java" $java_args -jar "$this_script" "$@"
exit
__JAR_BOUNDARY__
