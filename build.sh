#!/usr/bin/env sh
# Build one distributable ZIP on Linux or macOS.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TARGET_DIR="$PROJECT_DIR/target"
STAGING_DIR="$TARGET_DIR/pdf-handout-generator-v1"
JAR_NAME="pdf-handout-generator-v1.jar"
ZIP_NAME="pdf-handout-generator-v1.zip"
FINAL_ZIP="$TARGET_DIR/$ZIP_NAME"

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
    echo "Error: the 'zip' command is required to create the release archive." >&2
    exit 1
fi

echo "Building PDF Handout Generator v1..."
mvn clean package

# Create a temporary release directory. Only these files enter the ZIP.
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR"
cp "$TARGET_DIR/$JAR_NAME" "$STAGING_DIR/$JAR_NAME"
cp "$PROJECT_DIR/README.md" "$STAGING_DIR/README.md"
cp "$PROJECT_DIR/LICENSE" "$STAGING_DIR/LICENSE"

cat > "$STAGING_DIR/run.sh" <<'LAUNCHER'
#!/usr/bin/env sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if ! command -v java >/dev/null 2>&1; then
    echo "Java 21 or newer is required." >&2
    exit 1
fi
exec java -jar "$APP_DIR/pdf-handout-generator-v1.jar"
LAUNCHER

cat > "$STAGING_DIR/run.command" <<'LAUNCHER'
#!/usr/bin/env sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if ! command -v java >/dev/null 2>&1; then
    echo "Java 21 or newer is required."
    printf "Press Return to close..."
    read answer
    exit 1
fi
exec java -jar "$APP_DIR/pdf-handout-generator-v1.jar"
LAUNCHER

cat > "$STAGING_DIR/run.bat" <<'LAUNCHER'
@echo off
setlocal
where java >nul 2>nul
if errorlevel 1 (
    echo Java 21 or newer is required.
    pause
    exit /b 1
)
java -jar "%~dp0pdf-handout-generator-v1.jar"
if errorlevel 1 pause
endlocal
LAUNCHER

chmod +x "$STAGING_DIR/run.sh" "$STAGING_DIR/run.command"

# Build the release archive from the staging directory.
rm -f "$FINAL_ZIP"
(
    cd "$TARGET_DIR"
    zip -qr "$ZIP_NAME" "pdf-handout-generator-v1"
)

# Remove every Maven/intermediate artifact and the temporary staging tree,
# while preserving only the completed release ZIP.
TEMP_ZIP="$PROJECT_DIR/$ZIP_NAME.tmp"
cp "$FINAL_ZIP" "$TEMP_ZIP"
rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
mv "$TEMP_ZIP" "$FINAL_ZIP"

echo
echo "Build complete."
echo "Release ZIP: $FINAL_ZIP"
echo "All intermediate build artifacts have been removed."
