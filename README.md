# QuPath snapshots extension

A QuPath extension for generating snapshots & screenshots more easily.

The extension is intended for QuPath v0.6 and later. It is not compatible with earlier QuPath versions.

## Installing

To install the snapshots extension, you can:
* Open the QuPath [extension manager](https://qupath.readthedocs.io/en/latest/docs/intro/extensions.html#managing-extensions-with-the-extension-manager) and install the extension from there (recommended).
* Or download the latest `qupath-extension-snapshots-[version].jar` file from [releases](https://github.com/qupath/qupath-extension-snapshots/releases) and drag it onto the main QuPath window.

If you haven't installed any extensions before, you'll be prompted to select a QuPath user directory.
The extension will then be copied to a location inside that directory.

You might then need to restart QuPath (but not your computer).

## Building

You can build the extension using OpenJDK 21 or later with

```bash
./gradlew clean build
```

The output will be under `build/libs`.
You can drag the jar file on top of QuPath to install the extension.
