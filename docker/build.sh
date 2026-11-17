#!/usr/bin/env bash
# Builds the app zips locally in a builder container, then the runtime images on
# $TARGET_DOCKER_HOST (from docker/local.env), so no registry is needed.
set -euo pipefail
cd "$(dirname "$0")/.."

[ ! -f docker/local.env ] || . docker/local.env
target_host="${TARGET_DOCKER_HOST:?set it in docker/local.env, see local.env.example}"
version="$(sed -E 's/.*"(.*)".*/\1/' version.sbt)"
tag="${version}-$(git rev-parse --short HEAD)"
[ -z "$(git status --porcelain)" ] || tag="${tag}-dirty"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/docspell-ng-build"
mkdir -p "$cache"

docker build -t docspell-ng-builder -f docker/Dockerfile.builder docker
docker run --rm --user "$(id -u):$(id -g)" \
  -e HOME=/cache -e SBT_OPTS="-Duser.home=/cache -Xmx4g -Xss8m" \
  -e PATH="/src/modules/webapp/node_modules/.bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
  -v "$cache:/cache" -v "$PWD:/src" \
  docspell-ng-builder \
  sbt "set webapp/elmCompileMode := ElmCompileMode.Production" \
      "set webapp/stylesMode := StylesMode.Prod" \
      make-zip

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
for app in restserver joex; do
  mkdir "$stage/$app"
  cp "modules/$app/target/universal/docspell-$app-$version.zip" "docker/Dockerfile.$app" docker/joex-entrypoint.sh "$stage/$app/"
  DOCKER_HOST="$target_host" docker build \
    --build-arg DOCSPELL_VERSION="$tag" \
    -t "docspell-ng/$app:$tag" -f "$stage/$app/Dockerfile.$app" "$stage/$app"
done

echo "Built docspell-ng/{restserver,joex}:$tag on $target_host"
echo "Deploy with: docker/deploy.sh $tag"
