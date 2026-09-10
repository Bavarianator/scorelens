# Reproduzierbarer Build von FreeDarts ohne lokal installiertes Android-SDK.
#   docker build -t freedarts-builder .
#   docker run --rm -v "$PWD":/src -v freedarts-gradle:/root/.gradle freedarts-builder
# Ergebnis: app/build/outputs/apk/debug/app-debug.apk
FROM eclipse-temurin:17-jdk

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    CMDLINE_TOOLS_VERSION=13114758

RUN apt-get update && apt-get install -y --no-install-recommends unzip wget && rm -rf /var/lib/apt/lists/* \
    && mkdir -p $ANDROID_SDK_ROOT/cmdline-tools \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O /tmp/cmdtools.zip \
    && unzip -q /tmp/cmdtools.zip -d $ANDROID_SDK_ROOT/cmdline-tools \
    && mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest \
    && rm /tmp/cmdtools.zip \
    && yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
    && $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

WORKDIR /src
CMD ["bash", "-c", "printf 'sdk.dir=/opt/android-sdk\\n' > local.properties && ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug"]
