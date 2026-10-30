# VZ Network Booster — reproducible Android build environment
#
# Base choice: eclipse-temurin:17-jdk-jammy (the same pattern used by
# RedMadRobot/android-docker-images) instead of beevelop/docker-android, because:
#   * beevelop's image inherits from a moving `beevelop/java` tag and pins
#     build-tools 36 / Gradle 8.12 (no NDK, no CMake);
#   * react-native-community/docker-android is the most complete image (JDK 17,
#     NDK 28.x, CMake, Ninja, Gradle 9.x) but is tuned for React Native and a
#     newer SDK level than this project targets (Platform 35);
#   * RedMadRobot/android-docker-images is the smallest, most auditable base and
#     never bundles a Gradle version — the Gradle wrapper in this repo decides.
#
# Everything this project needs is pinned below and installed from Google's
# official repository. No NDK/CMake is required to build this app today: the
# WireGuard tunnel AAR ships prebuilt wg-go libraries. NDK + CMake are installed
# anyway so native work (custom tun2socks, BoringTun) can start without a new image.
FROM eclipse-temurin:17-jdk-jammy

LABEL org.opencontainers.image.title="vz-network-booster-build" \
      org.opencontainers.image.description="JDK 17 + Android SDK 35 + build-tools 35.0.0 + NDK + CMake + Ninja"

ARG DEBIAN_FRONTEND=noninteractive

ARG ANDROID_SDK_ROOT=/opt/android-sdk
ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_CMDLINE_TOOLS_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258
ARG ANDROID_PLATFORM=android-35
ARG ANDROID_BUILD_TOOLS=35.0.0
ARG ANDROID_NDK=27.0.12077973
ARG ANDROID_CMAKE=3.22.1

ENV ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT} \
    ANDROID_HOME=${ANDROID_SDK_ROOT} \
    ANDROID_USER_HOME=/root/.android \
    GRADLE_USER_HOME=/root/.gradle \
    LANG=C.UTF-8

ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/${ANDROID_BUILD_TOOLS}:${ANDROID_SDK_ROOT}/cmake/${ANDROID_CMAKE}/bin

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        ninja-build \
        unzip \
        zip \
        python3 \
        file \
        libncurses6 \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" /root/.android \
    && curl --fail --location --retry 5 --retry-all-errors \
        --output /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum --check \
    && unzip -q -d "${ANDROID_SDK_ROOT}/cmdline-tools" /tmp/cmdline-tools.zip \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && touch /root/.android/repositories.cfg \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
        "ndk;${ANDROID_NDK}" \
        "cmake;${ANDROID_CMAKE}" > /dev/null \
    && chmod -R a+rX "${ANDROID_SDK_ROOT}"

# Ninja that AGP/CMake pick up (Ubuntu package is 1.10+, new enough for AGP 8.x).
RUN ninja --version

# Gradle is deliberately NOT baked in: gradle/wrapper/gradle-wrapper.properties
# pins 8.9 and the wrapper verifies the distribution itself.
WORKDIR /workspace
