# NMS DevTools - Complete Feature Documentation

## Table of Contents
1. [Overview](#overview)
2. [Application Features](#application-features)
3. [Build Automation Tab](#build-automation-tab)
4. [Datastore Dump Tab](#datastore-dump-tab)
5. [JAR Decompiler Tab](#jar-decompiler-tab)
6. [VPN Manager Tab](#vpn-manager-tab)
7. [Process Monitor Tab](#process-monitor-tab)
8. [Project Management](#project-management)
9. [SSH Connection Management](#ssh-connection-management)
10. [Database Integration](#database-integration)
11. [Technical Architecture](#technical-architecture)
12. [Build and Deployment](#build-and-deployment)

---

## Overview

NMS DevTools is a comprehensive JavaFX desktop application designed to streamline development, deployment, and management operations for Oracle NMS (Network Management System) projects. The application provides a unified interface for managing multiple projects, automating builds, monitoring processes, analyzing datastores, and managing VPN connections.

### Key Capabilities
- **Multi-Project Management**: Manage multiple NMS projects from a single interface
- **Build Automation**: Automated build, deployment, and application lifecycle management
- **Datastore Analysis**: Parse and analyze Oracle datastore reports
- **Code Decompilation**: Browse and decompile JAR files with VS Code integration
- **VPN Management**: Cisco AnyConnect VPN connection management
- **Process Monitoring**: Real-time monitoring of server processes
- **SSH Integration**: Persistent SSH connections with LDAP and basic auth support
- **Database Integration**: Direct database connections for configuration management

### Supported Authentication Methods
- **LDAP Authentication**: SSH with LDAP credentials and sudo switching
- **Basic Authentication**: Direct SSH authentication
- **Database Authentication**: Oracle database connections for configuration storage
- **VPN Integration**: Cisco AnyConnect for secure network access

---

## Application Features

### Main Window Features

#### Project Selection
- Dropdown to select active project
- Projects loaded from database or local configuration
- Real-time project switching without restart
- Project-specific settings and configurations

#### Global Operations
- **Save All**: Persist changes across all tabs
- **Refresh**: Reload project data
- **Settings**: Application-wide configurations
- **Import/Export**: Project configuration management

#### Tab Navigation
- Build Automation
- Datastore Dump Analysis
- JAR Decompiler
- VPN Manager
- Process Monitor
- Project Details

---

## Build Automation Tab

### Purpose
Automates the complete build, deployment, and application lifecycle management for NMS projects. Handles compilation, installation, server control, and monitoring.

### Key Features

#### 1. **Build Management**
- **Build Modes**:
  - `Ant config`: Standard configuration build
  - `Ant clean config`: Clean build with full recompilation
- **Application Selection**: Multi-application support (NMS, Product, etc.)
- **Automated Compilation**: Ant-based build process with real-time logs
- **Build Log Access**: Direct access to build logs for debugging

#### 2. **Application Lifecycle Control**
- **Start**: Launch application server
- **Stop**: Gracefully stop running applications
- **Restart**: Quick restart for configuration changes
- **Status Monitoring**: Real-time application status display

#### 3. **Auto-Login Configuration**
- **User Management**: Configure auto-login users
- **User Types**: Integration with database user types
- **Password Management**: Secure password storage
- **Setup Modes**:
  - Enable/disable auto-login
  - Configure restart tools
  - Manage login scripts

#### 4. **Log Management**
- **NMS Logs**: Direct access to application logs
- **Build Logs**: Compilation and deployment logs
- **Real-time Monitoring**: Live log tailing during builds
- **Log Filtering**: Search and filter log entries

#### 5. **Patch Management**
- **SFTP Integration**: Download patch files from servers
- **Automated Deployment**: Extract and deploy patches
- **Version Management**: Track patch versions
- **Rollback Support**: Revert to previous versions

### Workflow

```
1. Select Project
   ↓
2. Choose Build Mode & Application
   ↓
3. Configure Auto-Login (Optional)
   ↓
4. Click "Build"
   ↓
5. Monitor Progress in Build Log
   ↓
6. Application Auto-starts After Build
   ↓
7. Monitor Status & Logs
```

### Technical Components
- **Services**:
  - `ControlApp`: Application lifecycle management
  - `SetupAutoLogin`: Auto-login configuration
  - `SetupRestartTool`: Restart tool configuration
  - `DeleteLoginSetup`: Cleanup operations
  - `Validation`: Input validation
  - `WritePropertiesFile`: Configuration file management
  - `SFTPDownloadAndUnzip`: Patch download and extraction
  - `CreateInstallerCommand`: Build command generation

---

## Datastore Dump Tab

### Purpose
Parse, analyze, and visualize Oracle datastore reports for diagnosing database issues, analyzing entity relationships, and understanding data structures.

### Key Features

#### 1. **Report Generation**
- **SSH-Based Execution**: Run datastore commands on remote servers
- **Automatic Parsing**: Parse datastore output into structured data
- **Report Caching**: Cache reports for quick access
- **Progress Tracking**: Real-time progress indicators

#### 2. **Data Visualization**
- **Tabular Display**: View entities in sortable, filterable tables
- **Columns**:
  - Entity Name
  - Entity ID
  - Parent ID
  - Object ID
  - Geometry Details
  - Topology Information
  
#### 3. **Advanced Filtering**
- **Entity Search**: Filter by entity name
- **ID Filtering**: Filter by entity ID, parent ID, object ID
- **Multi-column Search**: Search across multiple attributes
- **Case-insensitive Search**: Flexible search capabilities

#### 4. **Data Export**
- **CSV Export**: Export filtered data to CSV
- **Excel Export**: Export to Excel format
- **Copy to Clipboard**: Quick data copying
- **Report Archival**: Save reports for later analysis

#### 5. **Relationship Analysis**
- **Parent-Child Relationships**: Visualize entity hierarchies
- **Topology Analysis**: Understand network topology
- **Cross-Entity Analysis**: Find relationships between entities

### Workflow

```
1. Select Project
   ↓
2. Select Datastore Path
   ↓
3. Click "Generate Report"
   ↓
4. System SSH to Server
   ↓
5. Execute Datastore Command
   ↓
6. Parse Output
   ↓
7. Display in Table
   ↓
8. Apply Filters/Search
   ↓
9. Export Results (Optional)
```

### Technical Components
- **Services**:
  - `ReportGenerator`: SSH command execution and report generation
  - `ParseDataStoreReport`: Parse datastore output
  - `ReportCacheService`: Cache management for reports
- **Models**:
  - `DataStoreRecord`: Entity data structure
  - `Geometry`: Geometric data
  - `Coordinate`: Coordinate information

### Report Format
Datastore reports include:
- Entity metadata (name, ID, parent ID)
- Object references
- Coordinate data (latitude, longitude)
- Geometry information
- Topology details
- Custom attributes

---

## JAR Decompiler Tab

### Purpose
Browse, decompile, and analyze JAR files with a VS Code-like interface. Supports both JD-Core and CFR decompilers for accurate Java source recovery.

### Key Features

#### 1. **JAR Management**
- **Directory Browser**: Select JAR directory
- **JAR Listing**: Display all JAR files in directory
- **Multi-Selection**: Select multiple JARs for batch decompilation
- **Smart Defaults**: Auto-select `nms_*.jar` files
- **Search Filter**: Filter JAR list in real-time

#### 2. **Class Explorer**
- **Hierarchical Tree**: Package-based class organization
- **Class Search**: Real-time class name filtering
- **Click-to-View**: Click any class to view decompiled code
- **Package Expansion**: Expandable package structure
- **Class Count**: Display total classes found

#### 3. **Decompilation**
- **Dual Decompilers**:
  - **JD-Core**: Fast, reliable Java decompiler
  - **CFR**: Advanced decompiler for modern Java (8-17)
- **High-Quality Output**: Accurate decompilation with comments
- **Batch Processing**: Decompile multiple JARs at once
- **Progress Tracking**: Real-time decompilation progress
- **Error Handling**: Graceful fallback on decompilation errors

#### 4. **Code Display**
- **Syntax Highlighting**: Color-coded Java syntax
- **Monospace Font**: Professional code display (Consolas)
- **Line Numbers**: Easy code navigation
- **Large Display Area**: Maximized viewing space
- **Copy to Clipboard**: One-click code copying

#### 5. **VS Code Integration**
- **Smart Detection**: Auto-detect VS Code installation
- **Cross-Platform**: Windows, macOS, Linux support
- **Direct Launch**: Open decompiled folder in VS Code
- **Path Resolution**: Find VS Code in standard locations
- **Error Handling**: Clear messages if VS Code not found

### Workflow

```
1. Select Project
   ↓
2. Browse to JAR Directory
   ↓
3. JARs Listed (nms_*.jar pre-selected)
   ↓
4. Select JARs to Decompile
   ↓
5. Click "Decompile Selected"
   ↓
6. Monitor Progress
   ↓
7. Browse Class Tree
   ↓
8. Click Class to View Code
   ↓
9. Copy Code or Open in VS Code
```

### Technical Components
- **Services**:
  - `JarDecompilerService`: Decompilation logic
    - JD-Core integration
    - CFR integration
    - File management
    - VS Code detection
- **Controllers**:
  - `EnhancedJarDecompilerController`: Enhanced UI with CFR
  - `JarDecompilerController`: Basic UI with JD-Core

### Decompilation Settings
- **CFR Options**:
  - Comments enabled
  - Lambda decompilation
  - Enum improvements
  - Assert improvements
  - Silent mode
  - Output directory

### Output Location
Decompiled files saved to:
```
<user_home>/Documents/nms_support_data/extracted/<project_name>/
```

---

## VPN Manager Tab

### Purpose
Manage Cisco AnyConnect VPN connections for secure access to development and production environments.

### Key Features

#### 1. **Connection Management**
- **Connect**: Establish VPN connection
- **Disconnect**: Terminate active VPN connection
- **Auto-Reconnect**: Automatic reconnection on disconnect
- **Status Display**: Real-time connection status

#### 2. **Profile Management**
- **Multiple Profiles**: Support for multiple VPN hosts
- **Credential Storage**: Secure username/password storage
- **Profile Switching**: Quick switching between VPN hosts
- **Recent Connections**: Remember last used VPN

#### 3. **Cisco AnyConnect Integration**
- **CLI Support**: Uses vpncli.exe command-line interface
- **Auto-Detection**: Finds AnyConnect installation automatically
- **Process Management**: Handles vpncli processes
- **Error Handling**: Graceful handling of connection failures

#### 4. **Status Monitoring**
- **Connection Status**: Connected, Disconnected, Connecting
- **Duration Tracking**: Show connection duration
- **Error Display**: Show connection errors
- **Automatic Updates**: Real-time status updates

### Workflow

```
1. Enter VPN Host
   ↓
2. Enter Username & Password
   ↓
3. Click "Connect"
   ↓
4. System Finds vpncli.exe
   ↓
5. Executes VPN Connection
   ↓
6. Monitors Connection Status
   ↓
7. Display "Connected" Status
   ↓
8. Click "Disconnect" to Terminate
```

### Technical Components
- **Controller**: `VpnController`
- **Model**: `VpnDetails`
- **Manager**: `VpnManager` (userdata)

### VPN Client Requirements
- **Windows**: Cisco AnyConnect installed in standard location
- **Path Detection**: Searches Program Files, Program Files (x86)
- **CLI Access**: vpncli.exe must be accessible

---

## Process Monitor Tab

### Purpose
Real-time monitoring of server processes, SSH command execution, and process management across multiple projects.

### Key Features

#### 1. **Process Listing**
- **Active Processes**: Display all monitored processes
- **Process Details**:
  - Process ID
  - Command
  - Status (Running, Stopped, Error)
  - Start Time
  - Duration

#### 2. **Process Control**
- **Start Process**: Launch new processes
- **Stop Process**: Terminate running processes
- **Restart Process**: Quick restart
- **Kill Process**: Force termination

#### 3. **SSH Command Execution**
- **Remote Execution**: Run commands on remote servers
- **Output Capture**: Capture stdout and stderr
- **Exit Code Tracking**: Monitor command success/failure
- **Timeout Management**: Configurable command timeouts

#### 4. **Real-time Monitoring**
- **Status Updates**: Automatic status refresh
- **Process Tracking**: Track process lifecycle
- **Resource Monitoring**: Monitor process resources
- **Alert Notifications**: Notify on process failures

### Workflow

```
1. Select Project
   ↓
2. View Active Processes
   ↓
3. Select Process
   ↓
4. Click Control Button (Start/Stop/Restart)
   ↓
5. Monitor Status Changes
   ↓
6. View Command Output
```

### Technical Components
- **Controller**: `ProcessMonitorController`
- **Services**:
  - `ProcessMonitor`: Process execution and monitoring
  - `ProcessMonitorManager`: Global process management
  - `ProcessMonitorAdapter`: Process lifecycle adapter
- **Model**: `LogEntity`, `LogWrapper`

---

## Project Management

### Purpose
Centralized management of all project configurations, credentials, and settings.

### Key Features

#### 1. **Project Configuration**
- **Project Details**:
  - Project Name
  - Host Address
  - SSH Port
  - Database Connection
  - Authentication Method

#### 2. **Authentication Settings**
- **LDAP Authentication**:
  - LDAP Username
  - LDAP Password
  - Target User (for sudo)
  - Sudo Password
- **Basic Authentication**:
  - Host Username
  - Host Password

#### 3. **Database Configuration**
- **Oracle Database**:
  - RDBMS Host
  - Oracle User (Read-Only/Read-Write)
  - Oracle Service Name (SID)
  - TNS Names
  - Connection Strings

#### 4. **Build Configuration**
- **Build Settings**:
  - Build File Location
  - Application Names
  - Build Modes
  - Output Directories

#### 5. **Project Import/Export**
- **Import Projects**: Load from JSON
- **Export Projects**: Save to JSON
- **Bulk Operations**: Import/export multiple projects
- **Validation**: Validate project configurations

### Project Entity Structure
```java
ProjectEntity {
    - id: Long
    - projectName: String
    - host: String
    - port: Integer
    - useLdap: Boolean
    - ldapUser: String
    - ldapPassword: String
    - targetUser: String
    - sudoPassword: String
    - hostUser: String
    - hostPass: String
    - rdbmsHost: String
    - oracleUser: String
    - oracleSid: String
    - buildFilePath: String
    - // ... more fields
}
```

### Technical Components
- **Controller**: `ProjectDetailsController`
- **Services**:
  - `ProjectManager`: Project data management
  - `ServerProjectService`: Server-side project operations
  - `ProjectImportDialog`: Import UI
  - `EditProjectNameDialog`: Edit UI
  - `AddProjectDialog`: Create UI
- **Model**: `ProjectEntity`, `ProjectWrapper`

---

## SSH Connection Management

### Purpose
Unified SSH connection management with persistent sessions, automatic caching, and support for both LDAP and basic authentication.

### Architecture

#### 1. **UnifiedSSHService**
Central service for all SSH operations:
- Creates SSH sessions with unified auth logic
- Executes single or multiple commands
- Manages session lifecycle
- Validates authentication configuration

#### 2. **SSHSessionManager (JSch-based)**
Legacy SSH implementation:
- JSch library integration
- Basic SSH operations
- Session management
- Command execution

#### 3. **SSHJSessionManager (SSHJ-based)**
Modern SSH implementation:
- SSHJ library (actively maintained)
- Better reliability
- Modern SSH protocol support
- Improved performance

#### 4. **Session Caching**
Intelligent session caching for performance:
- **Cache Key**: `host:port:sshUser:targetUser:purpose`
- **Timeout**: 30 minutes of inactivity
- **Thread-Safe**: ConcurrentHashMap
- **Automatic Cleanup**: Expired session removal
- **Purpose Isolation**: Separate sessions for different operations

### Key Features

#### 1. **Authentication Methods**
- **LDAP Authentication**:
  ```
  1. Connect as LDAP user
  2. Execute: sudo su - <targetUser>
  3. Handle password prompt
  4. Verify user switch
  5. Maintain elevated shell
  ```

- **Basic Authentication**:
  ```
  1. Connect as host user
  2. No sudo switching
  3. Direct command execution
  ```

#### 2. **Persistent Sessions**
- **Pre-Sudoed Shells**: Sudo once, reuse for multiple commands
- **Marker-Based Completion**: Unique markers for command tracking
- **Concurrent I/O**: Non-blocking stdout/stderr capture
- **Timeout & Interruption**: Automatic command timeout with Ctrl+C
- **Heartbeat Checks**: Validate session liveness

#### 3. **Command Execution**
```java
// Simple command
CommandResult result = UnifiedSSHService.executeCommand(project, "ls -la", 30);

// With progress callback
CommandResult result = UnifiedSSHService.executeCommand(
    project, 
    "long_command", 
    300, 
    progressCallback
);

// Persistent session for multiple commands
SSHSessionManager ssh = UnifiedSSHService.createSSHSession(project);
try {
    ssh.initialize();
    ssh.executeCommand("cmd1", 30);
    ssh.executeCommand("cmd2", 30);
    // Don't close - let it cache for 30 minutes!
} catch (Exception e) {
    ssh.cancelCommand(); // Remove from cache on error
}
```

#### 4. **Session Caching Benefits**
- **Performance**:
  - First command: ~10-15 seconds (LDAP with sudo)
  - Cached commands: ~1-2 seconds (85-90% faster!)
- **Resource Efficiency**: Reuse connections
- **Automatic Expiration**: 30-minute timeout
- **Purpose Isolation**: Different operations don't interfere

### Cache Lifecycle

```
Session Creation:
1. Check cache with key
2. If exists and alive → Reuse
3. If not exists or dead → Create new
4. Store in cache
5. Update last activity time

Session Expiration:
1. Automatic cleanup every 30 minutes
2. Heartbeat validation before reuse
3. Recreate if dead

Session Termination:
1. User cancellation → Remove from cache
2. Application shutdown → Close all sessions
3. Error/timeout → Remove from cache
```

### Best Practices

#### ✅ DO:
- Use persistent sessions for repeated operations (DB imports, file transfers)
- Let the cache handle session lifecycle
- Use different purposes for different operation types
- Close sessions on user cancellation or errors

#### ❌ DON'T:
- Close persistent sessions after each use (breaks caching)
- Use generic purposes for everything (causes interference)
- Create new sessions for every command
- Forget to handle errors and cleanup

### Technical Components
- **Services**:
  - `UnifiedSSHService`: Unified SSH interface
  - `SSHSessionManager`: JSch-based implementation
  - `SSHJSessionManager`: SSHJ-based implementation
  - `UnifiedSSHManager`: Session cache manager
  - `PersistentSudoSession`: Pre-sudoed shell wrapper
  - `SSHExecutor`: Legacy SSH executor
  - `SSHCommandResult`: Result container

### SSH Libraries
- **JSch**: Legacy library (stable, well-tested)
- **SSHJ**: Modern library (active development, better features)

---

## Database Integration

### Purpose
Direct Oracle database connections for storing project configurations, user types, and application metadata.

### Key Features

#### 1. **Database Services**
- **DatabaseService**: Core database operations
  - Connection management
  - Query execution
  - Transaction handling
  - Connection pooling

- **DatabaseUserTypeService**: User type management
  - Fetch user types
  - Store user configurations
  - Manage permissions

- **FireData**: Firebase integration (if enabled)
  - Data synchronization
  - Cloud storage
  - Real-time updates

#### 2. **Data Management**
- **Project Storage**: Store project configurations
- **User Types**: Store application user types
- **Build Configurations**: Store build settings
- **Logs**: Store operation logs

#### 3. **Connection Management**
- **JDBC Integration**: Oracle JDBC driver
- **Connection Pooling**: Efficient connection reuse
- **Credential Security**: Encrypted credential storage
- **Error Handling**: Robust error recovery

### Database Schema
Projects are stored with:
- Project metadata
- SSH credentials
- Database credentials
- Build configurations
- Application settings

### Technical Components
- **Services**:
  - `DatabaseService`
  - `DatabaseUserTypeService`
  - `FireData`
- **Models**: Project and configuration entities

---

## Technical Architecture

### Application Structure

```
com.nms.support.nms_support/
├── controller/                    # UI Controllers
│   ├── BuildAutomation
│   ├── DatastoreDumpController
│   ├── EnhancedJarDecompilerController
│   ├── JarDecompilerController
│   ├── ProcessMonitorController
│   ├── ProjectDetailsController
│   ├── VpnController
│   ├── EntityCardController
│   └── MainController
│
├── service/                       # Business Logic
│   ├── buildTabPack/              # Build-related services
│   │   ├── ControlApp
│   │   ├── SetupAutoLogin
│   │   ├── SetupRestartTool
│   │   ├── DeleteLoginSetup
│   │   ├── DeleteRestartSetup
│   │   ├── WritePropertiesFile
│   │   ├── Validation
│   │   ├── LoadUserTypes
│   │   └── patchUpdate/
│   │       ├── SFTPDownloadAndUnzip
│   │       ├── CreateInstallerCommand
│   │       ├── DirectoryProcessor
│   │       ├── FileFetcher
│   │       ├── ExecHelper
│   │       └── Utils
│   │
│   ├── dataStoreTabPack/          # Datastore services
│   │   ├── ReportGenerator
│   │   ├── ParseDataStoreReport
│   │   └── ReportCacheService
│   │
│   ├── globalPack/                # Global services
│   │   ├── SSH Management:
│   │   │   ├── UnifiedSSHService
│   │   │   ├── SSHSessionManager
│   │   │   ├── SSHExecutor
│   │   │   └── sshj/
│   │   │       ├── SSHJSessionManager
│   │   │       ├── UnifiedSSHManager
│   │   │       ├── PersistentSudoSession
│   │   │       ├── SSHCommandResult
│   │   │       └── SSHJExample
│   │   │
│   │   ├── Dialog Services:
│   │   │   ├── DialogUtil
│   │   │   ├── AddProjectDialog
│   │   │   ├── EditProjectNameDialog
│   │   │   ├── ProjectImportDialog
│   │   │   ├── SaveConfirmationDialog
│   │   │   ├── SetupModeDialog
│   │   │   ├── ProcessSelectionDialog
│   │   │   ├── EnvironmentVariableDialog
│   │   │   ├── EnhancedEnvironmentVariableDialog
│   │   │   ├── ProgressDialog
│   │   │   ├── DialogProgressOverlay
│   │   │   └── SVN Dialogs:
│   │   │       ├── SVNAuthenticationDialog
│   │   │       └── SVNBrowserDialog
│   │   │
│   │   ├── Utilities:
│   │   │   ├── LoggerUtil
│   │   │   ├── ManageFile
│   │   │   ├── Checker
│   │   │   ├── IconUtils
│   │   │   ├── Mappings
│   │   │   ├── AppDetails
│   │   │   └── ProgressTracker
│   │   │
│   │   ├── Process Management:
│   │   │   ├── ProcessMonitor
│   │   │   ├── ProcessMonitorManager
│   │   │   └── ProcessMonitorAdapter
│   │   │
│   │   ├── Project Services:
│   │   │   ├── ServerProjectService
│   │   │   ├── SetupService
│   │   │   └── BuildFileParser
│   │   │
│   │   └── Other:
│   │       ├── JarDecompilerService
│   │       ├── SVNAutomationTool
│   │       ├── ProgressCallback
│   │       └── ChangeTrackingService
│   │
│   ├── database/                  # Database services
│   │   ├── DatabaseService
│   │   ├── DatabaseUserTypeService
│   │   └── FireData
│   │
│   ├── userdata/                  # User data managers
│   │   ├── IManager
│   │   ├── ProjectManager
│   │   ├── VpnManager
│   │   ├── LogManager
│   │   ├── Parser
│   │   └── ParserMp
│   │
│   └── ProjectCodeService         # Project-level services
│
├── model/                         # Data models
│   ├── ProjectEntity
│   ├── ProjectWrapper
│   ├── DataStoreRecord
│   ├── LogEntity
│   ├── LogWrapper
│   ├── VpnDetails
│   ├── Entity
│   ├── Coordinate
│   ├── Geometry
│   └── Diagram
│
├── util/                          # Utilities
│   ├── JarExplorer
│   └── ProjectPathUtil
│
├── component/                     # Custom components
│
├── NMSSupportApplication          # JavaFX Application
└── FakeMain                       # Entry point
```

### Technology Stack

#### Frontend
- **JavaFX 23.0.1**: UI framework
- **FXML**: UI layouts
- **CSS**: Styling
- **ControlsFX 11.1.2**: Enhanced controls
- **FormsFX 11.6.0**: Form validation
- **ValidatorFX 0.4.0**: Input validation
- **Ikonli 12.3.1**: Icon library (FontAwesome)
- **BootstrapFX 0.4.0**: Bootstrap-like styling
- **TilesFX 11.48**: Dashboard tiles

#### Backend
- **Java 22**: Programming language
- **Maven**: Build tool
- **JSch 0.1.55**: SSH library (legacy)
- **SSHJ 0.40.0**: SSH library (modern)
- **Oracle JDBC 23.3.0**: Database driver
- **Jackson 2.14.2**: JSON processing
- **Log4j 3.0.0**: Logging
- **SLF4J 2.0.0**: Logging facade

#### Decompilers
- **JD-Core 1.1.3**: Java decompiler
- **JD-GUI 1.6.6**: Decompiler UI components
- **CFR 0.152** (if enabled): Advanced decompiler

#### Security
- **Bouncy Castle 1.80**: Cryptography
- **ASN.1 0.6.0**: Certificate handling

#### Version Control
- **SVNKit 1.10.3**: Subversion integration
- **Trilead SSH2**: SVN SSH support

#### Other
- **Ant**: Build automation (project builds)
- **jpackage**: Application packaging
- **jlink**: Custom JRE creation

### Build Process

#### Maven Build
```bash
mvn clean install
```

Phases:
1. **Compile**: Compile Java sources
2. **Process Resources**: Copy resources
3. **Prepare Package**: Unpack JD-Core libraries (if used)
4. **Package**: Create main JAR
5. **Copy Dependencies**: Copy all dependencies to `target/libs`
6. **Shade**: Create uber JAR with all dependencies

#### Windows EXE Build
```bash
build_app_windows.bat
```

Steps:
1. **Module Detection**: Use jdeps to detect required modules
2. **JLink**: Create custom Java runtime
3. **Resource Copying**: Copy JARs to installer input directory
4. **JPackage**: Create Windows EXE installer
   - Type: EXE installer
   - Custom icon
   - Windows shortcuts
   - Per-user installation
   - Directory chooser

### Configuration Files

#### pom.xml
Maven project configuration:
- Dependencies
- Build plugins
- Assembly configuration
- Resource management

#### build_app_windows.bat
Windows build script:
- Environment setup
- Module detection
- Runtime creation
- Installer generation

#### MAIN_MENUBAR_BUTTONS.inc
Menu bar button configuration

---

## Build and Deployment

### Development Environment Setup

#### Prerequisites
- **Java 22**: JDK with JavaFX
- **Maven**: Build tool
- **Oracle JDBC**: Database connectivity
- **Cisco AnyConnect**: VPN (for VPN features)
- **VS Code**: For decompiler integration (optional)

#### Environment Variables
- `JAVA22_HOME`: Path to Java 22 installation
- `APP_VERSION`: Application version for builds

### Building from Source

#### 1. **Clone Repository**
```bash
git clone <repository_url>
cd nms_support
```

#### 2. **Build with Maven**
```bash
mvn clean install
```

Output: `target/nms-support-main.jar`

#### 3. **Run from IDE**
- Open in IntelliJ IDEA or Eclipse
- Run `FakeMain.java`

#### 4. **Create Windows Installer**
```bash
# Set environment variables
set APP_VERSION=3.0.0
set JAVA22_HOME=C:\Program Files\Java\jdk-22

# Run build script
build_app_windows.bat
```

Output: `target/installer/NMS DevTools-3.0.0.exe`

### Deployment

#### Windows Installation
1. Run the EXE installer
2. Choose installation directory
3. Installer creates:
   - Application files
   - Desktop shortcut
   - Start menu entry
4. Launch from desktop or start menu

#### Configuration
On first launch:
1. Configure database connection (if using)
2. Add projects (Import or Add New)
3. Configure authentication (LDAP or Basic)
4. Test connections

### Troubleshooting

#### Common Issues

**1. JAR Decompiler Not Loading**
- Ensure JD-Core libraries are included in build
- Check `target/libs` for `jd-core-*.jar` and `jd-gui-*.jar`
- Verify Maven shade plugin unpacked libraries

**2. SSH Connection Failures**
- Verify host, port, credentials
- Check network connectivity
- Test with manual SSH client
- Review authentication method (LDAP vs Basic)

**3. Database Connection Issues**
- Verify Oracle database is accessible
- Check JDBC connection string
- Validate credentials
- Ensure Oracle JDBC driver is included

**4. Build Failures**
- Check Java version (must be Java 22)
- Verify Maven is configured correctly
- Check dependency availability
- Review build logs for errors

**5. VPN Connection Failures**
- Ensure Cisco AnyConnect is installed
- Verify vpncli.exe path
- Check VPN credentials
- Review firewall settings

### Performance Optimization

#### SSH Session Caching
- Sessions are cached for 30 minutes
- Reuse sessions for repeated operations
- Don't close sessions after each use
- Use different purposes for different operation types

#### Database Connections
- Connection pooling is enabled
- Reuse database connections
- Close connections properly
- Monitor connection count

#### Memory Management
- Regular cleanup of expired sessions
- Cache size monitoring
- Resource cleanup on shutdown

---

## Appendix

### SSH Cache Decision Tree

**When to Close SSH Sessions?**

| Scenario | Close Session? | Reason |
|----------|---------------|--------|
| One-time command | ✅ Yes | No reuse expected |
| DB Import | ❌ No | User may repeat operation |
| File upload | ❌ No | May upload multiple files |
| Build process | ❌ No | Multiple steps, one session |
| User cancels | ✅ Yes | Bad state, force cleanup |
| 30min idle | ✅ Yes | Auto-expire, free resources |
| App closes | ✅ Yes | Release all resources |
| Connection error | ✅ Yes | Cleanup, retry fresh |

### Decompiler Comparison

| Feature | JD-Core | CFR |
|---------|---------|-----|
| Speed | Fast | Medium |
| Accuracy | Good | Excellent |
| Java 8 Support | ✅ Yes | ✅ Yes |
| Java 17 Support | ⚠️ Limited | ✅ Full |
| Lambda Support | ✅ Yes | ✅ Better |
| Comments | ⚠️ Basic | ✅ Detailed |

### Key Shortcuts

| Action | Shortcut |
|--------|----------|
| Save All | Ctrl+S |
| Refresh | F5 |
| Search | Ctrl+F |
| Copy | Ctrl+C |
| Paste | Ctrl+V |

---

## Version History

### Version 3.0.0
- Enhanced JAR Decompiler with CFR support
- Improved SSH session caching
- Better LDAP authentication handling
- Comprehensive documentation
- Build fixes for EXE installer

### Version 2.x
- SSHJ integration
- Persistent SSH sessions
- Datastore dump analysis
- Process monitoring

### Version 1.x
- Initial release
- Build automation
- Basic SSH integration
- VPN management

---

## Support and Contact

For issues, questions, or feature requests:
- Review logs in application
- Check SSH cache statistics
- Enable fine logging for diagnostics
- Refer to source code documentation

---

**End of Documentation**

*This documentation covers all features and functionalities of the NMS DevTools application.*

