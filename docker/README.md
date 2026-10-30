# Docker build environment

```bash
docker build -t vz-booster-build:jdk17-sdk35 docker/
docker run --rm -it -v "$PWD":/workspace -w /workspace vz-booster-build:jdk17-sdk35 \
  bash -lc './gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon'
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The image does not contain Gradle: `gradle/wrapper/gradle-wrapper.properties` pins
Gradle 8.9 and the wrapper downloads + verifies it, so the build is identical inside
and outside the container.
