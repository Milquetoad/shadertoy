# Publishing Shadertoy

The Gradle build produces a self-contained Windows app (bundled JRE + LWJGL
natives — users don't need Java installed). From there you publish a GitHub
Release, then optionally winget and/or Scoop.

## 0. One-time prerequisites

- **Add a LICENSE file** to the repo root. winget and Scoop both require a
  license; pick an SPDX id (e.g. `MIT`, `Apache-2.0`, or `Proprietary`) and put
  it in `packaging/scoop/shadertoy.json` (`"license"`) and the winget manifest.
- Bump `version` in `build.gradle` for each release (currently `1.0.0`).

## 1. Build the artifacts (I've wired these tasks up)

```sh
./gradlew packageZip      # build/distributions/JVRE-Shadertoy-<ver>-windows-x64.zip   (portable, for Scoop / winget-portable)
./gradlew jpackageMsi     # build/jpackage/JVRE Shadertoy-<ver>.msi                     (installer, for winget — needs WiX, see below)
```

- `packageZip` needs nothing extra and is the recommended starting point.
- `jpackageMsi` needs the **WiX Toolset v3** on PATH (`winget install WiXToolset.WiX`
  — v3, not v4/v5; jpackage in JDK 21 targets WiX 3). If you skip the MSI, you can
  still publish the zip as a winget *portable* package.

## 2. Publish a GitHub Release

```sh
# tag and create the release with the built artifacts attached
git tag v1.0.0 && git push origin v1.0.0
gh release create v1.0.0 \
  build/distributions/JVRE-Shadertoy-1.0.0-windows-x64.zip \
  "build/jpackage/JVRE Shadertoy-1.0.0.msi" \
  --title "JVRE Shadertoy 1.0.0" --notes "First release."
```

(Drop the `.msi` line if you didn't build it.) The release asset URLs are what
winget and Scoop point at.

## 3a. winget (Microsoft's package manager)

Easiest via `wingetcreate` — it autodetects the installer and opens the PR for you:

```sh
winget install Microsoft.WingetCreate
# the MSI asset name has a space; GitHub serves it with %20 in the URL — copy the exact
# download URL from the release page (or use the portable zip URL instead, see note below).
wingetcreate new "https://github.com/Milquetoad/shadertoy/releases/download/v1.0.0/JVRE%20Shadertoy-1.0.0.msi"
# answer the prompts (PackageIdentifier e.g. Milquetoad.JVREShadertoy, publisher, license, etc.)
wingetcreate submit --token <your-github-PAT>
```

This opens a PR against `microsoft/winget-pkgs`. Their CI validates it and a
moderator reviews (usually a day or two). Notes:
- **PackageIdentifier** must be `Publisher.Package`, e.g. `Milquetoad.JVREShadertoy`.
- An **MSI** is the smoothest installer type. To submit the **zip as a portable**
  instead, run `wingetcreate new <zip-url>` and choose installer type `portable`
  with `JVRE Shadertoy\JVRE Shadertoy.exe` as the portable command.
- Future versions: `wingetcreate update Milquetoad.JVREShadertoy --version <new> --urls <new-url>`.

## 3b. Scoop (lighter, no central review)

The manifest is already written: [`scoop/shadertoy.json`](scoop/shadertoy.json).
For each release, fill in the two `REPLACE-...` fields:

```sh
# sha256 of the zip:
sha256sum build/distributions/JVRE-Shadertoy-1.0.0-windows-x64.zip
```

Put the hash in `architecture.64bit.hash` and your SPDX id in `license`. Then host
the manifest in a **bucket** repo (a normal GitHub repo with a `bucket/` folder
containing `shadertoy.json`). Users then:

```sh
scoop bucket add milquetoad https://github.com/Milquetoad/scoop-bucket
scoop install shadertoy
```

`checkver`/`autoupdate` are set so `scoop update` and bucket auto-PRs track new
GitHub releases automatically (only `version`, `url`, `hash` change).

## What I can't do for you

The Release, the winget PR, and the Scoop bucket repo are all tied to your GitHub
account/token, so you drive those. Everything up to and including building the
artifacts is automated in `build.gradle`.
