#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHARED_POM="$ROOT_DIR/sharedpackage/pom.xml"
CLIENT_POM="$ROOT_DIR/client/pom.xml"

current_version="$(perl -0ne 'print $1 if /<artifactId>sharedpackage<\/artifactId>\s*<version>([^<]+)<\/version>/' "$SHARED_POM")"

skip_tests=false
new_version=""
for arg in "$@"; do
  case "$arg" in
    --skip-tests)
      skip_tests=true
      ;;
    *)
      if [[ -z "$new_version" ]]; then
        new_version="$arg"
      fi
      ;;
  esac
done

if [[ -z "$new_version" ]]; then
  base="${current_version%-SNAPSHOT}"
  IFS='.' read -r major minor patch <<< "$base"
  new_version="$major.$minor.$((patch + 1))-SNAPSHOT"
fi

perl -0pi -e 's|(<artifactId>sharedpackage</artifactId>\s*<version>)[^<]+|${1}'"$new_version"'|' "$SHARED_POM"

mvn_args=(clean install)
if [[ "$skip_tests" == true ]]; then
  mvn_args+=("-DskipTests")
fi

"$ROOT_DIR/sharedpackage/mvnw" -f "$SHARED_POM" "${mvn_args[@]}"

if grep -q "<artifactId>sharedpackage</artifactId>" "$CLIENT_POM"; then
  perl -0pi -e 's|(<artifactId>sharedpackage</artifactId>\s*<version>)[^<]+|${1}'"$new_version"'|' "$CLIENT_POM"
else
  perl -0pi -e "s|\\s*</dependencies>|\\n        <dependency>\\n            <groupId>vn.com.truongsonbank</groupId>\\n            <artifactId>sharedpackage</artifactId>\\n            <version>$new_version</version>\\n        </dependency>\\n    </dependencies>|" "$CLIENT_POM"
fi

echo "Installed vn.com.truongsonbank:sharedpackage:$new_version"
echo "Updated client dependency to $new_version"
