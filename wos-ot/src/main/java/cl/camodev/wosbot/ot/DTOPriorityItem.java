package cl.camodev.wosbot.ot;

/**
 * Generic model to represent an item with priority.
 * Can be used for purchase priorities, tasks, events, etc.
 * Each item has a name, a priority and a flag to indicate if it's enabled.
 */
public class DTOPriorityItem {

    private String identifier;  // Unique identifier (enum name)
    private String name;
    private int priority;
    private boolean enabled;

    public DTOPriorityItem(String name, int priority, boolean enabled) {
        this(name, name, priority, enabled);  // Use name as identifier by default
    }

    public DTOPriorityItem(String identifier, String name, int priority, boolean enabled) {
        this.identifier = identifier;
        this.name = name;
        this.priority = priority;
        this.enabled = enabled;
    }

    // Identifier methods
    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    // Name methods
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Priority methods
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // Enabled methods
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Converts the object to String format: identifier:name:priority:enabled
     * Example: "FIRE_CRYSTALS:Fire Crystals:1:true"
     */
    public String toConfigString() {
        return String.format("%s:%s:%d:%b", identifier, name, priority, enabled);
    }

    /**
     * Creates an OTPriorityItem object from a String
     */
    public static DTOPriorityItem fromConfigString(String configString) {
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
                return new DTOPriorityItem(name, name, priority, enabled);
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
                return new DTOPriorityItem(identifier, name, priority, enabled);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return String.format("%s (Priority: %d, %s)",
                name,
                priority,
                enabled ? "Enabled" : "Disabled");
    }
}