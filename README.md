# SDCleaner

SDCleaner is an Android cleanup app derived from [SD Maid SE](https://github.com/d4rken-org/sdmaid-se). It is being trimmed to focus on app and system cleanup, leftover files, and scheduled cleaning.

## Build

Build a local FOSS debug APK with:

```sh
./gradlew assembleFossDebug
```

GitHub Actions builds experimental APKs for pull requests and stable APKs from `main`. Signed APKs are published to the SDCleaner update channel when the repository signing and distribution secrets are available.

## License

The source code is licensed under [GNU GPL v3](LICENSE). SDCleaner has its own name and launcher icon. Upstream excluded brand assets, translations, store listings, and screenshots are not included.
