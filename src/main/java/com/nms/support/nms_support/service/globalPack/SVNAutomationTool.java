package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.controller.BuildAutomation;
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
                JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
                JTextField userField = new JTextField();
                JPasswordField passField = new JPasswordField();
                panel.add(new JLabel("Username:")); panel.add(userField);
                panel.add(new JLabel("Password:")); panel.add(passField);
                int option = JOptionPane.showConfirmDialog(null, panel, "SVN Authentication",
                        JOptionPane.OK_CANCEL_OPTION);
                if (option == JOptionPane.OK_OPTION) {
                    return SVNPasswordAuthentication.newInstance(
                            userField.getText(), new String(passField.getPassword()).toCharArray(),
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

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }


    /** Static checkout utility **/
    public static void performCheckout(String remoteUrl, String localDir, BuildAutomation buildAutomation) throws SVNException {
        SVNClientManager cm = SVNClientManager.newInstance();
        SVNURL svnUrl = SVNURL.parseURIEncoded(remoteUrl);
        File dest = new File(localDir);
        SVNUpdateClient uc = cm.getUpdateClient();
        uc.setIgnoreExternals(false);

        // Thread-safe lists
        java.util.List<String> checkedOutFiles = (java.util.List<String>) Collections.synchronizedList(new ArrayList<String>());
        Set<String> loggedFiles = Collections.synchronizedSet(new HashSet<>());

        // Timer to log new files every 10 seconds
        java.util.Timer timer = new java.util.Timer(true);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                java.util.List<String> newFiles = new ArrayList<>();
                synchronized (checkedOutFiles) {
                    for (String file : checkedOutFiles) {
                        if (!loggedFiles.contains(file)) {
                            newFiles.add(file);
                            loggedFiles.add(file);
                        }
                    }
                }

                if (!newFiles.isEmpty()) {
                    buildAutomation.appendTextToLog("New files checked out:");
                    for (String file : newFiles) {
                        buildAutomation.appendTextToLog("  - " + file);
                    }
                    buildAutomation.appendTextToLog("-------------------------------------");
                }
            }
        }, 10000, 10000); // Run every 10 seconds

        try {
            uc.setEventHandler(new ISVNEventHandler() {
                @Override
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    String path = (event.getFile() != null) ? event.getFile().getAbsolutePath() : "(unknown)";
                    SVNEventAction action = event.getAction();

                    // Only add meaningful events
                    if (action == SVNEventAction.UPDATE_ADD || action == SVNEventAction.UPDATE_UPDATE || action == SVNEventAction.UPDATE_EXTERNAL) {
                        checkedOutFiles.add(path);
                    }
                }

                @Override
                public void checkCancelled() throws SVNCancelException {
                    // Optional: implement cancel support
                }
            });

            uc.doCheckout(svnUrl, dest, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            timer.cancel();
            buildAutomation.appendTextToLog("SVN Checkout Completed.");

            // Final log for remaining files not yet logged
            java.util.List<String> finalUnlogged = new ArrayList<>();
            synchronized (checkedOutFiles) {
                for (String file : checkedOutFiles) {
                    if (!loggedFiles.contains(file)) {
                        finalUnlogged.add(file);
                    }
                }
            }

            if (!finalUnlogged.isEmpty()) {
                buildAutomation.appendTextToLog("Final files checked out:");
                for (String file : finalUnlogged) {
                    buildAutomation.appendTextToLog("  - " + file);
                }
            }
        }
    }


    public static void deleteFolderContents(File folder) throws IOException {
        if (!folder.exists() || !folder.isDirectory()) return;

        // Perform SVN cleanup if .svn folder exists
        File svnMetadata = new File(folder, ".svn");
        if (svnMetadata.exists()) {
            doSVNCleanup(folder);
            try {
                Thread.sleep(500); // Small wait after cleanup
            } catch (InterruptedException ignored) {}
        }

        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                deleteFolderContents(file);
            }

            // Try to make file writable first (for read-only .svn files)
            file.setWritable(true);

            if (!file.delete()) {
                // Try deleting again after small wait
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}

                if (!file.delete()) {
                    LoggerUtil.getLogger().info("Failed to delete file even after retry: " + file.getAbsolutePath());

                }
            }
        }
    }


    public static void doSVNCleanup(File folder) {
        SVNClientManager clientManager = null;
        try {
            clientManager = SVNClientManager.newInstance();
            SVNWCClient wcClient = clientManager.getWCClient();
            wcClient.doCleanup(folder);

            LoggerUtil.getLogger().info("SVN cleanup completed successfully for: " + folder.getAbsolutePath());
        } catch (SVNException e) {
            System.err.println("SVN cleanup failed for: " + folder.getAbsolutePath());
            LoggerUtil.error(e);
        } finally {
            if (clientManager != null) {
                clientManager.dispose();  // VERY IMPORTANT
            }
        }
    }

    public static void performUpdate(String localDir, BuildAutomation buildAutomation) throws SVNException {
        SVNClientManager cm = SVNClientManager.newInstance();
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

            }
        });

        try {
            long revision = uc.doUpdate(dest, SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
            buildAutomation.appendTextToLog("             >> Update completed to revision: " + revision);
        } catch (SVNException e) {
            buildAutomation.appendTextToLog("             >> Update failed: " + e.getMessage());
            LoggerUtil.error(e);
            throw e;
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
