# TiRTC Android Examples

This directory is the public mirror root for `tirtc-example-android`.

## What It Contains

- `example-server/`: media sender example app.
- `example-client/`: media receiver example app.
- Gradle root files needed to build both apps as a standalone Android project.

## Build Contract

- The default dependency shape is a published AAR.
- The default AV coordinate is derived from `TIRTC_ANDROID_VERSION` as `com.tange.ai:tirtc-av:<version>`.
- The default public Maven source is `http://repo-sdk.tange-ai.com/repository/maven-public/`.
- Default read-only credentials are `tange_user` / `tange_user`; you can override them with `TIRTC_PUBLIC_MAVEN_USERNAME` and `TIRTC_PUBLIC_MAVEN_PASSWORD`.
- If your published AAR is available from another repository as well, set `TIRTC_EXAMPLE_MAVEN_URL` or `-PtirtcExampleMavenUrl=<url>` to append it.

## Local Build

```sh
./gradlew :example-server:assembleDebug :example-client:assembleDebug
```

## Ownership

- The source-of-truth for the example code remains in the internal monorepo at `sdk/android/examples/`.
- This public project is updated by snapshot-style sync, not by ad hoc edits during export.
