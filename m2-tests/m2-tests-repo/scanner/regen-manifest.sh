#!/usr/bin/env bash
# Re-run from this folder after renaming entities/controllers/enums.
# Usage: bash regen-manifest.sh /absolute/path/to/your/student/project
set -euo pipefail
PROJECT="${1:?usage: $0 /absolute/path/to/your/student/project}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
java -jar "$SCRIPT_DIR/scanner.jar" "$PROJECT" -o "$SCRIPT_DIR/../src/test/resources/manifest.json"
echo "manifest.json regenerated from $PROJECT"
