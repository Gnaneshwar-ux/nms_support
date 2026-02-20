# NMS Support Build Guide

This project targets **JDK 25** while many developer machines still keep `JAVA_HOME` on older JDKs for legacy tools.

To avoid “works on my machine” issues, the project uses **Maven Toolchains** to select the JDK used to run `javac`, independent of `JAVA_HOME`.

## Prerequisites

- Any JDK **25** installation (keep it separate from the JDK used by other apps).
- Maven 3.9+ or simply use the included wrapper: `./mvnw` (Linux/macOS) or `mvnw.cmd` (Windows).

## Maven toolchains

Maven selects the JDK automatically through the `maven-toolchains-plugin`.

This repo is configured to use the **repository-local** `toolchains.xml` (so new checkouts build without copying anything into `~/.m2`).

If you prefer the global Maven config approach, you can still register your JDK path once in `${user.home}/.m2/toolchains.xml`. A template is provided at `toolchains.xml` in this repository.

```
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>25</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>D:/Tools/jdk-25</jdkHome>
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

After this, simply run `./mvnw clean package` and Maven will compile and run JavaFX tooling with **JDK 25** regardless of what `JAVA_HOME` points to.

### Bundled JD libraries

The proprietary `jd-core-1.1.3.jar` and `jd-gui-1.6.6.jar` files live in the tracked `libs/` folder. During the Maven `initialize` phase the build installs them into your local repository automatically, so no manual `mvn install:install-file` steps are required. Just keep the JARs in `libs/` when cloning or updating the repo.

## Windows installer build

The `build_app_windows.bat` script looks for a `DEVTOOLS_JAVA_HOME` variable (and falls back to `JAVA_HOME` if it already points to JDK 25). Before creating the installer, set the variable once per shell:

```
set DEVTOOLS_JAVA_HOME=C:\Program Files\Java\jdk-25
build_app_windows.bat
```

This isolates the packaging toolchain from the rest of your system. The script verifies the variable and exits early with a clear message if it cannot find a JDK 25 installation.

## Typical workflow

1. Install JDK 25 and ensure IntelliJ has an SDK entry for it.
2. Ensure `toolchains.xml` points to a valid JDK 25 on your machine (or copy it to `~/.m2/toolchains.xml` if you prefer global).
3. (Windows installer only) set `DEVTOOLS_JAVA_HOME` to that JDK when you need to run `build_app_windows.bat`.
3. Run `./mvnw clean package` to build the shaded application JAR; artifacts land in `target/`.

With these steps the project imports and builds on any machine without editing `pom.xml` or batch files.

## IntelliJ toolbar Run Configurations (shared)

This repo includes shared IntelliJ run configurations under `.idea/runConfigurations/` so you get one-click actions from the top toolbar.

After pulling latest changes:
1. In IntelliJ: **Maven tool window → Reload All Maven Projects** (or re-open the project).
2. Use the Run/Debug dropdown in the top-right to select one of:
   - **Run App (JavaFX)** → runs `javafx:run`
   - **Maven Package (-DskipTests)** → runs `clean package`
   - **Package Windows EXE (Maven)** → runs `clean install` (this triggers the `exec-maven-plugin` execution that calls `build_app_windows.bat` and produces the installer)

Notes:
- The EXE packaging requires WiX and a JDK 25 (see sections above). If WiX/JDK are missing, run `bootstrap_windows.cmd` first.
- If you don’t see the configurations immediately, invalidate caches or delete `.idea/workspace.xml` (do not commit workspace.xml).

## Quick start (Windows) - recommended

If you want the easiest setup on Windows (no system-wide JDK/WiX required):

```bat
bootstrap_windows.cmd clean compile
```

Then:

```bat
mvnw.cmd -DskipTests javafx:run
```

And to build the installer:

```bat
mvnw.cmd -DskipTests clean install
```

The EXE will be created under:

`target\installer\NMS DevTools-<version>.exe`

## Avoiding JDK conflicts in IntelliJ

To avoid IntelliJ showing module JDK as "0" when you update JDKs:

1. **Don’t hard-code a patch version in the Project SDK name** unless you keep it stable.
   - Prefer naming the IntelliJ SDK `openjdk-25` even if the folder is `openjdk-25.0.x`.
2. Consider enabling:
   - **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner →** “Delegate IDE build/run actions to Maven”.
   - This makes IntelliJ use Maven + toolchains for compilation (so changing JDK is mostly just updating `toolchains.xml` + `java.version`).

## Fresh clone bootstrap (Windows, no Java installed)

If you want **zero manual installs** on a new Windows machine (no Java, no WiX), use the bootstrap:

```bat
bootstrap_windows.cmd clean compile
```

What it does:
- Downloads an official **Temurin JDK 25** into `./.jdk/` (gitignored)
- Downloads **WiX Toolset** into `./.wix/` (gitignored) so `jpackage --type exe` works
- Writes `%USERPROFILE%\.m2\toolchains.xml` pointing to the downloaded JDK
- Runs `mvnw.cmd` with `JAVA_HOME` and `PATH` set for just that process

Then you can package:

```bat
bootstrap_windows.cmd -MavenArgs "clean package"
build_app_windows.bat
```

