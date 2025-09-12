package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modern JavaFX-based SVN Browser Dialog
 * Replaces the old Swing implementation with a clean, integrated approach
 */
public class SVNBrowserDialog {
    
    private final Stage dialog;
    private final String baseUrl;
    private final ISVNAuthenticationManager authManager;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    
    // UI Components
    private TextField searchField;
    private TreeView<SVNFolderItem> folderTree;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button selectButton;
    private Button cancelButton;
    private VBox loadingContainer;
    private VBox contentContainer;
    
    // Data
    private SVNFolderItem selectedItem;
    private final ObservableList<SVNFolderItem> rootItems = FXCollections.observableArrayList();
    private String currentSearchText = "";
    
    // Search navigation
    private List<TreeItem<SVNFolderItem>> searchResults = new ArrayList<>();
    private int currentSearchIndex = -1;
    
    public SVNBrowserDialog(Window parent, String baseUrl, ISVNAuthenticationManager authManager) {
        // Always use the default URL
        this.baseUrl = "https://adc4110315.us.oracle.com/svn/nms-projects/trunk/projects/";
        this.authManager = authManager;
        
        dialog = new Stage();
        dialog.initOwner(parent);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("SVN Repository Browser - Professional Edition");
        dialog.setResizable(true);
        dialog.setMinWidth(700);
        dialog.setMinHeight(600);
        dialog.setWidth(800);
        dialog.setHeight(650);
        
        // Center the dialog on screen
        dialog.centerOnScreen();
        
        // Set the standard dialog icon
        IconUtils.setStageIcon(dialog);
        
        createUI();
        setupEventHandlers();
    }
    
    private void createUI() {
        // Use BorderPane for stable layout with fixed bottom buttons
        BorderPane mainContainer = new BorderPane();
        mainContainer.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1;");
        
        // Top section - Header with search
        VBox topSection = new VBox(10);
        topSection.setPadding(new Insets(15, 15, 10, 15));
        
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-font-weight: 500; -fx-text-fill: #374151;");
        
        searchField = new TextField();
        searchField.setPromptText("Search folders... (Press Enter to search, Shift+Enter for previous)");
        searchField.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-padding: 8 12; -fx-font-size: 13px;");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        // Search button
        Button searchButton = new Button("🔍 Search");
        searchButton.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 8 16; -fx-border-radius: 6; -fx-font-weight: 500; -fx-cursor: hand;");
        searchButton.setOnAction(e -> {
            performSearch();
        });
        
        // Current URL label
        Label urlLabel = new Label("Repository: " + baseUrl);
        urlLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280; -fx-padding: 4 0;");
        urlLabel.setWrapText(true);
        
        // Search shortcuts info
        Label shortcutsLabel = new Label("💡 Shortcuts: Enter=Search, Shift+Enter=Previous, Double-click=Expand/Collapse, Single-click=Select");
        shortcutsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #059669; -fx-padding: 2 0; -fx-font-style: italic;");
        shortcutsLabel.setWrapText(true);
        
        headerBox.getChildren().addAll(searchLabel, searchField, searchButton);
        topSection.getChildren().addAll(headerBox, urlLabel, shortcutsLabel);
        
        mainContainer.setTop(topSection);
        
        // Center section - Content area
        contentContainer = new VBox(10);
        contentContainer.setPadding(new Insets(0, 15, 0, 15));
        
        // Loading container (initially visible)
        loadingContainer = new VBox(20);
        loadingContainer.setAlignment(Pos.CENTER);
        loadingContainer.setPadding(new Insets(40));
        
        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(40, 40);
        
        statusLabel = new Label("Connecting to SVN repository...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280; -fx-font-weight: 500;");
        
        loadingContainer.getChildren().addAll(progressIndicator, statusLabel);
        
        // Folder tree (initially hidden)
        folderTree = new TreeView<>();
        folderTree.setShowRoot(false);
        folderTree.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-border-width: 1;");
        
        // Set up tree cell factory for highlighting approach
        folderTree.setCellFactory(treeView -> new TreeCell<SVNFolderItem>() {
            @Override
            protected void updateItem(SVNFolderItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                setGraphic(null);
                setText(item.getName());

                String name = item.getName();
                boolean isPlaceholder = "Loading...".equals(name) ||
                    "(No subfolders)".equals(name) ||
                    "Error loading folder".equals(name);

                String baseStyle = "-fx-font-size: 13px; -fx-padding: 4 8; -fx-text-fill: #1e293b;";
                String selectedStyle = "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af; -fx-font-weight: 500;";
                String currentHitStyle = "-fx-background-color: #fef2f2; -fx-text-fill: #dc2626; -fx-font-weight: 600;"; // red for current hit
                String allHitsStyle = "-fx-background-color: #fff7ed; -fx-text-fill: #ea580c; -fx-font-weight: 500;"; // orange for all hits

                // Check if this is the current search hit
                boolean isCurrentHit = !searchResults.isEmpty() && currentSearchIndex >= 0 && 
                    currentSearchIndex < searchResults.size() && 
                    searchResults.get(currentSearchIndex) == getTreeItem();

                // Check if this is any search hit
                boolean isAnyHit = !isPlaceholder && currentSearchText != null && !currentSearchText.isEmpty() &&
                    name.toLowerCase().contains(currentSearchText);

                // Apply styles in priority order: Selection > Current Hit > All Hits > Default
                if (isSelected()) {
                    setStyle(baseStyle + selectedStyle);
                } else if (isCurrentHit) {
                    setStyle(baseStyle + currentHitStyle);
                } else if (isAnyHit) {
                    setStyle(baseStyle + allHitsStyle);
                } else {
                    setStyle(baseStyle);
                }
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                // Force restyle when selection changes
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), selected);
            }
        });
        
        VBox.setVgrow(folderTree, Priority.ALWAYS);
        contentContainer.getChildren().add(loadingContainer);
        
        mainContainer.setCenter(contentContainer);
        
        // Bottom section - Fixed button bar
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(15, 15, 15, 15));
        buttonBar.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");
        
        selectButton = new Button("✅ Select");
        selectButton.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 6; -fx-font-weight: 500; -fx-cursor: hand;");
        selectButton.setDisable(true);
        
        cancelButton = new Button("❌ Cancel");
        cancelButton.setStyle("-fx-background-color: #6b7280; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 6; -fx-font-weight: 500; -fx-cursor: hand;");
        
        buttonBar.getChildren().addAll(cancelButton, selectButton);
        
        mainContainer.setBottom(buttonBar);
        
        Scene scene = new Scene(mainContainer);
        dialog.setScene(scene);
    }
    
    private void setupEventHandlers() {
        // Search functionality - only on Enter key and Search button
        searchField.setOnAction(e -> {
            performSearch();
        });
        
        // Add Shift+Enter support for previous occurrence
        searchField.setOnKeyPressed(e -> {
            if (e.isShiftDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                findPreviousOccurrence();
            }
        });
        
        // Tree selection
        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null && newSelection.getValue() != null) {
                selectedItem = newSelection.getValue();
                selectButton.setDisable(false);
            } else {
                selectButton.setDisable(true);
            }
        });
        
        // Tree expansion - only handle LEFT double clicks for expand/collapse
        folderTree.setOnMouseClicked(event -> {
            // Only handle left mouse button double clicks
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.SECONDARY) {
                TreeItem<SVNFolderItem> item = folderTree.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null && item.getValue().isDirectory()) {
                    if (item.isExpanded()) {
                        item.setExpanded(false);
                    } else {
                        item.setExpanded(true);
                    }
                }
            }
        });
        
        // Handle tree expansion events (arrow clicks) - will be set up after root is created
        
        // Button actions
        selectButton.setOnAction(e -> {
            if (selectedItem != null) {
                dialog.close();
            }
        });
        
        cancelButton.setOnAction(e -> {
            cancelled.set(true);
            dialog.close();
        });
        
        // Dialog close handling
        dialog.setOnCloseRequest(e -> {
            cancelled.set(true);
        });
    }
    
    public String showAndWait() {
        // Start loading in background
        loadRepositoryStructure();
        
        // Show dialog
        dialog.showAndWait();
        
        if (cancelled.get() || selectedItem == null) {
            return null;
        }
        
        return buildFullPath(selectedItem);
    }
    
    private void loadRepositoryStructure() {
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Update status
                    Platform.runLater(() -> statusLabel.setText("Connecting to SVN repository..."));
                    
                    // Test connection first with progress update
                    Platform.runLater(() -> statusLabel.setText("Testing connection..."));
                    if (!testConnection()) {
                        Platform.runLater(() -> showError("Connection Failed", 
                            "Unable to connect to SVN repository.\n\nPlease verify:\n• Network connection is active\n• VPN is connected (if required)\n• SVN server is accessible\n• URL is correct and reachable"));
                        return null;
                    }
                    
                    // Update status
                    Platform.runLater(() -> statusLabel.setText("Loading repository structure..."));
                    
                    // Load root folders
                    List<SVNFolderItem> folders = loadFolders("");
                    
                    // Update UI on success
                    Platform.runLater(() -> {
                        loadingContainer.setVisible(false);
                        contentContainer.getChildren().clear();
                        contentContainer.getChildren().add(folderTree);
                        
                        // Create tree root
                        TreeItem<SVNFolderItem> root = new TreeItem<>(new SVNFolderItem("Repository", "", true));
                        root.setExpanded(true);
                        
                        // Sort folders alphabetically
                        folders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        
                        // Add folders to tree with proper structure
                        for (SVNFolderItem folder : folders) {
                            TreeItem<SVNFolderItem> item = new TreeItem<>(folder);
                            item.setExpanded(false);
                            
                            // Add a placeholder child to show expand arrow
                            TreeItem<SVNFolderItem> placeholder = new TreeItem<>(new SVNFolderItem("Loading...", "", false));
                            item.getChildren().add(placeholder);
                            
                            root.getChildren().add(item);
                        }
                        
                        folderTree.setRoot(root);
                        
                        // Now that root is set, add the expansion event handlers
                        root.addEventHandler(TreeItem.<SVNFolderItem>branchExpandedEvent(), event -> {
                            TreeItem<SVNFolderItem> expandedItem = event.getTreeItem();
                            if (expandedItem != null && expandedItem.getValue() != null && expandedItem.getValue().isDirectory()) {
                                loadSubfoldersIfNeeded(expandedItem);
                            }
                        });
                        
                        root.addEventHandler(TreeItem.<SVNFolderItem>branchCollapsedEvent(), event -> {
                            // Collapse event - no action needed
                        });
                        
                        statusLabel.setText("Repository loaded successfully - " + folders.size() + " folders found");
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Loading Failed", 
                        "Failed to load repository structure.\n\nError details:\n" + e.getMessage() + "\n\nPlease check your connection and try again."));
                }
                
                return null;
            }
        };
        
        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }
    
    private boolean testConnection() {
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(baseUrl);
            SVNRepository repository = SVNRepositoryFactory.create(svnUrl);
            repository.setAuthenticationManager(authManager);
            
            // Simple connection test with timeout handling
            repository.getRepositoryRoot(true);
            return true;
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("SVN connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    private List<SVNFolderItem> loadFolders(String path) throws SVNException {
        List<SVNFolderItem> folders = new ArrayList<>();
        
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(baseUrl);
            SVNRepository repository = SVNRepositoryFactory.create(svnUrl);
            repository.setAuthenticationManager(authManager);
            
            // Note: SVNRepository doesn't support direct timeout setting
            // Timeouts are handled at the HTTP client level in SVNKit
            
            Collection<SVNDirEntry> entries = repository.getDir(path, -1, null, (Collection<?>) null);
            for (SVNDirEntry entry : entries) {
                if (entry.getKind() == SVNNodeKind.DIR) {
                    String fullPath = path.isEmpty() ? entry.getName() : path + "/" + entry.getName();
                    folders.add(new SVNFolderItem(entry.getName(), fullPath, true));
                }
            }
        } catch (SVNException e) {
            LoggerUtil.error(e);
            throw e;
        }
        
        return folders;
    }
    
    private List<SVNFolderItem> loadFoldersFromUrl(String url) throws Exception {
        List<SVNFolderItem> folders = new ArrayList<>();
        
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(url);
            SVNRepository repository = SVNRepositoryFactory.create(svnUrl);
            repository.setAuthenticationManager(authManager);
            
            // Get only direct children (level 1)
            Collection<SVNDirEntry> entries = repository.getDir("", -1, null, (Collection<SVNDirEntry>) null);
            
            for (SVNDirEntry entry : entries) {
                if (entry.getKind() == SVNNodeKind.DIR) {
                    String name = entry.getName();
                    String fullPath = entry.getRelativePath();
                    folders.add(new SVNFolderItem(name, fullPath, true));
                }
            }
            
        } catch (Exception e) {
            LoggerUtil.error(e);
            throw e;
        }
        
        return folders;
    }
    
    private void loadSubfoldersIfNeeded(TreeItem<SVNFolderItem> treeItem) {
        if (treeItem.getValue() == null || !treeItem.getValue().isDirectory()) {
            return;
        }
        
        // Check if already has real children (not just placeholders)
        boolean hasRealChildren = false;
        for (TreeItem<SVNFolderItem> child : treeItem.getChildren()) {
            String childName = child.getValue().getName();
            if (!childName.equals("Loading...") && !childName.equals("(No subfolders)") && !childName.equals("Error loading folder")) {
                hasRealChildren = true;
                break;
            }
        }
        
        if (hasRealChildren) {
            return;
        }
        
        expandFolder(treeItem);
    }
    
    private void expandFolder(TreeItem<SVNFolderItem> treeItem) {
        if (treeItem.getValue() == null || !treeItem.getValue().isDirectory()) {
            return;
        }
        
        // Remove any existing placeholder
        treeItem.getChildren().clear();
        
        // Show loading indicator
        TreeItem<SVNFolderItem> loadingItem = new TreeItem<>(new SVNFolderItem("Loading...", "", false));
        treeItem.getChildren().add(loadingItem);
        treeItem.setExpanded(true);
        
        // Build the full URL for this specific folder by concatenating the path
        String folderPath = buildFullPathFromTreeItem(treeItem);
        final String fullUrl;
        if (!folderPath.isEmpty() && !folderPath.equals("/")) {
            String tempUrl = baseUrl;
            if (!tempUrl.endsWith("/")) {
                tempUrl += "/";
            }
            tempUrl += folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
            fullUrl = tempUrl;
        } else {
            fullUrl = baseUrl;
        }
        
        // Load children in background
        Task<List<SVNFolderItem>> loadTask = new Task<List<SVNFolderItem>>() {
            @Override
            protected List<SVNFolderItem> call() throws Exception {
                return loadFoldersFromUrl(fullUrl);
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    treeItem.getChildren().clear();
                    List<SVNFolderItem> folders = getValue();
                    
                    if (folders.isEmpty()) {
                        // No subfolders
                        TreeItem<SVNFolderItem> emptyItem = new TreeItem<>(new SVNFolderItem("(No subfolders)", "", false));
                        treeItem.getChildren().add(emptyItem);
                    } else {
                        // Sort folders alphabetically
                        folders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        
                        for (SVNFolderItem folder : folders) {
                            // Create a new SVNFolderItem with the full path
                            String fullPath = buildFullPathFromTreeItem(treeItem) + "/" + folder.getName();
                            if (fullPath.startsWith("/")) {
                                fullPath = fullPath.substring(1);
                            }
                            SVNFolderItem folderWithPath = new SVNFolderItem(folder.getName(), fullPath, true);
                            
                            TreeItem<SVNFolderItem> item = new TreeItem<>(folderWithPath);
                            
                            // Add placeholder for folders that might have subfolders
                            TreeItem<SVNFolderItem> placeholder = new TreeItem<>(new SVNFolderItem("Loading...", "", false));
                            item.getChildren().add(placeholder);
                            
                            treeItem.getChildren().add(item);
                        }
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    treeItem.getChildren().clear();
                    TreeItem<SVNFolderItem> errorItem = new TreeItem<>(new SVNFolderItem("Error loading folder", "", false));
                    treeItem.getChildren().add(errorItem);
                });
            }
        };
        
        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }
    
    // Optional: expand to reveal matches (keeps tree structure but expands matching branches)
    private boolean expandToMatches(TreeItem<SVNFolderItem> item, String query) {
        if (item == null || item.getValue() == null) return false;
        String name = item.getValue().getName();
        boolean isPlaceholder = "Loading...".equals(name) || 
            "(No subfolders)".equals(name) || 
            "Error loading folder".equals(name);
        
        boolean selfMatches = !isPlaceholder && query != null && !query.isEmpty() && 
            name.toLowerCase().contains(query.toLowerCase());
        
        boolean childMatches = false;
        for (TreeItem<SVNFolderItem> c : item.getChildren()) {
            childMatches |= expandToMatches(c, query);
        }
        item.setExpanded(selfMatches || childMatches);
        return selfMatches || childMatches;
    }
    
    private String buildFullPath(SVNFolderItem item) {
        if (item == null) {
            return null;
        }
        
        String path = item.getFullPath();
        if (path.startsWith("/")) {
            return baseUrl + path;
        } else {
            return baseUrl + "/" + path;
        }
    }
    
    private String buildFullPathFromTreeItem(TreeItem<SVNFolderItem> treeItem) {
        if (treeItem == null || treeItem.getValue() == null) {
            return "";
        }
        
        // Build path by traversing up the tree
        StringBuilder path = new StringBuilder();
        TreeItem<SVNFolderItem> current = treeItem;
        
        while (current != null && current.getValue() != null) {
            String name = current.getValue().getName();
            if (!name.isEmpty() && !name.equals("Repository")) {
                if (path.length() > 0) {
                    path.insert(0, "/");
                }
                path.insert(0, name);
            }
            current = current.getParent();
        }
        
        return path.toString();
    }
    
    private void performSearch() {
        String newSearchText = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        
        if (folderTree.getRoot() != null) {
            // If search text is empty, just refresh to show all items
            if (newSearchText.isEmpty()) {
                currentSearchText = "";
                searchResults.clear();
                currentSearchIndex = -1;
                folderTree.refresh();
                return;
            }
            
            // Check if this is a new search (different text) or continuation of same search
            boolean isNewSearch = !newSearchText.equals(currentSearchText);
            
            if (isNewSearch) {
                // New search - find all matching items
                currentSearchText = newSearchText;
                searchResults.clear();
                currentSearchIndex = -1;
                findSearchResults(folderTree.getRoot());
                
                // Navigate to first occurrence
                if (!searchResults.isEmpty()) {
                    currentSearchIndex = 0;
                    navigateToSearchResult(currentSearchIndex);
                }
            } else {
                // Same search - navigate to next occurrence
                if (!searchResults.isEmpty()) {
                    currentSearchIndex++;
                    if (currentSearchIndex >= searchResults.size()) {
                        currentSearchIndex = 0; // Wrap to first
                    }
                    navigateToSearchResult(currentSearchIndex);
                }
            }
            
            // Always refresh to update highlighting
            folderTree.refresh();
        }
    }
    
    private void findSearchResults(TreeItem<SVNFolderItem> item) {
        if (item == null || item.getValue() == null) {
            return;
        }
        
        String name = item.getValue().getName();
        boolean isPlaceholder = "Loading...".equals(name) || 
            "(No subfolders)".equals(name) || 
            "Error loading folder".equals(name);
        
        if (!isPlaceholder && currentSearchText != null && !currentSearchText.isEmpty()) {
            if (name.toLowerCase().contains(currentSearchText)) {
                searchResults.add(item);
            }
        }
        
        // Recursively search children
        for (TreeItem<SVNFolderItem> child : item.getChildren()) {
            findSearchResults(child);
        }
    }
    
    private void findPreviousOccurrence() {
        if (searchResults.isEmpty()) {
            return;
        }
        
        if (currentSearchIndex > 0) {
            currentSearchIndex--;
        } else {
            currentSearchIndex = searchResults.size() - 1; // Wrap to last
        }
        
        navigateToSearchResult(currentSearchIndex);
        folderTree.refresh(); // Refresh to update highlighting
    }
    
    private void navigateToSearchResult(int index) {
        if (index >= 0 && index < searchResults.size()) {
            TreeItem<SVNFolderItem> targetItem = searchResults.get(index);
            
            // Expand parent path to make item visible (only if it's not already visible)
            TreeItem<SVNFolderItem> parent = targetItem.getParent();
            while (parent != null) {
                if (!parent.isExpanded()) {
                    parent.setExpanded(true);
                }
                parent = parent.getParent();
            }
            
            // Select the item
            folderTree.getSelectionModel().select(targetItem);
            folderTree.scrollTo(folderTree.getRow(targetItem));
        }
    }
    
    private void showError(String title, String message) {
        loadingContainer.getChildren().clear();
        
        Label errorIcon = new Label("⚠️");
        errorIcon.setStyle("-fx-font-size: 32px; -fx-text-fill: #f59e0b;");
        
        Label errorTitle = new Label(title);
        errorTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        
        Label errorMessage = new Label(message);
        errorMessage.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280; -fx-wrap-text: true; -fx-text-alignment: center; -fx-alignment: center;");
        errorMessage.setMaxWidth(450);
        errorMessage.setAlignment(Pos.CENTER);
        
        Button retryButton = new Button("🔄 Retry");
        retryButton.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 6; -fx-font-weight: 500; -fx-cursor: hand;");
        retryButton.setOnAction(e -> {
            loadingContainer.getChildren().clear();
            loadingContainer.getChildren().addAll(progressIndicator, statusLabel);
            loadRepositoryStructure();
        });
        
        VBox errorContainer = new VBox(10);
        errorContainer.setAlignment(Pos.CENTER);
        errorContainer.getChildren().addAll(errorIcon, errorTitle, errorMessage, retryButton);
        
        loadingContainer.getChildren().add(errorContainer);
    }
    
    /**
     * Represents a folder item in the SVN repository
     */
    public static class SVNFolderItem {
        private final String name;
        private final String fullPath;
        private final boolean isDirectory;
        
        public SVNFolderItem(String name, String fullPath, boolean isDirectory) {
            this.name = name;
            this.fullPath = fullPath;
            this.isDirectory = isDirectory;
        }
        
        public String getName() { return name; }
        public String getFullPath() { return fullPath; }
        public boolean isDirectory() { return isDirectory; }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
