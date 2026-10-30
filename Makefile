# Convenience targets. Requires bash + make (macOS/Linux) or WSL on Windows.
SHELL := /bin/bash
IMAGE ?= vz-booster-build:jdk17-sdk35
GRADLE_TASKS = :app:testDebugUnitTest :app:assembleDebug

.PHONY: help env-check env-report docker-image docker-apk apk test clean

help:
	@echo "make env-check    - report which build tools are present/missing (read-only)"
	@echo "make env-report   - full toolchain + compatibility report"
	@echo "make apk          - build debug APK with the local toolchain"
	@echo "make docker-image - build the reproducible Docker build image"
	@echo "make docker-apk   - build the debug APK inside the Docker image"

env-check:
	@bash scripts/verify-toolchain.sh --check

env-report:
	@bash scripts/verify-toolchain.sh

apk:
	@bash scripts/build-apk.sh

test:
	@./gradlew :app:testDebugUnitTest --no-daemon --console=plain

docker-image:
	docker build -t $(IMAGE) docker/

docker-apk:
	docker run --rm -v "$$PWD":/workspace -w /workspace $(IMAGE) \
		bash -lc './gradlew $(GRADLE_TASKS) --no-daemon --console=plain && cp app/build/outputs/apk/debug/*.apk dist/'

clean:
	@./gradlew clean --no-daemon --console=plain || true
	@rm -rf dist/*.apk
