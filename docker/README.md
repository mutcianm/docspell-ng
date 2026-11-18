# Fork images

Upstream builds its images in [docspell/docker](https://github.com/docspell/docker).
The Dockerfiles here are copies of those. The only change: they install the zips
built from this repo instead of downloading a release.

## Setup

Copy `local.env.example` to `local.env` (git-ignored) and fill in the target
Docker host, the deployment directory and the PostgreSQL container name.

## Build

    docker/build.sh

1. Compiles the restserver and joex zips in a builder container (sbt, elm, node).
   The repo is mounted into it, and the dependency caches live in
   `~/.cache/docspell-ng-build`.
2. Builds `docspell-ng/restserver:<version>-<sha>` and `docspell-ng/joex:<version>-<sha>`
   directly on `TARGET_DOCKER_HOST`, so no registry is needed. A dirty working
   tree gets a `-dirty` suffix on the tag.

The restserver zip includes the documentation site from `website/`, built with Zola
and served at `/docs` (`set restserver/bundleDocs := true`; the builder image has
zola, yarn and redocly for it). Builds without that setting skip the docs and
`/docs` returns 404.

The joex image replaces the distribution's Tesseract models with the more accurate
`tessdata_best` models for the languages in `TESSDATA_BEST_LANGS` (default: `eng osd`).
Each model adds about 10–15 MB to the image, and OCR with them takes about twice as long.

## Deploy

    docker/deploy.sh <tag>

On `DEPLOY_HOST`, inside `DEPLOY_DIR`, the script:

1. Dumps the `docspell` DB to `backups/docspell-<ts>.dump`.
2. Writes `docker-compose.override.yml`, which pins both services to `<tag>`.
   The existing `docker-compose.yml` stays untouched.
3. Runs `docker compose up -d` and waits for `/api/info/version`.

## Rollback

- **Previous fork build:** run `docker/deploy.sh <previous-tag>`. Old images stay on
  the host; `docker images 'docspell-ng/*'` lists them.
- **Upstream images:** delete `docker-compose.override.yml`, then run `docker compose up -d`.
- **After a release that ran DB migrations:** stop both apps, then restore the dump
  taken before the deploy:

      docker compose stop
      docker exec -i --user postgres <pg-container> pg_restore --clean --if-exists -d docspell < backups/docspell-<ts>.dump

  Then deploy the previous tag.
