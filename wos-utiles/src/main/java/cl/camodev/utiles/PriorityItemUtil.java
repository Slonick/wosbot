package cl.camodev.utiles;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.ot.DTOPriorityItem;
import cl.camodev.wosbot.ot.DTOProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic utility to work with priority items in business logic.
 * Allows obtaining sorted and filtered priorities for use in bot tasks.
 */
public class PriorityItemUtil {

    /**
     * Gets priority items from a profile, sorted and only enabled ones.
     *
     * @param profile User profile
     * @param configKey Configuration key
     * @return List of sorted items (only enabled ones)
     */
    public static List<DTOPriorityItem> getEnabledPriorities(DTOProfiles profile, EnumConfigurationKey configKey) {
        String configValue = profile.getConfig(configKey, String.class);

        if (configValue == null || configValue.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return parsePriorities(configValue).stream()
                .filter(DTOPriorityItem::isEnabled)
                .sorted(Comparator.comparingInt(DTOPriorityItem::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Gets all items (enabled and disabled) from a profile.
     *
     * @param profile User profile
     * @param configKey Configuration key
     * @return List of all items sorted by priority
     */
    public static List<DTOPriorityItem> getAllPriorities(DTOProfiles profile, EnumConfigurationKey configKey) {
        String configValue = profile.getConfig(configKey, String.class);

        if (configValue == null || configValue.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return parsePriorities(configValue).stream()
                .sorted(Comparator.comparingInt(DTOPriorityItem::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a specific item is enabled in the priorities.
     *
     * @param profile User profile
     * @param configKey Configuration key
     * @param itemName Name of the item to search for
     * @return true if the item is enabled, false otherwise
     */
    public static boolean isItemEnabled(DTOProfiles profile, EnumConfigurationKey configKey, String itemName) {
        return getEnabledPriorities(profile, configKey).stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(itemName));
    }

    /**
     * Gets the priority of a specific item.
     *
     * @param profile User profile
     * @param configKey Configuration key
     * @param itemName Name of the item
     * @return Item priority (1-N), or -1 if not found
     */
    public static int getItemPriority(DTOProfiles profile, EnumConfigurationKey configKey, String itemName) {
        return getAllPriorities(profile, configKey).stream()
                .filter(p -> p.getName().equalsIgnoreCase(itemName))
                .findFirst()
                .map(DTOPriorityItem::getPriority)
                .orElse(-1);
    }

    /**
     * Converts a configuration String into a list of OTPriorityItem.
     */
    private static List<DTOPriorityItem> parsePriorities(String configString) {
        List<DTOPriorityItem> priorities = new ArrayList<>();

        if (configString == null || configString.trim().isEmpty()) {
            return priorities;
        }

        String[] items = configString.split("\\|");
        for (String item : items) {
            DTOPriorityItem priority = DTOPriorityItem.fromConfigString(item);
            if (priority != null) {
                priorities.add(priority);
            }
        }

        return priorities;
    }
}

