# JAR Decompiler Build Fix

## Problem
The JAR Decompiler tab was stuck in a loading state after installing the EXE file, but it worked fine when launched from IntelliJ. This was caused by missing JD-Core decompiler library dependencies in the EXE build.

## Root Cause
The JAR decompiler functionality uses the JD-Core library (`jd-core-1.1.3.jar` and `jd-gui-1.6.6.jar`) located in the `libs/` folder. These are loaded as system-scoped dependencies in Maven, which are **not automatically included** in:
1. The `maven-dependency-plugin` copy operation
2. The `maven-assembly-plugin` jar-with-dependencies assembly

When running from IntelliJ, these JARs were available on the classpath, but in the built EXE they were missing.

## Solution Applied

### 1. Added JD-Core as System Dependency (pom.xml)
Added explicit system-scoped dependency declaration for both JD-Core JARs:

```xml
<!-- JD-Core Java Decompiler Library -->
<dependency>
    <groupId>org.jd</groupId>
    <artifactId>jd-core</artifactId>
    <version>1.1.3</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/jd-core-1.1.3.jar</systemPath>
</dependency>

<!-- JD-GUI Java Decompiler (includes JD-Core) -->
<dependency>
    <groupId>org.jd</groupId>
    <artifactId>jd-gui</artifactId>
    <version>1.6.6</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/jd-gui-1.6.6.jar</systemPath>
</dependency>
```

### 2. Configured Maven Resources Plugin (pom.xml)
Added `maven-resources-plugin` execution to copy JD-Core libraries to `target/libs`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.3.1</version>
    <executions>
        <execution>
            <id>copy-jd-libs</id>
            <phase>package</phase>
            <goals>
                <goal>copy-resources</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.build.directory}/libs</outputDirectory>
                <resources>
                    <resource>
                        <directory>${project.basedir}/libs</directory>
                        <includes>
                            <include>jd-core-1.1.3.jar</include>
                            <include>jd-gui-1.6.6.jar</include>
                        </includes>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3. Created Custom Assembly Descriptor
Created `src/main/assembly/jar-with-system-dependencies.xml` to explicitly include system-scoped dependencies in the main JAR:

```xml
<assembly>
    ...
    <fileSets>
        <!-- Manually include system-scoped JARs -->
        <fileSet>
            <directory>${project.basedir}/libs</directory>
            <outputDirectory>/</outputDirectory>
            <includes>
                <include>jd-core-1.1.3.jar</include>
                <include>jd-gui-1.6.6.jar</include>
            </includes>
            <unpack>true</unpack>
        </fileSet>
    </fileSets>
</assembly>
```

### 4. Updated Maven Assembly Plugin (pom.xml)
Changed from using `descriptorRefs` to custom `descriptors`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.3.0</version>
    <configuration>
        <descriptors>
            <descriptor>src/main/assembly/jar-with-system-dependencies.xml</descriptor>
        </descriptors>
        ...
    </configuration>
</plugin>
```

### 5. Documented in Build Script
Added comments to `build_app_windows.bat` to document that JD-Core libraries are included.

## Build Process Flow

1. **Maven Package Phase**:
   - `maven-resources-plugin` copies `libs/jd-core-1.1.3.jar` and `libs/jd-gui-1.6.6.jar` to `target/libs/`
   - `maven-dependency-plugin` copies all regular dependencies to `target/libs/`
   - `maven-assembly-plugin` creates `nms-support-main.jar` with all dependencies (including JD-Core) unpacked into it

2. **Build Script (build_app_windows.bat)**:
   - Copies all JARs from `target/libs/` to `target/installer/input/libs/`
   - Copies `nms-support-main.jar` (which now contains JD-Core classes) to `target/installer/input/libs/`
   - `jpackage` creates the EXE installer with all required dependencies

## Verification

After rebuilding with these changes:
1. Run `mvn clean install` to build the EXE
2. Install the generated EXE from `target/installer/NMS-DevTools-3.0.0.exe`
3. Launch the installed application
4. Navigate to the JAR Decompiler tab
5. The tab should load properly and allow JAR decompilation

## Files Modified

- `pom.xml` - Added JD-Core dependencies and build plugin configurations
- `src/main/assembly/jar-with-system-dependencies.xml` - Created custom assembly descriptor
- `build_app_windows.bat` - Added documentation comments
- `JAR_DECOMPILER_BUILD_FIX.md` - This documentation file

## Technical Notes

- **System-scoped dependencies**: Maven treats these specially and doesn't include them in standard dependency operations
- **Assembly plugin**: The default `jar-with-dependencies` descriptor doesn't include system-scoped dependencies
- **JD-Core classes**: Now unpacked directly into the main JAR, eliminating runtime classpath issues
- **Backward compatibility**: These changes don't affect running from IntelliJ, which will continue to work as before

## Related Files

- `src/main/java/com/nms/support/nms_support/service/globalPack/JarDecompilerService.java` - Uses JD-Core API
- `src/main/java/com/nms/support/nms_support/controller/JarDecompilerController.java` - JAR Decompiler UI controller
- `libs/jd-core-1.1.3.jar` - JD-Core library
- `libs/jd-gui-1.6.6.jar` - JD-GUI library (contains additional utilities)

