# Publishing JVRE-shadertoy (winget)

The Gradle build produces a self-contained Windows app (bundled JRE + LWJGL
natives — users don't need Java installed). From there you publish a GitHub
Release and submit it to winget.

## 0. One-time prerequisites

- **Add a LICENSE file** to the repo root. winget requires a license; pick an
  SPDX id (e.g. `MIT`, `Apache-2.0`, or `Proprietary`).
- Bump `version` in `build.gradle` for each release (currently `1.0.0`).

## 1. Build the installer (I've wired these tasks up)

```sh
./gradlew jpackageMsi     # build/jpackage/JVRE-shadertoy-<ver>.msi    (installer — the winget artifact)
./gradlew packageZip      # build/distributions/JVRE-shadertoy-<ver>-windows-x64.zip   (optional: winget-portable)
```

- `jpackageMsi` needs the **WiX Toolset v3** on PATH
  (`winget install WiXToolset.WiX` — v3, not v4/v5; jpackage in JDK 21 targets WiX 3).
  The MSI is the smoothest winget installer type.
- If you'd rather not install WiX, `packageZip` produces a self-contained zip you
  can submit as a winget **portable** package instead (no extra tooling).

## 2. Publish a GitHub Release

```sh
# tag and create the release with the installer attached
git tag v1.0.0 && git push origin v1.0.0
gh release create v1.0.0 \
  build/jpackage/JVRE-shadertoy-1.0.0.msi \
  --title "JVRE-shadertoy 1.0.0" --notes "First release."
```

(Use the zip path instead if you went the portable route.) The release asset URL
is what winget points at.

## 3. Submit to winget

Easiest via `wingetcreate` — it autodetects the installer and opens the PR for you:

```sh
winget install Microsoft.WingetCreate
wingetcreate new https://github.com/Milquetoad/shadertoy/releases/download/v1.0.0/JVRE-shadertoy-1.0.0.msi
# answer the prompts (PackageIdentifier e.g. Milquetoad.JVREShadertoy, publisher, license, etc.)
wingetcreate submit --token <your-github-PAT>
```

This opens a PR against `microsoft/winget-pkgs`. Their CI validates it and a
moderator reviews (usually a day or two). Notes:
- **PackageIdentifier** must be `Publisher.Package`, e.g. `Milquetoad.JVREShadertoy`.
- For the **zip as a portable** instead of an MSI: `wingetcreate new <zip-url>`,
  choose installer type `portable`, command `JVRE-shadertoy\JVRE-shadertoy.exe`.
- Future versions: `wingetcreate update Milquetoad.JVREShadertoy --version <new> --urls <new-url>`.

## What I can't do for you

The Release and the winget PR are tied to your GitHub account/token, so you drive
those. Everything up to and including building the artifacts is automated in
`build.gradle`.
