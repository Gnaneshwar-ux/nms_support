# Enhanced Jar Decompiler Tab - VS Code-like Experience

## 🚀 Overview

This implementation provides a polished, professional JAR decompiler tab with a VS Code-like interface, featuring accurate CFR-based decompilation, hierarchical class browsing, and modern UI design.

## ✨ Key Features

### 🎨 **VS Code-like UI Design**
- **Clean Toolbar**: Modern button styling with FontAwesome icons
- **Split Panel Layout**: Resizable left panel for JAR/class browsing, right panel for code
- **Status Bar**: Real-time progress and status updates with icons
- **Professional Styling**: GitHub-inspired color scheme and typography

### 🔍 **Advanced JAR & Class Management**
- **JAR Browser**: List all JARs with checkboxes for multi-selection
- **Smart Defaults**: Pre-selects `nms_*.jar` files automatically
- **Hierarchical Class Tree**: Package-based class organization
- **Dual Search**: Separate search for JARs and classes
- **Real-time Filtering**: Dynamic search as you type

### ⚡ **Accurate CFR Decompilation**
- **High-Quality Output**: Uses CFR decompiler for accurate Java 8-17 support
- **Advanced Options**: Configured for best decompilation quality
- **Background Processing**: Non-blocking UI with progress updates
- **Error Handling**: Graceful fallback with detailed error messages

### 🎯 **Enhanced Code Display**
- **Monospace Font**: Professional code display with Consolas font
- **Line Numbers**: Easy navigation and reference
- **Copy to Clipboard**: One-click code copying
- **Large Display Area**: Maximized code viewing space

### 🔧 **VS Code Integration**
- **Smart Detection**: Automatically finds VS Code installation
- **Cross-Platform**: Supports Windows, macOS, and Linux
- **Direct Launch**: Opens extracted folder in VS Code
- **Error Handling**: Clear messages if VS Code not found

## 📁 File Structure

```
src/main/java/com/nms/support/nms_support/
├── controller/
│   └── EnhancedJarDecompilerController.java    # Enhanced UI controller
├── service/globalPack/
│   └── JarDecompilerService.java               # CFR-based decompilation service
└── resources/com/nms/support/nms_support/
    ├── view/tabs/
    │   └── jar-decompiler.fxml                 # VS Code-like FXML layout
    └── styles/light/tabs/
        └── jar-decompiler.css                  # Professional styling
```

## 🛠️ Technical Implementation

### **Dependencies Added**
```xml
<!-- CFR Java Decompiler -->
<dependency>
    <groupId>org.benf</groupId>
    <artifactId>cfr</artifactId>
    <version>0.152</version>
</dependency>

<!-- RichTextFX for syntax highlighting -->
<dependency>
    <groupId>org.fxmisc.richtext</groupId>
    <artifactId>richtextfx</artifactId>
    <version>0.11.0</version>
</dependency>

<!-- Ikonli for icons -->
<dependency>
    <groupId>org.kordamp.ikonli</groupId>
    <artifactId>ikonli-javafx</artifactId>
    <version>12.3.1</version>
</dependency>
```

### **CFR Decompilation Configuration**
```java
String[] cfrArgs = {
    jarFilePath,
    "--outputdir", jarOutputDir,
    "--silent", "true",
    "--comments", "true",
    "--decodelambdas", "true",
    "--sugarenums", "true",
    "--sugarasserts", "true",
    // ... many more options for high-quality output
};
```

### **VS Code Detection**
- **Windows**: Checks `LOCALAPPDATA`, `PROGRAMFILES`, `PROGRAMFILES(X86)`
- **macOS**: Checks `/Applications` and user Applications folder
- **Linux**: Checks `/usr/bin`, `/usr/local/bin`, `~/.local/bin`

## 🎯 Usage Workflow

### **1. Setup**
1. Select a project from the dropdown
2. Click "Browse" to select JAR directory
3. JARs are automatically listed with `nms_*.jar` pre-selected

### **2. Explore Classes**
1. Select JARs using checkboxes
2. Class tree automatically updates
3. Use search to filter classes
4. Click on classes to view decompiled code

### **3. Decompile**
1. Select desired JARs
2. Click "Decompile Selected"
3. Monitor progress in status bar
4. Files saved to `<user_home>/Documents/nms_support_data/extracted/<project_name>/`

### **4. View Code**
1. Click on any class in the tree
2. Decompiled code appears in right panel
3. Use "Copy" button to copy code to clipboard
4. Click "Open in VS Code" to open extracted folder

## 🎨 UI Components

### **Top Toolbar**
- **JAR Path Field**: Shows selected directory
- **Browse Button**: Folder icon for directory selection
- **Decompile Button**: Play icon for starting decompilation
- **Open VS Button**: Code icon for VS Code integration

### **Left Panel - Explorer**
- **JAR Files Section**: 
  - Search field for filtering JARs
  - List with checkboxes for selection
  - Count display showing total JARs
- **Classes Section**:
  - Search field for filtering classes
  - Hierarchical tree view
  - Count display showing total classes

### **Right Panel - Code Viewer**
- **Code Header**: Shows current class name
- **Copy Button**: Copy code to clipboard
- **Code Area**: Large text area with monospace font

### **Status Bar**
- **Status Icon**: Visual indicator (info, success, error, etc.)
- **Status Text**: Current operation description
- **Progress Bar**: Shows decompilation progress
- **Progress Label**: Percentage display

## 🔧 Advanced Features

### **Smart JAR Selection**
- Automatically selects JARs starting with `nms_`
- Maintains selection state during operations
- Visual feedback for selected items

### **Hierarchical Class Tree**
- Package-based organization
- Expandable/collapsible structure
- Classes sorted alphabetically
- Packages shown before classes

### **Real-time Search**
- **JAR Search**: Filters JAR list as you type
- **Class Search**: Filters class tree dynamically
- Case-insensitive matching
- Instant results

### **Progress Tracking**
- Background decompilation with progress updates
- Status messages for each operation
- Error handling with user-friendly messages
- Non-blocking UI during long operations

### **VS Code Integration**
- Automatic VS Code detection
- Cross-platform path resolution
- Direct folder opening
- Error handling for missing VS Code

## 🎯 Benefits

### **For Developers**
- **Professional Interface**: VS Code-like experience
- **Accurate Decompilation**: High-quality CFR output
- **Efficient Workflow**: Multi-select and batch operations
- **Easy Navigation**: Hierarchical class browsing

### **For Projects**
- **Consistent Styling**: Matches existing application theme
- **Robust Error Handling**: Graceful failure recovery
- **Performance Optimized**: Background processing
- **Cross-Platform**: Works on Windows, macOS, Linux

### **For Maintenance**
- **Modular Design**: Separate service and controller
- **Extensible**: Easy to add new features
- **Well-Documented**: Comprehensive code comments
- **Error Logging**: Detailed logging for debugging

## 🚀 Future Enhancements

### **Planned Features**
- **Syntax Highlighting**: RichTextFX integration
- **Multiple Decompilers**: CFR, FernFlower, Procyon options
- **Export Options**: Save individual classes
- **Batch Operations**: Decompile multiple projects
- **Settings Panel**: Configure decompiler options

### **Potential Improvements**
- **Dark Theme**: VS Code dark mode styling
- **Code Folding**: Collapsible code sections
- **Search in Code**: Find text within decompiled code
- **Class Dependencies**: Show class relationships
- **Method Signatures**: Quick method overview

## 📋 Requirements Met

✅ **VS Code-like UI**: Clean, professional interface  
✅ **CFR Decompilation**: Accurate Java 8-17 support  
✅ **Hierarchical Browsing**: Package-based class tree  
✅ **Dual Search**: JAR and class filtering  
✅ **VS Code Integration**: Smart detection and launch  
✅ **Progress Tracking**: Real-time status updates  
✅ **Error Handling**: Graceful failure recovery  
✅ **Modern Styling**: GitHub-inspired design  
✅ **Icons & Tooltips**: Professional visual feedback  
✅ **Background Processing**: Non-blocking operations  

## 🎉 Ready to Use!

The enhanced Jar Decompiler tab is fully implemented and ready for production use. It provides a professional, VS Code-like experience for JAR decompilation with accurate CFR-based output and modern UI design.

**Key Files:**
- `EnhancedJarDecompilerController.java` - Main UI controller
- `JarDecompilerService.java` - CFR decompilation service
- `jar-decompiler.fxml` - VS Code-like layout
- `jar-decompiler.css` - Professional styling

The implementation is complete, tested, and ready for integration! 🚀
