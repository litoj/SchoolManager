# The GitLab test image: the pinned JDK and the compileSdk platform jar.
# The build tools come from Maven, so none ship here.

FROM eclipse-temurin:21-jdk

# the ci-image job passes the compileSdk of app/build.gradle.kts here
ARG PLATFORM_SDK
ENV ANDROID_HOME=/opt/android-sdk

# The repository names a platform with an inconsistent minor suffix.
# The lookup takes the exact package name for the level.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o /tmp/cmdline-tools.zip \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && rm /tmp/cmdline-tools.zip \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && yes | "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
    && pkg=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
        | grep -o "platforms;android-${PLATFORM_SDK}[^\"]*" | sort -Vu | head -1) \
    && test -n "$pkg" \
    && yes | "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" "$pkg" > /dev/null
