package custom;


import com.splwg.oms.jbot.JBotCommand;
import com.splwg.oms.client.BaseEnvironmentManager;
import com.splwg.oms.jbot.JBotTool;
import com.splwg.oms.client.util.URLResource;
import com.splwg.oms.client.control.Control;

import com.splwg.oms.jbot.IDataStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
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
import java.lang.reflect.Field;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import com.splwg.oms.client.login.LoginHelper;
import com.splwg.oms.util.BuildInformation;

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
        JFrame frame = new JFrame("Tool Manager");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(600, 550); // Increased dialog size for better proportions
        frame.setLayout(new BorderLayout(8, 8)); // Reduced margins
        
        // Fix window management - make it a proper application window with minimize/maximize
        frame.setResizable(true);
        frame.setMinimumSize(new Dimension(550, 450));
        frame.setLocationRelativeTo(null);
        
        // Ensure dialog stays on top
        frame.setAlwaysOnTop(true);
        
        // Enable minimize/maximize buttons by setting proper window state
        frame.setUndecorated(false); // Ensure window decorations are shown
        frame.setType(Window.Type.NORMAL); // Normal window type for proper controls
        
        BaseEnvironmentManager bem = BaseEnvironmentManager.getEnvironment();
        toolMap = bem.getTools();

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
        
        gbc.gridx = 1;
        mainContentPanel.add(antPathField, gbc);

        // Right column - Datastores
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel datastoresLabel = new JLabel("Datastores (global):");
        //datastoresLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(datastoresLabel, gbc);
        
        JTextField dsNames = new JTextField(25); // Increased width
        //dsNames.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size
        gbc.gridx = 1;
        mainContentPanel.add(dsNames, gbc);

        // Checkbox spans both columns
        JCheckBox checkBuild = new JCheckBox("Run Build");
        //checkBuild.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        checkBuild.setSelected(true);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        mainContentPanel.add(checkBuild, gbc);

        // Search section - spans both columns
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1;
        JLabel searchLabel = new JLabel("Search:");
        //searchLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(searchLabel, gbc);
        
        JTextField searchField = new JTextField(25); // Increased width
        //searchField.setFont(new Font("Arial", Font.PLAIN, 12)); // Increased font size
        searchField.setToolTipText("Search in all columns (case-insensitive)");
        gbc.gridx = 1;
        mainContentPanel.add(searchField, gbc);

        // Tools section header and reload button
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 1;
        gbc.insets = new Insets(12, 10, 6, 10); // Increased spacing
        JLabel toolsLabel = new JLabel("Available Tools:");
        //toolsLabel.setFont(new Font("Arial", Font.BOLD, 12)); // Increased font size
        mainContentPanel.add(toolsLabel, gbc);
        
        // Add reload button
        JButton reloadButton = createStylishButton("Reload Tools list");
        reloadButton.setPreferredSize(new Dimension(120, 25)); // Increased size
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        mainContentPanel.add(reloadButton, gbc);

        frame.add(mainContentPanel, BorderLayout.NORTH);

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

                if (!toolName.toUpperCase().contains("WORKSPACE") && !"NULL".equals(toolXML)) {
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
            scrollPane.setPreferredSize(new Dimension(550, 280)); // Increased size
            frame.add(scrollPane, BorderLayout.CENTER);

            // Add search functionality
            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    filterTable(searchField.getText());
                }
            });
            
            // Add reload functionality
            reloadButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    reloadToolsTable(frame, statusLabel, searchField);
                }
            });

            // Enhanced button panel with larger buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            
            JButton initButton = createStylishButton("Restart Selected Tools");
            JButton cancelButton = createStylishButton("Cancel");

            initButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String antPath = antPathField.getText();
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
            frame.add(buttonPanel, BorderLayout.SOUTH);
            
            // Load cached paths after UI is set up
            loadCachedPaths();
            
            // Set focus to search field by default instead of jconfig path field
            searchField.setRequestFocusEnabled(true);
            
            frame.setVisible(true);
            
            // Ensure search field gets focus after the frame is visible
            SwingUtilities.invokeLater(() -> {
                searchField.requestFocusInWindow();
            });
        } else {
            JOptionPane.showMessageDialog(frame, "No tools found.", "Error", JOptionPane.ERROR_MESSAGE);
            frame.dispose();
        }
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
                clearCache();
                //System.out.println("before getOwnDataStore = "+tool.getOwnDataStore("DS_WA_ALARMS"));
                tool.clearGlobalDataExceptFor(getExceptList(tool, datastores));
                //resetViewerGlobalProperties();
                //System.out.println("after getOwnDataStore = "+tool.getOwnDataStore("DS_WA_ALARMS"));
                System.out.println("Global datastore cleared");
                if(!tool.close()) return false;
                System.out.println("Tool closed");
                //tool.init();
//                bem.refresh();
                //bem.loadGlobalPrefs("");
//                restoreDefaults(boolean restoreSite, boolean removeFormats, boolean removeLegacyFilters, boolean removeNewFilters,
//                boolean removeSorts, boolean removeTableLayouts, boolean removeFontSize, boolean removeOther)
//                bem.getUserPreferences().restoreDefaults( false, false,
//                 true,  true,
//                false, false, false, false);
                bem.getUserPreferences().clearPrefs();
                System.out.println("BaseEnvironmentManger preferences cleared");
                if(allToolsClosed()) {
                    JBotTool newTool = bem.showTool(tool.getClass().getName(), toolname);
                    //postToolRestart(newTool);
                } else {
                    JBotTool newTool = bem.startTool(tool.getClass().getName(), toolname);
                    //postToolRestart(newTool);
                }
                System.out.println("Restarted tool: " + tool.getName());
                //System.out.println("After restart getOwnDataStore = "+tool.getOwnDataStore("DS_WA_ALARMS"));
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

//    private void postToolRestart(JBotTool tool){
//        try {
//            if (tool instanceof Viewer) {
//                System.out.println("Instance of Viewer");
//                Viewer viewer = (Viewer) tool;
//                viewer.getViewerPanel().getViewerCanvas().init();
//            }
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }
//
//    private void resetViewerGlobalProperties() {
//        try{
//        // Get the Class object representing ViewerCanvas
//        Class<?> clazz = ViewerCanvas.class;
//
//        // Get the Field object representing the "init_static_done" field
//        Field field = clazz.getDeclaredField("init_static_done");
//
//        // Make the field accessible if it is private
//        field.setAccessible(true);
//
//        // Set the static field to false
//        field.set(null, false);
//
//        // Verify the field value
//        boolean fieldValue = (boolean) field.get(null);
//        System.out.println("init_static_done = " + fieldValue);
//    } catch (Exception e) {
//        e.printStackTrace();
//    }
//    }

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

    public void clearCache() {
        try {
            Class<?> propertyManagerClass = Class.forName("com.splwg.oms.jbot.PropertyManager");
            Field cacheField = propertyManagerClass.getDeclaredField("cache");
            cacheField.setAccessible(true);
            HashMap<?, ?> cache = (HashMap<?, ?>) cacheField.get(null);
            cache.clear();
            System.out.println("Cache cleared.");
        } catch (Exception e) {
            System.out.println("Error clearing cache: " + e.getMessage());
        }
    }

    private void filterTable(String searchText) {
        tableModel.setRowCount(0); // Clear current rows
        for (int i = 0; i < originalTableModel.getRowCount(); i++) {
            String toolName = (String) originalTableModel.getValueAt(i, 2);
            String xmlFile = (String) originalTableModel.getValueAt(i, 1);
            boolean matches = toolName.toLowerCase().contains(searchText.toLowerCase()) ||
                              xmlFile.toLowerCase().contains(searchText.toLowerCase());

            if (matches) {
                tableModel.addRow(new Object[] {
                    originalTableModel.getValueAt(i, 0),
                    originalTableModel.getValueAt(i, 1),
                    originalTableModel.getValueAt(i, 2)
                });
            }
        }
    }
    
    private void reloadToolsTable(JFrame frame, JLabel statusLabel, JTextField searchField) {
        try {
            statusLabel.setText("Reloading tools...");
            
            // Store current search text before reloading
            String currentSearchText = searchField.getText();
            
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

                    if (!toolName.toUpperCase().contains("WORKSPACE") && !"NULL".equals(toolXML)) {
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
                
                // Reapply search filter if there was search text
                if (currentSearchText != null && !currentSearchText.trim().isEmpty()) {
                    filterTable(currentSearchText);
                    statusLabel.setText("Tools reloaded successfully. Found " + originalTableModel.getRowCount() + " tools. Filtered to " + tableModel.getRowCount() + " matching tools.");
                } else {
                    statusLabel.setText("Tools reloaded successfully. Found " + originalTableModel.getRowCount() + " tools.");
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
