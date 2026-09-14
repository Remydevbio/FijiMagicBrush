#!/bin/sh
set -eu
cd "$(dirname "$0")"
FIJI_DIR=${FIJI_DIR:-/home/rbonnav/Documents/Codex/Fiji}
JAVA_HOME=${JAVA_HOME:-$FIJI_DIR/java/linux64/zulu21.42.19-ca-jdk21.0.7-linux_x64}
IJ_JAR=${IJ_JAR:-$FIJI_DIR/jars/ij-1.54p.jar}
mkdir -p build/classes dist
rm -rf build/plugin
mkdir -p build/plugin
"$JAVA_HOME/bin/javac" --release 8 -cp "$IJ_JAR" -d build/plugin src/*.java
cp build/plugin/*.class build/classes/
cp plugins.config LICENSE build/plugin/
"$JAVA_HOME/bin/jar" cf dist/Intensity_Selection_Tools.jar -C build/plugin .
"$JAVA_HOME/bin/javac" -cp "$IJ_JAR:build/classes" -d build/classes test/*.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$IJ_JAR:build/classes" EngineTest
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$IJ_JAR:build/classes" ExampleChannelTest
