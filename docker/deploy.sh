#!/usr/bin/env bash
# Deploys images built by build.sh: DB backup, pin images via a compose override, restart, smoke test.
set -euo pipefail

tag="${1?usage: deploy.sh <image-tag>}"
cd "$(dirname "$0")/.."
[ ! -f docker/local.env ] || . docker/local.env
host="${DEPLOY_HOST:?set it in docker/local.env, see local.env.example}"
dir="${DEPLOY_DIR:?set it in docker/local.env}"
pg="${PG_CONTAINER:?set it in docker/local.env}"

ssh "$host" bash -s -- "$tag" "$dir" "$pg" <<'EOF'
set -euo pipefail
tag="$1"
cd "$2"
pg="$3"
umask 077

for app in restserver joex; do
  docker image inspect "docspell-ng/$app:$tag" >/dev/null
done

ts="$(date +%Y%m%d-%H%M%S)"
mkdir -p backups
docker exec --user postgres "$pg" pg_dump -Fc docspell > "backups/docspell-$ts.dump"
[ -s "backups/docspell-$ts.dump" ]
[ ! -f docker-compose.override.yml ] || cp docker-compose.override.yml "backups/docker-compose.override-$ts.yml"
echo "DB backup: $PWD/backups/docspell-$ts.dump"

cat > docker-compose.override.yml <<YML
services:
  restserver:
    image: docspell-ng/restserver:$tag
  joex:
    image: docspell-ng/joex:$tag
YML
chmod 644 docker-compose.override.yml

docker compose up -d

for _ in $(seq 90); do
  if v="$(curl -fsS http://localhost:7880/api/info/version 2>/dev/null)"; then
    echo "restserver up: $v"
    exit 0
  fi
  sleep 2
done
echo "restserver did not answer within 3 minutes; see docker/README.md for rollback" >&2
exit 1
EOF
