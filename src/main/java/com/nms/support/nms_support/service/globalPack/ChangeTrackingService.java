package com.nms.support.nms_support.service.globalPack;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Control;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.collections.ListChangeListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.Objects;

/**
 * Service to track changes across all tabs and manage save state
 */
public class ChangeTrackingService {
    private static final Logger logger = LoggerUtil.getLogger();
    
    private static ChangeTrackingService instance;
    private final BooleanProperty hasUnsavedChanges = new SimpleBooleanProperty(false);
    private final Map<String, Set<Control>> tabControls = new HashMap<>();
    private final Map<String, Map<Control, Object>> originalValues = new HashMap<>();
    private boolean isLoading = false;
    
    private ChangeTrackingService() {
        // Private constructor for singleton
    }
    
    public static ChangeTrackingService getInstance() {
        if (instance == null) {
            instance = new ChangeTrackingService();
        }
        return instance;
    }
    
    /**
     * Get the property that indicates if there are unsaved changes
     */
    public BooleanProperty hasUnsavedChangesProperty() {
        return hasUnsavedChanges;
    }
    
    /**
     * Check if there are unsaved changes
     */
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges.get();
    }
    
    /**
     * Check if the service is currently in loading mode
     */
    public boolean isLoading() {
        return isLoading;
    }
    
    /**
     * Register a tab with its controls for change tracking
     */
    public void registerTab(String tabName, Set<Control> controls) {
        logger.info("Registering tab for change tracking: " + tabName);
        tabControls.put(tabName, controls);
        originalValues.put(tabName, new HashMap<>());
        
        // Store original values and add listeners
        for (Control control : controls) {
            storeOriginalValue(tabName, control);
            addChangeListener(tabName, control);
        }
    }
    
    /**
     * Add a single control to change tracking for a specific tab
     */
    public void addControlToTab(String tabName, Control control) {
        tabControls.computeIfAbsent(tabName, k -> new HashSet<>()).add(control);
        originalValues.computeIfAbsent(tabName, k -> new HashMap<>());
        
        storeOriginalValue(tabName, control);
        addChangeListener(tabName, control);
    }
    
    /**
     * Store the original value of a control
     */
    private void storeOriginalValue(String tabName, Control control) {
        Object originalValue = getControlValue(control);
        originalValues.get(tabName).put(control, originalValue);
    }
    
    /**
     * Get the current value of a control
     */
    private Object getControlValue(Control control) {
        if (control instanceof TextInputControl) {
            return ((TextInputControl) control).getText();
        } else if (control instanceof ComboBox) {
            return ((ComboBox<?>) control).getValue();
        } else if (control instanceof CheckBox) {
            return ((CheckBox) control).isSelected();
        } else if (control instanceof TableView) {
            // For TableView, we'll track if the items have changed
            return new TableViewSnapshot((TableView<?>) control);
        }
        return null;
    }
    
    /**
     * Add change listener to a control
     */
    private void addChangeListener(String tabName, Control control) {
        if (control instanceof TextInputControl) {
            TextInputControl textControl = (TextInputControl) control;
            textControl.textProperty().addListener((observable, oldValue, newValue) -> {
                checkForChanges(tabName, control, oldValue, newValue);
            });
        } else if (control instanceof ComboBox) {
            ComboBox<?> comboBox = (ComboBox<?>) control;
            comboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                checkForChanges(tabName, control, oldValue, newValue);
            });
        } else if (control instanceof CheckBox) {
            CheckBox checkBox = (CheckBox) control;
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                checkForChanges(tabName, control, oldValue, newValue);
            });
        } else if (control instanceof TableView) {
            TableView<?> tableView = (TableView<?>) control;
            tableView.getItems().addListener((ListChangeListener<Object>) change -> {
                checkForChanges(tabName, control, null, null);
            });
        }
    }
    
    /**
     * Check if a control's value has changed
     */
    private void checkForChanges(String tabName, Control control, Object oldValue, Object newValue) {
        // Don't track changes during loading
        if (isLoading) {
            return;
        }
        
        Object originalValue = originalValues.get(tabName).get(control);
        Object currentValue = getControlValue(control);
        
        boolean hasChanged = !isEqual(originalValue, currentValue);
        
        if (hasChanged) {
            logger.info("Change detected in tab: " + tabName + ", control: " + control.getId());
            hasUnsavedChanges.set(true);
        } else {
            // Check if any other controls have changes
            boolean anyChanges = false;
            for (String tab : tabControls.keySet()) {
                for (Control ctrl : tabControls.get(tab)) {
                    Object origVal = originalValues.get(tab).get(ctrl);
                    Object currVal = getControlValue(ctrl);
                    if (!isEqual(origVal, currVal)) {
                        anyChanges = true;
                        break;
                    }
                }
                if (anyChanges) break;
            }
            hasUnsavedChanges.set(anyChanges);
        }
    }
    
    /**
     * Compare two values for equality
     */
    private boolean isEqual(Object value1, Object value2) {
        if (value1 == null && value2 == null) return true;
        if (value1 == null || value2 == null) return false;
        
        if (value1 instanceof TableViewSnapshot && value2 instanceof TableViewSnapshot) {
            return ((TableViewSnapshot) value1).equals((TableViewSnapshot) value2);
        }
        
        return value1.equals(value2);
    }
    
    /**
     * Mark all changes as saved (reset original values)
     */
    public void markAsSaved() {
        logger.info("Marking all changes as saved");
        
        for (String tabName : tabControls.keySet()) {
            for (Control control : tabControls.get(tabName)) {
                Object currentValue = getControlValue(control);
                originalValues.get(tabName).put(control, currentValue);
            }
        }
        
        hasUnsavedChanges.set(false);
    }
    
    /**
     * Start loading mode - changes won't be tracked during loading
     */
    public void startLoading() {
        logger.info("Starting loading mode - change tracking disabled");
        isLoading = true;
    }
    
    /**
     * End loading mode - changes will be tracked again
     */
    public void endLoading() {
        logger.info("Ending loading mode - change tracking enabled");
        isLoading = false;
        // Update original values to current values after loading
        for (String tabName : tabControls.keySet()) {
            for (Control control : tabControls.get(tabName)) {
                Object currentValue = getControlValue(control);
                originalValues.get(tabName).put(control, currentValue);
            }
        }
        // Use Platform.runLater to ensure UI updates happen after all data loading is complete
        javafx.application.Platform.runLater(() -> {
            hasUnsavedChanges.set(false);
        });
    }
    
    /**
     * Reset changes for a specific tab
     */
    public void resetTabChanges(String tabName) {
        logger.info("Resetting changes for tab: " + tabName);
        
        if (tabControls.containsKey(tabName)) {
            for (Control control : tabControls.get(tabName)) {
                Object originalValue = originalValues.get(tabName).get(control);
                setControlValue(control, originalValue);
            }
            
            // Re-check for any remaining changes
            checkForChanges(tabName, null, null, null);
        }
    }
    
    /**
     * Set a control's value
     */
    private void setControlValue(Control control, Object value) {
        if (control instanceof TextInputControl && value instanceof String) {
            ((TextInputControl) control).setText((String) value);
        } else if (control instanceof ComboBox) {
            ((ComboBox<Object>) control).setValue(value);
        } else if (control instanceof CheckBox && value instanceof Boolean) {
            ((CheckBox) control).setSelected((Boolean) value);
        }
        // Note: TableView restoration would need more complex logic
    }
    
    /**
     * Get the number of tabs with unsaved changes
     */
    public int getTabsWithChangesCount() {
        int count = 0;
        for (String tabName : tabControls.keySet()) {
            boolean hasChanges = false;
            for (Control control : tabControls.get(tabName)) {
                Object originalValue = originalValues.get(tabName).get(control);
                Object currentValue = getControlValue(control);
                if (!isEqual(originalValue, currentValue)) {
                    hasChanges = true;
                    break;
                }
            }
            if (hasChanges) count++;
        }
        return count;
    }
    
    /**
     * Get names of tabs with unsaved changes
     */
    public Set<String> getTabsWithChanges() {
        Set<String> tabsWithChanges = new HashSet<>();
        for (String tabName : tabControls.keySet()) {
            boolean hasChanges = false;
            for (Control control : tabControls.get(tabName)) {
                Object originalValue = originalValues.get(tabName).get(control);
                Object currentValue = getControlValue(control);
                if (!isEqual(originalValue, currentValue)) {
                    hasChanges = true;
                    break;
                }
            }
            if (hasChanges) tabsWithChanges.add(tabName);
        }
        return tabsWithChanges;
    }
    
    /**
     * Inner class to snapshot TableView state
     */
    private static class TableViewSnapshot {
        private final int itemCount;
        private final String itemsHash;
        
        public TableViewSnapshot(TableView<?> tableView) {
            this.itemCount = tableView.getItems().size();
            this.itemsHash = tableView.getItems().toString();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TableViewSnapshot that = (TableViewSnapshot) obj;
            return itemCount == that.itemCount && itemsHash.equals(that.itemsHash);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(itemCount, itemsHash);
        }
    }
}
