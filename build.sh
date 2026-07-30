#!/usr/bin/env bash
# Build plumbline.jar with plain javac.
#
# No Gradle setup here. Every jar needed is already sitting in a working Minecraft
# install, and shipping a NeoGradle config nobody has run seemed worse than shipping
# none. If you want to add a tested one, please do.
#
# Usage:
#   MC_LIBS=/path/to/launcher/libraries \
#   SABLE_JAR=/path/to/sable-neoforge-1.21.1-2.0.3.jar \
#   ./build.sh
#
# On Windows use Git Bash, but note javac needs Windows-style paths (C:/...), not /c/...

set -euo pipefail

: "${MC_LIBS:?set MC_LIBS to your launcher's libraries directory}"
: "${SABLE_JAR:?set SABLE_JAR to the Sable mod jar}"

MC_VERSION="${MC_VERSION:-1.21.1-20240808.144430}"
NEOFORGE="${NEOFORGE:-21.1.235}"
FML="${FML:-4.0.42}"
MIXIN="${MIXIN:-0.17.2+mixin.0.8.7}"

OUT="build"
rm -rf "$OUT"
mkdir -p "$OUT/classes" libs

# Sable's math types live in a jar-in-jar; extract it for the classpath.
if [ ! -f libs/sable-companion.jar ]; then
  TMP="$(mktemp -d)"
  ( cd "$TMP" && unzip -o -q "$SABLE_JAR" 'META-INF/jarjar/*' )
  cp "$TMP"/META-INF/jarjar/sable-companion-common-*.jar libs/sable-companion.jar
  rm -rf "$TMP"
fi

CP="$MC_LIBS/net/minecraft/client/$MC_VERSION/client-$MC_VERSION-srg.jar"
CP="$CP;$MC_LIBS/net/fabricmc/sponge-mixin/$MIXIN/sponge-mixin-$MIXIN.jar"
CP="$CP;$MC_LIBS/net/neoforged/fancymodloader/loader/$FML/loader-$FML.jar"
CP="$CP;$MC_LIBS/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-universal.jar"
CP="$CP;$MC_LIBS/net/neoforged/bus/8.0.5/bus-8.0.5.jar"
CP="$CP;$MC_LIBS/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar"
CP="$CP;$MC_LIBS/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar"
CP="$CP;$MC_LIBS/com/mojang/logging/1.6.11/logging-1.6.11.jar"
CP="$CP;$MC_LIBS/org/joml/joml/1.10.8/joml-1.10.8.jar"
CP="$CP;$MC_LIBS/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar"
CP="$CP;$MC_LIBS/org/apache/maven/maven-artifact/3.8.5/maven-artifact-3.8.5.jar"
CP="$CP;$SABLE_JAR"
CP="$CP;libs/sable-companion.jar"

javac -nowarn -proc:none -d "$OUT/classes" -cp "$CP" $(find src -name '*.java')

cp -r res/* "$OUT/classes/"
cp LICENSE README.md icon.png "$OUT/classes/"
( cd "$OUT/classes" && jar --create --file ../plumbline.jar . )

echo "built $OUT/plumbline.jar"
