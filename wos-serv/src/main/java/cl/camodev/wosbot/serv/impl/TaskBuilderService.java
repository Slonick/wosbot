package cl.camodev.wosbot.serv.impl;

import cl.camodev.utiles.UtilOCR;
import cl.camodev.wosbot.console.enumerable.EnumTaskFlowNodeType;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.ot.TaskFlowDefinition;
import cl.camodev.wosbot.ot.TaskFlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * Service that manages a Task Builder recording session.
 *
 * <p>Bridges the UI (TaskBuilderLayoutController) with the EmulatorManager
 * to enable the "record → execute → capture" flow:</p>
 * <ol>
 *   <li>User picks coordinates/params on a screenshot preview</li>
 *   <li>User finalizes the node → this service executes it on the emulator</li>
 *   <li>Service captures a fresh screenshot for the next step</li>
 * </ol>
 */
public class TaskBuilderService {

    private static final Logger logger = LoggerFactory.getLogger(TaskBuilderService.class);

    /** Sentinel prefix used for user-selected custom template files. */
    private static final String FILE_PREFIX = "file://";

    private final EmulatorManager emuManager;
    private TaskFlowDefinition currentDefinition;
    private String activeEmulatorNumber;

    public TaskBuilderService() {
        this.emuManager = EmulatorManager.getInstance();
    }

    // ========================================================================
    // Session management
    // ========================================================================

    /**
     * Starts a new recording session.
     *
     * @param taskName        Name for the new task
     * @param emulatorNumber  Emulator to use for live preview/execution
     */
    public void startSession(String taskName, String emulatorNumber) {
        this.currentDefinition = new TaskFlowDefinition(taskName);
        this.activeEmulatorNumber = emulatorNumber;
        logger.info("Task Builder session started: '{}' on emulator {}", taskName, emulatorNumber);
    }

    /**
     * Captures a fresh screenshot from the active emulator.
     *
     * @return BufferedImage of the current emulator screen, or null on failure
     */
    public BufferedImage captureScreenshot() {
        if (activeEmulatorNumber == null) {
            logger.warn("No active emulator set for task builder");
            return null;
        }

        try {
            DTORawImage rawImage = emuManager.captureScreenshotViaADB(activeEmulatorNumber);
            if (rawImage != null) {
                return UtilOCR.convertRawImageToBufferedImage(rawImage);
            }
        } catch (Exception e) {
            logger.error("Failed to capture screenshot for task builder", e);
        }
        return null;
    }

    // ========================================================================
    // Node execution
    // ========================================================================

    /**
     * Executes a finalized node on the emulator.
     * After execution, the node is marked as executed.
     *
     * @param node The node to execute
     * @return true if execution succeeded
     */
    public boolean executeNode(TaskFlowNode node) {
        if (activeEmulatorNumber == null) {
            logger.warn("Cannot execute node: no active emulator");
            return false;
        }

        try {
            boolean success = performNodeAction(node);
            if (success) {
                node.setExecuted(true);
                logger.info("Node executed successfully: {}", node.getSummary());
            }
            return success;
        } catch (Exception e) {
            logger.error("Failed to execute node: {}", node.getSummary(), e);
            return false;
        }
    }

    /**
     * Adds a node to the current definition and executes it.
     *
     * @param node The node to add and execute
     * @return true if both add and execute succeeded
     */
    public boolean addAndExecuteNode(TaskFlowNode node) {
        if (currentDefinition == null) {
            logger.warn("No active session. Call startSession() first.");
            return false;
        }
        currentDefinition.addNode(node);
        return executeNode(node);
    }

    /**
     * Adds a node to the current definition without executing it.
     */
    public void addNode(TaskFlowNode node) {
        if (currentDefinition == null) {
            logger.warn("No active session. Call startSession() first.");
            return;
        }
        currentDefinition.addNode(node);
    }

    /**
     * Removes a node from the current definition by index.
     */
    public void removeNode(int index) {
        if (currentDefinition != null) {
            currentDefinition.removeNode(index);
        }
    }

    // ========================================================================
    // Node action dispatch
    // ========================================================================

    /**
     * Performs the actual emulator action for a node.
     */
    private boolean performNodeAction(TaskFlowNode node) {
        return switch (node.getType()) {
            case TAP_POINT       -> executeTap(node);
            case WAIT            -> executeWait(node);
            case SWIPE           -> executeSwipe(node);
            case BACK_BUTTON     -> executeBackButton();
            case OCR_READ        -> executeOcr(node);
            case TEMPLATE_SEARCH -> executeTemplateSearch(node);
            case NAVIGATE        -> { logger.info("Navigate node recorded"); yield true; }
        };
    }

    // ── Tap ────────────────────────────────────────────────────────────────

    private boolean executeTap(TaskFlowNode node) {
        int tlX = node.getParamAsInt("tlX", -1);
        int tlY = node.getParamAsInt("tlY", -1);
        int brX = node.getParamAsInt("brX", -1);
        int brY = node.getParamAsInt("brY", -1);

        if (tlX < 0 || tlY < 0) {
            logger.warn("Invalid tap coordinates: TL({}, {})", tlX, tlY);
            return false;
        }

        int x = (tlX == brX) ? tlX : tlX + new java.util.Random().nextInt(Math.abs(brX - tlX) + 1);
        int y = (tlY == brY) ? tlY : tlY + new java.util.Random().nextInt(Math.abs(brY - tlY) + 1);

        emuManager.tapAtPoint(activeEmulatorNumber, new DTOPoint(x, y));
        return true;
    }

    // ── Wait ───────────────────────────────────────────────────────────────

    private boolean executeWait(TaskFlowNode node) {
        int durationMs = node.getParamAsInt("durationMs", 1000);
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    // ── Swipe ──────────────────────────────────────────────────────────────

    private boolean executeSwipe(TaskFlowNode node) {
        int startX = node.getParamAsInt("startX", -1);
        int startY = node.getParamAsInt("startY", -1);
        int endX   = node.getParamAsInt("endX", -1);
        int endY   = node.getParamAsInt("endY", -1);

        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            logger.warn("Invalid swipe coordinates");
            return false;
        }

        emuManager.executeSwipe(activeEmulatorNumber,
                new DTOPoint(startX, startY), new DTOPoint(endX, endY));
        return true;
    }

    // ── Back Button ────────────────────────────────────────────────────────

    private boolean executeBackButton() {
        emuManager.tapBackButton(activeEmulatorNumber);
        return true;
    }

    // ── OCR ────────────────────────────────────────────────────────────────

    private boolean executeOcr(TaskFlowNode node) {
        int tlX = node.getParamAsInt("tlX", -1);
        int tlY = node.getParamAsInt("tlY", -1);
        int brX = node.getParamAsInt("brX", -1);
        int brY = node.getParamAsInt("brY", -1);

        if (tlX < 0 || tlY < 0 || brX < 0 || brY < 0) {
            logger.warn("OCR node missing region coordinates");
            node.setLastOcrResult("[missing coords]");
            return false;
        }

        try {
            DTORawImage rawImage = emuManager.captureScreenshotViaADB(activeEmulatorNumber);
            if (rawImage == null) {
                node.setLastOcrResult("[no screenshot]");
                return false;
            }
            String ocrText = UtilOCR.ocrFromRegion(rawImage,
                    new DTOPoint(tlX, tlY), new DTOPoint(brX, brY), "eng");
            node.setLastOcrResult(ocrText != null ? ocrText.trim() : "");
            logger.info("OCR result: '{}'", node.getLastOcrResult());
            return true;
        } catch (Exception ex) {
            logger.error("OCR execution failed", ex);
            node.setLastOcrResult("[error: " + ex.getMessage() + "]");
            return false;
        }
    }

    // ── Template Search ────────────────────────────────────────────────────

    private boolean executeTemplateSearch(TaskFlowNode node) {
        String templateName = node.getParam("templatePath");
        if (templateName == null || templateName.isEmpty()) {
            logger.warn("Template Search node missing templatePath");
            setTemplateResult(node, false, 0);
            return false;
        }

        try {
            int threshold       = node.getParamAsInt("threshold", 90);
            double thresholdPct = threshold;
            boolean grayscale   = "true".equals(node.getParam("grayscale"));
            int maxAttempts     = node.getParamAsInt("maxAttempts", 1);
            int delayMs         = node.getParamAsInt("delayMs", 300);

            // Optional search area
            DTOPoint topLeft     = parseSearchAreaPoint(node, "tlX", "tlY");
            DTOPoint bottomRight = parseSearchAreaPoint(node, "brX", "brY");
            boolean hasArea      = topLeft != null && bottomRight != null;

            // Execute the search with retries
            DTOImageSearchResult result = null;
            boolean isCustomFile = templateName.startsWith(FILE_PREFIX);

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                result = isCustomFile
                        ? searchCustomTemplate(templateName, grayscale, hasArea, topLeft, bottomRight, thresholdPct)
                        : searchEnumTemplate(templateName, grayscale, hasArea, topLeft, bottomRight, thresholdPct);

                if (result != null && result.isFound()) break;
                if (attempt < maxAttempts) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }

            // Store result and optionally tap
            boolean found = result != null && result.isFound();
            double matchPct = result != null ? result.getMatchPercentage() : 0;
            setTemplateResult(node, found, matchPct);
            handleTapIfFound(node, result, found);

            logger.info("Template Search '{}': {} (match: {}%)", templateName,
                    found ? "FOUND" : "NOT FOUND", String.format("%.1f", matchPct));
            return true;

        } catch (IllegalArgumentException ex) {
            logger.error("Invalid template name: {}", templateName, ex);
            setTemplateResult(node, false, 0);
            return false;
        } catch (Exception ex) {
            logger.error("Template search execution failed", ex);
            setTemplateResult(node, false, 0);
            return false;
        }
    }

    /**
     * Parses an optional search area coordinate pair from node params.
     * Returns null if either param is missing.
     */
    private DTOPoint parseSearchAreaPoint(TaskFlowNode node, String xKey, String yKey) {
        String xs = node.getParam(xKey);
        String ys = node.getParam(yKey);
        if (xs == null || ys == null) return null;
        try {
            return new DTOPoint(Integer.parseInt(xs), Integer.parseInt(ys));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DTOImageSearchResult searchCustomTemplate(String templateName, boolean grayscale,
                                                       boolean hasArea, DTOPoint topLeft, DTOPoint bottomRight,
                                                       double thresholdPct) {
        String absolutePath = templateName.substring(FILE_PREFIX.length());
        String method = grayscale ? "Grayscale" : "";

        if (hasArea) {
            return grayscale
                    ? emuManager.searchTemplateGrayscaleFromFile(activeEmulatorNumber, absolutePath, topLeft, bottomRight, thresholdPct)
                    : emuManager.searchTemplateFromFile(activeEmulatorNumber, absolutePath, topLeft, bottomRight, thresholdPct);
        } else {
            return grayscale
                    ? emuManager.searchTemplateGrayscaleFromFile(activeEmulatorNumber, absolutePath, thresholdPct)
                    : emuManager.searchTemplateFromFile(activeEmulatorNumber, absolutePath, thresholdPct);
        }
    }

    private DTOImageSearchResult searchEnumTemplate(String templateName, boolean grayscale,
                                                     boolean hasArea, DTOPoint topLeft, DTOPoint bottomRight,
                                                     double thresholdPct) {
        EnumTemplates template = EnumTemplates.valueOf(templateName);

        if (hasArea) {
            return grayscale
                    ? emuManager.searchTemplateGrayscale(activeEmulatorNumber, template, topLeft, bottomRight, thresholdPct)
                    : emuManager.searchTemplate(activeEmulatorNumber, template, topLeft, bottomRight, thresholdPct);
        } else {
            return grayscale
                    ? emuManager.searchTemplateGrayscale(activeEmulatorNumber, template, thresholdPct)
                    : emuManager.searchTemplate(activeEmulatorNumber, template, thresholdPct);
        }
    }

    private void setTemplateResult(TaskFlowNode node, boolean found, double matchPct) {
        node.setParam("__lastSearchFound", String.valueOf(found));
        node.setParam("__lastMatchPct", String.format("%.1f", matchPct));
    }

    private void handleTapIfFound(TaskFlowNode node, DTOImageSearchResult result, boolean found) {
        boolean tapIfFound = "true".equals(node.getParam("tapIfFound"));
        int offsetX = node.getParamAsInt("offsetX", 0);
        int offsetY = node.getParamAsInt("offsetY", 0);

        if (found && tapIfFound && result != null) {
            int tapX = result.getPoint().getX() + offsetX;
            int tapY = result.getPoint().getY() + offsetY;
            emuManager.tapAtPoint(activeEmulatorNumber, new DTOPoint(tapX, tapY));
            node.setParam("__lastTappedAt", tapX + ", " + tapY);
            logger.info("Tapped at {}, {}", tapX, tapY);
        } else {
            node.getParams().remove("__lastTappedAt");
        }
    }

    // ========================================================================
    // Getters & Setters
    // ========================================================================

    public TaskFlowDefinition getCurrentDefinition() {
        return currentDefinition;
    }

    public String getActiveEmulatorNumber() {
        return activeEmulatorNumber;
    }

    public void setActiveEmulatorNumber(String emulatorNumber) {
        this.activeEmulatorNumber = emulatorNumber;
    }

    public boolean hasActiveSession() {
        return currentDefinition != null && activeEmulatorNumber != null;
    }
}
