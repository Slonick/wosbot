package cl.camodev.wosbot.console.enumerable;

/**
 * Interface that must be implemented by enums that define items for prioritized lists.
 * This allows the AbstractProfileController to automatically handle the merge logic
 * between saved configurations and enum items.
 */
public interface PrioritizableItem {

    /**
     * Gets the unique identifier for this item (typically the enum name)
     * @return The identifier used for matching with saved data
     */
    String getIdentifier();

    /**
     * Gets the display name shown in the UI
     * @return The user-friendly display name
     */
    String getDisplayName();
}

