# syntax=docker/dockerfile:1.7
# Reproduzierbarer Build von Scorelens ohne lokal installiertes JDK oder Android-SDK.
#
#   scripts/docker-build.sh            → Tests + Debug-APK nach out/ (Arbeitsbaum)
#   scripts/docker-build.sh --head     → dasselbe für den letzten Commit (git archive)
#   scripts/docker-build.sh --image    → Builder-Image "scorelens-builder" (SDK + Gradle-Abhängigkeiten)
#
# Stufen: sdk (JDK 17 + Android SDK) → deps (Gradle-Wrapper und Abhängigkeiten liegen im Image)
#         → build (Unit-Tests + assembleDebug) → apk (nur die Artefakte, für `docker build --output`)
#         → builder (Standardziel: deps + Startbefehl, für interaktive Nutzung mit gemountetem Quellcode)

ARG JDK_IMAGE=eclipse-temurin:17-jdk

FROM ${JDK_IMAGE} AS sdk
ARG CMDLINE_TOOLS_VERSION=15859902
ARG ANDROID_PLATFORM=36
ARG BUILD_TOOLS=36.0.0
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}
RUN apt-get update && apt-get install -y --no-install-recommends unzip wget ca-certificates git && rm -rf /var/lib/apt/lists/* \
 && mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O /tmp/cmdtools.zip \
 && unzip -q /tmp/cmdtools.zip -d "$ANDROID_HOME/cmdline-tools" \
 && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
 && rm /tmp/cmdtools.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "platforms;android-${ANDROID_PLATFORM}" "build-tools;${BUILD_TOOLS}" > /dev/null \
 && rm -rf "$ANDROID_HOME/emulator"

# Gradle-Wrapper, Plugins und Abhängigkeiten in das Image laden (nur Build-Dateien → Layer bleibt lange gültig)
FROM sdk AS deps
WORKDIR /src
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY app/build.gradle.kts app/proguard-rules.pro ./app/
COPY app/src/main/AndroidManifest.xml ./app/src/main/AndroidManifest.xml
RUN printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties \
 && chmod +x gradlew \
 && ./gradlew --no-daemon --console=plain --quiet \
      :app:dependencies --configuration debugCompileClasspath > /dev/null \
 && ./gradlew --no-daemon --console=plain --quiet \
      :app:dependencies --configuration debugRuntimeClasspath > /dev/null \
 && ./gradlew --no-daemon --console=plain --quiet \
      :app:dependencies --configuration debugUnitTestRuntimeClasspath > /dev/null

FROM deps AS build
COPY . .
RUN printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties \
 && ./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug \
 && mkdir -p /out \
 && cp app/build/outputs/apk/debug/app-debug.apk /out/ \
 && cp -r app/build/reports/tests/testDebugUnitTest /out/test-report \
 && cd /out && sha256sum app-debug.apk > app-debug.apk.sha256

FROM scratch AS apk
COPY --from=build /out/ /

FROM deps AS builder
CMD ["bash", "-c", "printf 'sdk.dir=/opt/android-sdk\\n' > local.properties && ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug"]
