# jpackage packaging scripts

These scripts build the Maven shaded jar (with `jSerialComm` and its native libraries for
all platforms already bundled) and turn it into a native installer/app-image with `jpackage`.

## Important: jpackage is per-OS, not cross-platform

`jpackage` always bundles a native Java runtime image for the OS/architecture it runs on. It
cannot produce a Windows `.exe`/`.msi` from Linux or macOS, or vice versa. To ship installers
for all platforms, run the matching script **on each target OS**:

The same restriction applies to CPU architectures: build Windows ARM64 on Windows ARM64 with
an ARM64 JDK, and Linux ARM64 on Linux ARM64 with an ARM64 JDK. Builds on x64 produce x64
packages. The bundled jSerialComm library supports the required platform-specific native code.

| OS      | Script                          | Default output type |
|---------|----------------------------------|----------------------|
| Windows | `build-jpackage.ps1`              | portable app image with `.exe` |
| Linux   | `build-jpackage.sh`                | portable app image with executable |
| macOS   | `build-jpackage.sh`                | portable app image with `.app` |

`jSerialComm` itself does not need any per-platform build steps: the single shaded jar produced
by `mvn package` already contains the native libraries for Windows, Linux, macOS, and their
architectures, so the same jar is used as `--input`/`--main-jar` on every OS.

## Requirements

- JDK 17+ with `jpackage` on `PATH` (bundled with the standard JDK since Java 16).
- Maven on `PATH`.
- Linux: `dpkg-deb` (for `deb`) or `rpmbuild` (for `rpm`) installed.
- macOS: Xcode command line tools installed for `pkg`/`dmg` signing-free builds.

## Usage

Windows (PowerShell):

```powershell
.\packaging\build-jpackage.ps1            # portable app image with TouchDRO Desktop.exe
.\packaging\build-jpackage.ps1 -Type exe
.\packaging\build-jpackage.ps1 -Type msi
.\packaging\build-jpackage.ps1 -Type app-image
```

Linux / macOS:

```bash
./packaging/build-jpackage.sh             # portable app image
./packaging/build-jpackage.sh deb         # Linux only
./packaging/build-jpackage.sh rpm         # Linux only
./packaging/build-jpackage.sh dmg         # macOS only
./packaging/build-jpackage.sh pkg         # macOS only
./packaging/build-jpackage.sh app-image   # any OS
```

Output installers/app-images are written to `target/jpackage/`.

For a portable release, distribute the complete application directory in
`target/jpackage/`, not only its launcher. On Windows the launcher is
`TouchDRO Desktop.exe`; on Linux it is `bin/TouchDRO Desktop`.

## Icons

Place platform-specific icons in `assets/` and the scripts will pick them up automatically:

- Windows: `assets/touchdro.ico`
- macOS: `assets/touchdro.icns`
- Linux: `assets/touchdro.png`

If a platform icon is missing, the script prints a warning and packages without a custom icon.
