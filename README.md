# SDCleaner

SDCleaner is an Android cleanup app based on [SD Maid SE](https://github.com/d4rken-org/sdmaid-se). The planned focus is app and system cleanup, leftover files, and scheduled cleaning.

## Build

Build a FOSS debug APK locally:

```sh
./gradlew assembleFossDebug
```

GitHub Actions builds experimental APKs for pull requests and stable APKs from `main`. Signed APKs and update manifests are published through the SDCleaner update channel.

## License

This project is licensed under [GNU GPL v3](LICENSE). See the license file for upstream attribution and the applicable exclusions.
