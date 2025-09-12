package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.core.wc.*;

import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import javafx.stage.Window;

public class SVNAutomationTool {

    private final ISVNAuthenticationManager authManager;
    private SVNRepository repository;
    private boolean userCancelled = false;

    public SVNAutomationTool() {
        authManager = SVNWCUtil.createDefaultAuthenticationManager();
        authManager.setAuthenticationProvider(new ISVNAuthenticationProvider() {
            public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm,
                                                                 SVNErrorMessage errorMessage, SVNAuthentication previousAuth,
                                                                 boolean authMayBeStored) {
                // Use JavaFX authentication dialog
                final String[] credentials = new String[2];
                final boolean[] dialogResult = new boolean[1];
                
                Platform.runLater(() -> {
                    try {
                        SVNAuthenticationDialog authDialog = new SVNAuthenticationDialog(null, realm, url.toString());
                        dialogResult[0] = authDialog.showAndWait();
                        if (dialogResult[0]) {
                            credentials[0] = authDialog.getUsername();
                            credentials[1] = authDialog.getPassword();
                        }
                    } catch (Exception e) {
                        LoggerUtil.error(e);
                        dialogResult[0] = false;
                    }
                });
                
                // Wait for dialog result (with timeout)
                long startTime = System.currentTimeMillis();
                while (!dialogResult[0] && (System.currentTimeMillis() - startTime) < 30000) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                if (dialogResult[0] && credentials[0] != null && credentials[1] != null) {
                    return SVNPasswordAuthentication.newInstance(
                            credentials[0], credentials[1].toCharArray(),
                            authMayBeStored, url, false);
                }
                return null;
            }
            public int acceptServerAuthentication(SVNURL url, String realm,
                                                  Object certificate, boolean resultMayBeStored) {
                return ISVNAuthenticationProvider.ACCEPTED;
            }
        });
    }

    /**
     * Gets the authentication manager for SVN operations
     */
    public ISVNAuthenticationManager getAuthManager() {
        return authManager;
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }


    /** Static checkout utility **/
    public static void performCheckout(String remoteUrl, String localDir, ProgressCallback callback) throws SVNException {
        SVNClientManager cm = null;
        try {
            cm = SVNClientManager.newInstance();
            SVNURL svnUrl = SVNURL.parseURIEncoded(remoteUrl);
            File dest = new File(localDir);
            SVNUpdateClient uc = cm.getUpdateClient();
            uc.setIgnoreExternals(false);

            // Enhanced progress tracking with coordinated updates
            final int[] fileCount = {0};
            final int[] totalFiles = {0};
            final long startTime = System.currentTimeMillis();
            final long[] lastProgressUpdate = {0};
            final int[] lastReportedProgress = {0}; // Track last reported progress to prevent fluctuations
            final boolean[] hasRealProgress = {false}; // Track if we have real SVN progress

            // Initial progress update
            if (callback != null) {
                callback.onProgress(0, "Starting SVN checkout from " + remoteUrl + "...");
            }

            uc.setEventHandler(new ISVNEventHandler() {
                @Override
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    // Check for cancellation
                    if (callback != null && callback.isCancelled()) {
                        throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Operation cancelled by user"));
                    }

                    String path = (event.getFile() != null) ? event.getFile().getAbsolutePath() : "(unknown)";
                    SVNEventAction action = event.getAction();

                    // Track all meaningful events for progress
                    if (action == SVNEventAction.UPDATE_ADD || 
                        action == SVNEventAction.UPDATE_UPDATE || 
                        action == SVNEventAction.UPDATE_EXTERNAL ||
                        action == SVNEventAction.UPDATE_DELETE ||
                        action == SVNEventAction.UPDATE_REPLACE) {
                        
                        fileCount[0]++;
                        
                        // Calculate progress percentage with smoothing
                        int progressPercent = 0;
                        if (progress > 0) {
                            hasRealProgress[0] = true; // Mark that we have real SVN progress
                            progressPercent = Math.min((int) (progress * 100), 95); // Cap at 95% until completion
                        } else {
                            // Fallback progress calculation based on file count
                            progressPercent = Math.min(fileCount[0] * 2, 95); // Simple linear progress
                        }
                        
                        // Only update if progress has meaningfully increased (prevent fluctuations)
                        if (progressPercent > lastReportedProgress[0] + 2) { // Minimum 2% increase
                            long currentTime = System.currentTimeMillis();
                            if (fileCount[0] % 5 == 0 || 
                                progress > 0 || 
                                (currentTime - lastProgressUpdate[0]) > 1000) { // Update at least every second
                                
                                String message;
                                if (progress > 0) {
                                    // Real SVN progress available
                                    message = String.format("Checked out %d files (%.1f%% complete)", fileCount[0], progress * 100);
                                } else {
                                    // Fallback progress based on file count
                                    message = String.format("Checked out %d files (processing...)", fileCount[0]);
                                }
                                
                                if (callback != null) {
                                    callback.onProgress(progressPercent, message);
                                    lastProgressUpdate[0] = currentTime;
                                    lastReportedProgress[0] = progressPercent;
                                }
                            }
                        }
                    }
                    
                    // Handle specific events for better progress tracking
                    if (action == SVNEventAction.UPDATE_STARTED) {
                        if (callback != null) {
                            callback.onProgress(5, "SVN checkout started...");
                            lastReportedProgress[0] = 5;
                        }
                    } else if (action == SVNEventAction.UPDATE_COMPLETED) {
                        if (callback != null) {
                            callback.onProgress(95, "SVN checkout finalizing...");
                            lastReportedProgress[0] = 95;
                        }
                    }
                }

                @Override
                public void checkCancelled() throws SVNCancelException {
                    // Check for cancellation
                    if (callback != null && callback.isCancelled()) {
                        throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Operation cancelled by user"));
                    }
                }
            });

            // Perform the checkout
            if (callback != null) {
                callback.onProgress(10, "Connecting to SVN repository...");
            }
            
            // Start a background thread to provide fallback progress updates (only when no real progress)
            final boolean[] checkoutCompleted = {false};
            Thread progressThread = null;
            
            if (callback != null) {
                progressThread = new Thread(() -> {
                    try {
                        int fallbackProgress = 15;
                        while (!checkoutCompleted[0] && !callback.isCancelled()) {
                            Thread.sleep(10000); // Update every 10 seconds
                            if (!checkoutCompleted[0] && !callback.isCancelled()) {
                                // Only use fallback progress if we don't have real SVN progress
                                if (!hasRealProgress[0] && fallbackProgress < lastReportedProgress[0]) {
                                    // Create simple progress message without estimation
                                    String progressMessage;
                                    if (fileCount[0] > 0) {
                                        progressMessage = String.format("SVN checkout in progress... (%d files processed)", fileCount[0]);
                                    } else {
                                        progressMessage = "SVN checkout in progress... (connecting to repository)";
                                    }
                                    
                                    callback.onProgress(fallbackProgress, progressMessage);
                                    fallbackProgress = Math.min(fallbackProgress + 1, 85); // Slower increment, lower cap
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        // Thread interrupted, exit gracefully
                    }
                });
                progressThread.setDaemon(true);
                progressThread.start();
            }
            
            try {
                uc.doCheckout(svnUrl, dest, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false);
            } finally {
                checkoutCompleted[0] = true;
                if (progressThread != null) {
                    progressThread.interrupt();
                }
            }

            // Final progress update
            if (callback != null && !callback.isCancelled()) {
                long duration = System.currentTimeMillis() - startTime;
                String durationText = duration > 1000 ? String.format(" (%.1fs)", duration / 1000.0) : "";
                callback.onProgress(100, "SVN Checkout completed successfully" + durationText);
                callback.onComplete("SVN Checkout completed - " + fileCount[0] + " files processed" + durationText);
            }

        } catch (SVNCancelException e) {
            // Handle cancellation
            if (callback != null) {
                callback.onError("Operation cancelled by user");
            }
            throw e;
        } catch (SVNException e) {
            // Handle specific SVN errors
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_OBSTRUCTED_UPDATE) {
                String errorMsg = "SVN working copy conflict detected. The directory is already a working copy for a different URL. " +
                                "Please clean up the directory manually or use a different location.";
                LoggerUtil.getLogger().severe(errorMsg);
                if (callback != null) {
                    callback.onError(errorMsg);
                }
            } else {
                LoggerUtil.error(e);
                if (callback != null) {
                    callback.onError("SVN Checkout failed: " + e.getMessage());
                }
            }
            throw e;
        } catch (Exception e) {
            LoggerUtil.error(e);
            if (callback != null) {
                callback.onError("SVN Checkout failed: " + e.getMessage());
            }
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e.getMessage()), e);
        } finally {
            // Ensure proper cleanup
            if (cm != null) {
                cm.dispose();
            }
        }
    }


    public static void deleteFolderContents(File folder) throws IOException {
        deleteFolderContents(folder, null);
    }

    public static void deleteFolderContents(File folder, ProgressCallback callback) throws IOException {
        if (!folder.exists() || !folder.isDirectory()) {
            if (callback != null) {
                callback.onError("Folder does not exist or is not a directory");
            }
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            if (callback != null) {
                callback.onComplete("Folder is already empty");
            }
            return;
        }

        int totalFiles = countFilesRecursively(folder);
        int deletedFiles = 0;
        
        // Limit total files to prevent memory issues with extremely large directories
        if (totalFiles > 10000) {
            LoggerUtil.getLogger().warning("Large directory detected (" + totalFiles + " files). Limiting progress updates to prevent memory issues.");
            totalFiles = 10000; // Cap at 10,000 files for progress calculation
        }

        // Perform SVN cleanup if .svn folder exists
        File svnMetadata = new File(folder, ".svn");
        if (svnMetadata.exists()) {
            if (callback != null) {
                callback.onProgress(0, "Performing SVN cleanup...");
            }
            try {
                doSVNCleanup(folder);
            } catch (Exception e) {
                // If SVN cleanup fails, try to remove the .svn directory manually
                LoggerUtil.getLogger().warning("SVN cleanup failed, attempting manual cleanup: " + e.getMessage());
                if (callback != null) {
                    callback.onProgress(10, "Manual SVN cleanup...");
                }
                try {
                    deleteDirectoryRecursively(svnMetadata);
                    LoggerUtil.getLogger().info("Manual SVN cleanup completed");
                } catch (Exception manualEx) {
                    LoggerUtil.getLogger().warning("Manual SVN cleanup also failed: " + manualEx.getMessage());
                    if (callback != null) {
                        callback.onError("Failed to clean up SVN metadata: " + manualEx.getMessage());
                        return;
                    }
                }
            }
            // Check for cancellation after cleanup
            if (callback != null && callback.isCancelled()) {
                callback.onError("Operation cancelled by user");
                return;
            }
        }

        // Delete files with progress updates
        deletedFiles = deleteFilesWithProgress(folder, callback, totalFiles, deletedFiles);
        
        if (callback != null && !callback.isCancelled()) {
            callback.onComplete("Successfully deleted " + deletedFiles + " files/folders");
        }
    }

    private static int deleteFilesWithProgress(File folder, ProgressCallback callback, int totalFiles, int deletedFiles) {
        File[] files = folder.listFiles();
        if (files == null) return deletedFiles;

        for (File file : files) {
            if (callback != null && callback.isCancelled()) {
                callback.onError("Operation cancelled by user");
                return deletedFiles;
            }

            if (file.isDirectory()) {
                deletedFiles = deleteFilesWithProgress(file, callback, totalFiles, deletedFiles);
            }

            // Try to make file writable first (for read-only .svn files)
            file.setWritable(true);

            if (!file.delete()) {
                // Try deleting again with a more efficient retry approach
                int retryCount = 0;
                boolean deleted = false;
                
                while (retryCount < 3 && !deleted) {
                    // Check for cancellation between retries
                    if (callback != null && callback.isCancelled()) {
                        callback.onError("Operation cancelled by user");
                        return deletedFiles;
                    }
                    
                    // Use a more efficient approach instead of Thread.sleep
                    try {
                        // Force garbage collection to release file handles
                        System.gc();
                        deleted = file.delete();
                        retryCount++;
                    } catch (Exception e) {
                        retryCount++;
                    }
                }

                if (!deleted) {
                    String errorMsg = "Failed to delete file after retries: " + file.getAbsolutePath();
                    LoggerUtil.getLogger().info(errorMsg);
                    if (callback != null) {
                        callback.onError(errorMsg);
                        return deletedFiles;
                    }
                }
            }

            deletedFiles++;
            if (callback != null) {
                int progress = (int) ((double) deletedFiles / totalFiles * 100);
                // Limit progress updates to avoid memory issues with large file counts
                if (deletedFiles % 10 == 0 || progress % 5 == 0 || deletedFiles == totalFiles) {
                    String fileName = file.getName();
                    // Truncate long file names to prevent memory issues
                    if (fileName.length() > 50) {
                        fileName = fileName.substring(0, 47) + "...";
                    }
                    callback.onProgress(progress, "Deleted: " + fileName);
                }
            }
        }
        
        return deletedFiles;
    }

    private static int countFilesRecursively(File folder) {
        int count = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countFilesRecursively(file);
                }
                count++;
                
                // Limit counting to prevent memory issues with extremely large directories
                if (count > 15000) {
                    LoggerUtil.getLogger().warning("Directory too large, stopping file count at " + count + " files");
                    return count;
                }
            }
        }
        return count;
    }


    public static void doSVNCleanup(File folder) {
        SVNClientManager clientManager = null;
        try {
            clientManager = SVNClientManager.newInstance();
            SVNWCClient wcClient = clientManager.getWCClient();
            wcClient.doCleanup(folder);

            LoggerUtil.getLogger().info("SVN cleanup completed successfully for: " + folder.getAbsolutePath());
        } catch (SVNException e) {
            // Handle specific SVN errors more gracefully
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_OBSTRUCTED_UPDATE) {
                LoggerUtil.getLogger().warning("SVN working copy conflict detected. Attempting to resolve...");
                try {
                    // Try to resolve the conflict by removing the .svn directory
                    File svnDir = new File(folder, ".svn");
                    if (svnDir.exists()) {
                        deleteDirectoryRecursively(svnDir);
                        LoggerUtil.getLogger().info("Removed conflicting .svn directory: " + svnDir.getAbsolutePath());
                    }
                } catch (Exception cleanupEx) {
                    LoggerUtil.getLogger().warning("Failed to remove conflicting .svn directory: " + cleanupEx.getMessage());
                }
            } else {
                LoggerUtil.getLogger().warning("SVN cleanup failed for: " + folder.getAbsolutePath() + " - " + e.getMessage());
            }
        } finally {
            if (clientManager != null) {
                clientManager.dispose();  // VERY IMPORTANT
            }
        }
    }
    
    private static void deleteDirectoryRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        if (!dir.delete()) {
            LoggerUtil.getLogger().warning("Could not delete: " + dir.getAbsolutePath());
        }
    }

    public static void performUpdate(String localDir, BuildAutomation buildAutomation) throws SVNException {
        SVNClientManager cm = null;
        try {
            cm = SVNClientManager.newInstance();
            File dest = new File(localDir);
            SVNUpdateClient uc = cm.getUpdateClient();
            uc.setIgnoreExternals(false);
            long startTime = System.currentTimeMillis();
            final int[] lastLoggedPercent = {-1};

            uc.setEventHandler(new ISVNEventHandler() {
                @Override
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    String path = event.getFile() != null ? event.getFile().getAbsolutePath() : "(unknown)";
                    SVNEventAction action = event.getAction();

                    int percent = (int) (progress * 100);
                    if (percent - lastLoggedPercent[0] >= 5) {
                        lastLoggedPercent[0] = percent;

                        long elapsed = System.currentTimeMillis() - startTime;
                        double rate = progress > 0 ? (elapsed / progress) : 0;
                        long eta = (long) (rate * (1.0 - progress));
                        String etaFormatted = formatTime(eta);

                        buildAutomation.appendTextToLog(String.format("              >> Update Progress: %3d%% complete, %3d%% remaining, ETA: %s\n",
                                percent, 100 - percent, etaFormatted));
                    }
                }

                @Override
                public void checkCancelled() throws SVNCancelException {
                    // Optional: implement cancel support
                }
            });

            long revision = uc.doUpdate(dest, SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
            buildAutomation.appendTextToLog("             >> Update completed to revision: " + revision);
        } catch (SVNException e) {
            buildAutomation.appendTextToLog("             >> Update failed: " + e.getMessage());
            LoggerUtil.error(e);
            throw e;
        } finally {
            // Ensure proper cleanup
            if (cm != null) {
                cm.dispose();
            }
        }
    }


    /**
     * Shows a modal dialog where user can browse one level at a time,
     * search, and double-click or press Select to choose a folder.
     * Blocks until user selects or cancels.
     *
     * @param fxOwner
     * @param baseUrl  the root SVN URL to browse
     * @return the selected folder URL (full), or null if cancelled
     */
    public String browseAndSelectFolder(Window fxOwner, String baseUrl) throws SVNException {
        // Prepare repository

        java.awt.Window swingParent = SwingUtilities.getWindowAncestor(
                new JFXPanel() {{ setScene(new Scene(new Group())); setVisible(false); }}
        );
        if (swingParent == null && fxOwner instanceof Stage) {
            swingParent = new JFrame();  // fallback if owner conversion fails
        }

        SVNURL svnurl = SVNURL.parseURIEncoded(baseUrl);
        repository = SVNRepositoryFactory.create(svnurl);
        repository.setAuthenticationManager(authManager);

        // Tree model
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode("Loading...");
        root.add(loadingNode);
        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean exp,
                                                          boolean leaf, int row, boolean focus) {
                JLabel lbl = (JLabel) super.getTreeCellRendererComponent(
                        t, value, sel, exp, leaf, row, focus);
                lbl.setFont(new Font("Arial", Font.PLAIN, 14));
                return lbl;
            }
        });

        // Lazy expansion
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            public void treeWillExpand(TreeExpansionEvent ev) throws ExpandVetoException {
                DefaultMutableTreeNode nd = (DefaultMutableTreeNode) ev.getPath().getLastPathComponent();
                if (nd.getChildCount() == 1 && "loading...".equals(nd.getChildAt(0).toString())) {
                    // Cancel automatic expand
                    throw new ExpandVetoException(ev);  // 🚫 veto current expansion

                    // Load children in background and re-expand manually
                    // We'll handle this below (outside the veto)
                }
            }

            public void treeWillCollapse(TreeExpansionEvent ev) {}
        });



        // Double-click to select
        final String[] chosen = new String[1];
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            public void treeExpanded(TreeExpansionEvent event) {}
            public void treeCollapsed(TreeExpansionEvent event) {}
        });

        tree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getChildCount() == 1 && "loading...".equals(node.getChildAt(0).toString())) {
                        node.removeAllChildren();
                        SwingWorker<Void, Void> loader = new SwingWorker<>() {
                            protected Void doInBackground() throws Exception {
                                loadChildren(node, buildPath(node));
                                return null;
                            }

                            protected void done() {
                                SwingUtilities.invokeLater(() -> {
                                    model.reload(node);
                                    tree.expandPath(path); // ✅ expand it after children are loaded
                                });
                            }
                        };
                        loader.execute();
                    }
                }
            }
        });


        // Search field
        JTextField search = new JTextField();
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String txt = search.getText().trim().toLowerCase();
                for (int i = 0; i < tree.getRowCount(); i++) {
                    TreePath p = tree.getPathForRow(i);
                    if (p.getLastPathComponent().toString().toLowerCase().contains(txt)) {
                        tree.expandPath(p.getParentPath());
                        tree.setSelectionPath(p);
                        tree.scrollPathToVisible(p);
                        return;
                    }
                }
                tree.clearSelection();
            }
        });

        // Main dialog
        JDialog dlg = new JDialog( swingParent, "SVN Folder Browser", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout(5, 5));
        dlg.setAlwaysOnTop(true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        top.add(new JLabel("Search:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        dlg.add(top, BorderLayout.NORTH);
        dlg.add(new JScrollPane(tree), BorderLayout.CENTER);

        JButton btnSelect = new JButton("Select");
        btnSelect.addActionListener(e -> {
            TreePath sp = tree.getSelectionPath();
            if (sp != null && !sp.getLastPathComponent().toString().contains("Loading.")) {
                chosen[0] = buildPath((DefaultMutableTreeNode) sp.getLastPathComponent());
                dlg.dispose();
            } else {
                JOptionPane.showMessageDialog(dlg, "Please select a folder.");
            }
        });

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bot.add(btnSelect);
        dlg.add(bot, BorderLayout.SOUTH);

        dlg.setSize(600, 450);
        dlg.setLocationRelativeTo(null);
        // Show dialog after load starts
        // Start loading folders AFTER showing the dialog
        SwingWorker<Void, Void> loader = new SwingWorker<>() {
            protected Void doInBackground() throws Exception {
                root.removeAllChildren();
                loadChildren(root, "");
                return null;
            }

            protected void done() {
                SwingUtilities.invokeLater(() -> model.reload());
            }
        };

        loader.execute();  // Start async load
        dlg.setVisible(true);


        if (chosen[0] == null) return null;
        String sel = chosen[0].startsWith("/") ? chosen[0] : "/" + chosen[0];
        return baseUrl + sel;
    }


    /** Load *one* level of subdirs under given path into parent node **/
    private void loadChildren(DefaultMutableTreeNode parentNode, String parentPath) throws SVNException {

        try {
            Collection<SVNDirEntry> entries = repository.getDir(parentPath, -1, null, (Collection<?>) null);
            for (SVNDirEntry entry : entries) {
                if (entry.getKind() == SVNNodeKind.DIR) {
                    DefaultMutableTreeNode child = new DefaultMutableTreeNode(entry.getName());
                    child.add(new DefaultMutableTreeNode("loading...")); // Lazy load
                    parentNode.add(child);
                }
            }
        } catch (SVNException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
        }
    }

    /** Build slash‑separated path from root to this node **/
    private String buildPath(DefaultMutableTreeNode node){
        Object[] arr = node.getUserObjectPath();
        StringBuilder sb = new StringBuilder();
        for(Object o: arr){
            String s = o.toString();
            if(s.isEmpty()) continue;
            if(sb.length()>0) sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }

    /** Creates a small modal dialog with a centered spinner and Cancel **/
    private JDialog createLoadingDialog(String msg){
        JDialog d = new JDialog((Frame)null, true);
        d.setUndecorated(true);
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setUI(new BasicProgressBarUI());
        p.add(new JLabel(msg, SwingConstants.CENTER), BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        JButton c = new JButton("Cancel");
        c.addActionListener(e->{ userCancelled=true; d.dispose(); });
        JPanel bp=new JPanel(); bp.add(c);
        p.add(bp, BorderLayout.SOUTH);
        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(null);
        d.setAlwaysOnTop(true);
        return d;
    }
}
