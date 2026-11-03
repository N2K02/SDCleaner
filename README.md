# SDCleaner

SDCleaner is an Android cleanup app derived from SD Maid SE. This fork is being narrowed to app and system cleanup, leftover-file cleanup, and scheduled cleaning.

## Build

Build with JDK 17, the Android SDK, and `N2K02_PACKAGES_TOKEN` set to a token with read access to the shared theme and updater packages:

```sh
./gradlew assembleFossDebug
```

GitHub Actions builds experimental APKs for pull requests and stable APKs from `main`. Signed APKs and update manifests are published to the SDCleaner update channel.

## Credits and license

SDCleaner is a modified fork of **SD Maid SE**, originally created by Matthias Urhahn with contributions from the SD Maid SE community. The original project and source history are available at [d4rken-org/sdmaid-se](https://github.com/d4rken-org/sdmaid-se).

The covered source code is licensed under **GNU General Public License v3.0**. See this repository's [LICENSE](LICENSE) and the [original project's license](https://github.com/d4rken-org/sdmaid-se/blob/main/LICENSE). SDCleaner is an independent fork and is not endorsed by the original project authors.
