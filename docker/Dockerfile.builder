# Toolchain for building the app zips (sbt, elm, node); the repo is mounted, not copied.
# JDK 17 like upstream CI (flake.nix): JDK 21 deprecations break -Werror.
FROM docker.io/library/eclipse-temurin:17-jdk-noble

ARG SBT_VERSION=1.11.7
ARG ELM_VERSION=0.19.1
# for the documentation bundled into the restserver (#32)
ARG ZOLA_VERSION=0.19.2
ARG YARN_VERSION=1.22.22
ARG REDOCLY_VERSION=1.25.15

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl git nodejs npm \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" | tar xz -C /opt \
 && ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt \
 && curl -fsSL "https://github.com/elm/compiler/releases/download/${ELM_VERSION}/binary-for-linux-64-bit.gz" | gunzip > /usr/local/bin/elm \
 && chmod +x /usr/local/bin/elm \
 && curl -fsSL "https://github.com/getzola/zola/releases/download/v${ZOLA_VERSION}/zola-v${ZOLA_VERSION}-x86_64-unknown-linux-gnu.tar.gz" | tar xz -C /usr/local/bin zola \
 && npm install -g "yarn@${YARN_VERSION}" "@redocly/cli@${REDOCLY_VERSION}"

WORKDIR /src
