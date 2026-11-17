#!/bin/sh
# Builds every module at version major.minor.<first 6 characters of the commit>.
# Usage: ./build.sh [maven arguments]       default: clean verify
# A plain mvn build uses the version <major.minor>.dev instead.
set -e
cd "$(dirname "$0")"

base=$(sed -n 's:.*<app.version.base>\(.*\)</app.version.base>.*:\1:p' pom.xml | head -1)
commit=$(git rev-parse --short=6 HEAD)
if [ -z "$base" ] || [ -z "$commit" ]; then
  echo "build.sh: cannot read app.version.base from pom.xml or the commit from git" >&2
  exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "build.sh: warning: uncommitted changes, version $base.$commit does not match the source" >&2
fi

version="$base.$commit"
echo "Building version $version"
if [ "$#" -eq 0 ]; then
  set -- clean verify
fi
exec mvn -Drevision="$version" "$@"
