package cl.camodev.wosbot.console.enumerable;

/**
 * Enumerates the types of nodes available in the visual Task Builder.
 * Each node type maps to an atomic emulator operation.
 */
public enum EnumTaskFlowNodeType {

    TAP_POINT("Tap Point", "Tap at a specific coordinate"),
    WAIT("Wait", "Pause execution for a duration"),
    SWIPE("Swipe", "Swipe between two points"),
    BACK_BUTTON("Back Button", "Press the Android back button"),
    OCR_READ("OCR Read", "Read text from a screen region"),
    TEMPLATE_SEARCH("Template Search", "Search for an image on screen"),
    NAVIGATE("Navigate", "Ensure correct screen location (Home/World)");

    private final String displayName;
    private final String description;

    EnumTaskFlowNodeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
