# NMS Support Build Guide

This project targets JDK 22 while many developer machines still keep `JAVA_HOME` on JDK 8 for legacy tools. The Maven build and the Windows packaging script now discover their own JDK 22 runtime so you do not have to reconfigure your operating system.

## Prerequisites

- Any JDK 22 installation (keep it separate from the JDK 8 used by other apps).
- Maven 3.9+ or simply use the included wrapper: `./mvnw` (Linux/macOS) or `mvnw.cmd` (Windows).

## Maven toolchains

Maven selects the JDK automatically through the `maven-toolchains-plugin`. Register your JDK 22 path once in `${user.home}/.m2/toolchains.xml`. A ready-to-copy template is provided at `toolchains.xml` in this repository:

```
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>22</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>D:/Tools/jdk-22</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

Adjust the `jdkHome` path to match your local install, then copy it into Maven’s config directory. On Windows Command Prompt:

1. Open the template (this repo’s `toolchains.xml`) in a text editor and update `<jdkHome>` with your own path.
2. Create the Maven config directory if it doesn’t exist:
   ```
   mkdir "%USERPROFILE%\.m2"
   ```
3. Copy the edited file into place:
   ```
   copy /Y toolchains.xml "%USERPROFILE%\.m2\toolchains.xml"
   ```

After this, simply run `./mvnw clean package` and Maven will compile and run JavaFX tooling with JDK 22 regardless of what `JAVA_HOME` points to.

### Bundled JD libraries

The proprietary `jd-core-1.1.3.jar` and `jd-gui-1.6.6.jar` files live in the tracked `libs/` folder. During the Maven `initialize` phase the build installs them into your local repository automatically, so no manual `mvn install:install-file` steps are required. Just keep the JARs in `libs/` when cloning or updating the repo.

## Windows installer build

The `build_app_windows.bat` script now looks for a `DEVTOOLS_JAVA_HOME` variable (and falls back to `JAVA_HOME` if it already points to JDK 22). Before creating the installer, set the variable once per shell:

```
set DEVTOOLS_JAVA_HOME=C:\Program Files\Java\jdk-22
build_app_windows.bat
```

This isolates the packaging toolchain from the rest of your system. The script verifies the variable and exits early with a clear message if it cannot find a JDK 22 installation.

## Typical workflow

1. Install JDK 22 and register it in `toolchains.xml`.
2. (Windows installer only) set `DEVTOOLS_JAVA_HOME` to that JDK when you need to run `build_app_windows.bat`.
3. Run `./mvnw clean package` to build the shaded application JAR; artifacts land in `target/`.

With these steps the project imports and builds on any machine without editing `pom.xml` or batch files.

