# Jar Decompiler Tab Implementation

## Overview
This implementation adds a new "Jar Decompiler" tab to the NMS Support JavaFX desktop application, allowing users to browse, select, and decompile JAR files.

## Features Implemented

### UI Components
- **Jar Decompiler Tab**: New tab in the main application
- **Top Toolbar**: 
  - JAR Path text field with Browse button
  - Decompile button
  - Open VS button (opens extracted folder in VS Code)
- **Search Field**: Filter classes by name
- **Left Panel**: 
  - List of JAR files with checkboxes for selection
  - List of classes in selected JAR
- **Right Panel**: 
  - Large text area displaying decompiled Java code
- **Status Bar**: Shows current operation status and progress

### Backend Services
- **JarDecompilerService**: Handles JAR decompilation operations
- **JarDecompilerController**: Manages UI interactions and data binding

## File Structure

```
src/main/java/com/nms/support/nms_support/
├── controller/
│   └── JarDecompilerController.java          # UI controller
├── service/globalPack/
│   └── JarDecompilerService.java             # Backend service
└── resources/com/nms/support/nms_support/
    ├── view/tabs/
    │   └── jar-decompiler.fxml               # FXML layout
    └── styles/light/tabs/
        └── jar-decompiler.css                # Styling
```

## Key Features

### 1. JAR File Management
- Browse and select directory containing JAR files
- Display JAR files in a list with checkboxes
- Pre-select JAR files starting with `nms_*.jar`
- Support for multiple JAR selection

### 2. Class Exploration
- List all classes in selected JAR
- Search/filter classes by name
- Click to view decompiled code

### 3. Decompilation Process
- Decompile selected JAR files to temporary directory
- Store results in `<user_home>/Documents/nms_support_data/extracted/<project_name>/`
- Preserve package structure
- Show progress during decompilation

### 4. Code Display
- Display decompiled Java code in syntax-highlighted text area
- Monospace font for better code readability
- Support for large files with scrolling

### 5. VS Code Integration
- Open extracted folder directly in VS Code
- Uses `code <folder>` command

## Current Implementation Status

### ✅ Completed
- Complete UI layout and styling
- JAR file browsing and selection
- Class listing and filtering
- Basic decompilation structure
- VS Code integration
- Progress tracking
- Error handling

### 🔄 Placeholder Implementation
- **Decompilation**: Currently creates placeholder Java files instead of actual decompiled code
- **Syntax Highlighting**: Uses basic TextArea instead of RichTextFX

### 🚀 Future Enhancements
- Integrate actual Java decompiler (CFR, FernFlower, or Procyon)
- Add RichTextFX for syntax highlighting
- Support for different decompiler options
- Export functionality
- Batch operations

## Usage

1. **Select Project**: Choose a project from the dropdown
2. **Browse JARs**: Click "Browse" to select directory containing JAR files
3. **Select JARs**: Check the JAR files you want to decompile
4. **Decompile**: Click "Decompile" to start the process
5. **View Code**: Click on classes in the left panel to view decompiled code
6. **Open in VS Code**: Click "Open VS" to open the extracted folder

## Integration

The tab is fully integrated into the main application:
- Added to `main-view.fxml`
- Integrated into `MainController.java`
- Follows existing styling patterns
- Respects project selection state
- Includes proper error handling and logging

## Dependencies

Currently uses only standard JavaFX components. Future decompiler integration will require:
- CFR, FernFlower, or Procyon decompiler libraries
- RichTextFX for syntax highlighting (optional)

## Notes

- The current implementation creates placeholder Java files for demonstration
- To enable actual decompilation, integrate a proper Java decompiler library
- The UI is fully functional and ready for real decompilation backend
- All error handling and user feedback mechanisms are in place
