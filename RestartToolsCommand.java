package custom;

import com.splwg.oms.fcp.JWSLauncher;
import com.splwg.oms.jbot.JBotCommand;
import com.splwg.oms.client.BaseEnvironmentManager;
import com.splwg.oms.jbot.JBotTool;
import com.splwg.oms.client.util.URLResource;
import com.splwg.oms.client.control.Control;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

public class RestartToolsCommand extends JBotCommand {
    private JTextField antPathField;
    private static final String CACHE_FILE = "cache.properties"; 
    private Map<String, JBotTool> toolMap;
    private String valRes = "";
    public void createAndShowUI() {
        JDialog dialog = new JDialog((JFrame) null, "Tool Manager", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(600, 500);
        dialog.setLayout(new BorderLayout());

        BaseEnvironmentManager bem = BaseEnvironmentManager.getEnvironment();
        toolMap = bem.getTools();

        JProgressBar progressBar = new JProgressBar(0, toolMap.size());
        
        JLabel statusLabel = new JLabel();
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setText("");

        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.add(progressBar);
        progressPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        progressPanel.add(statusLabel);

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel antPathLabel = new JLabel("jconfig Path: ");
        antPathField = new JTextField(30);
        JCheckBox checkBuild = new JCheckBox("Run Build");
        checkBuild.setSelected(true);
        loadCachedPaths();

        configPanel.add(antPathLabel);
        configPanel.add(antPathField);
        configPanel.add(checkBuild);
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(progressPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(configPanel);

        dialog.add(mainPanel, BorderLayout.NORTH);

        if (toolMap != null && !toolMap.isEmpty()) {
            String[] columnNames = {"Select", "XML File", "Tool Class"};
            DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
            JTable toolTable = new JTable(tableModel) {
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnIndex == 0 ? Boolean.class : String.class;
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 0;
                }
            };

            List<String> controlTools = new ArrayList<>(); // To store tools containing "CONTROL"

            // First pass: Add all non-"CONTROL" tools
            for (String toolName : toolMap.keySet()) {
                String toolXML = getXMLName(toolMap.get(toolName));

                if (!toolName.toUpperCase().contains("WORKSPACE") && !"NULL".equals(toolXML)) {
                    if (toolName.toUpperCase().contains("CONTROL")) {
                        controlTools.add(toolName); // Store "CONTROL" tools for later
                    } else {
                        tableModel.addRow(new Object[] { false, toolXML, toolName }); // Add non-"CONTROL" tools now
                    }
                } else {
                    System.out.println("Skipped Tool = " + toolName + " | xmlFile = " + toolXML);
                }
            }

            // Second pass: Add the "CONTROL" tools at the end
            for (String controlTool : controlTools) {
                tableModel.addRow(new Object[] { false, getXMLName(toolMap.get(controlTool)), controlTool });
            }

            TableColumn selectColumn = toolTable.getColumnModel().getColumn(0);
            TableColumn nameColumn = toolTable.getColumnModel().getColumn(1);
            TableColumn fileColumn = toolTable.getColumnModel().getColumn(2);
            selectColumn.setPreferredWidth(40);
            nameColumn.setPreferredWidth(250);
            fileColumn.setPreferredWidth(300);
            JScrollPane scrollPane = new JScrollPane(toolTable);
            dialog.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel();
            JButton initButton = createStylishButton("Start Selected Tools");
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
                                JOptionPane.showMessageDialog(dialog, "Ant config command failed. See log for details.", "Failed", JOptionPane.INFORMATION_MESSAGE);
                                dialog.dispose();
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
                                            JOptionPane.showMessageDialog(dialog,
                                                    "Tool : " + selectedToolName + "\n" + valRes, "Validation Failed",
                                                    JOptionPane.INFORMATION_MESSAGE);
                                            System.out.println("Tool : " + selectedToolName + "\n" + valRes);
                                            continue;
                                        }
                                        if (!handleTool(tool, selectedToolName)) {
                                            JOptionPane.showMessageDialog(dialog,
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
                            JOptionPane.showMessageDialog(dialog, "Selected tools have been started.", "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                            dialog.dispose();
                        }
                        
                        // Modified executeAnt to move publish calls here
                        private boolean executeAnt(String jconfigPath, JLabel statusLabel) {
                            try {
                                publish("Ant config running...");
                                createOrUpdateCacheFile(jconfigPath);
                                ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "cd /d " + jconfigPath + " && " + "ant config");
                                processBuilder.redirectErrorStream(true);

                                Process process = processBuilder.start();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    System.out.println(line);
                                    // Moved publish call here since it's inside SwingWorker now
                                    //publish("Running Ant command: " + line);
                                }
                                return process.waitFor() == 0;
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
                    dialog.dispose();
                }
            });

            buttonPanel.add(initButton);
            buttonPanel.add(cancelButton);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(dialog, "No tools found.", "Error", JOptionPane.ERROR_MESSAGE);
            dialog.dispose();
        }
    }

    private void loadCachedPaths() {
        try (InputStream input = new FileInputStream(CACHE_FILE)) {
            Properties properties = new Properties();
            properties.load(input);
            antPathField.setText(properties.getProperty("jconfigPath", ""));
        } catch (IOException e) {
            System.out.println("Could not load cached paths: " + e.getMessage());
        }
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
        button.setFont(new Font("Arial", Font.PLAIN, 14));
        button.setBackground(Color.WHITE); // White background
        button.setForeground(Color.BLACK); // Black text color
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(200, 25));
        button.setBorder(BorderFactory.createLineBorder(Color.BLACK)); // Black border
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        )); // Add curved appearance
        return button;
    }

    private boolean handleTool(JBotTool tool, String toolname) {
        if (tool != null) {
            System.out.println("\nHandling tool: " + tool.getName());
            try {
                if(!tool.close()) return false;
                clearCache();
                tool.init();
                System.out.println("Restarted tool: " + tool.getName());
                return true;
            } catch (Exception e) {
                System.out.println("Error handling tool " + tool.getName() + ": " + e);
                return false;
            }
        }
        return false;
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

    

    public void execute() {
        System.out.println("RestartToolsCommand starting...");
        SwingUtilities.invokeLater(this::createAndShowUI);
    }
}
