package custom;


import com.splwg.oms.jbot.JBotCommand;
import com.splwg.oms.client.BaseEnvironmentManager;
import com.splwg.oms.jbot.JBotTool;
import com.splwg.oms.client.util.URLResource;
import com.splwg.oms.client.control.Control;

import com.splwg.oms.jbot.IDataStore;
import com.splwg.oms.jbot.IDataRow;
import com.reloader.HotReloadAgent;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import com.splwg.oms.client.login.LoginHelper;
import com.splwg.oms.util.BuildInformation;

/**
 * RestartToolsCommand - Hot Reload Tool for NMS Application
 *
 * This command provides intelligent hot reload functionality that allows tools to be restarted
 * without requiring a complete application restart. It handles cache invalidation and static
 * field resets to ensure fresh configuration is loaded from XML files.
 *
 * HOT RELOAD MECHANISM:
 * =====================
 *
 * The application uses several caching mechanisms that prevent configuration changes from
 * taking effect without proper cache invalidation:
 *
 * 1. PropertyManager Cache (com.splwg.oms.jbot.PropertyManager)
 *    - Caches property bundles for each tool
 *    - Must be cleared before tool restart
 *    - Handled by: clearCache()
 *
 * 2. Static Initialization Flags (Viewer Components)
 *    - ViewerCanvas.init_static_done
 *    - ViewerCanvasLayerManager.init_static_done
 *    - PowerFlowResultsDataStore.initialized
 *    - Prevent re-initialization of static fields that read global properties
 *    - Must be reset to false before tool restart
 *    - Handled by: resetViewerStaticFlags()
 *
 * 3. Global Properties (JBotTool.globalProperties)
 *    - Loaded from <GlobalProperties> section in tool XML
 *    - Applied via CentricityToolXMLGui.processToolSpecificConfig()
 *    - Automatically reloaded when tool restarts
 *
 * 4. User Preferences Cache
 *    - Cleared via BaseEnvironmentManager.getUserPreferences().clearPrefs()
 *    - Prevents stale UI state
 *
 * USAGE:
 * ======
 * 1. Make changes to tool XML files (e.g., Viewer.xml)
 * 2. Optionally run "ant init create_config" to rebuild configuration
 * 3. Select tools to restart in the UI
 * 4. Click "Restart Selected Tools"
 * 5. Tools will reload with new configuration from XML
 *
 * VIEWER GLOBAL PROPERTIES FLOW:
 * ===============================
 *
 * How viewer renders global properties (mimicked by this hot reload script):
 *
 * 1. Tool XML Parsing (CentricityToolXMLGui):
 *    - Reads <GlobalProperties> from XML
 *    - Populates JBotTool.globalProperties hashtable
 *    - Example: <Property name="viewer.background_color" value="WHITE"/>
 *
 * 2. ViewerPanel Initialization:
 *    - Reads properties via getObjectProperty(name)
 *    - Which calls getParentTool().getGlobalProperties().get(name)
 *    - Sets instance fields
 *
 * 3. ViewerCanvas/ViewerCanvasLayerManager Static Initialization:
 *    - initStatic() methods read global properties
 *    - Set static fields (colors, cache sizes, zoom levels, etc.)
 *    - Only run once due to init_static_done flag
 *    - THIS IS THE KEY ISSUE: Without resetting init_static_done, changes don't apply
 *
 * 4. Hot Reload Solution:
 *    - Clear PropertyManager cache → forces XML re-read
 *    - Reset init_static_done flags → forces initStatic() to run again
 *    - Close and restart tool → new instance with fresh properties
 *    - Result: Global properties from XML are applied without app restart
 */
public class RestartToolsCommand extends JBotCommand {
    private JTextField antPathField;
    private static final String CACHE_FILE = "cache.properties";
    private Map<String, JBotTool> toolMap;
    private String valRes = "";
    private DefaultTableModel originalTableModel;
    private DefaultTableModel tableModel;
    private JTable toolTable;

    public void createAndShowUI() {
        // Create a JFrame instead of JDialog for Windows preview integration
        JFrame frame = new JFrame("Tool Manager & Datastore Viewer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(700, 600); // Increased size for tabs
        frame.setLayout(new BorderLayout());

        // Fix window management - make it a proper application window with minimize/maximize
        frame.setResizable(true);
        frame.setMinimumSize(new Dimension(650, 500));
        frame.setLocationRelativeTo(null);

        // Ensure dialog stays on top
        frame.setAlwaysOnTop(true);

        // Enable minimize/maximize buttons by setting proper window state
        frame.setUndecorated(false); // Ensure window decorations are shown
        frame.setType(Window.Type.NORMAL); // Normal window type for proper controls

        BaseEnvironmentManager bem = BaseEnvironmentManager.getEnvironment();
        toolMap = bem.getTools();

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Tool Restart
        JPanel toolRestartPanel = createToolRestartPanel(frame);
        tabbedPane.addTab("Tool Restart", toolRestartPanel);

        // Tab 2: Datastore Dump
        JPanel datastoreDumpPanel = createDatastoreDumpPanel();
        tabbedPane.addTab("Datastore Dump", datastoreDumpPanel);

        frame.add(tabbedPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private JPanel createToolRestartPanel(JFrame frame) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        // Create main content panel with GridBagLayout for two-column layout
        JPanel mainContentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 10, 4, 10); // Increased padding for better spacing
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Progress section - spans both columns
        JProgressBar progressBar = new JProgressBar(0, toolMap.size());
        progressBar.setPreferredSize(new Dimension(350, 20)); // Increased size

        JLabel statusLabel = new JLabel("Ready");
        //statusLabel.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size

        JLabel progressLabel = new JLabel("Progress:");
        //progressLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        mainContentPanel.add(progressLabel, gbc);

        gbc.gridy = 1;
        mainContentPanel.add(progressBar, gbc);

        gbc.gridy = 2;
        mainContentPanel.add(statusLabel, gbc);

        // Two-column layout for configuration fields
        // Left column
        gbc.gridy = 3; gbc.gridwidth = 1; gbc.gridx = 0;
        JLabel jconfigLabel = new JLabel("jconfig Path:");
        //jconfigLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(jconfigLabel, gbc);

        // Panel for jconfig path and Run Build checkbox
        JPanel jconfigPanel = new JPanel(new BorderLayout(5, 0));
        antPathField = new JTextField(25); // Increased width
        //antPathField.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size

        // Add document listener to save changes to cache file
        antPathField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveToCache(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveToCache(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveToCache(); }

            private void saveToCache() {
                String jconfigPath = antPathField.getText();
                if (!jconfigPath.isEmpty()) {
                    createOrUpdateCacheFile(jconfigPath);
                }
            }
        });
        
        jconfigPanel.add(antPathField, BorderLayout.CENTER);
        
        // Add Run Build checkbox next to jconfig path
        JCheckBox checkBuild = new JCheckBox("Run Build");
        //checkBuild.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        checkBuild.setSelected(true);
        jconfigPanel.add(checkBuild, BorderLayout.EAST);

        gbc.gridx = 1;
        mainContentPanel.add(jconfigPanel, gbc);

        // Right column - Datastores
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel datastoresLabel = new JLabel("Datastores (global):");
        //datastoresLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(datastoresLabel, gbc);

        JTextField dsNames = new JTextField(25); // Increased width
        //dsNames.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size
        gbc.gridx = 1;
        mainContentPanel.add(dsNames, gbc);

        // Add class name input field for hot reload
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1;
        JLabel classNameLabel = new JLabel("Class to Reload:");
        //classNameLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        classNameLabel.setToolTipText("Enter simple class name (e.g., RestartToolsCommand)");
        mainContentPanel.add(classNameLabel, gbc);

        JTextField classNameField = new JTextField(25); // Increased width
        //classNameField.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size
        classNameField.setToolTipText("Simple class name, not fully qualified");
        gbc.gridx = 1;
        mainContentPanel.add(classNameField, gbc);

        // Search section - spans both columns
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1;
        JLabel searchLabel = new JLabel("Search:");
        //searchLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(searchLabel, gbc);

        JTextField searchField = new JTextField(25); // Increased width
        //searchField.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size
        searchField.setToolTipText("Search in all columns (Selected tools always visible)");
        gbc.gridx = 1;
        mainContentPanel.add(searchField, gbc);

        // Tools section header and buttons
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 1;
        gbc.insets = new Insets(12, 10, 6, 10); // Increased spacing
        JLabel toolsLabel = new JLabel("Available Tools:");
        //toolsLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(toolsLabel, gbc);

        // Add button panel for reload and clear selections
        JPanel toolButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton clearSelectionsButton = createStylishButton("Clear Selections");
        clearSelectionsButton.setPreferredSize(new Dimension(120, 25));
        clearSelectionsButton.setToolTipText("Uncheck all selected tools");

        JButton reloadButton = createStylishButton("Reload Tools");
        reloadButton.setPreferredSize(new Dimension(120, 25));
        reloadButton.setToolTipText("Refresh the tools list");

        toolButtonPanel.add(clearSelectionsButton);
        toolButtonPanel.add(reloadButton);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        mainContentPanel.add(toolButtonPanel, gbc);

        panel.add(mainContentPanel, BorderLayout.NORTH);

        if (toolMap != null && !toolMap.isEmpty()) {
            String[] columnNames = {"Select", "XML File", "Tool Class"};
            originalTableModel = new DefaultTableModel(columnNames, 0);
            tableModel = new DefaultTableModel(columnNames, 0);
            toolTable = new JTable(tableModel) {
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnIndex == 0 ? Boolean.class : String.class;
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 0;
                }
            };

            // Enhanced table styling with larger fonts
            toolTable.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size
            toolTable.setRowHeight(24); // Increased row height
            toolTable.setShowGrid(true);
            toolTable.setIntercellSpacing(new Dimension(1, 1));

            // Add a listener to update the checkbox based on row selection
            toolTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    int selectedRow = toolTable.getSelectedRow();
                    int selectedColumn = toolTable.getSelectedColumn();

                    // Ensure a valid row and column are selected
                    if (selectedRow != -1 && selectedColumn > 0) { // Only toggle if the first column (checkbox) is clicked
                        boolean currentValue = (Boolean) tableModel.getValueAt(selectedRow, 0);
                        tableModel.setValueAt(!currentValue, selectedRow, 0);
                    }
                }
            });

            List<String> controlTools = new ArrayList<>(); // To store tools containing "CONTROL"

            // First pass: Add all non-"CONTROL" tools
            for (String toolName : toolMap.keySet()) {
                String toolXML = getXMLName(toolMap.get(toolName));

                if (!toolName.toUpperCase().contains("WORKSPACE") ) {
                    if (toolName.toUpperCase().contains("CONTROL")) {
                        controlTools.add(toolName); // Store "CONTROL" tools for later
                    } else {
                        originalTableModel.addRow(new Object[] { false, toolXML, toolName }); // Add non-"CONTROL" tools now
                    }
                } else {
                    System.out.println("Skipped Tool = " + toolName + " | xmlFile = " + toolXML);
                }
            }

            // Second pass: Add the "CONTROL" tools at the end
            for (String controlTool : controlTools) {
                originalTableModel.addRow(new Object[] { false, getXMLName(toolMap.get(controlTool)), controlTool });
            }

            // Copy all data to the display table model
            for (int i = 0; i < originalTableModel.getRowCount(); i++) {
                tableModel.addRow(new Object[] {
                        originalTableModel.getValueAt(i, 0),
                        originalTableModel.getValueAt(i, 1),
                        originalTableModel.getValueAt(i, 2)
                });
            }

            // Optimized column widths for larger dialog
            TableColumn selectColumn = toolTable.getColumnModel().getColumn(0);
            TableColumn nameColumn = toolTable.getColumnModel().getColumn(1);
            TableColumn fileColumn = toolTable.getColumnModel().getColumn(2);
            selectColumn.setPreferredWidth(50);
            selectColumn.setMaxWidth(60);
            nameColumn.setPreferredWidth(180);
            fileColumn.setPreferredWidth(300);

            JScrollPane scrollPane = new JScrollPane(toolTable);
            scrollPane.setPreferredSize(new Dimension(550, 250)); // Adjusted for tab
            panel.add(scrollPane, BorderLayout.CENTER);

            // Add search functionality with selection preservation
            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    filterTable(searchField.getText());
                    updateSelectionCount(statusLabel);
                }
            });

            // Add reload functionality
            reloadButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    reloadToolsTable(frame, statusLabel, searchField);
                }
            });

            // Add clear selections functionality
            clearSelectionsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    clearAllSelections();
                    // Re-apply current filter to update display
                    filterTable(searchField.getText());
                    updateSelectionCount(statusLabel);
                }
            });

            // Add table model listener to update selection count
            tableModel.addTableModelListener(e -> updateSelectionCount(statusLabel));

            // Enhanced button panel with larger buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

            JButton initButton = createStylishButton("Restart Selected Tools");
            JButton cancelButton = createStylishButton("Cancel");

            initButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String antPath = antPathField.getText();
                    String classToReload = classNameField.getText().trim();
                    progressBar.setIndeterminate(true);
                    statusLabel.setText("Starting selected tools...");

                    // Use SwingWorker for running background tasks
                    new SwingWorker<Void, String>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            if (checkBuild.isSelected() && !executeAnt(antPath, statusLabel)) {
                                JOptionPane.showMessageDialog(frame, "Ant config command failed. See log for details.", "Failed", JOptionPane.INFORMATION_MESSAGE);
                                frame.dispose();
                                return null;
                            }

                            for (int i = 0; i < tableModel.getRowCount(); i++) {
                                Boolean isSelected = (Boolean) tableModel.getValueAt(i, 0);
                                if (isSelected) {
                                    String selectedToolName = (String) tableModel.getValueAt(i, 2);
                                    JBotTool tool = toolMap.get(selectedToolName);
                                    if (tool != null) {
                                        publish("Restarting tool: " + selectedToolName + "...");
                                        if (!validation(tool, selectedToolName)) {
                                            JOptionPane.showMessageDialog(frame,
                                                    "Tool : " + selectedToolName + "\n" + valRes, "Validation Failed",
                                                    JOptionPane.INFORMATION_MESSAGE);
                                            System.out.println("Tool : " + selectedToolName + "\n" + valRes);
                                            continue;
                                        }
                                        if (!handleTool(tool, selectedToolName, dsNames.getText())) {
                                            JOptionPane.showMessageDialog(frame,
                                                    "Tool : " + selectedToolName + "\n" + "check logs", "Initialization Failed",
                                                    JOptionPane.INFORMATION_MESSAGE);
                                        }
                                    }
                                }
                            }

                            // Hot reload class if provided
                            if (!classToReload.isEmpty()) {
                                publish("Reloading class: " + classToReload + "...");
                                reloadClass(classToReload);
                            }

                            return null;
                        }

                        @Override
                        protected void process(java.util.List<String> chunks) {
                            for (String message : chunks) {
                                statusLabel.setText(message);
                            }
                        }

                        @Override
                        protected void done() {
                            statusLabel.setText("All selected tools have been started.");
                            progressBar.setIndeterminate(false);
                            progressBar.setValue(100);
                            JOptionPane.showMessageDialog(frame, "Restart Tools Process Completed.", "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                            frame.dispose();
                        }

                        // Modified executeAnt to move publish calls here
                        private boolean executeAnt(String jconfigPath, JLabel statusLabel) {
                            try {
                                publish("'ant init create_config' running...");
                                createOrUpdateCacheFile(jconfigPath);
                                ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "cd /d " + jconfigPath + " && " + "ant init create_config");
                                processBuilder.redirectErrorStream(true);

                                Process process = processBuilder.start();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    System.out.println(line);
                                    // Moved publish call here since it's inside SwingWorker now
                                    //publish("Running Ant command: " + line);
                                }
                                int resp = process.waitFor();
                                System.out.println(resp);
                                return 0 == resp;
                            } catch (IOException | InterruptedException e) {
                                System.out.println("Error executing Ant command: " + e.getMessage());
                                return false;
                            }
                        }
                    }.execute(); // Start the background task
                }
            });

            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    frame.dispose();
                }
            });

            buttonPanel.add(initButton);
            buttonPanel.add(cancelButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);

            // Load cached paths after UI is set up
            loadCachedPaths();

            // Initialize selection count display
            updateSelectionCount(statusLabel);

            // Set focus to search field by default instead of jconfig path field
            searchField.setRequestFocusEnabled(true);

            frame.setVisible(true);

            // Ensure search field gets focus after the frame is visible
            SwingUtilities.invokeLater(() -> {
                searchField.requestFocusInWindow();
            });
        } else {
            JLabel errorLabel = new JLabel("No tools found.", SwingConstants.CENTER);
            panel.add(errorLabel, BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * Creates the Datastore Dump panel for viewing datastore contents.
     * Allows dumping multiple datastores separated by commas.
     */
    private JPanel createDatastoreDumpPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel for input
        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "Datastore Input",
                0, 0, new Font("Dialog", Font.BOLD, 12)
        ));

        JLabel instructionLabel = new JLabel("Enter Datastore Names (comma-separated):");
        instructionLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        inputPanel.add(instructionLabel, BorderLayout.NORTH);

        JTextField datastoreNamesField = new JTextField();
        datastoreNamesField.setFont(new Font("Consolas", Font.PLAIN, 12));
        datastoreNamesField.setToolTipText("Example: DS_WA_ALARMS, DS_VIEWER_DEFAULT, DS_TABLE_FILTER_CUSTOMER");
        datastoreNamesField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(150, 150, 150), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        inputPanel.add(datastoreNamesField, BorderLayout.CENTER);

        JButton dumpButton = new JButton("Dump Datastores");
        dumpButton.setPreferredSize(new Dimension(140, 28));
        dumpButton.setFont(new Font("Dialog", Font.PLAIN, 11));
        dumpButton.setFocusPainted(false);
        dumpButton.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        inputPanel.add(dumpButton, BorderLayout.EAST);

        panel.add(inputPanel, BorderLayout.NORTH);

        // Center panel for output
        JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 13)); // Larger, cleaner font
        outputArea.setLineWrap(false);
        outputArea.setWrapStyleWord(false);
        outputArea.setBackground(new Color(248, 248, 248)); // Light gray background
        outputArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12)); // Padding

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        )); // Clean border

        panel.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel for status and controls
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));

        JLabel statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 11));

        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JButton clearButton = createStylishButton("Clear Output");
        clearButton.setPreferredSize(new Dimension(130, 28));
        clearButton.setFont(new Font("Dialog", Font.PLAIN, 11));
        clearButton.addActionListener(e -> {
            outputArea.setText("");
            statusLabel.setText("Output cleared");
        });

        JButton copyButton = createStylishButton("Copy to Clipboard");
        copyButton.setPreferredSize(new Dimension(150, 28));
        copyButton.setFont(new Font("Dialog", Font.PLAIN, 11));
        copyButton.addActionListener(e -> {
            if (!outputArea.getText().isEmpty()) {
                java.awt.datatransfer.StringSelection selection =
                        new java.awt.datatransfer.StringSelection(outputArea.getText());
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                statusLabel.setText("Copied to clipboard");
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(copyButton);
        buttonPanel.add(clearButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Add dump functionality
        dumpButton.addActionListener(e -> {
            String datastoreNames = datastoreNamesField.getText().trim();
            if (datastoreNames.isEmpty()) {
                statusLabel.setText("Please enter datastore names");
                return;
            }

            dumpButton.setEnabled(false);
            statusLabel.setText("Dumping datastores...");
            outputArea.setText("");

            // Run dump in background thread
            SwingWorker<String, String> worker = new SwingWorker<String, String>() {
                @Override
                protected String doInBackground() throws Exception {
                    return dumpDatastores(datastoreNames);
                }

                @Override
                protected void done() {
                    try {
                        String result = get();
                        outputArea.setText(result);
                        outputArea.setCaretPosition(0); // Scroll to top

                        // Count datastores
                        String[] names = datastoreNames.split(",");
                        statusLabel.setText("Dumped " + names.length + " datastore(s) - " +
                                outputArea.getLineCount() + " lines");
                    } catch (Exception ex) {
                        outputArea.setText("Error: " + ex.getMessage() + "\n\n" +
                                getStackTraceString(ex));
                        statusLabel.setText("Error occurred during dump");
                    } finally {
                        dumpButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        });

        // Add keyboard shortcut - Enter key in text field triggers dump
        datastoreNamesField.addActionListener(e -> dumpButton.doClick());

        return panel;
    }

    /**
     * Dumps multiple datastores and returns the combined output.
     */
    private String dumpDatastores(String datastoreNames) {
        StringBuilder output = new StringBuilder();
        String[] names = datastoreNames.split(",");
        BaseEnvironmentManager bem = BaseEnvironmentManager.getEnvironment();

        for (String dsName : names) {
            dsName = dsName.trim();
            if (dsName.isEmpty()) continue;

            output.append("\n");
            output.append(repeatString("=", 85)).append("\n");
            output.append("  DATASTORE: ").append(dsName).append("\n");
            output.append(repeatString("=", 85)).append("\n");
            output.append("\n");

            try {
                // Try to get datastore from current tools
                IDataStore ds = null;
                for (JBotTool tool : toolMap.values()) {
                    try {
                        ds = tool.getDataStore(dsName);
                        if (ds != null) break;
                    } catch (Exception e) {
                        // Try next tool
                    }
                }

                if (ds == null) {
                    output.append("  ERROR: Datastore '").append(dsName).append("' not found in any open tool.\n");
                    output.append("     Make sure the tool that owns this datastore is open.\n\n");
                    continue;
                }

                // Dump the datastore
                output.append(dumpDatastore(ds));

            } catch (Exception e) {
                output.append("  ERROR: ").append(e.getMessage()).append("\n");
                output.append("     ").append(getStackTraceString(e).replace("\n", "\n     ")).append("\n");
            }

            output.append("\n");
        }

        if (output.length() == 0) {
            output.append("  INFO: No datastores to dump.\n");
        }

        return output.toString();
    }

    /**
     * Dumps a single datastore using the same logic as DumpDataStoreCommand.
     * Based on AbstractDataStore.dumpInternal()
     */
    private String dumpDatastore(IDataStore dataStore) {
        StringBuilder dump = new StringBuilder();

        try {
            int rowCount = dataStore.getDataRowCount();
            dump.append("  Row Count: ").append(rowCount).append("\n");
            dump.append("  ").append(repeatString("─", 75)).append("\n");
            dump.append("\n");

            if (rowCount == 0) {
                dump.append("  (No rows in datastore)\n");
                return dump.toString();
            }

            for (int index = 0; index < rowCount; index++) {
                IDataRow dataRow = dataStore.getDataRow(index);
                dump.append("  Row ").append(index).append(":\n");
                dump.append(dumpDataRow(dataRow));
                dump.append("\n");
            }

        } catch (Exception e) {
            dump.append("  ERROR dumping datastore: ").append(e.getMessage()).append("\n");
        }

        return dump.toString();
    }

    /**
     * Dumps a single data row.
     * Based on AbstractDataRow.dump()
     */
    private String dumpDataRow(IDataRow dataRow) {
        StringBuilder dump = new StringBuilder();

        try {
            Iterator<String> iter = dataRow.getKeys();
            while (iter.hasNext()) {
                String columnName = iter.next();
                Object columnValue = dataRow.getValue(columnName);

                dump.append("    ").append(columnName).append(" = ");
                if (columnValue != null) {
                    dump.append("\"").append(columnValue).append("\"");
                } else {
                    dump.append("null");
                }
                dump.append("\n");
            }
        } catch (Exception e) {
            dump.append("    ERROR: ").append(e.getMessage()).append("\n");
        }

        return dump.toString();
    }

    /**
     * Helper to get stack trace as string.
     */
    private String getStackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Helper to repeat a string n times (for Java versions without String.repeat).
     */
    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private void loadCachedPaths() {
        // First try to load from cache file
        String jconfigPath = "";
        try (InputStream input = new FileInputStream(CACHE_FILE)) {
            Properties properties = new Properties();
            properties.load(input);
            jconfigPath = properties.getProperty("jconfigPath", "");
        } catch (IOException e) {
            System.out.println("Could not load cached paths: " + e.getMessage());
        }

        // If cache file has no data or file doesn't exist, try to load from properties file
        if (jconfigPath.isEmpty()) {
            try {
                // Get system and project information similar to LoadCredentialsExternalCommand
                String systemCode = (String) LoginHelper.getSystem();
                BuildInformation buildinfo = BuildInformation.getInfo("CLIENT_TOOL");

                String CLIENT_TOOL_PROJECT_NAME = (String)buildinfo.getProperties().get("CLIENT_TOOL_PROJECT_NAME");
                String CLIENT_TOOL_CVS_TAG = (String) buildinfo.getProperties().get("CLIENT_TOOL_CVS_TAG");
                String projectCode = CLIENT_TOOL_PROJECT_NAME+"#"+CLIENT_TOOL_CVS_TAG;

                String user = System.getProperty("user.name");
                String propPath = "C:/Users/" + user + "/Documents/nms_support_data";
                String filePath = propPath + "/cred.properties";

                FileReader file = new FileReader(filePath);
                Properties p = new Properties();
                p.load(file);

                // Construct the property key for jconfigPath
                String jconfigKey = projectCode + "_jconfigPath";
                jconfigPath = p.getProperty(jconfigKey, "");

                file.close();
                System.out.println("Loaded jconfig path from properties file: " + jconfigPath);

                // Save the loaded jconfig path to cache file for future use
                if (!jconfigPath.isEmpty()) {
                    createOrUpdateCacheFile(jconfigPath);
                }

            } catch (Exception e) {
                System.out.println("Exception raised while loading jconfig path from properties file: " + e.getMessage());
            }
        }

        // Set the jconfig path in the field
        antPathField.setText(jconfigPath);
    }

    private void createOrUpdateCacheFile(String jconfigPath) {
        try {
            // Check if the file exists, create if not
            File cacheFile = new File(CACHE_FILE);
            if (!cacheFile.exists()) {
                cacheFile.createNewFile();
            }

            // Load existing properties (if any)
            Properties properties = new Properties();
            try (InputStream input = new FileInputStream(CACHE_FILE)) {
                properties.load(input);
            } catch (IOException e) {
                System.out.println("Could not load existing properties: " + e.getMessage());
            }

            // Add the new parameter if not exists or update it
            properties.setProperty("jconfigPath", jconfigPath);

            // Save the updated properties to the file
            try (OutputStream output = new FileOutputStream(CACHE_FILE)) {
                properties.store(output, null);
            }

        } catch (IOException e) {
            System.out.println("Error creating or updating cache file: " + e.getMessage());
        }
    }


    private JButton createStylishButton(String text) {
        JButton button = new JButton(text);
        //button.setFont(new Font("Arial", Font.BOLD, 13)); // Increased font size for better visibility
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(180, 28)); // Increased button size
        button.setBorder(BorderFactory.createEmptyBorder(4, 15, 4, 15)); // Add padding

        return button;
    }

    private boolean handleTool(JBotTool tool, String toolname, String datastores) {
        BaseEnvironmentManager bem = BaseEnvironmentManager.getEnvironment();
        bem.setEnableShutdown(false);
        if (tool != null) {
            System.out.println("\nHandling tool: " + tool.getName());
            try {
                // Clear PropertyManager cache to reload properties from XML
                clearCache();

                // Reset viewer static initialization flags to force re-reading of global properties
                resetViewerStaticFlags(tool, toolname);

                // Clear global datastores except the ones specified
                tool.clearGlobalDataExceptFor(getExceptList(tool, datastores));
                System.out.println("Global datastore cleared");

                // Close the tool
                if(!tool.close()) return false;
                System.out.println("Tool closed");

                // Clear user preferences to avoid stale UI state
                bem.getUserPreferences().clearPrefs();
                System.out.println("BaseEnvironmentManger preferences cleared");

                // Restart the tool
                if(allToolsClosed()) {
                    JBotTool newTool = bem.showTool(tool.getClass().getName(), toolname);
                } else {
                    JBotTool newTool = bem.startTool(tool.getClass().getName(), toolname);
                }
                System.out.println("Restarted tool: " + tool.getName());

                return true;
            } catch (Exception e) {
                System.out.println("Error handling tool " + tool.getName() + ": " + e);
                e.printStackTrace();
                return false;
            }
            finally {
                bem.setEnableShutdown(true);
            }
        }
        return false;
    }

    /**
     * Resets static initialization flags for Viewer components to force re-reading of global properties.
     * This is essential for hot reload to pick up changes in XML configuration without full app restart.
     *
     * Background:
     * - ViewerCanvas and ViewerCanvasLayerManager have static init_static_done flags
     * - These flags prevent initStatic() from running more than once
     * - initStatic() reads global properties from XML and configures static fields
     * - Without resetting these flags, global property changes won't take effect
     *
     * This mimics the viewer's initialization behavior where global properties are read
     * from the tool's GlobalProperties hashtable and applied to static configuration.
     */
    private void resetViewerStaticFlags(JBotTool tool, String toolname) {
        try {
            String xmlFile = getXMLName(tool);

            // Only reset for viewer-related tools
            if (xmlFile.toUpperCase().equals("VIEWER.XML") ||

                    toolname.toUpperCase().contains("VIEWER")) {

                System.out.println("Resetting static initialization flags for viewer components...");

                // Reset ViewerCanvas init_static_done flag
                // This forces ViewerCanvas.initStatic() to run again, reloading properties like:
                // - viewer.view_history_cache_size, viewer.background_color, viewer.selected_color, etc.
                resetStaticField("com.splwg.oms.client.viewer.ViewerCanvas", "init_static_done");

                // Reset ViewerCanvasLayerManager init_static_done flag
                // This forces ViewerCanvasLayerManager.initStatic() to run again, reloading properties like:
                // - viewer.initial_view_height, viewer.layer_manager_device_cache_size,
                // - viewer.auto_load_on_focus, viewer.background_color, viewer.highlight_color, etc.
                resetStaticField("com.splwg.oms.client.viewer.ViewerCanvasLayerManager", "init_static_done");

                // Reset PowerFlowResultsDataStore if it's initialized
                // This is related to viewer power flow functionality
                resetStaticField("com.splwg.oms.client.viewer.PowerFlowResultsDataStore", "initialized");

                System.out.println("Successfully reset viewer static flags - global properties will be reloaded");
            }
        } catch (Exception e) {
            System.out.println("Warning: Could not reset viewer static flags: " + e.getMessage());
            // Don't fail the restart if this fails - it's an enhancement
        }
    }

    /**
     * Helper method to reset a static boolean field to false using reflection.
     */
    private void resetStaticField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, false);
            boolean fieldValue = (boolean) field.get(null);
            System.out.println("  - " + className + "." + fieldName + " = " + fieldValue);
        } catch (ClassNotFoundException e) {
            System.out.println("  - Class not found: " + className + " (this is OK if not using viewer)");
        } catch (NoSuchFieldException e) {
            System.out.println("  - Field not found: " + className + "." + fieldName);
        } catch (Exception e) {
            System.out.println("  - Error resetting " + className + "." + fieldName + ": " + e.getMessage());
        }
    }

    /**
     * Hot reload a class using the hotreload-agent.jar Java agent.
     * This method triggers class reloading for the specified simple class name.
     * All exceptions are caught and logged silently without failing the hot reload process.
     * 
     * Searches for the class file recursively in java\working\config and constructs
     * the fully qualified class name based on the directory structure.
     * 
     * Example: If class file is at "java\working\config\custom\viewer\SetLocationNoteTypeCommand.class"
     *          The fully qualified name will be "custom.viewer.SetLocationNoteTypeCommand"
     * 
     * @param simpleClassName Simple class name (e.g., "SetLocationNoteTypeCommand")
     */
    private void reloadClass(String simpleClassName) {
        try {
            System.out.println("Attempting to hot reload class: " + simpleClassName);
            
            // Check if agent is active
            if (!HotReloadAgent.isAgentActive()) {
                System.out.println("Warning: HotReloadAgent is not active - " +
                        "make sure hotreload-agent.jar is loaded with -javaagent");
                return;
            }
            
            // Find the class file and get its package info
            String classFileName = simpleClassName + ".class";
            ClassFileInfo classInfo = findClassFileRecursive(classFileName);
            
            if (classInfo == null || classInfo.classFile == null) {
                System.out.println("Warning: Class file not found for " + simpleClassName + 
                        " - skipping hot reload");
                return;
            }
            
            String fullClassName = classInfo.fullClassName;
            System.out.println("Reloading: " + fullClassName);
            
            // Verify the class is already loaded
            try {
                Class.forName(fullClassName);
            } catch (ClassNotFoundException e) {
                System.out.println("Class not loaded yet: " + fullClassName);
                return;
            }
            
            // SIMPLE - just call the API!
            boolean success = HotReloadAgent.triggerReload(fullClassName);
            
            if (success) {
                System.out.println("✓ Successfully reloaded: " + fullClassName);
            } else {
                System.out.println("✗ Reload failed for: " + fullClassName);
            }
            
        } catch (Exception e) {
            System.out.println("Error reloading class: " + e.getMessage());
        }
    }
    
    /**
     * Inner class to hold class file information including the file and its fully qualified name.
     */
    private static class ClassFileInfo {
        java.io.File classFile;
        String fullClassName;
        
        ClassFileInfo(java.io.File classFile, String fullClassName) {
            this.classFile = classFile;
            this.fullClassName = fullClassName;
        }
    }
    
    /**
     * Find a class file recursively in the Oracle NMS java/working/config directory
     * and construct the fully qualified class name based on directory structure.
     * 
     * ONLY searches in java\working\config directory.
     * Example: custom\viewer\SetLocationNoteTypeCommand.class -> custom.viewer.SetLocationNoteTypeCommand
     * 
     * @param classFileName Simple class file name (e.g., "SetLocationNoteTypeCommand.class")
     * @return ClassFileInfo containing the file and fully qualified class name, or null if not found
     */
    private ClassFileInfo findClassFileRecursive(String classFileName) {
        try {
            // ONLY search in java/working/config directory (Oracle NMS standard location)
            String searchRoot = "java\\working\\config";
            java.io.File rootDir = new java.io.File(searchRoot);
            
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                System.out.println("Directory not found: " + rootDir.getAbsolutePath());
                return null;
            }
            
            // Search recursively in the config directory
            ClassFileInfo result = searchDirectory(rootDir, classFileName, rootDir);
            
            if (result == null) {
                System.out.println("Class file " + classFileName + " not found in " + searchRoot);
            }
            
            return result;
            
        } catch (Exception e) {
            System.out.println("Exception while searching for class file: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Recursively search for a class file in a directory and construct its fully qualified name.
     * 
     * @param currentDir Current directory being searched
     * @param classFileName Class file name to find
     * @param rootDir Root directory (config folder) for calculating relative path
     * @return ClassFileInfo if found, null otherwise
     */
    private ClassFileInfo searchDirectory(java.io.File currentDir, String classFileName, java.io.File rootDir) {
        try {
            java.io.File[] files = currentDir.listFiles();
            if (files == null) {
                return null;
            }
            
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    // Recursively search subdirectories
                    ClassFileInfo result = searchDirectory(file, classFileName, rootDir);
                    if (result != null) {
                        return result;
                    }
                } else if (file.isFile() && file.getName().equals(classFileName)) {
                    // Found the class file - construct fully qualified name
                    String fullClassName = constructFullyQualifiedName(file, rootDir, classFileName);
                    return new ClassFileInfo(file, fullClassName);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            System.out.println("Exception during directory search: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Construct the fully qualified class name from the file path.
     * Converts the relative path from config root to package notation.
     * 
     * Example: config/custom/viewer/SetLocationNoteTypeCommand.class -> custom.viewer.SetLocationNoteTypeCommand
     * 
     * @param classFile The class file
     * @param rootDir The root config directory
     * @param classFileName The simple class file name
     * @return Fully qualified class name
     */
    private String constructFullyQualifiedName(java.io.File classFile, java.io.File rootDir, String classFileName) {
        try {
            // Get the absolute paths
            String classFilePath = classFile.getAbsolutePath();
            String rootDirPath = rootDir.getAbsolutePath();
            
            // Get relative path from root to class file
            String relativePath = classFilePath.substring(rootDirPath.length());
            
            // Remove leading separator and trailing .class filename
            relativePath = relativePath.replace("\\", "/"); // Normalize to forward slashes
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            
            // Remove the .class filename to get just the package path
            int lastSlash = relativePath.lastIndexOf("/");
            String packagePath = lastSlash > 0 ? relativePath.substring(0, lastSlash) : "";
            
            // Convert path separators to dots for package name
            String packageName = packagePath.replace("/", ".");
            
            // Get simple class name without .class extension
            String simpleClassName = classFileName.replace(".class", "");
            
            // Construct fully qualified name
            if (packageName.isEmpty()) {
                return simpleClassName; // Default package (unlikely but handle it)
            } else {
                return packageName + "." + simpleClassName;
            }
            
        } catch (Exception e) {
            System.out.println("Error constructing fully qualified name: " + e.getMessage());
            // Fallback to simple class name
            return classFileName.replace(".class", "");
        }
    }


    private boolean allToolsClosed(){
        int c= 0;
        for (Window w : Window.getWindows()) {
            if (w.isVisible()) {
                c++;
            }
        }
        return c <= 1;
    }

    private IDataStore[] getExceptList(JBotTool tool, String datastores) {
        Map<String, IDataStore> globalMap = tool.getGlobalDataStoreMap();
        Map<String, IDataStore> localMap = tool.getLocalDataStoreMap();

        // Print keys in the global map
//        System.out.println("\nGlobal DataStore Keys:");
//        for (String key : globalMap.keySet()) {
//            System.out.println("\t"+key);
//        }
//
//        // Print keys in the local map
//        System.out.println("\nLocal DataStore Keys:");
//        for (String key : localMap.keySet()) {
//            System.out.println("\t"+key);
//        }

        // Create a list to store IDataStore values that are not in the excludeKeys
        List<IDataStore> result = new ArrayList<>();

        List<String> excludeKeys = Arrays.asList(datastores.split(","));


        // Iterate over globalMap and add entries not in excludeKeys
        for (Map.Entry<String, IDataStore> entry : globalMap.entrySet()) {
            if (!excludeKeys.contains(entry.getKey())) {
                result.add(entry.getValue());
            }
        }

        // Return the result list as an array
        return result.toArray(new IDataStore[0]);
    }

    private boolean validation(JBotTool tool, String toolname) {
        valRes = "";
        System.out.println("Checking validation - "+getXMLName(tool));
        if (getXMLName(tool).toUpperCase().equals("VIEWER.XML") || getXMLName(tool).toUpperCase().equals("WORKAGENDA.XML")) {
            System.out.println("Checking validation");
            if (!checkClass(Control.class.getName())) {
                valRes += "Missing control class : Keep control tool open and try again.\n";
                return false;
            }
            else {
                return true;
            }
        }

        return true;
    }

    private boolean checkClass(String classname) {
        System.err.println("checkClass = "+classname);
        return toolMap.containsKey(classname);
    }

    private String getXMLName(JBotTool tool)  {
        String xml_file = fromLastDotOnwards(tool.getName()) + ".xml";
        InputStream istream = URLResource.getResourceAsStream(tool.getClass(), xml_file);
        if (istream == null) {
            xml_file = fromLastDotOnwards(tool.getClass().getName()) + ".xml";
            istream = URLResource.getResourceAsStream(tool.getClass(), xml_file);
            if (istream == null)
                return "NULL";
        }
        return xml_file;
    }
    private static String fromLastDotOnwards(String xml_file) {
        String baseName;
        int idx = xml_file.lastIndexOf('.');
        if (idx != -1) {
            baseName = xml_file.substring(idx + 1);
        } else {
            baseName = xml_file;
        }
        return baseName;
    }

    /**
     * Clears the PropertyManager cache to force reloading of properties from XML files.
     *
     * Background:
     * - PropertyManager maintains a static cache of property bundles for each tool
     * - This cache is keyed by (toolname, parentManager, includeFiles)
     * - When a tool restarts, it needs fresh properties from the updated XML
     * - Without clearing this cache, old property values would persist
     *
     * The cache stores:
     * - Tool-specific properties from CentricityTool.properties
     * - XML file references and property bundles
     * - Dialog property managers
     *
     * This works in conjunction with resetViewerStaticFlags() to ensure complete reload:
     * 1. clearCache() - forces PropertyManager to reload from XML
     * 2. resetViewerStaticFlags() - forces static initialization code to re-run
     * 3. tool.close() + tool.init() - reinitializes the tool with new properties
     */
    public void clearCache() {
        try {
            Class<?> propertyManagerClass = Class.forName("com.splwg.oms.jbot.PropertyManager");
            Field cacheField = propertyManagerClass.getDeclaredField("cache");
            cacheField.setAccessible(true);
            HashMap<?, ?> cache = (HashMap<?, ?>) cacheField.get(null);
            int cacheSize = cache.size();
            cache.clear();
            System.out.println("PropertyManager cache cleared (" + cacheSize + " entries removed)");
        } catch (Exception e) {
            System.out.println("Error clearing PropertyManager cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears all checkbox selections in both the display and original table models.
     */
    private void clearAllSelections() {
        // Clear selections in original model
        for (int i = 0; i < originalTableModel.getRowCount(); i++) {
            originalTableModel.setValueAt(Boolean.FALSE, i, 0);
        }
        // Clear selections in display model
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(Boolean.FALSE, i, 0);
        }
    }

    /**
     * Updates the status label to show selection count and current view state.
     */
    private void updateSelectionCount(JLabel statusLabel) {
        int selectedCount = 0;
        for (int i = 0; i < originalTableModel.getRowCount(); i++) {
            Boolean isSelected = (Boolean) originalTableModel.getValueAt(i, 0);
            if (isSelected != null && isSelected) {
                selectedCount++;
            }
        }

        int totalTools = originalTableModel.getRowCount();
        int visibleTools = tableModel.getRowCount();

        if (selectedCount > 0) {
            if (visibleTools < totalTools) {
                statusLabel.setText(selectedCount + " tool(s) selected | Showing " + visibleTools + " of " + totalTools + " tools");
            } else {
                statusLabel.setText(selectedCount + " tool(s) selected | " + totalTools + " tools available");
            }
        } else {
            if (visibleTools < totalTools) {
                statusLabel.setText("Showing " + visibleTools + " of " + totalTools + " tools");
            } else {
                statusLabel.setText(totalTools + " tools available");
            }
        }
    }

    /**
     * Filters the table based on search text while preserving checkbox selections.
     *
     * Behavior:
     * - Tools matching the search text are shown
     * - Selected tools are ALWAYS shown (regardless of search match)
     * - Checkbox state is preserved across searches
     * - Empty search shows all tools
     */
    private void filterTable(String searchText) {
        // Store current selection state
        Map<String, Boolean> selectionState = new HashMap<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String toolName = (String) tableModel.getValueAt(i, 2);
            Boolean isSelected = (Boolean) tableModel.getValueAt(i, 0);
            if (isSelected != null && isSelected) {
                selectionState.put(toolName, true);
            }
        }

        // Clear and rebuild table
        tableModel.setRowCount(0);
        boolean isSearching = searchText != null && !searchText.trim().isEmpty();

        for (int i = 0; i < originalTableModel.getRowCount(); i++) {
            String toolName = (String) originalTableModel.getValueAt(i, 2);
            String xmlFile = (String) originalTableModel.getValueAt(i, 1);
            Boolean originalSelection = (Boolean) originalTableModel.getValueAt(i, 0);

            // Determine if row should be shown
            boolean isSelected = selectionState.containsKey(toolName);
            boolean matchesSearch = !isSearching ||
                    toolName.toLowerCase().contains(searchText.toLowerCase()) ||
                    xmlFile.toLowerCase().contains(searchText.toLowerCase());

            // Show if: matches search OR is selected (selected tools always visible)
            if (matchesSearch || isSelected) {
                tableModel.addRow(new Object[] {
                        isSelected ? Boolean.TRUE : originalSelection,
                        xmlFile,
                        toolName
                });
            }
        }
    }

    /**
     * Reloads the tools table while preserving checkbox selections.
     * This allows users to keep their selections even after reloading the list.
     */
    private void reloadToolsTable(JFrame frame, JLabel statusLabel, JTextField searchField) {
        try {
            statusLabel.setText("Reloading tools...");

            // Store current search text and selection state before reloading
            String currentSearchText = searchField.getText();
            Map<String, Boolean> selectionState = new HashMap<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String toolName = (String) tableModel.getValueAt(i, 2);
                Boolean isSelected = (Boolean) tableModel.getValueAt(i, 0);
                if (isSelected != null && isSelected) {
                    selectionState.put(toolName, true);
                }
            }

            // Get fresh tool instances
            BaseEnvironmentManager bem = BaseEnvironmentManager.getEnvironment();
            toolMap = bem.getTools();

            // Clear existing data
            originalTableModel.setRowCount(0);
            tableModel.setRowCount(0);

            if (toolMap != null && !toolMap.isEmpty()) {
                List<String> controlTools = new ArrayList<>(); // To store tools containing "CONTROL"

                // First pass: Add all non-"CONTROL" tools
                for (String toolName : toolMap.keySet()) {
                    String toolXML = getXMLName(toolMap.get(toolName));

                    if (!toolName.toUpperCase().contains("WORKSPACE") ) {
                        if (toolName.toUpperCase().contains("CONTROL")) {
                            controlTools.add(toolName); // Store "CONTROL" tools for later
                        } else {
                            // Restore selection state if tool was previously selected
                            boolean wasSelected = selectionState.containsKey(toolName);
                            originalTableModel.addRow(new Object[] { wasSelected, toolXML, toolName });
                        }
                    } else {
                        System.out.println("Skipped Tool = " + toolName + " | xmlFile = " + toolXML);
                    }
                }

                // Second pass: Add the "CONTROL" tools at the end
                for (String controlTool : controlTools) {
                    boolean wasSelected = selectionState.containsKey(controlTool);
                    originalTableModel.addRow(new Object[] { wasSelected, getXMLName(toolMap.get(controlTool)), controlTool });
                }

                // Copy all data to the display table model (will be filtered if needed)
                for (int i = 0; i < originalTableModel.getRowCount(); i++) {
                    tableModel.addRow(new Object[] {
                            originalTableModel.getValueAt(i, 0),
                            originalTableModel.getValueAt(i, 1),
                            originalTableModel.getValueAt(i, 2)
                    });
                }

                // Reapply search filter if there was search text
                if (currentSearchText != null && !currentSearchText.trim().isEmpty()) {
                    filterTable(currentSearchText);
                    int selectedCount = selectionState.size();
                    statusLabel.setText("Tools reloaded. Found " + originalTableModel.getRowCount() + " tools (" + selectedCount + " selected). Showing " + tableModel.getRowCount() + " matching.");
                } else {
                    int selectedCount = selectionState.size();
                    statusLabel.setText("Tools reloaded successfully. Found " + originalTableModel.getRowCount() + " tools" + (selectedCount > 0 ? " (" + selectedCount + " selected)" : "") + ".");
                }
            } else {
                statusLabel.setText("No tools found.");
            }

        } catch (Exception e) {
            statusLabel.setText("Error reloading tools: " + e.getMessage());
            System.out.println("Error reloading tools: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void execute() {
        System.out.println("RestartToolsCommand starting...");
        SwingUtilities.invokeLater(this::createAndShowUI);
    }
}
