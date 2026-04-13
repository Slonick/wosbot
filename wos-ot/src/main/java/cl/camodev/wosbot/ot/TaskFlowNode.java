package cl.camodev.wosbot.ot;

import cl.camodev.wosbot.console.enumerable.EnumTaskFlowNodeType;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single step/node in a user-built task flow.
 * Each node maps to one atomic emulator operation (tap, wait, swipe, etc.).
 *
 * <p>Parameters are stored in a flexible key-value map to accommodate
 * different node types without requiring type-specific subclasses:</p>
 * <ul>
 *   <li><b>TAP_POINT:</b> "x", "y"</li>
 *   <li><b>WAIT:</b> "durationMs"</li>
 *   <li><b>SWIPE:</b> "startX", "startY", "endX", "endY"</li>
 *   <li><b>BACK_BUTTON:</b> (no params)</li>
 *   <li><b>OCR_READ:</b> "topLeftX", "topLeftY", "bottomRightX", "bottomRightY", "variable"</li>
 *   <li><b>TEMPLATE_SEARCH:</b> "templatePath", "threshold"</li>
 *   <li><b>NAVIGATE:</b> "location" (HOME/WORLD)</li>
 * </ul>
 */
public class TaskFlowNode {

    private int id;
    private EnumTaskFlowNodeType type;
    private Map<String, String> params;
    private boolean executed;

    // Canvas position for the visual flow editor
    private double canvasX;
    private double canvasY;

    // Connection: which node comes next (-1 = none / end)
    private int nextNodeId = -1;
    // Branching: "false" / "No" branch (for condition nodes like OCR_READ)
    private int nextNodeFalseId = -1;
    // Last OCR read result (populated after execute)
    private String lastOcrResult = null;

    public TaskFlowNode() {
        this.params = new HashMap<>();
        this.executed = false;
    }

    public TaskFlowNode(int id, EnumTaskFlowNodeType type) {
        this.id = id;
        this.type = type;
        this.params = new HashMap<>();
        this.executed = false;
    }

    // ========== Getters & Setters ==========

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public EnumTaskFlowNodeType getType() {
        return type;
    }

    public void setType(EnumTaskFlowNodeType type) {
        this.type = type;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public void setParam(String key, String value) {
        this.params.put(key, value);
    }

    public String getParam(String key) {
        return this.params.get(key);
    }

    public int getParamAsInt(String key, int defaultValue) {
        String value = this.params.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public double getCanvasX() {
        return canvasX;
    }

    public void setCanvasX(double canvasX) {
        this.canvasX = canvasX;
    }

    public double getCanvasY() {
        return canvasY;
    }

    public void setCanvasY(double canvasY) {
        this.canvasY = canvasY;
    }

    public int getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(int nextNodeId) {
        this.nextNodeId = nextNodeId;
    }

    public int getNextNodeFalseId() {
        return nextNodeFalseId;
    }

    public void setNextNodeFalseId(int nextNodeFalseId) {
        this.nextNodeFalseId = nextNodeFalseId;
    }

    public String getLastOcrResult() {
        return lastOcrResult;
    }

    public void setLastOcrResult(String lastOcrResult) {
        this.lastOcrResult = lastOcrResult;
    }

    /**
     * Returns a human-readable summary of this node for the steps list.
     * Example: "Tap (350, 920)" or "Wait 1500ms"
     */
    public String getSummary() {
        return switch (type) {
            case TAP_POINT -> String.format("Tap (%s→%s, %s→%s)",
                    getParam("tlX") != null ? getParam("tlX") : "?",
                    getParam("brX") != null ? getParam("brX") : "?",
                    getParam("tlY") != null ? getParam("tlY") : "?",
                    getParam("brY") != null ? getParam("brY") : "?");
            case WAIT -> String.format("Wait %sms", 
                    getParam("durationMs") != null ? getParam("durationMs") : "?");
            case SWIPE -> String.format("Swipe (%s,%s) → (%s,%s)",
                    getParam("startX"), getParam("startY"),
                    getParam("endX"), getParam("endY"));
            case BACK_BUTTON -> "Back Button";
            case OCR_READ -> {
                String cond = getParam("condition") != null ? getParam("condition") : "CONTAINS";
                String expected = getParam("expectedValue") != null ? getParam("expectedValue") : "?";
                yield String.format("OCR (%s, %s)→(%s, %s) %s '%s'",
                        getParam("tlX") != null ? getParam("tlX") : "?",
                        getParam("tlY") != null ? getParam("tlY") : "?",
                        getParam("brX") != null ? getParam("brX") : "?",
                        getParam("brY") != null ? getParam("brY") : "?",
                        cond, expected);
            }
            case TEMPLATE_SEARCH -> {
                String path = getParam("templatePath") != null ? getParam("templatePath") : "?";
                String thresh = getParam("threshold") != null ? getParam("threshold") : "90";
                boolean gs = "true".equals(getParam("grayscale"));
                boolean hasArea = getParam("tlX") != null && getParam("brX") != null;
                String extra = (gs ? " GS" : "") + (hasArea ? " [area]" : " [full]");
                yield String.format("Find: %s @%s%%%s", path, thresh, extra);
            }
            case NAVIGATE -> String.format("Navigate: %s",
                    getParam("location") != null ? getParam("location") : "HOME");
        };
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", id, getSummary());
    }
}
