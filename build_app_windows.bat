@ECHO OFF

setlocal EnableExtensions

rem Set NMS_TRACE=1 to see executed lines for debugging
IF DEFINED NMS_TRACE echo on

rem ------ ENVIRONMENT --------------------------------------------------------
rem The script depends on various environment variables to exist in order to
rem run properly. The java version we want to use, the location of the java
rem binaries (java home), and the project version as defined inside the pom.xml
rem file, e.g. 1.0-SNAPSHOT.
rem
rem PROJECT_VERSION: version used in pom.xml, e.g. 1.0-SNAPSHOT
rem APP_VERSION: the application version, e.g. 1.0.0, shown in "about" dialog

set JAVA_VERSION=25
set MAIN_JAR=nms-support-main.jar

rem DEVTOOLS_JAVA_HOME should point to a JDK %JAVA_VERSION% install.
rem We set it dynamically for this script run (no need to set system env vars).
rem Resolution order:
rem  1) If DEVTOOLS_JAVA_HOME already set by caller, keep it
rem  2) If JetBrains-managed JDK exists under %USERPROFILE%\.jdks\openjdk-25.0.2, use it
rem  3) Fallback to JAVA_HOME
IF NOT DEFINED DEVTOOLS_JAVA_HOME (
    IF EXIST "%USERPROFILE%\.jdks\openjdk-25.0.2\bin\java.exe" (
        set "DEVTOOLS_JAVA_HOME=%USERPROFILE%\.jdks\openjdk-25.0.2"
    ) ELSE IF DEFINED JAVA_HOME (
        set "DEVTOOLS_JAVA_HOME=%JAVA_HOME%"
    ) ELSE (
        echo [ERROR] Could not locate a JDK %JAVA_VERSION% installation.
        echo [ERROR] Either set DEVTOOLS_JAVA_HOME or install a JDK at:
        echo         %USERPROFILE%\.jdks\openjdk-25.0.2
        exit /B 1
    )
)

rem Basic sanity check
IF NOT EXIST "%DEVTOOLS_JAVA_HOME%\bin\java.exe" (
    echo [ERROR] DEVTOOLS_JAVA_HOME is set but java.exe was not found:
    echo         %DEVTOOLS_JAVA_HOME%
    exit /B 1
)

set "JAVA_BIN=%DEVTOOLS_JAVA_HOME%\bin"
echo Using JDK from: %DEVTOOLS_JAVA_HOME%

rem ------ WiX Toolset -------------------------------------------------------
rem jpackage needs WiX on Windows to build EXE/MSI.
rem It supports:
rem  - WiX v3: candle.exe + light.exe
rem  - WiX v4/v5: wix.exe
rem We automatically add a locally bootstrapped WiX v3.11 to PATH if present.

rem Use script directory instead of %CD% to avoid issues if the current path contains special chars.
set "WIX_BIN=%~dp0.wix\wix311"

rem IMPORTANT: Do NOT use parentheses blocks here. Some machines have parentheses in PATH
rem (e.g., "Program Files (x86)") and cmd.exe can mis-parse blocks.
IF EXIST "%WIX_BIN%\candle.exe" set "PATH=%WIX_BIN%;%PATH%"
IF EXIST "%WIX_BIN%\candle.exe" echo Using WiX from: %WIX_BIN%

IF NOT EXIST "%WIX_BIN%\candle.exe" goto WIX_CHECK
goto WIX_OK

:WIX_CHECK
where candle.exe >NUL 2>&1
IF ERRORLEVEL 1 (
    echo [ERROR] Can not find WiX tools (candle.exe/light.exe).
    echo [ERROR] Run: bootstrap_windows.cmd clean package
    echo [ERROR] (it will download WiX 3.11 into .wix\wix311) OR install WiX and add it to PATH.
    exit /B 1
)

:WIX_OK

rem Set desired installer type: "app-image" "msi" "exe".
set INSTALLER_TYPE=exe

rem ------ SETUP DIRECTORIES AND FILES ----------------------------------------
rem Remove previously generated java runtime and installers. Copy all required
rem jar files into the input/libs folder.
rem Note: JD-Core decompiler libraries (jd-core-1.1.3.jar, jd-gui-1.6.6.jar) 
rem are copied to target/libs by maven-resources-plugin and included in the 
rem main JAR by maven-assembly-plugin.

IF EXIST target\java-runtime rmdir /S /Q  .\target\java-runtime
IF EXIST target\installer rmdir /S /Q target\installer

xcopy /S /E /Q target\libs\* target\installer\input\libs\
echo -----
xcopy target\%MAIN_JAR% target\installer\input\libs\

rem ------ REQUIRED MODULES ---------------------------------------------------
rem Use jlink to detect all modules that are required to run the application.
rem Starting point for the jdep analysis is the set of jars being used by the
rem application.

echo detecting required modules

call "%JAVA_BIN%\jdeps" ^
  -q ^
  --multi-release %JAVA_VERSION% ^
  --ignore-missing-deps ^
  --class-path "target\installer\input\libs\*" ^
  --print-module-deps .\target\classes\com\nms\support\nms_support\ > temp.txt

set /p detected_modules=<temp.txt

echo detected modules: %detected_modules%

rem ------ MANUAL MODULES -----------------------------------------------------
rem jdk.crypto.ec has to be added manually bound via --bind-services or
rem otherwise HTTPS does not work.
rem
rem See: https://bugs.openjdk.java.net/browse/JDK-8221674
rem
rem In addition we need jdk.localedata if the application is localized.
rem This can be reduced to the actually needed locales via a jlink parameter,
rem e.g., --include-locales=en,de.
rem
rem Don't forget the leading ','!

set manual_modules=,jdk.crypto.ec,jdk.localedata
echo manual modules: %manual_modules%

rem ------ RUNTIME IMAGE ------------------------------------------------------
rem Use the jlink tool to create a runtime image for our application. We are
rem doing this in a separate step instead of letting jlink do the work as part
rem of the jpackage tool. This approach allows for finer configuration and also
rem works with dependencies that are not fully modularized, yet.

echo creating java runtime image

call "%JAVA_BIN%\jlink" ^
  --strip-native-commands ^
  --no-header-files ^
  --no-man-pages ^
  --compress=zip-2 ^
  --strip-debug ^
  --add-modules %detected_modules%%manual_modules% ^
  --include-locales=en,de ^
  --output target/java-runtime


rem ------ PACKAGING ----------------------------------------------------------
rem In the end we will find the package inside the target/installer directory.

rem If APP_VERSION is not provided by the caller/Maven, use a safe default.
IF NOT DEFINED APP_VERSION set "APP_VERSION=1.0"

echo APP_VERSION is set to: %APP_VERSION%
echo Creating executable...

rem jpackage WiX detection is sensitive to some PATH entries (notably those with parentheses).
rem To make it deterministic, run jpackage with a minimal PATH that still contains WiX.
set "JP_ORIG_PATH=%PATH%"
set "PATH=%WIX_BIN%;%SystemRoot%\System32;%SystemRoot%"

call "%JAVA_BIN%\jpackage" ^
  --type %INSTALLER_TYPE% ^
  --dest target/installer ^
  --input target/installer/input/libs ^
  --name "NMS DevTools" ^
  --main-class com.nms.support.nms_support.FakeMain ^
  --main-jar %MAIN_JAR% ^
  --java-options -Xmx2048m ^
  --runtime-image target/java-runtime ^
  --icon src/main/nmssupport.ico ^
  --app-version %APP_VERSION% ^
  --vendor Oracle ^
  --copyright "Copyright Oracle NMS." ^
  --win-dir-chooser ^
  --win-shortcut ^
  --win-per-user-install ^
  --win-menu 

set "PATH=%JP_ORIG_PATH%"
