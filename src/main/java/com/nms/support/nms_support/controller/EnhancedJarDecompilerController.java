package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.service.globalPack.JarDecompilerService;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ChangeTrackingService;
import com.nms.support.nms_support.service.globalPack.IconUtils;
import com.nms.support.nms_support.model.ProjectEntity;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Enhanced Controller for the Jar Decompiler tab with VS Code-like features
 */
public class EnhancedJarDecompilerController implements Initializable {
    
    private static final Logger logger = LoggerUtil.getLogger();
    
    // FXML Controls
    @FXML private TextField jarPathField;
    @FXML private Button browseButton;
    @FXML private Button decompileButton;
    @FXML private Button openVsButton;
    @FXML private TextField jarSearchField;
    @FXML private TextField classSearchField;
    @FXML private ListView<String> jarListView;
    @FXML private ListView<String> classListView;
    @FXML private CodeArea codeArea;
    @FXML private ScrollPane codeScrollPane;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private FontIcon statusIcon;
    @FXML private Label progressLabel;
    @FXML private Label jarCountLabel;
    @FXML private Label classCountLabel;
    @FXML private Label currentClassLabel;
    @FXML private Label decompilationInfoLabel;
    @FXML private Button copyCodeButton;
    @FXML private StackPane loadingOverlay;
    @FXML private VBox mainContent;
    
    // Data
    private ObservableList<String> jarFiles = FXCollections.observableArrayList();
    private FilteredList<String> filteredJarFiles;
    private SortedList<String> sortedJarFiles;
    private ObservableList<String> classFiles = FXCollections.observableArrayList();
    private FilteredList<String> filteredClassFiles;
    private SortedList<String> sortedClassFiles;
    
    // Search terms for sorting
    private String currentJarSearchTerm = "";
    private String currentClassSearchTerm = "";
    private Map<String, Boolean> jarSelectionMap = new HashMap<>();
    private Map<String, JarDecompilerService.ClassTreeNode> jarClassTrees = new HashMap<>();
    private String currentJarPath = "";
    private String currentProjectName = "";
    private JarDecompilerService decompilerService;
    // Existing decompiled data
    private List<String> existingClasses = new ArrayList<>();
    private List<String> existingJars = new ArrayList<>();
    
    // Main controller reference
    private MainController mainController;
    private ChangeTrackingService changeTrackingService;
    
    // Background loading control
    private volatile String loadingProjectName = null;
    
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing Enhanced Jar Decompiler Controller");
        
        // Initialize UI components
        initializeUI();
        setupEventHandlers();
        setupCodeArea();
        
        // Initialize service
        decompilerService = new JarDecompilerService();
        changeTrackingService = ChangeTrackingService.getInstance();
        setupServiceHandlers();
        
        // Register with change tracking service
        registerWithChangeTracking();
    }
    
    /**
     * Register controls with change tracking service
     */
    private void registerWithChangeTracking() {
        Set<Control> controls = new HashSet<>();
        controls.add(jarPathField);
        changeTrackingService.registerTab("Jar Decompiler", controls);
    }
    
    /**
     * Set the main controller reference
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Initialize UI components
     */
    private void initializeUI() {
        // Setup jar list view with checkboxes
        jarListView.setCellFactory(CheckBoxListCell.forListView(item -> {
            if (!jarSelectionMap.containsKey(item)) {
                // Default selection for nms_*.jar files
                boolean selected = item.startsWith("nms_") && item.endsWith(".jar");
                jarSelectionMap.put(item, selected);
            }
            // Create a simple property for the checkbox
            javafx.beans.property.BooleanProperty property = new javafx.beans.property.SimpleBooleanProperty(jarSelectionMap.get(item));
            property.addListener((observable, oldValue, newValue) -> {
                jarSelectionMap.put(item, newValue);
                updateDecompileButtonState();
                updateClassTree();
                // Note: JAR selection is saved only after successful decompilation
            });
            return property;
        }));
        
        // Setup filtered and sorted lists for JAR search
        filteredJarFiles = new FilteredList<>(jarFiles);
        sortedJarFiles = new SortedList<>(filteredJarFiles);
        jarListView.setItems(sortedJarFiles);
        
        // Setup filtered and sorted lists for class search
        filteredClassFiles = new FilteredList<>(classFiles);
        sortedClassFiles = new SortedList<>(filteredClassFiles);
        classListView.setItems(sortedClassFiles);
        
        // Setup tooltip for class list items
        classListView.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    // Show only class name
                    String className = item.substring(item.lastIndexOf('.') + 1);
                    setText(className);
                    
                    // Set tooltip with full package path
                    Tooltip tooltip = new Tooltip(item);
                    setTooltip(tooltip);
                }
            }
        });
        
        // Setup buttons
        decompileButton.setDisable(true);
        openVsButton.setDisable(true);
        copyCodeButton.setVisible(false);
        
        // Setup progress bar
        progressBar.setVisible(false);
        progressLabel.setVisible(false);
        
        // Setup status
        updateStatus("Ready", "fa-info-circle");
        updateCounts();
        
        // Initialize decompilation info label
        updateDecompilationInfo();
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Browse button
        browseButton.setOnAction(event -> browseForJarDirectory());
        
        // Decompile button
        decompileButton.setOnAction(event -> startDecompilation());
        
        // Open VS button
        openVsButton.setOnAction(event -> openInVSCode());
        
        // Copy code button
        copyCodeButton.setOnAction(event -> copyCodeToClipboard());
        
        // Test search button (for debugging)
        // You can add this button to test the search functionality
        // Button testSearchButton = new Button("Test Search");
        // testSearchButton.setOnAction(event -> testSearchInUI());
        
        
        // JAR search field with simple substring search (case insensitive)
        jarSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterJarListSimple(newValue);
        });
        
        // Add tooltip and placeholder for simple search functionality
        Tooltip jarSearchTooltip = new Tooltip("Search JAR files (case insensitive substring search)\nSearches for JAR files containing the search term\n• nms - finds all JAR files containing 'nms'\n• common - finds all JAR files containing 'common'");
        jarSearchField.setTooltip(jarSearchTooltip);
        jarSearchField.setPromptText("Search JAR files... (case insensitive)");
        
        // JAR path field - make it editable and set default
        jarPathField.setOnAction(event -> {
            String path = jarPathField.getText().trim();
            if (!path.isEmpty() && new File(path).exists()) {
                loadJarFiles(path);
            }
        });
        
        // JAR path field text change listener
        jarPathField.textProperty().addListener((observable, oldValue, newValue) -> {
            String path = newValue != null ? newValue.trim() : "";
            if (!path.isEmpty() && new File(path).exists()) {
                loadJarFiles(path);
            } else {
                // Clear JAR list if path is invalid
                jarFiles.clear();
                jarListView.getItems().clear();
                updateCounts();
            }
        });
        
        // Set default JAR path based on project
        setDefaultJarPath();
        
        // Class search field with prioritized search (exact match first, then substring, then regex)
        classSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterClassListWithPriority(newValue);
        });
        
        // Add tooltip and placeholder for prioritized search functionality
        Tooltip classSearchTooltip = new Tooltip("Search class names only (prioritized results)\nSearches only the class name part (after last dot)\n1. Exact matches first\n2. Substring matches second\n3. Wildcard/regex matches third\n\nUse * for wildcards:\n• Test* - classes starting with 'Test'\n• *Service - classes ending with 'Service'\n• *Manager* - classes containing 'Manager'\n• Test*Service - classes starting with 'Test' and ending with 'Service'");
        classSearchField.setTooltip(classSearchTooltip);
        classSearchField.setPromptText("Search class names only... (prioritized results)");
        
        // JAR list selection
        jarListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadClassTreeForJar(newValue);
            }
        });
        
        // Class list selection
        classListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadDecompiledCode(newValue);
            }
        });
    }
    
    /**
     * Setup code area with line numbers and syntax highlighting
     */
    private void setupCodeArea() {
        // Configure ScrollPane for simple scrolling behavior
        codeScrollPane.setFitToWidth(true); // Fit to width for wrapped text
        codeScrollPane.setFitToHeight(true); // Fit to height
        codeScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // No horizontal scrollbar needed
        codeScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Show vertical scrollbar when needed
        codeScrollPane.setStyle("-fx-background-color: #ffffff;");
        
        // Configure CodeArea for simple, clean appearance
        codeArea.setEditable(false);
        codeArea.setWrapText(true); // Enable text wrapping by default
        codeArea.setFocusTraversable(true); // Allow focus for mouse wheel events
        
        // Simple CodeArea configuration
        
        // Simple styling
        codeArea.setStyle(
            "-fx-font-family: 'Consolas', 'Monaco', 'Courier New', 'SF Mono', monospace; " +
            "-fx-font-size: 13px; " +
            "-fx-text-fill: #24292e; " +
            "-fx-background-color: #ffffff; " +
            "-fx-padding: 2px; " +
            "-fx-line-spacing: 1px;"
        );
        
        // Add line numbers
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // Let ScrollPane handle mouse wheel scrolling naturally
        
        // Setup syntax highlighting for Java
        codeArea.richChanges()
                .filter(ch -> !ch.getInserted().equals(ch.getRemoved()))
                .subscribe(change -> {
                    Platform.runLater(() -> {
                        codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
                    });
                });
        
        // Initial syntax highlighting
        codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
        
        // Add listener to scroll to top when content changes
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.isEmpty() && !newText.equals(oldText)) {
                Platform.runLater(() -> {
                    codeScrollPane.setVvalue(0);
                    codeArea.moveTo(0);
                });
            }
        });
    }
    
    
    /**
     * Compute syntax highlighting for Java code
     */
    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        
        while (matcher.find()) {
            String styleClass = matcher.group("KEYWORD") != null ? "keyword" :
                               matcher.group("PAREN") != null ? "paren" :
                               matcher.group("BRACE") != null ? "brace" :
                               matcher.group("BRACKET") != null ? "bracket" :
                               matcher.group("SEMICOLON") != null ? "semicolon" :
                               matcher.group("STRING") != null ? "string" :
                               matcher.group("COMMENT") != null ? "comment" :
                               matcher.group("ANNOTATION") != null ? "annotation" :
                               matcher.group("NUMBER") != null ? "number" :
                               null; /* never happens */
            
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
    
    // Pattern for Java syntax highlighting
    private static final String[] KEYWORDS = new String[] {
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null"
    };
    
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
    private static final String ANNOTATION_PATTERN = "@\\w+";
    private static final String NUMBER_PATTERN = "\\b\\d+\\.?\\d*\\b";
    
    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<PAREN>" + PAREN_PATTERN + ")"
        + "|(?<BRACE>" + BRACE_PATTERN + ")"
        + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
        + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
        + "|(?<ANNOTATION>" + ANNOTATION_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    );
    
    /**
     * Browse for JAR directory
     * Automatically saves the selected path to prevent save prompts
     */
    private void browseForJarDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select JAR Directory");
        
        // Set initial directory if jarPathField has content
        if (!jarPathField.getText().isEmpty()) {
            File initialDir = new File(jarPathField.getText());
            if (initialDir.exists() && initialDir.isDirectory()) {
                directoryChooser.setInitialDirectory(initialDir);
            }
        }
        
        Stage stage = (Stage) browseButton.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);
        
        if (selectedDirectory != null) {
            String directoryPath = selectedDirectory.getAbsolutePath();
            jarPathField.setText(directoryPath);
            loadJarFiles(directoryPath);
            
            // Auto-save the selected path to prevent save prompts
            if (mainController != null && mainController.getSelectedProject() != null) {
                String projectName = mainController.getSelectedProject().getName();
                saveJarPath(projectName, directoryPath);
            }
        }
    }
    
    /**
     * Load JAR files from the specified directory
     * Smart selection: Uses saved selection from decompilation info if exists, otherwise defaults to nms_*.jar
     */
    private void loadJarFiles(String directoryPath) {
        try {
            currentJarPath = directoryPath;
            List<String> jars = JarDecompilerService.getJarFiles(directoryPath);
            
            jarFiles.clear();
            jarSelectionMap.clear();
            jarClassTrees.clear();
            
            // Try to load saved selection from decompilation info
            DecompilationInfo info = getDecompilationInfo();
            Map<String, Boolean> savedSelection = (info != null) ? info.jarSelection : null;
            
            for (String jar : jars) {
                jarFiles.add(jar);
                
                // Use saved selection if available
                // Otherwise use default selection (nms_*.jar files)
                boolean selected;
                if (savedSelection != null && savedSelection.containsKey(jar)) {
                    selected = savedSelection.get(jar);
                    logger.info("Loaded saved selection from decompilation info for " + jar + ": " + selected);
                } else {
                    // Default selection for nms_*.jar files
                    selected = jar.startsWith("nms_") && jar.endsWith(".jar");
                }
                jarSelectionMap.put(jar, selected);
            }
            
            // Refresh the list view to show checkboxes
            jarListView.refresh();
            
            updateDecompileButtonState();
            updateCounts();
            
            // Save JAR path for current project
            if (mainController != null && mainController.getSelectedProject() != null) {
                String projectName = mainController.getSelectedProject().getName();
                saveJarPath(projectName, directoryPath);
            }
            
            updateStatus("Found " + jars.size() + " JAR files", "fa-archive");
            logger.info("Loaded " + jars.size() + " JAR files from: " + directoryPath + " (saved selection: " + (savedSelection != null) + ")");
            
        } catch (Exception e) {
            logger.severe("Error loading JAR files: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load JAR files: " + e.getMessage());
            updateStatus("Error loading JAR files", "fa-exclamation-triangle");
        }
    }
    
    /**
     * Load class list for the selected JAR
     */
    private void loadClassTreeForJar(String jarName) {
        try {
            // Get all classes from the JAR
            List<String> classes = JarDecompilerService.getClassesFromJar(currentJarPath + File.separator + jarName);
            
            // Update class list
            classFiles.clear();
            classFiles.addAll(classes);
            
            // Update count
            classCountLabel.setText("(" + classes.size() + ")");
            
            // Clear any existing search and reset sorting
            currentClassSearchTerm = "";
            classSearchField.clear();
            filteredClassFiles.setPredicate(null);
            sortedClassFiles.setComparator(null);
            
            updateStatus("Loaded " + classes.size() + " classes from " + jarName, "fa-code");
            logger.info("Loaded " + classes.size() + " classes from JAR: " + jarName);
            
        } catch (Exception e) {
            logger.severe("Error loading classes from JAR " + jarName + ": " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load classes: " + e.getMessage());
            updateStatus("Error loading classes", "fa-exclamation-triangle");
        }
    }
    
    /**
     * Update class tree based on selected JARs
     */
    private void updateClassTree() {
        TreeItem<ClassTreeNode> root = new TreeItem<ClassTreeNode>();
        
        // Get selected JARs
        List<String> selectedJars = jarFiles.stream()
            .filter(jar -> jarSelectionMap.getOrDefault(jar, false))
            .collect(java.util.stream.Collectors.toList());
        
        for (String jarName : selectedJars) {
            JarDecompilerService.ClassTreeNode jarTree = jarClassTrees.get(jarName);
            if (jarTree != null) {
                TreeItem<ClassTreeNode> jarItem = new TreeItem<ClassTreeNode>(new ClassTreeNode(jarName, jarName, false));
                addTreeChildren(jarItem, jarTree);
                root.getChildren().add(jarItem);
            }
        }
        
        // No longer using tree view - using list view instead
        updateCounts();
    }
    
    /**
     * Add children to tree item
     */
    private void addTreeChildren(TreeItem<ClassTreeNode> parent, JarDecompilerService.ClassTreeNode node) {
        for (JarDecompilerService.ClassTreeNode child : node.getChildren()) {
            ClassTreeNode controllerNode = new ClassTreeNode(child.getName(), child.getFullName(), child.isClass());
            TreeItem<ClassTreeNode> childItem = new TreeItem<ClassTreeNode>(controllerNode);
            addTreeChildren(childItem, child);
            parent.getChildren().add(childItem);
        }
    }
    
    
    /**
     * Filter class list based on search text with prioritized search
     * Results are sorted: exact match first, then substring match, then regex match
     */
    private void filterClassListWithPriority(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredClassFiles.setPredicate(null);
            currentClassSearchTerm = "";
        } else {
            String trimmedSearchText = searchText.trim();
            currentClassSearchTerm = trimmedSearchText;
            
            // Set filter predicate
            filteredClassFiles.setPredicate(className -> 
                matchesSearchPattern(className, trimmedSearchText)
            );
            
            // Set comparator for sorting based on search priority
            sortedClassFiles.setComparator((a, b) -> {
                int priorityA = getSearchPriority(a, trimmedSearchText);
                int priorityB = getSearchPriority(b, trimmedSearchText);
                return Integer.compare(priorityA, priorityB);
            });
            
            // Force refresh the UI
            Platform.runLater(() -> {
                classListView.refresh();
            });
        }
        updateCounts();
    }
    
    /**
     * Get search priority for sorting (lower number = higher priority)
     * 1 = exact match, 2 = substring match, 3 = regex match, 4 = no match
     * Searches only on the class name part (after last dot), not the full package name
     */
    private int getSearchPriority(String text, String searchText) {
        // Extract class name only (after last dot)
        String className = extractClassName(text);
        String lowerClassName = className.toLowerCase();
        String lowerSearchText = searchText.toLowerCase();
        
        // 1. Exact match (highest priority) - check this first
        if (lowerClassName.equals(lowerSearchText)) {
            return 1;
        }
        
        // 2. Substring match (second priority) - only if not exact match
        if (lowerClassName.contains(lowerSearchText)) {
            return 2;
        }
        
        // 3. Regex/wildcard match (third priority) - only if not exact or substring match
        if (matchesWildcardPattern(lowerClassName, lowerSearchText)) {
            return 3;
        }
        
        // 4. No match (lowest priority)
        return 4;
    }
    
    /**
     * Extract class name from fully qualified class name
     * e.g., "com.nms.support.Handler" -> "Handler"
     */
    private String extractClassName(String fullyQualifiedName) {
        if (fullyQualifiedName == null || fullyQualifiedName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fullyQualifiedName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            // No package, return the whole string
            return fullyQualifiedName;
        }
        
        // Return everything after the last dot
        return fullyQualifiedName.substring(lastDotIndex + 1);
    }
    
    /**
     * Check if text matches search pattern with priority support
     * Returns true if it's an exact match, substring match, or regex match
     * Searches only on the class name part (after last dot), not the full package name
     */
    private boolean matchesSearchPattern(String text, String searchText) {
        // Extract class name only (after last dot)
        String className = extractClassName(text);
        String lowerClassName = className.toLowerCase();
        String lowerSearchText = searchText.toLowerCase();
        
        // 1. Exact match
        if (lowerClassName.equals(lowerSearchText)) {
            return true;
        }
        
        // 2. Substring match
        if (lowerClassName.contains(lowerSearchText)) {
            return true;
        }
        
        // 3. Regex/wildcard match
        return matchesWildcardPattern(lowerClassName, lowerSearchText);
    }
    
    /**
     * Filter JAR list with simple case-insensitive substring search
     */
    private void filterJarListSimple(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredJarFiles.setPredicate(null);
            sortedJarFiles.setComparator(null);
        } else {
            String trimmedSearchText = searchText.trim().toLowerCase();
            
            // Set filter predicate for simple substring search
            filteredJarFiles.setPredicate(jar -> 
                jar.toLowerCase().contains(trimmedSearchText)
            );
            
            // No special sorting - just use natural order
            sortedJarFiles.setComparator(null);
        }
        updateCounts();
    }
    
    /**
     * Filter JAR list based on search text with prioritized search
     * Results are sorted: exact match first, then substring match, then regex match
     */
    private void filterJarListWithPriority(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredJarFiles.setPredicate(null);
            currentJarSearchTerm = "";
        } else {
            String trimmedSearchText = searchText.trim();
            currentJarSearchTerm = trimmedSearchText;
            
            // Set filter predicate
            filteredJarFiles.setPredicate(jar -> 
                matchesSearchPattern(jar, trimmedSearchText)
            );
            
            // Set comparator for sorting based on search priority
            sortedJarFiles.setComparator((a, b) -> {
                int priorityA = getSearchPriority(a, trimmedSearchText);
                int priorityB = getSearchPriority(b, trimmedSearchText);
                return Integer.compare(priorityA, priorityB);
            });
            
            // Force refresh the UI
            Platform.runLater(() -> {
                jarListView.refresh();
            });
        }
        updateCounts();
    }
    
    /**
     * Check if a class name matches a wildcard pattern
     * Supports * as wildcard for any number of characters
     * Examples:
     * - "Test*" matches "TestClass", "TestUtils", "TestHelper"
     * - "*Service" matches "UserService", "DataService", "AuthService"
     * - "*Manager*" matches "UserManager", "DataManager", "ManagerHelper"
     * - "Test*Service" matches "TestUserService", "TestDataService"
     */
    private boolean matchesWildcardPattern(String className, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        
        // If no wildcards, use simple contains match
        if (!pattern.contains("*")) {
            return className.contains(pattern);
        }
        
        // Handle wildcard patterns
        String[] patternParts = pattern.split("\\*", -1); // -1 to keep empty strings
        
        // If pattern starts and ends with *, check if all parts are contained
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            for (String part : patternParts) {
                if (!part.isEmpty() && !className.contains(part)) {
                    return false;
                }
            }
            return true;
        }
        
        // If pattern starts with *, check if className ends with the remaining pattern
        if (pattern.startsWith("*")) {
            String remainingPattern = pattern.substring(1); // Remove leading *
            if (remainingPattern.isEmpty()) {
                return true; // Pattern is just "*"
            }
            
            // Split by remaining * characters and check each part
            String[] remainingParts = remainingPattern.split("\\*", -1);
            int currentIndex = 0;
            
            for (String part : remainingParts) {
                if (part.isEmpty()) continue;
                
                int foundIndex = className.indexOf(part, currentIndex);
                if (foundIndex == -1) {
                    return false;
                }
                currentIndex = foundIndex + part.length();
            }
            return true;
        }
        
        // If pattern ends with *, check if className starts with the pattern
        if (pattern.endsWith("*")) {
            String startPattern = pattern.substring(0, pattern.length() - 1); // Remove trailing *
            if (startPattern.isEmpty()) {
                return true; // Pattern is just "*"
            }
            
            // Split by * characters and check each part sequentially
            String[] startParts = startPattern.split("\\*", -1);
            int currentIndex = 0;
            
            for (String part : startParts) {
                if (part.isEmpty()) continue;
                
                int foundIndex = className.indexOf(part, currentIndex);
                if (foundIndex == -1) {
                    return false;
                }
                currentIndex = foundIndex + part.length();
            }
            return className.startsWith(startParts[0]); // Must start with first part
        }
        
        // If pattern has * in the middle, use more complex matching
        if (pattern.contains("*")) {
            String[] middleParts = pattern.split("\\*", -1);
            
            // Must start with first part (if not empty)
            if (!middleParts[0].isEmpty() && !className.startsWith(middleParts[0])) {
                return false;
            }
            
            // Must end with last part (if not empty)
            if (!middleParts[middleParts.length - 1].isEmpty() && !className.endsWith(middleParts[middleParts.length - 1])) {
                return false;
            }
            
            // Check all middle parts are present in order
            int currentIndex = middleParts[0].length();
            for (int i = 1; i < middleParts.length - 1; i++) {
                String part = middleParts[i];
                if (part.isEmpty()) continue;
                
                int foundIndex = className.indexOf(part, currentIndex);
                if (foundIndex == -1) {
                    return false;
                }
                currentIndex = foundIndex + part.length();
            }
            
            return true;
        }
        
        // Fallback to exact match if no wildcards
        return className.equals(pattern);
    }
    
    
    /**
     * Load decompiled code for the selected class
     */
    private void loadDecompiledCode(String className) {
        try {
            // Check if decompiled files exist
            String extractedPath = getExtractedPath();
            if (extractedPath == null || !new File(extractedPath).exists()) {
                codeArea.clear();
                codeArea.appendText("// Please decompile the JAR first to view the source code");
                
                // Force immediate scroll to top
                codeScrollPane.setVvalue(0);
                codeArea.moveTo(0);
                
                currentClassLabel.setText("Select a class to view decompiled code");
                copyCodeButton.setVisible(false);
                return;
            }
            
            // Find the JAR that contains this class
            String jarName = findJarForClass(className);
            if (jarName == null) {
                codeArea.clear();
                codeArea.appendText("// Class not found in any decompiled JAR");
                
                // Force immediate scroll to top
                codeScrollPane.setVvalue(0);
                codeArea.moveTo(0);
                
                currentClassLabel.setText("Class: " + className);
                copyCodeButton.setVisible(false);
                return;
            }
            
            String code = JarDecompilerService.getDecompiledCode(extractedPath, jarName, className);
            codeArea.clear();
            codeArea.replaceText(code);
            
            // Force immediate scroll to top
            codeScrollPane.setVvalue(0);
            codeArea.moveTo(0);
            
            // Apply syntax highlighting
            codeArea.setStyleSpans(0, computeHighlighting(code));
            
            // Scroll to top when new content is loaded - use multiple attempts to ensure it works
            Platform.runLater(() -> {
                codeScrollPane.setVvalue(0);
                codeArea.moveTo(0);
                codeArea.requestFollowCaret();
                
                // Try again after a short delay to ensure content is fully rendered
                Platform.runLater(() -> {
                    codeScrollPane.setVvalue(0);
                    codeArea.moveTo(0);
                });
            });
            
            currentClassLabel.setText(className);
            copyCodeButton.setVisible(true);
            
            updateStatus("Loaded decompiled code for: " + className, "fa-file-code-o");
            logger.info("Loaded decompiled code for class: " + className);
            
        } catch (Exception e) {
            logger.severe("Error loading decompiled code for " + className + ": " + e.getMessage());
            codeArea.clear();
            codeArea.appendText("// Error loading decompiled code: " + e.getMessage());
            
            // Force immediate scroll to top
            codeScrollPane.setVvalue(0);
            codeArea.moveTo(0);
            
            currentClassLabel.setText("Error loading class");
            copyCodeButton.setVisible(false);
            
            updateStatus("Error loading decompiled code", "fa-exclamation-triangle");
        }
    }
    
    /**
     * Start decompilation process
     */
    private void startDecompilation() {
        if (currentJarPath.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Directory", "Please select a JAR directory first.");
            return;
        }
        
        // Get selected JARs
        List<String> selectedJars = jarFiles.stream()
            .filter(jar -> jarSelectionMap.getOrDefault(jar, false))
            .collect(java.util.stream.Collectors.toList());
        
        if (selectedJars.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Selection", "Please select at least one JAR file to decompile.");
            return;
        }
        
        // Get project name
        if (mainController != null && mainController.getSelectedProject() != null) {
            currentProjectName = mainController.getSelectedProject().getName();
        } else {
            // Prompt for project name
            TextInputDialog dialog = new TextInputDialog("default_project");
            dialog.setTitle("Project Name");
            dialog.setHeaderText("Enter project name for decompiled files:");
            dialog.setContentText("Project name:");
            
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                currentProjectName = result.get().trim();
            } else {
                return;
            }
        }
        
        // Check for existing decompilation with same project code
        if (shouldShowProjectCodeConfirmation()) {
            return; // User cancelled
        }
        
        // Start decompilation
        decompilerService.setDecompilationParams(currentJarPath, currentProjectName, selectedJars);
        decompilerService.restart();
    }
    
    /**
     * Check if we should show project code confirmation dialog
     */
    private boolean shouldShowProjectCodeConfirmation() {
        String extractedPath = getExtractedPath();
        if (extractedPath == null || !new File(extractedPath).exists()) {
            return false; // No existing decompilation
        }
        
        // Get current project code
        String currentProjectCode = getProjectCode();
        if (currentProjectCode == null || "No tag".equals(currentProjectCode)) {
            return false; // No project code to compare
        }
        
        // Get last decompilation project code from metadata
        String lastProjectCode = getLastDecompilationProjectCode();
        if (lastProjectCode == null || !currentProjectCode.equals(lastProjectCode)) {
            return false; // Different project codes
        }
        
        // Same project code - show custom confirmation dialog
        return showProjectCodeConfirmationDialog(currentProjectCode);
    }
    
    /**
     * Show custom confirmation dialog for project code regeneration
     */
    private boolean showProjectCodeConfirmationDialog(String projectCode) {
        try {
            // Create dialog stage
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.DECORATED);
            dialogStage.setTitle("Project Code Already Generated");
            dialogStage.setResizable(false);
            dialogStage.setAlwaysOnTop(true);
            
            // Set application icon
            IconUtils.setStageIcon(dialogStage);
            
            // Create main container with modern styling
            VBox mainContainer = new VBox(20);
            mainContainer.setPadding(new Insets(24));
            mainContainer.setAlignment(Pos.CENTER);
            mainContainer.setStyle("""
                -fx-background-color: #FFFFFF;
                -fx-background-radius: 12;
                -fx-border-color: #E5E7EB;
                -fx-border-width: 1;
                -fx-border-radius: 12;
                -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 10, 0, 0, 2);
                """);
            
            // Create header with icon and title
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER);
            
            // Warning icon
            FontIcon warningIcon = new FontIcon("fa-exclamation-triangle");
            warningIcon.setIconSize(32);
            warningIcon.setIconColor(javafx.scene.paint.Color.valueOf("#F59E0B"));
            
            // Title label
            Label titleLabel = new Label("Project Code Already Generated");
            titleLabel.setStyle("""
                -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                -fx-text-fill: #1F2937;
                """);
            
            header.getChildren().addAll(warningIcon, titleLabel);
            
            // Content area
            VBox contentArea = new VBox(12);
            contentArea.setAlignment(Pos.CENTER);
            contentArea.setMaxWidth(400);
            
            // Main message
            Label mainMessage = new Label("The project code '" + projectCode + "' has already been generated for this version.");
            mainMessage.setStyle("""
                -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                -fx-font-size: 14px;
                -fx-text-fill: #374151;
                -fx-text-alignment: center;
                """);
            mainMessage.setWrapText(true);
            
            // Secondary message
            Label secondaryMessage = new Label("Do you want to generate again? This will overwrite existing decompiled files.");
            secondaryMessage.setStyle("""
                -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                -fx-font-size: 13px;
                -fx-text-fill: #6B7280;
                -fx-text-alignment: center;
                """);
            secondaryMessage.setWrapText(true);
            
            contentArea.getChildren().addAll(mainMessage, secondaryMessage);
            
            // Button container
            HBox buttonContainer = new HBox(12);
            buttonContainer.setAlignment(Pos.CENTER);
            
            // OK button (Generate)
            Button generateButton = new Button("Generate");
            generateButton.setStyle("""
                -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                -fx-font-size: 13px;
                -fx-font-weight: 600;
                -fx-background-color: #3B82F6;
                -fx-text-fill: white;
                -fx-padding: 10 20;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-cursor: hand;
                -fx-min-width: 100;
                -fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 3, 0, 0, 1);
                """);
            
            // Hover effect for generate button
            generateButton.setOnMouseEntered(e -> {
                generateButton.setStyle("""
                    -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                    -fx-font-size: 13px;
                    -fx-font-weight: 600;
                    -fx-background-color: #2563EB;
                    -fx-text-fill: white;
                    -fx-padding: 10 20;
                    -fx-background-radius: 6;
                    -fx-border-radius: 6;
                    -fx-cursor: hand;
                    -fx-min-width: 100;
                    -fx-effect: dropshadow(gaussian, rgba(37, 99, 235, 0.4), 4, 0, 0, 2);
                    """);
            });
            
            generateButton.setOnMouseExited(e -> {
                generateButton.setStyle("""
                    -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                    -fx-font-size: 13px;
                    -fx-font-weight: 600;
                    -fx-background-color: #3B82F6;
                    -fx-text-fill: white;
                    -fx-padding: 10 20;
                    -fx-background-radius: 6;
                    -fx-border-radius: 6;
                    -fx-cursor: hand;
                    -fx-min-width: 100;
                    -fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 3, 0, 0, 1);
                    """);
            });
            
            // Cancel button
            Button cancelButton = new Button("Cancel");
            cancelButton.setStyle("""
                -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                -fx-font-size: 13px;
                -fx-font-weight: 600;
                -fx-background-color: #6B7280;
                -fx-text-fill: white;
                -fx-padding: 10 20;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-cursor: hand;
                -fx-min-width: 100;
                -fx-effect: dropshadow(gaussian, rgba(107, 114, 128, 0.3), 3, 0, 0, 1);
                """);
            
            // Hover effect for cancel button
            cancelButton.setOnMouseEntered(e -> {
                cancelButton.setStyle("""
                    -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                    -fx-font-size: 13px;
                    -fx-font-weight: 600;
                    -fx-background-color: #4B5563;
                    -fx-text-fill: white;
                    -fx-padding: 10 20;
                    -fx-background-radius: 6;
                    -fx-border-radius: 6;
                    -fx-cursor: hand;
                    -fx-min-width: 100;
                    -fx-effect: dropshadow(gaussian, rgba(75, 85, 99, 0.4), 4, 0, 0, 2);
                    """);
            });
            
            cancelButton.setOnMouseExited(e -> {
                cancelButton.setStyle("""
                    -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif;
                    -fx-font-size: 13px;
                    -fx-font-weight: 600;
                    -fx-background-color: #6B7280;
                    -fx-text-fill: white;
                    -fx-padding: 10 20;
                    -fx-background-radius: 6;
                    -fx-border-radius: 6;
                    -fx-cursor: hand;
                    -fx-min-width: 100;
                    -fx-effect: dropshadow(gaussian, rgba(107, 114, 128, 0.3), 3, 0, 0, 1);
                    """);
            });
            
            buttonContainer.getChildren().addAll(generateButton, cancelButton);
            
            // Assemble main container
            mainContainer.getChildren().addAll(header, contentArea, buttonContainer);
            
            // Create scene
            Scene scene = new Scene(mainContainer);
            scene.getRoot().setStyle("-fx-background-color: transparent;");
            dialogStage.setScene(scene);
            
            // Result handling
            final boolean[] result = new boolean[1];
            final boolean[] dialogClosed = new boolean[1];
            
            generateButton.setOnAction(e -> {
                result[0] = false; // User chose to generate
                dialogClosed[0] = true;
                dialogStage.close();
            });
            
            cancelButton.setOnAction(e -> {
                result[0] = true; // User cancelled
                dialogClosed[0] = true;
                dialogStage.close();
            });
            
            // Handle close button (X) - treat as cancel
            dialogStage.setOnCloseRequest(e -> {
                if (!dialogClosed[0]) {
                    result[0] = true; // User cancelled
                    dialogClosed[0] = true;
                }
            });
            
            // Handle ESC key - treat as cancel
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && !dialogClosed[0]) {
                    result[0] = true; // User cancelled
                    dialogClosed[0] = true;
                    dialogStage.close();
                }
            });
            
            // Set default button and focus
            dialogStage.setOnShown(e -> generateButton.requestFocus());
            
            // Center dialog on screen
            dialogStage.centerOnScreen();
            
            // Show dialog and wait
            dialogStage.showAndWait();
            
            return result[0]; // true if cancelled, false if user chose to generate
            
        } catch (Exception e) {
            logger.severe("Error showing project code confirmation dialog: " + e.getMessage());
            // Fallback to default behavior (allow generation)
            return false;
        }
    }
    
    /**
     * Get last decompilation project code from info file
     */
    private String getLastDecompilationProjectCode() {
        DecompilationInfo info = getDecompilationInfo();
        if (info != null && info.projectCode != null && !info.projectCode.isEmpty()) {
            return info.projectCode;
        }
        
        // Fallback to old metadata file for backward compatibility
        String extractedPath = getExtractedPath();
        if (extractedPath == null) {
            return null;
        }
        
        try {
            File metadataFile = new File(extractedPath, ".decompilation_metadata");
            if (metadataFile.exists()) {
                return Files.readString(metadataFile.toPath()).trim();
            }
        } catch (Exception e) {
            logger.warning("Error reading decompilation metadata: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Save decompilation metadata
     */
    private void saveDecompilationMetadata(String projectCode) {
        String extractedPath = getExtractedPath();
        if (extractedPath == null) {
            return;
        }
        
        try {
            File metadataFile = new File(extractedPath, ".decompilation_metadata");
            Files.write(metadataFile.toPath(), projectCode.getBytes());
        } catch (Exception e) {
            logger.warning("Error saving decompilation metadata: " + e.getMessage());
        }
    }
    
    /**
     * Open extracted folder in VS Code
     */
    private void openInVSCode() {
        String extractedPath = getExtractedPath();
        if (extractedPath == null || !new File(extractedPath).exists()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Extracted Files", "Please decompile JARs first.");
            return;
        }
        
        try {
            // Get currently selected class
            String selectedClass = classListView.getSelectionModel().getSelectedItem();
            if (selectedClass != null && !selectedClass.isEmpty()) {
                // Open VS Code with the selected class file
                JarDecompilerService.openInVSCode(extractedPath, selectedClass);
                updateStatus("Opened VS Code with class: " + selectedClass, "fa-code");
            } else {
                // No class selected, open directory
                JarDecompilerService.openInVSCode(extractedPath);
                updateStatus("Opened VS Code with extracted files", "fa-code");
            }
        } catch (Exception e) {
            logger.severe("Error opening VS Code: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to open VS Code: " + e.getMessage());
            updateStatus("Error opening VS Code", "fa-exclamation-triangle");
        }
    }
    
    /**
     * Copy code to clipboard
     */
    private void copyCodeToClipboard() {
        String code = codeArea.getText();
        if (code != null && !code.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            clipboard.setContent(content);
            updateStatus("Code copied to clipboard", "fa-copy");
        }
    }
    
    
    /**
     * Setup service event handlers
     */
    private void setupServiceHandlers() {
        decompilerService.setOnRunning(event -> {
            Platform.runLater(() -> {
                progressBar.setVisible(true);
                progressLabel.setVisible(true);
                decompileButton.setDisable(true);
                updateStatus("Decompiling JARs...", "fa-spinner");
            });
        });
        
        decompilerService.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
                decompileButton.setDisable(false);
                openVsButton.setDisable(false);
                updateStatus("Decompilation completed successfully", "fa-check-circle");
                
                // Save decompilation info (timestamp and project code)
                saveDecompilationInfo();
                
                // Update UI to show the new decompilation info
                updateDecompilationInfo();
                
                // Also save project code metadata for backward compatibility
                DecompilationInfo info = getDecompilationInfo();
                if (info != null && info.projectCode != null && !"No tag".equals(info.projectCode)) {
                    saveDecompilationMetadata(info.projectCode);
                }
                
                // JAR selection is now saved in decompilation info file (no separate save needed)
                logger.info("JAR selection saved in decompilation info");
                
                DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Success", "JARs decompiled successfully!");
            });
        });
        
        decompilerService.setOnFailed(event -> {
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
                decompileButton.setDisable(false);
                updateStatus("Decompilation failed", "fa-exclamation-triangle");
                DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Decompilation failed: " + 
                    decompilerService.getException().getMessage());
            });
        });
        
        decompilerService.progressProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                progressBar.setProgress(newValue.doubleValue());
                progressLabel.setText(String.format("%.0f%%", newValue.doubleValue() * 100));
            });
        });
    }
    
    /**
     * Update decompile button state based on selection
     */
    private void updateDecompileButtonState() {
        boolean hasSelection = jarSelectionMap.values().stream().anyMatch(selected -> selected);
        decompileButton.setDisable(!hasSelection || currentJarPath.isEmpty());
    }
    
    /**
     * Update status message and icon
     */
    private void updateStatus(String message, String icon) {
        statusLabel.setText(message);
        statusIcon.setIconLiteral(icon);
    }
    
    /**
     * Show loading overlay with blur effect
     */
    private void showLoadingOverlay() {
        if (loadingOverlay != null && mainContent != null) {
            // Apply blur effect to main content
            javafx.scene.effect.GaussianBlur blur = new javafx.scene.effect.GaussianBlur(10);
            mainContent.setEffect(blur);
            
            // Show loading overlay
            loadingOverlay.setVisible(true);
            loadingOverlay.toFront();
        }
    }
    
    /**
     * Hide loading overlay and remove blur effect
     */
    private void hideLoadingOverlay() {
        if (loadingOverlay != null && mainContent != null) {
            // Remove blur effect from main content
            mainContent.setEffect(null);
            
            // Hide loading overlay
            loadingOverlay.setVisible(false);
        }
    }
    
    /**
     * Update count labels
     */
    private void updateCounts() {
        jarCountLabel.setText("(" + sortedJarFiles.size() + ")");
        
        int classCount = sortedClassFiles.size();
        classCountLabel.setText("(" + classCount + ")");
    }
    
    /**
     * Update decompilation info label
     * Always loads from the saved .decompilation_info file
     */
    private void updateDecompilationInfo() {
        if (currentProjectName.isEmpty()) {
            decompilationInfoLabel.setText("");
            logger.info("Decompilation info: No project name, clearing label");
            return;
        }
        
        String extractedPath = getExtractedPath();
        if (extractedPath == null || !new File(extractedPath).exists()) {
            decompilationInfoLabel.setText("");
            logger.info("Decompilation info: Extracted path does not exist, clearing label");
            return;
        }
        
        // Get decompilation info from file (timestamp and project code)
        logger.info("Updating decompilation info for project: " + currentProjectName);
        DecompilationInfo info = getDecompilationInfo();
        if (info != null && info.timestamp > 0) {
            String relativeTime = getRelativeTime(info.timestamp);
            String projectCode = info.projectCode != null && !info.projectCode.isEmpty() ? info.projectCode : "No tag";
            String displayText = "Decompiled: " + relativeTime + " - " + projectCode;
            decompilationInfoLabel.setText(displayText);
            logger.info("Decompilation info updated: " + displayText);
        } else {
            decompilationInfoLabel.setText("");
            logger.info("Decompilation info: No valid info found, clearing label");
        }
    }
    
    /**
     * Get decompilation info (timestamp, project code, and JAR selection) from file
     * This is much more efficient than scanning files or reading JARs
     * Always loads from the saved file, showing exactly what was decompiled
     */
    private DecompilationInfo getDecompilationInfo() {
        String extractedPath = getExtractedPath();
        if (extractedPath == null) {
            return null;
        }
        
        try {
            File infoFile = new File(extractedPath, ".decompilation_info");
            if (infoFile.exists()) {
                List<String> lines = Files.readAllLines(infoFile.toPath());
                if (lines.size() >= 2) {
                    long timestamp = Long.parseLong(lines.get(0).trim());
                    String projectCode = lines.get(1).trim();
                    
                    // Load JAR selection if available (from line 3 onwards)
                    Map<String, Boolean> jarSelection = new HashMap<>();
                    if (lines.size() > 2) {
                        for (int i = 2; i < lines.size(); i++) {
                            String line = lines.get(i).trim();
                            if (line.isEmpty() || line.startsWith("#")) {
                                continue;
                            }
                            // Parse jarname=true/false
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                String jarName = parts[0].trim();
                                boolean selected = Boolean.parseBoolean(parts[1].trim());
                                jarSelection.put(jarName, selected);
                            }
                        }
                    }
                    
                    logger.info("Loaded decompilation info - Timestamp: " + timestamp + ", Project Code: " + projectCode + ", JAR Selection: " + jarSelection.size() + " entries");
                    return new DecompilationInfo(timestamp, projectCode, jarSelection.isEmpty() ? null : jarSelection);
                } else {
                    logger.warning("Decompilation info file exists but has insufficient data (lines: " + lines.size() + ")");
                }
            } else {
                logger.info("Decompilation info file does not exist: " + infoFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.warning("Error reading decompilation info: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Save decompilation info (timestamp, project code, and JAR selection) to file
     * Called when decompilation completes successfully
     * Always overwrites existing file to ensure latest info is saved
     */
    private void saveDecompilationInfo() {
        String extractedPath = getExtractedPath();
        if (extractedPath == null) {
            logger.warning("Cannot save decompilation info: extracted path is null");
            return;
        }
        
        try {
            // Ensure directory exists
            File extractedDir = new File(extractedPath);
            if (!extractedDir.exists()) {
                extractedDir.mkdirs();
                logger.info("Created extracted directory: " + extractedPath);
            }
            
            File infoFile = new File(extractedPath, ".decompilation_info");
            long currentTime = System.currentTimeMillis();
            String projectCode = getProjectCode(); // Get project code at the time of decompilation
            
            // Build content: timestamp, project code, and JAR selection
            StringBuilder content = new StringBuilder();
            content.append(currentTime).append("\n");
            content.append(projectCode).append("\n");
            
            // Add JAR selection (only save selected JARs to keep file clean)
            content.append("# JAR Selection\n");
            for (Map.Entry<String, Boolean> entry : jarSelectionMap.entrySet()) {
                content.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            
            // Save with explicit overwrite
            Files.write(infoFile.toPath(), content.toString().getBytes(), 
                       java.nio.file.StandardOpenOption.CREATE, 
                       java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            
            logger.info("Saved decompilation info - Timestamp: " + currentTime + ", Project Code: " + projectCode + ", JAR Selection: " + jarSelectionMap.size() + " entries, File: " + infoFile.getAbsolutePath());
        } catch (Exception e) {
            logger.severe("Error saving decompilation info: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Helper class to hold decompilation information
     */
    private static class DecompilationInfo {
        final long timestamp;
        final String projectCode;
        final Map<String, Boolean> jarSelection;
        
        DecompilationInfo(long timestamp, String projectCode) {
            this(timestamp, projectCode, null);
        }
        
        DecompilationInfo(long timestamp, String projectCode, Map<String, Boolean> jarSelection) {
            this.timestamp = timestamp;
            this.projectCode = projectCode;
            this.jarSelection = jarSelection;
        }
    }
    
    /**
     * Get relative time string
     */
    private String getRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);
        
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " " + hours + "hr" + (hours > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + "hr" + (hours > 1 ? "s" : "") + " " + minutes + "min ago";
        } else if (minutes > 0) {
            return minutes + "min ago";
        } else {
            return "Just now";
        }
    }
    
    /**
     * Get project code from nms_common.jar
     */
    private String getProjectCode() {
        if (currentJarPath == null || currentJarPath.isEmpty()) {
            return "No tag";
        }
        
        try {
            String nmsCommonJarPath = currentJarPath + File.separator + "nms_common.jar";
            File nmsCommonJar = new File(nmsCommonJarPath);
            
            if (!nmsCommonJar.exists()) {
                return "No tag";
            }
            
            // Get project name from main controller
            String projectName = null;
            if (mainController != null && mainController.getSelectedProject() != null) {
                projectName = mainController.getSelectedProject().getName();
            }
            
            // Use ProjectCodeService to get CVS tag directly
            String cvsTag = com.nms.support.nms_support.service.ProjectCodeService.getCvsTagFromNmsCommonJar(currentJarPath);
            if (cvsTag != null && !cvsTag.trim().isEmpty()) {
                return cvsTag;
            }
            
            return "No tag";
            
        } catch (Exception e) {
            logger.warning("Error getting project code: " + e.getMessage());
            return "No tag";
        }
    }
    
    /**
     * Set default JAR path based on project
     * Only sets a path if user has explicitly configured a product folder path
     * Automatically saves paths when loaded by system to avoid save prompts
     */
    private void setDefaultJarPath() {
        if (mainController != null && mainController.getSelectedProject() != null) {
            String projectName = mainController.getSelectedProject().getName();
            
            // Start loading mode to prevent change tracking during automatic path setting
            if (changeTrackingService != null) {
                changeTrackingService.startLoading();
            }
            
            try {
                // First, try to load saved JAR path for this project (only if user previously set one)
                String savedJarPath = getSavedJarPath(projectName);
                if (savedJarPath != null && !savedJarPath.isEmpty()) {
                    File savedDir = new File(savedJarPath);
                    if (savedDir.exists() && savedDir.isDirectory()) {
                        jarPathField.setText(savedJarPath);
                        loadJarFiles(savedJarPath);
                        // Auto-save the loaded path to prevent future save prompts
                        saveJarPath(projectName, savedJarPath);
                        return;
                    }
                }
                
                // Only set default path if user has explicitly configured a product folder path
                String productFolderPath = getProductFolderPathFromProject(projectName);
                if (productFolderPath != null && !productFolderPath.trim().isEmpty()) {
                    String defaultJarPath = productFolderPath + File.separator + "nmslib";
                    File nmslibDir = new File(defaultJarPath);
                    if (nmslibDir.exists() && nmslibDir.isDirectory()) {
                        jarPathField.setText(defaultJarPath);
                        loadJarFiles(defaultJarPath);
                        // Auto-save the default path to prevent save prompts
                        saveJarPath(projectName, defaultJarPath);
                        return;
                    }
                }
                
                // If no product folder path is configured by user, leave jar path empty
                jarPathField.setText("");
            } finally {
                // End loading mode to re-enable change tracking
                if (changeTrackingService != null) {
                    changeTrackingService.endLoading();
                }
            }
        }
    }
    
    /**
     * Save JAR path for a project
     */
    private void saveJarPath(String projectName, String jarPath) {
        try {
            String userHome = System.getProperty("user.home");
            String configDir = Paths.get(userHome, "Documents", "nms_support_data", "config").toString();
            Files.createDirectories(Paths.get(configDir));
            
            String configFile = Paths.get(configDir, projectName + "_jar_path.txt").toString();
            Files.write(Paths.get(configFile), jarPath.getBytes());
            
            logger.info("Saved JAR path for project " + projectName + ": " + jarPath);
        } catch (Exception e) {
            logger.warning("Error saving JAR path for project " + projectName + ": " + e.getMessage());
        }
    }
    
    // Note: JAR selection is now saved in the decompilation info file
    // No need for separate selection files
    
    /**
     * Get saved JAR path for a project
     */
    private String getSavedJarPath(String projectName) {
        try {
            String userHome = System.getProperty("user.home");
            String configFile = Paths.get(userHome, "Documents", "nms_support_data", "config", projectName + "_jar_path.txt").toString();
            File file = new File(configFile);
            
            if (file.exists()) {
                String jarPath = Files.readString(Paths.get(configFile)).trim();
                logger.info("Loaded saved JAR path for project " + projectName + ": " + jarPath);
                return jarPath;
            }
        } catch (Exception e) {
            logger.warning("Error loading saved JAR path for project " + projectName + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get product folder path from project
     */
    private String getProductFolderPathFromProject(String projectName) {
        if (mainController == null) {
            return null;
        }
        
        try {
            // Get the project entity and extract exePath
            ProjectEntity project = mainController.getSelectedProject();
            if (project != null && project.getExePath() != null && !project.getExePath().isEmpty()) {
                return project.getExePath();
            }
        } catch (Exception e) {
            logger.warning("Error getting product folder path: " + e.getMessage());
        }
        
        return null;
    }
    
    
    /**
     * Get extracted path for current project
     */
    private String getExtractedPath() {
        if (currentProjectName.isEmpty()) {
            return null;
        }
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "Documents", "nms_support_data", "extracted", currentProjectName).toString();
    }
    
    // Note: Syntax highlighting removed for simplicity - using basic TextArea
    
    /**
     * Called when the tab is selected
     */
    public void onTabSelected() {
        // Refresh project name if main controller is available
        if (mainController != null && mainController.getSelectedProject() != null) {
            currentProjectName = mainController.getSelectedProject().getName();
            
            // Load data in background without managing loading mode (handled by main controller)
            loadDataInBackground();
        }
    }
    
    /**
     * Handle project selection change
     */
    public void onProjectSelectionChanged(String newProjectName) {
        logger.info("Project selection changed to: " + newProjectName);
        currentProjectName = newProjectName;
        
        // Clear current data immediately (fast UI operation)
        jarFiles.clear();
        classFiles.clear();
        jarListView.getItems().clear();
        classListView.getItems().clear();
        codeArea.clear();
        
        // Force immediate scroll to top
        codeScrollPane.setVvalue(0);
        codeArea.moveTo(0);
        
        currentClassLabel.setText("Select a class to view decompiled code");
        copyCodeButton.setVisible(false);
        
        // Clear JAR path field to ensure it gets updated
        jarPathField.clear();
        currentJarPath = null;
        
        updateCounts();
        
        // Load new project data in background thread to avoid UI lag
        loadDataInBackground();
    }
    
    /**
     * Load data in background to avoid UI lag
     * This method runs heavy I/O operations in a separate thread
     * Thread-safe: only the latest project change will be applied to the UI
     */
    private void loadDataInBackground() {
        // Capture the project name we're loading for
        final String projectNameToLoad = currentProjectName;
        
        // Mark this project as currently loading
        loadingProjectName = projectNameToLoad;
        
        // Show loading overlay with blur effect immediately
        Platform.runLater(() -> {
            showLoadingOverlay();
            updateStatus("Loading project data...", "fa-spinner");
        });
        
        // Run heavy operations in background thread
        new Thread(() -> {
            try {
                String projectName = projectNameToLoad;
                if (projectName == null || projectName.isEmpty()) {
                    if (loadingProjectName == projectName) {
                        Platform.runLater(() -> {
                            hideLoadingOverlay();
                            updateStatus("Ready", "fa-info-circle");
                        });
                    }
                    return;
                }
                
                // Load existing decompiled data in background
                List<String> tempExistingClasses = new ArrayList<>();
                List<String> tempExistingJars = new ArrayList<>();
                JarDecompilerService.loadExistingDecompiledData(projectName, tempExistingClasses, tempExistingJars);
                
                // Check if we're still loading this project (user might have switched)
                if (loadingProjectName != projectName) {
                    logger.info("Project loading cancelled for " + projectName + " (user switched to " + loadingProjectName + ")");
                    return;
                }
                
                // Get saved JAR path in background
                String savedJarPath = getSavedJarPath(projectName);
                String productFolderPath = null;
                
                // Get product folder path if no saved path exists
                if (savedJarPath == null || savedJarPath.isEmpty()) {
                    productFolderPath = getProductFolderPathFromProject(projectName);
                }
                
                // Load JAR files in background (only if we have a path)
                String jarPathToLoad = null;
                if (savedJarPath != null && !savedJarPath.isEmpty()) {
                    File savedDir = new File(savedJarPath);
                    if (savedDir.exists() && savedDir.isDirectory()) {
                        jarPathToLoad = savedJarPath;
                    }
                } else if (productFolderPath != null && !productFolderPath.trim().isEmpty()) {
                    String defaultJarPath = productFolderPath + File.separator + "nmslib";
                    File nmslibDir = new File(defaultJarPath);
                    if (nmslibDir.exists() && nmslibDir.isDirectory()) {
                        jarPathToLoad = defaultJarPath;
                    }
                }
                
                // Load JAR list if we have a path
                List<String> jarsToLoad = new ArrayList<>();
                if (jarPathToLoad != null) {
                    jarsToLoad = JarDecompilerService.getJarFiles(jarPathToLoad);
                }
                
                // Try to load saved selection from decompilation info (not separate files)
                // This is done in background to avoid I/O on UI thread
                Map<String, Boolean> savedSelection = null;
                DecompilationInfo info = getDecompilationInfo();
                if (info != null && info.jarSelection != null) {
                    savedSelection = info.jarSelection;
                }
                
                // Update UI on JavaFX thread
                final List<String> finalJarList = jarsToLoad;
                final String finalJarPathForUI = jarPathToLoad;
                final Map<String, Boolean> finalSavedSelection = savedSelection;
                Platform.runLater(() -> {
                    try {
                        // Final check: make sure we're still on the same project
                        if (loadingProjectName != projectName) {
                            logger.info("UI update cancelled for " + projectName + " (user switched projects)");
                            return;
                        }
                        
                        // Start loading mode to prevent change tracking
                        if (changeTrackingService != null) {
                            changeTrackingService.startLoading();
                        }
                        
                        // Update existing decompiled data
                        existingClasses.clear();
                        existingClasses.addAll(tempExistingClasses);
                        existingJars.clear();
                        existingJars.addAll(tempExistingJars);
                        
                        // Update class list with existing decompiled classes
                        classFiles.clear();
                        classFiles.addAll(existingClasses);
                        
                        // Update JAR list to show existing decompiled JARs as checked
                        for (String existingJar : existingJars) {
                            jarSelectionMap.put(existingJar, true);
                        }
                        
                        // Update JAR path and list with smart selection
                        if (finalJarPathForUI != null) {
                            currentJarPath = finalJarPathForUI;
                            jarPathField.setText(finalJarPathForUI);
                            
                            // Update JAR files with smart selection
                            jarFiles.clear();
                            jarSelectionMap.clear();
                            jarClassTrees.clear();
                            
                            for (String jar : finalJarList) {
                                jarFiles.add(jar);
                                
                                // Use saved selection if available and decompilation exists
                                // Otherwise use default selection (nms_*.jar files)
                                boolean selected;
                                if (finalSavedSelection != null && finalSavedSelection.containsKey(jar)) {
                                    selected = finalSavedSelection.get(jar);
                                    logger.info("Background load: Using saved selection for " + jar + ": " + selected);
                                } else {
                                    // Default selection for nms_*.jar files
                                    selected = jar.startsWith("nms_") && jar.endsWith(".jar");
                                }
                                jarSelectionMap.put(jar, selected);
                            }
                            
                            // Refresh the list view to show checkboxes
                            jarListView.refresh();
                            
                            updateDecompileButtonState();
                            
                            logger.info("Background load: Loaded " + finalJarList.size() + " JARs (saved selection: " + (finalSavedSelection != null) + ")");
                        }
                        
                        // Refresh JAR list display
                        jarListView.refresh();
                        
                        // Enable VS Code button if there are existing decompiled classes
                        if (!existingClasses.isEmpty()) {
                            openVsButton.setDisable(false);
                        }
                        
                        updateCounts();
                        updateStatus("Loaded " + existingClasses.size() + " existing decompiled classes", "fa-code");
                        
                        // Update decompilation info (non-blocking)
                        updateDecompilationInfo();
                        
                        logger.info("Loaded project data in background for: " + projectName);
                        
                        // Clear loading flag if this is still the current project
                        if (loadingProjectName == projectName) {
                            loadingProjectName = null;
                        }
                        
                        // Hide loading overlay after everything is loaded
                        hideLoadingOverlay();
                        
                    } finally {
                        // End loading mode
                        if (changeTrackingService != null) {
                            changeTrackingService.endLoading();
                        }
                    }
                });
                
            } catch (Exception e) {
                logger.warning("Error loading project data in background: " + e.getMessage());
                final String failedProjectName = projectNameToLoad;
                Platform.runLater(() -> {
                    hideLoadingOverlay();
                    updateStatus("Error loading project data", "fa-exclamation-triangle");
                    // Clear loading flag on error
                    if (loadingProjectName == failedProjectName) {
                        loadingProjectName = null;
                    }
                });
            }
        }, "JarDecompiler-BackgroundLoader").start();
    }
    
    /**
     * Load existing decompiled data for the current project
     */
    private void loadExistingDecompiledData() {
        if (currentProjectName != null && !currentProjectName.isEmpty()) {
            // Load existing decompiled classes and JARs
            JarDecompilerService.loadExistingDecompiledData(currentProjectName, existingClasses, existingJars);
            
            // Update class list with existing decompiled classes
            classFiles.clear();
            classFiles.addAll(existingClasses);
            classCountLabel.setText("(" + existingClasses.size() + ")");
            
            // Update JAR list to show existing decompiled JARs as checked
            for (String existingJar : existingJars) {
                jarSelectionMap.put(existingJar, true);
            }
            
            // Refresh JAR list display
            jarListView.refresh();
            
            // Enable VS Code button if there are existing decompiled classes
            if (!existingClasses.isEmpty()) {
                openVsButton.setDisable(false);
            }
            
            updateStatus("Loaded " + existingClasses.size() + " existing decompiled classes", "fa-code");
            updateDecompilationInfo();
            logger.info("Loaded existing decompiled data for project: " + currentProjectName);
        }
    }
    
    /**
     * Find which JAR contains the given class
     */
    private String findJarForClass(String className) {
        String extractedPath = getExtractedPath();
        if (extractedPath == null) {
            return null;
        }
        
        File[] jarDirs = new File(extractedPath).listFiles(File::isDirectory);
        if (jarDirs != null) {
            for (File jarDir : jarDirs) {
                String jarName = jarDir.getName();
                String javaFilePath = findJavaFile(jarDir, className);
                if (javaFilePath != null) {
                    return jarName;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find Java file for a class in a JAR directory
     */
    private String findJavaFile(File jarDir, String className) {
        String fileName = className.replace('.', File.separatorChar) + ".java";
        File javaFile = new File(jarDir, fileName);
        if (javaFile.exists()) {
            return javaFile.getAbsolutePath();
        }
        return null;
    }
    
    /**
     * Test method to verify search prioritization works correctly
     * This method can be called for testing purposes
     */
    public void testSearchPrioritization() {
        logger.info("Testing search prioritization...");
        
        // Test data for "handle" search
        List<String> testData = Arrays.asList(
            "Handle",                   // Should match "handle" exactly (priority 1)
            "handle",                   // Should match "handle" exactly (priority 1)
            "ErrorHandler",             // Should match "handle" as substring (priority 2)
            "RequestHandler",           // Should match "handle" as substring (priority 2)
            "HandlerUtil",              // Should match "handle" as substring (priority 2)
            "handle*",                  // Should match "handle" as wildcard (priority 3)
            "other"                     // Should not match (priority 4)
        );
        
        String searchTerm = "handle";
        
        // Test priority calculation
        for (String item : testData) {
            int priority = getSearchPriority(item, searchTerm);
            boolean matches = matchesSearchPattern(item, searchTerm);
            logger.info("Item: " + item + " | Priority: " + priority + " | Matches: " + matches);
        }
    }
    
    /**
     * Test method to simulate search and show results in UI
     * This method can be called to test the search functionality
     */
    public void testSearchInUI() {
        logger.info("Testing search in UI...");
        
        // Add some test data to class files with fully qualified names
        classFiles.clear();
        classFiles.addAll(Arrays.asList(
            "com.nms.support.Handler",
            "com.nms.util.handle", 
            "com.nms.service.ErrorHandler",
            "com.nms.controller.RequestHandler",
            "com.nms.helper.HandlerUtil",
            "com.nms.model.SomeOtherClass",
            "com.nms.service.AnotherClass"
        ));
        
        // Simulate search for "handle"
        classSearchField.setText("handle");
        filterClassListWithPriority("handle");
        
        logger.info("Test search completed. Check the class list for prioritized results.");
    }
    
    /**
     * Method to manually test search prioritization
     * Call this method to test the search functionality
     */
    public void manualTestSearch() {
        logger.info("=== Manual Search Test ===");
        
        // Test the priority calculation directly with fully qualified class names
        String[] testItems = {
            "com.nms.support.Handler", 
            "com.nms.util.handle", 
            "com.nms.service.ErrorHandler", 
            "com.nms.controller.RequestHandler", 
            "com.nms.helper.HandlerUtil", 
            "com.nms.model.SomeOtherClass"
        };
        String searchTerm = "handle";
        
        for (String item : testItems) {
            String className = extractClassName(item);
            int priority = getSearchPriority(item, searchTerm);
            boolean matches = matchesSearchPattern(item, searchTerm);
            logger.info("FQN: '" + item + "' | Class: '" + className + "' | Priority: " + priority + " | Matches: " + matches);
        }
        
        logger.info("=== End Manual Search Test ===");
    }
    
    /**
     * Simple class tree node for display
     */
    public static class ClassTreeNode {
        private final String name;
        private final String fullName;
        private final boolean isClass;
        
        public ClassTreeNode(String name, String fullName, boolean isClass) {
            this.name = name;
            this.fullName = fullName;
            this.isClass = isClass;
        }
        
        public String getName() { return name; }
        public String getFullName() { return fullName; }
        public boolean isClass() { return isClass; }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
