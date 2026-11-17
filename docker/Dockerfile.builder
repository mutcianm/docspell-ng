# Toolchain for building the app zips (sbt, elm, node); the repo is mounted, not copied.
# JDK 17 like upstream CI (flake.nix): JDK 21 deprecations break -Werror.
FROM docker.io/library/eclipse-temurin:17-jdk-noble

ARG SBT_VERSION=1.11.7
ARG ELM_VERSION=0.19.1

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl git nodejs npm \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" | tar xz -C /opt \
 && ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt \
 && curl -fsSL "https://github.com/elm/compiler/releases/download/${ELM_VERSION}/binary-for-linux-64-bit.gz" | gunzip > /usr/local/bin/elm \
 && chmod +x /usr/local/bin/elm

WORKDIR /src
