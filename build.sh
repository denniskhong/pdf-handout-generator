#!/usr/bin/env sh
# Build one versioned distributable ZIP on Linux or macOS.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TARGET_DIR="$PROJECT_DIR/target"
APP_BASENAME="pdf-handout-generator"

cd "$PROJECT_DIR"

if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java 21 or newer is required." >&2
    exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
    echo "Error: Maven is required to build the project." >&2
    exit 1
fi
if ! command -v zip >/dev/null 2>&1; then
    echo "Error: the 'zip' command is required." >&2
    exit 1
fi

# Locate the main source file rather than assuming a particular package path.
SOURCE_FILE=$(find "$PROJECT_DIR/src/main/java" -type f -name 'PdfHandoutApp.java' -print -quit)
if [ -z "$SOURCE_FILE" ] || [ ! -f "$SOURCE_FILE" ]; then
    echo "Error: PdfHandoutApp.java was not found under src/main/java." >&2
    exit 1
fi

# Extract the one quoted value assigned to APP_VERSION.
VERSION=$(sed -nE 's/^[[:space:]]*private[[:space:]]+static[[:space:]]+final[[:space:]]+String[[:space:]]+APP_VERSION[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*;.*/\1/p' "$SOURCE_FILE" | head -n 1)

if [ -z "$VERSION" ]; then
    echo "Error: Could not extract APP_VERSION from $SOURCE_FILE" >&2
    exit 1
fi

# Permit conventional semantic versions and optional prerelease/build suffixes.
case "$VERSION" in
    *[!0-9A-Za-z.+-]*|'')
        echo "Error: APP_VERSION contains unsupported filename characters: $VERSION" >&2
        exit 1
        ;;
esac

RELEASE_NAME="$APP_BASENAME-v$VERSION"
STAGING_DIR="$TARGET_DIR/$RELEASE_NAME"
JAR_NAME="$RELEASE_NAME.jar"
ZIP_NAME="$RELEASE_NAME.zip"
FINAL_ZIP="$TARGET_DIR/$ZIP_NAME"

echo "Building PDF Handout Generator v$VERSION..."
mvn clean package

# Find the executable shaded JAR. Exclude Maven Shade's retained thin JAR and
# non-runtime artifacts. The build is rejected if the result is ambiguous.
FAT_JARS=$(find "$TARGET_DIR" -maxdepth 1 -type f -name '*.jar' \
    ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print)
FAT_JAR_COUNT=$(printf '%s\n' "$FAT_JARS" | sed '/^$/d' | wc -l | tr -d ' ')
if [ "$FAT_JAR_COUNT" -ne 1 ]; then
    echo "Error: Expected exactly one executable JAR in target, found $FAT_JAR_COUNT." >&2
    printf '%s\n' "$FAT_JARS" >&2
    exit 1
fi
FAT_JAR=$(printf '%s\n' "$FAT_JARS" | sed -n '1p')

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR"
cp "$FAT_JAR" "$STAGING_DIR/$JAR_NAME"
cp "$PROJECT_DIR/README.md" "$STAGING_DIR/README.md"
cp "$PROJECT_DIR/LICENSE" "$STAGING_DIR/LICENSE"
if [ -f "$PROJECT_DIR/tech_specs.md" ]; then
    cp "$PROJECT_DIR/tech_specs.md" "$STAGING_DIR/tech_specs.md"
fi

cat > "$STAGING_DIR/run.sh" <<EOF_RUN_SH
#!/usr/bin/env sh
set -eu
APP_DIR=\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)
if ! command -v java >/dev/null 2>&1; then
    echo "Java 21 or newer is required." >&2
    exit 1
fi
exec java -jar "\$APP_DIR/$JAR_NAME"
EOF_RUN_SH

cat > "$STAGING_DIR/run.command" <<EOF_RUN_COMMAND
#!/usr/bin/env sh
set -eu
APP_DIR=\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)
if ! command -v java >/dev/null 2>&1; then
    echo "Java 21 or newer is required."
    printf "Press Return to close..."
    read answer
    exit 1
fi
exec java -jar "\$APP_DIR/$JAR_NAME"
EOF_RUN_COMMAND

cat > "$STAGING_DIR/run.bat" <<EOF_RUN_BAT
@echo off
setlocal
where java >nul 2>nul
if errorlevel 1 (
    echo Java 21 or newer is required.
    pause
    exit /b 1
)
java -jar "%~dp0$JAR_NAME"
if errorlevel 1 pause
endlocal
EOF_RUN_BAT

chmod +x "$STAGING_DIR/run.sh" "$STAGING_DIR/run.command"

rm -f "$FINAL_ZIP"
(
    cd "$TARGET_DIR"
    zip -qr "$ZIP_NAME" "$RELEASE_NAME"
)

# Preserve only the completed release ZIP under target/.
TEMP_ZIP="$PROJECT_DIR/$ZIP_NAME.tmp"
cp "$FINAL_ZIP" "$TEMP_ZIP"
rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
mv "$TEMP_ZIP" "$FINAL_ZIP"

echo
echo "Build complete."
echo "Application version: $VERSION"
echo "Release ZIP: $FINAL_ZIP"
echo "All intermediate build artifacts have been removed."
