# Reproducible build environment for County Line: JDK 17 + Android SDK.
# Build:  docker compose build
# Use:    docker compose run --rm android ./gradlew assembleDebug
#
# The project is bind-mounted at /workspace (see docker-compose.yml); this image
# only carries the toolchain, so source changes never require a rebuild.

FROM eclipse-temurin:17-jdk-jammy

# Versions kept in sync with gradle/libs.versions.toml and CLAUDE.md.
ARG CMDLINE_TOOLS_VERSION=13114758
ARG ANDROID_PLATFORM=android-35
ARG BUILD_TOOLS_VERSION=35.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip curl git && \
    rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools"; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"; \
    unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools"; \
    mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"; \
    rm /tmp/cmdline-tools.zip

ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

RUN set -eux; \
    yes | sdkmanager --licenses > /dev/null; \
    sdkmanager --install \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${BUILD_TOOLS_VERSION}"; \
    # World-writable so the container can run as an arbitrary host UID (--user),
    # including the dir that backs the Gradle-cache named volume.
    mkdir -p "${GRADLE_USER_HOME}"; \
    chmod -R a+rwX "${GRADLE_USER_HOME}" "${ANDROID_HOME}"

# Default non-root user; override at runtime with `--user "$(id -u):$(id -g)"`
# to keep bind-mounted build outputs owned by you.
RUN useradd -m -u 1000 builder
USER builder

WORKDIR /workspace
CMD ["./gradlew", "assembleDebug"]
