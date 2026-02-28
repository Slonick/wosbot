package cl.camodev.wosbot.common.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Generic model to represent an item with priority.
 * Can be used for purchase priorities, tasks, events, etc.
 * Each item has a name, a priority and a flag to indicate if it's enabled.
 */
public class PriorityItem {

    private final StringProperty identifier;  // Unique identifier (enum name)
    private final StringProperty name;
    private final IntegerProperty priority;
    private final BooleanProperty enabled;

    public PriorityItem(String name, int priority, boolean enabled) {
        this(name, name, priority, enabled);  // Use name as identifier by default
    }

    public PriorityItem(String identifier, String name, int priority, boolean enabled) {
        this.identifier = new SimpleStringProperty(identifier);
        this.name = new SimpleStringProperty(name);
        this.priority = new SimpleIntegerProperty(priority);
        this.enabled = new SimpleBooleanProperty(enabled);
    }

    // Identifier property methods
    public String getIdentifier() {
        return identifier.get();
    }

    public void setIdentifier(String identifier) {
        this.identifier.set(identifier);
    }

    public StringProperty identifierProperty() {
        return identifier;
    }

    // Name property methods
    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    // Priority property methods
    public int getPriority() {
        return priority.get();
    }

    public void setPriority(int priority) {
        this.priority.set(priority);
    }

    public IntegerProperty priorityProperty() {
        return priority;
    }

    // Enabled property methods
    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    /**
     * Converts the object to String format: identifier:name:priority:enabled
     * Example: "FIRE_CRYSTALS:Fire Crystals:1:true"
     */
    public String toConfigString() {
        return String.format("%s:%s:%d:%b", identifier.get(), name.get(), priority.get(), enabled.get());
    }

    /**
     * Creates a PriorityItem object from a String
     */
    public static PriorityItem fromConfigString(String configString) {
        if (configString == null || configString.trim().isEmpty()) {
            return null;
        }

        String[] parts = configString.split(":");

        // Support both old format (name:priority:enabled) and new format (identifier:name:priority:enabled)
        if (parts.length == 3) {
            // Old format - use name as identifier
            try {
                String name = parts[0];
                int priority = Integer.parseInt(parts[1]);
                boolean enabled = Boolean.parseBoolean(parts[2]);
                return new PriorityItem(name, name, priority, enabled);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (parts.length == 4) {
            // New format with identifier
            try {
                String identifier = parts[0];
                String name = parts[1];
                int priority = Integer.parseInt(parts[2]);
                boolean enabled = Boolean.parseBoolean(parts[3]);
                return new PriorityItem(identifier, name, priority, enabled);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return String.format("%s (Priority: %d, %s)",
            name.get(),
            priority.get(),
            enabled.get() ? "Enabled" : "Disabled");
    }
}
