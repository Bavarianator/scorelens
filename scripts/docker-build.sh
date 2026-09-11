#!/usr/bin/env bash
# Baut Unit-Tests + Debug-APK vollständig in Docker (JDK 17, Android SDK 36, Gradle-Abhängigkeiten im Image).
# Ergebnis: out/app-debug.apk, out/app-debug.apk.sha256, out/test-report/index.html
#
#   scripts/docker-build.sh           Arbeitsbaum (inkl. uncommitteter Änderungen)
#   scripts/docker-build.sh --head    exakt der letzte Commit (git archive HEAD), unabhängig vom Arbeitsbaum
#   scripts/docker-build.sh --image   nur das Builder-Image "scorelens-builder" bauen; danach z.B.
#                                     docker run --rm -it -v "$PWD":/src scorelens-builder bash
#
# Mit BuildKit (docker buildx) landet die APK direkt per --output in out/; ohne buildx wird das Build-Image
# gebaut und die Artefakte per `docker cp` herausgeholt.
set -euo pipefail
cd "$(dirname "$0")/.."
MODE=${1:-}
case "$MODE" in ""|--head|--image) ;; *) echo "Unbekannte Option: $MODE (erlaubt: --head, --image)" >&2; exit 2 ;; esac

if docker buildx version > /dev/null 2>&1; then
  export DOCKER_BUILDKIT=1
  BUILDKIT=1
else
  export DOCKER_BUILDKIT=0
  BUILDKIT=0
  echo "Hinweis: docker buildx fehlt – klassischer Builder (Arch: sudo pacman -S docker-buildx)." >&2
fi

context() { if [ "$MODE" = "--head" ]; then git archive --format=tar HEAD; fi; }
ctx_arg() { if [ "$MODE" = "--head" ]; then echo "-"; else echo "."; fi; }

if [ "$MODE" = "--image" ]; then
  docker build --target builder -t scorelens-builder .
  echo "Image: scorelens-builder"
  exit 0
fi

rm -rf out
if [ "$BUILDKIT" = 1 ]; then
  if [ "$MODE" = "--head" ]; then context | docker build --target apk --output type=local,dest=out -
  else docker build --target apk --output type=local,dest=out .; fi
else
  if [ "$MODE" = "--head" ]; then context | docker build --target build -t scorelens-build -
  else docker build --target build -t scorelens-build .; fi
  id=$(docker create scorelens-build)
  trap 'docker rm -f "$id" > /dev/null 2>&1 || true' EXIT
  docker cp "$id:/out" ./out
fi
echo "APK: out/app-debug.apk ($(du -h out/app-debug.apk | cut -f1)) · Test-Report: out/test-report/index.html"
