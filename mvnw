#!/bin/sh
set -eu

wrapper_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/.mvn/wrapper"
distribution_url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
maven_home="$wrapper_dir/apache-maven-3.9.11"

if [ ! -x "$maven_home/bin/mvn" ]; then
  mkdir -p "$wrapper_dir"
  archive="$(mktemp)"
  trap 'rm -f "$archive"' EXIT
  curl --fail --location --silent --show-error "$distribution_url" --output "$archive"
  unzip -q "$archive" -d "$wrapper_dir"
fi

exec "$maven_home/bin/mvn" "$@"
