package cl.camodev.wosbot.serv.impl;

import cl.camodev.wosbot.console.enumerable.EnumTaskFlowNodeType;
import cl.camodev.wosbot.ot.TaskFlowDefinition;
import cl.camodev.wosbot.ot.TaskFlowNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a compilable Java source file from a {@link TaskFlowDefinition}.
 *
 * <p>The generated class extends {@code DelayedTask} and implements a
 * state-machine that walks the node DAG at runtime, supporting
 * branching nodes (OCR Read, Template Search).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 *   TaskCodeGenerator gen = new TaskCodeGenerator();
 *   String javaCode = gen.generate(definition, "MyTask", "My Cool Task");
 * }</pre>
 *
 * <p>This class was extracted from {@code TaskBuilderLayoutController.handleExportJson()}
 * to provide a single-responsibility, testable code generator with consistent
 * formatting and maintainability.</p>
 */
public class TaskCodeGenerator {

    /** Sentinel prefix that distinguishes custom file paths from EnumTemplates names. */
    private static final String CUSTOM_TEMPLATE_PREFIX = "file://";

    private static final String INDENT = "    ";
    private static final String INDENT2 = INDENT + INDENT;
    private static final String INDENT3 = INDENT2 + INDENT;
    private static final String INDENT4 = INDENT3 + INDENT;
    private static final String INDENT5 = INDENT4 + INDENT;

    /**
     * Generates the full Java source code for a compiled task class.
     *
     * @param definition the task flow DAG definition
     * @param className  the desired Java class name (must be a valid identifier)
     * @param taskName   a human-readable task name for log messages
     * @return a compilable Java source string
     */
    public String generate(TaskFlowDefinition definition, String className, String taskName) {
        StringBuilder code = new StringBuilder(4096);

        emitPackageAndImports(code);
        emitClassHeader(code, className);
        emitConstructor(code, className);
        emitGetDistinctKey(code, className);
        emitGetRequiredStartLocation(code, definition);
        emitExecuteMethod(code, definition, taskName);
        emitClassFooter(code);

        return code.toString();
    }

    // ========================================================================
    // Top-level structure
    // ========================================================================

    private void emitPackageAndImports(StringBuilder code) {
        code.append("package cl.camodev.wosbot.serv.task.impl;\n\n");
        code.append("import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;\n");
        code.append("import cl.camodev.wosbot.ot.DTOPoint;\n");
        code.append("import cl.camodev.wosbot.ot.DTOProfiles;\n");
        code.append("import cl.camodev.wosbot.console.enumerable.EnumTemplates;\n");
        code.append("import cl.camodev.wosbot.ot.DTOImageSearchResult;\n");
        code.append("import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;\n");
        code.append("import cl.camodev.wosbot.serv.task.DelayedTask;\n");
        code.append("import cl.camodev.wosbot.serv.task.EnumStartLocation;\n");
        code.append("import java.time.LocalDateTime;\n\n");
    }

    private void emitClassHeader(StringBuilder code, String className) {
        code.append("public class ").append(className).append(" extends DelayedTask {\n\n");
    }

    private void emitConstructor(StringBuilder code, String className) {
        code.append(INDENT).append("public ").append(className)
            .append("(DTOProfiles profile, TpDailyTaskEnum tpTask) {\n");
        code.append(INDENT2).append("super(profile, tpTask);\n");
        code.append(INDENT).append("}\n\n");
    }

    private void emitGetDistinctKey(StringBuilder code, String className) {
        code.append(INDENT).append("@Override\n");
        code.append(INDENT).append("protected Object getDistinctKey() {\n");
        code.append(INDENT2).append("return \"").append(className).append("\";\n");
        code.append(INDENT).append("}\n\n");
    }

    private void emitGetRequiredStartLocation(StringBuilder code, TaskFlowDefinition definition) {
        String startLoc = definition.getStartLocation();
        if (startLoc == null) startLoc = "ANY";

        String enumVal = switch (startLoc) {
            case "HOME"  -> "HOME";
            case "WORLD" -> "WORLD";
            default      -> "ANY";
        };

        code.append(INDENT).append("@Override\n");
        code.append(INDENT).append("protected EnumStartLocation getRequiredStartLocation() {\n");
        code.append(INDENT2).append("return EnumStartLocation.").append(enumVal).append(";\n");
        code.append(INDENT).append("}\n\n");
    }

    private void emitClassFooter(StringBuilder code) {
        code.append("}\n");
    }

    // ========================================================================
    // execute() method — state machine
    // ========================================================================

    private void emitExecuteMethod(StringBuilder code, TaskFlowDefinition definition, String taskName) {
        // Detect back-edges (loops) for guard generation
        List<LoopDetector.BackEdge> backEdges = LoopDetector.detectBackEdges(definition);
        Map<Integer, TaskFlowNode> nodeMap = new LinkedHashMap<>();
        for (TaskFlowNode n : definition.getNodes()) {
            nodeMap.put(n.getId(), n);
        }
        // Build a lookup: sourceId → list of back-edges originating from that node
        Map<Integer, List<LoopDetector.BackEdge>> backEdgesBySource = backEdges.stream()
                .collect(Collectors.groupingBy(LoopDetector.BackEdge::sourceId));

        code.append(INDENT).append("@Override\n");
        code.append(INDENT).append("protected void execute() {\n");
        code.append(INDENT2).append("logInfo(\"Starting task: '").append(taskName).append("'\");\n");

        int firstId = definition.getNodes().isEmpty() ? -1 : definition.getNodes().get(0).getId();

        // Emit loop counter variables for each detected back-edge
        for (LoopDetector.BackEdge be : backEdges) {
            code.append(INDENT2).append("int __loopCount_").append(be.key()).append(" = 0;\n");
        }

        // State machine loop
        code.append(INDENT2).append("int __state = ").append(firstId < 0 ? "-1" : String.valueOf(firstId)).append(";\n");
        code.append(INDENT2).append("while (__state != -1) {\n");
        code.append(INDENT3).append("checkPreemption();\n");
        code.append(INDENT3).append("switch (__state) {\n");

        for (TaskFlowNode node : definition.getNodes()) {
            emitNodeCase(code, node, backEdgesBySource.getOrDefault(node.getId(), Collections.emptyList()), nodeMap);
        }

        code.append(INDENT4).append("default: __state = -1; break;\n");
        code.append(INDENT3).append("}\n"); // close switch
        code.append(INDENT2).append("}\n"); // close while

        // Reschedule
        emitReschedule(code);

        code.append(INDENT2).append("logInfo(\"Generated task complete.\");\n");
        code.append(INDENT).append("}\n");
    }

    private void emitReschedule(StringBuilder code) {
        code.append(INDENT2).append("// Reschedule for next run using the configured repeat interval\n");
        code.append(INDENT2).append("int __interval = getRepeatIntervalMinutes();\n");
        code.append(INDENT2).append("if (__interval > 0) {\n");
        code.append(INDENT3).append("reschedule(LocalDateTime.now().plusMinutes(__interval));\n");
        code.append(INDENT3).append("logInfo(\"Task rescheduled in \" + __interval + \" minutes.\");\n");
        code.append(INDENT2).append("} else {\n");
        code.append(INDENT3).append("setRecurring(false);\n");
        code.append(INDENT3).append("logInfo(\"Task interval is 0, disabling recurrence.\");\n");
        code.append(INDENT2).append("}\n");
    }

    // ========================================================================
    // Individual node case emitters
    // ========================================================================

    private void emitNodeCase(StringBuilder code, TaskFlowNode node,
                              List<LoopDetector.BackEdge> nodeBackEdges,
                              Map<Integer, TaskFlowNode> nodeMap) {
        code.append(INDENT4).append("case ").append(node.getId()).append(": {\n");
        code.append(INDENT5).append("// ").append(node.getType().getDisplayName()).append("\n");

        switch (node.getType()) {
            case TAP_POINT       -> emitTapPoint(code, node);
            case WAIT            -> emitWait(code, node);
            case SWIPE           -> emitSwipe(code, node);
            case BACK_BUTTON     -> emitBackButton(code, node);
            case OCR_READ        -> emitOcrRead(code, node, nodeBackEdges);
            case TEMPLATE_SEARCH -> emitTemplateSearch(code, node, nodeBackEdges);
            default              -> code.append(INDENT5).append("// Unknown action\n");
        }

        // Non-branching nodes: emit next state transition (with loop guard if back-edge)
        if (node.getType() != EnumTaskFlowNodeType.OCR_READ
                && node.getType() != EnumTaskFlowNodeType.TEMPLATE_SEARCH) {
            int nextId = node.getNextNodeId();
            LoopDetector.BackEdge trueBackEdge = findBackEdge(nodeBackEdges, false);
            if (trueBackEdge != null && nextId > 0) {
                emitLoopGuardedTransition(code, node, trueBackEdge, nextId, -1);
            } else {
                code.append(INDENT5).append("__state = ").append(nextId > 0 ? nextId : -1).append(";\n");
            }
        }

        code.append(INDENT5).append("break;\n");
        code.append(INDENT4).append("}\n");
    }

    // ── Tap Point ──────────────────────────────────────────────────────────

    private void emitTapPoint(StringBuilder code, TaskFlowNode node) {
        int tlX = node.getParamAsInt("tlX", 0);
        int tlY = node.getParamAsInt("tlY", 0);
        int brX = node.getParamAsInt("brX", 0);
        int brY = node.getParamAsInt("brY", 0);

        if (tlX == brX && tlY == brY) {
            code.append(INDENT5).append("tapPoint(new DTOPoint(")
                .append(tlX).append(", ").append(tlY).append("));\n");
        } else {
            code.append(INDENT5).append("tapRandomPoint(new DTOPoint(")
                .append(tlX).append(", ").append(tlY).append("), new DTOPoint(")
                .append(brX).append(", ").append(brY).append("));\n");
        }
    }

    // ── Wait ───────────────────────────────────────────────────────────────

    private void emitWait(StringBuilder code, TaskFlowNode node) {
        code.append(INDENT5).append("sleepTask(")
            .append(node.getParamAsInt("durationMs", 1000)).append("L);\n");
    }

    // ── Swipe ──────────────────────────────────────────────────────────────

    private void emitSwipe(StringBuilder code, TaskFlowNode node) {
        code.append(INDENT5).append("swipe(new DTOPoint(")
            .append(node.getParamAsInt("startX", 0)).append(", ")
            .append(node.getParamAsInt("startY", 0)).append("), new DTOPoint(")
            .append(node.getParamAsInt("endX", 0)).append(", ")
            .append(node.getParamAsInt("endY", 0)).append("));\n");
    }

    // ── Back Button ────────────────────────────────────────────────────────

    private void emitBackButton(StringBuilder code, TaskFlowNode node) {
        code.append(INDENT5).append("tapBackButton();\n");
    }

    // ── OCR Read (branching) ───────────────────────────────────────────────

    private void emitOcrRead(StringBuilder code, TaskFlowNode node, List<LoopDetector.BackEdge> nodeBackEdges) {
        String cond     = node.getParam("condition") != null ? node.getParam("condition") : "CONTAINS";
        String expected = node.getParam("expectedValue") != null ? node.getParam("expectedValue") : "";
        int tX = node.getParamAsInt("tlX", 0);
        int tY = node.getParamAsInt("tlY", 0);
        int bX = node.getParamAsInt("brX", 100);
        int bY = node.getParamAsInt("brY", 100);

        String varName = "__ocrText_" + node.getId();

        code.append(INDENT5).append("String ").append(varName).append(" = \"\";\n");
        code.append(INDENT5).append("try {\n");
        code.append(INDENT5).append(INDENT).append(varName)
            .append(" = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(")
            .append(tX).append(", ").append(tY).append("), new DTOPoint(")
            .append(bX).append(", ").append(bY).append("));\n");
        code.append(INDENT5).append("} catch (Exception __ocrEx) {\n");
        code.append(INDENT5).append(INDENT).append("logInfo(\"OCR failed: \" + __ocrEx.getMessage());\n");
        code.append(INDENT5).append("}\n");

        // Branching condition
        int trueNext  = node.getNextNodeId();
        int falseNext = node.getNextNodeFalseId();

        String condExpr = buildOcrConditionExpression(varName, cond, expected);

        LoopDetector.BackEdge trueBackEdge  = findBackEdge(nodeBackEdges, false);
        LoopDetector.BackEdge falseBackEdge = findBackEdge(nodeBackEdges, true);

        code.append(INDENT5).append("if (").append(condExpr).append(") {\n");
        if (trueBackEdge != null && trueNext > 0) {
            emitLoopGuardedTransitionInBranch(code, node, trueBackEdge, trueNext, falseNext > 0 ? falseNext : -1);
        } else {
            code.append(INDENT5).append(INDENT).append("__state = ").append(trueNext > 0 ? trueNext : -1).append(";\n");
        }
        code.append(INDENT5).append("} else {\n");
        if (falseBackEdge != null && falseNext > 0) {
            emitLoopGuardedTransitionInBranch(code, node, falseBackEdge, falseNext, trueNext > 0 ? trueNext : -1);
        } else {
            code.append(INDENT5).append(INDENT).append("__state = ").append(falseNext > 0 ? falseNext : -1).append(";\n");
        }
        code.append(INDENT5).append("}\n");
    }

    private String buildOcrConditionExpression(String varName, String cond, String expected) {
        return switch (cond) {
            case "EQUALS"       -> varName + ".equalsIgnoreCase(\"" + expected + "\")";
            case "STARTS_WITH"  -> varName + ".toLowerCase().startsWith(\"" + expected.toLowerCase() + "\")";
            case "ENDS_WITH"    -> varName + ".toLowerCase().endsWith(\"" + expected.toLowerCase() + "\")";
            case "NOT_CONTAINS" -> "!" + varName + ".toLowerCase().contains(\"" + expected.toLowerCase() + "\")";
            default             -> varName + ".toLowerCase().contains(\"" + expected.toLowerCase() + "\")";
        };
    }

    // ── Template Search (branching) ────────────────────────────────────────

    private void emitTemplateSearch(StringBuilder code, TaskFlowNode node, List<LoopDetector.BackEdge> nodeBackEdges) {
        String tmpl = node.getParam("templatePath");
        if (tmpl == null) tmpl = "GAME_HOME_FURNACE";

        boolean isCustomTpl   = tmpl.startsWith(CUSTOM_TEMPLATE_PREFIX);
        int     threshold     = node.getParamAsInt("threshold", 90);
        int     maxAttempts   = node.getParamAsInt("maxAttempts", 1);
        int     delayMs       = node.getParamAsInt("delayMs", 300);
        boolean grayscale     = "true".equals(node.getParam("grayscale"));
        boolean tapIfFound    = "true".equals(node.getParam("tapIfFound"));
        int     offsetX       = node.getParamAsInt("offsetX", 0);
        int     offsetY       = node.getParamAsInt("offsetY", 0);

        String tlXs = node.getParam("tlX"), tlYs = node.getParam("tlY");
        String brXs = node.getParam("brX"), brYs = node.getParam("brY");
        boolean hasArea = tlXs != null && tlYs != null && brXs != null && brYs != null;

        String varName  = "__tplResult_" + node.getId();
        int trueNext    = node.getNextNodeId();
        int falseNext   = node.getNextNodeFalseId();

        code.append(INDENT5).append("DTOImageSearchResult ").append(varName).append(" = null;\n");
        code.append(INDENT5).append("try {\n");

        if (isCustomTpl) {
            emitCustomTemplateSearch(code, node, tmpl, varName, threshold, grayscale, hasArea, tlXs, tlYs, brXs, brYs);
        } else {
            emitEnumTemplateSearch(code, node, tmpl, varName, threshold, maxAttempts, delayMs, grayscale, hasArea, tlXs, tlYs, brXs, brYs);
        }

        code.append(INDENT5).append("} catch (Exception __tplEx) {\n");
        code.append(INDENT5).append(INDENT).append("logInfo(\"Template search failed: \" + __tplEx.getMessage());\n");
        code.append(INDENT5).append("}\n");

        LoopDetector.BackEdge trueBackEdge  = findBackEdge(nodeBackEdges, false);
        LoopDetector.BackEdge falseBackEdge = findBackEdge(nodeBackEdges, true);

        // Branching + optional tap
        code.append(INDENT5).append("if (").append(varName).append(" != null && ").append(varName).append(".isFound()) {\n");

        if (tapIfFound) {
            emitTapIfFound(code, node, varName, offsetX, offsetY);
        }

        if (trueBackEdge != null && trueNext > 0) {
            emitLoopGuardedTransitionInBranch(code, node, trueBackEdge, trueNext, falseNext > 0 ? falseNext : -1);
        } else {
            code.append(INDENT5).append(INDENT).append("__state = ").append(trueNext > 0 ? trueNext : -1).append(";\n");
        }
        code.append(INDENT5).append("} else {\n");
        if (falseBackEdge != null && falseNext > 0) {
            emitLoopGuardedTransitionInBranch(code, node, falseBackEdge, falseNext, trueNext > 0 ? trueNext : -1);
        } else {
            code.append(INDENT5).append(INDENT).append("__state = ").append(falseNext > 0 ? falseNext : -1).append(";\n");
        }
        code.append(INDENT5).append("}\n");
    }

    private void emitCustomTemplateSearch(StringBuilder code, TaskFlowNode node, String tmpl,
                                          String varName, int threshold, boolean grayscale,
                                          boolean hasArea, String tlXs, String tlYs, String brXs, String brYs) {
        String absPath = tmpl.substring(CUSTOM_TEMPLATE_PREFIX.length()).replace("\\", "\\\\");
        String method  = grayscale ? "searchTemplateGrayscaleFromFile" : "searchTemplateFromFile";

        if (hasArea) {
            code.append(INDENT5).append(INDENT).append(varName)
                .append(" = emuManager.").append(method)
                .append("(EMULATOR_NUMBER, \"").append(absPath).append("\", ")
                .append("new DTOPoint(").append(tlXs).append(", ").append(tlYs).append("), ")
                .append("new DTOPoint(").append(brXs).append(", ").append(brYs).append("), ")
                .append(threshold).append(");\n");
        } else {
            code.append(INDENT5).append(INDENT).append(varName)
                .append(" = emuManager.").append(method)
                .append("(EMULATOR_NUMBER, \"").append(absPath).append("\", ")
                .append(threshold).append(");\n");
        }
    }

    private void emitEnumTemplateSearch(StringBuilder code, TaskFlowNode node, String tmpl,
                                        String varName, int threshold, int maxAttempts, int delayMs,
                                        boolean grayscale, boolean hasArea,
                                        String tlXs, String tlYs, String brXs, String brYs) {
        // Build SearchConfig
        StringBuilder configBuilder = new StringBuilder("TemplateSearchHelper.SearchConfig.builder()");
        configBuilder.append(".withThreshold(").append(threshold).append(")");
        configBuilder.append(".withMaxAttempts(").append(maxAttempts).append(")");
        configBuilder.append(".withDelay(").append(delayMs).append("L)");
        if (hasArea) {
            configBuilder.append(".withCoordinates(new DTOPoint(").append(tlXs).append(", ").append(tlYs)
                .append("), new DTOPoint(").append(brXs).append(", ").append(brYs).append("))");
        }
        configBuilder.append(".build()");

        String searchMethod = grayscale ? "searchTemplateGrayscale" : "searchTemplate";
        String helperId     = "__tplHelper_" + node.getId();

        code.append(INDENT5).append(INDENT).append("TemplateSearchHelper ").append(helperId)
            .append(" = new TemplateSearchHelper(emuManager, EMULATOR_NUMBER, profile);\n");
        code.append(INDENT5).append(INDENT).append(varName).append(" = ").append(helperId)
            .append(".").append(searchMethod).append("(EnumTemplates.").append(tmpl).append(", ")
            .append(configBuilder).append(");\n");
    }

    private void emitTapIfFound(StringBuilder code, TaskFlowNode node, String varName, int offsetX, int offsetY) {
        code.append(INDENT5).append(INDENT).append("// Tap at found location\n");

        if (offsetX != 0 || offsetY != 0) {
            String tapPtVar = "__tapPt_" + node.getId();
            code.append(INDENT5).append(INDENT).append("DTOPoint ").append(tapPtVar)
                .append(" = new DTOPoint(").append(varName).append(".getPoint().getX() + ").append(offsetX)
                .append(", ").append(varName).append(".getPoint().getY() + ").append(offsetY).append(");\n");
            code.append(INDENT5).append(INDENT).append("emuManager.tapAtPoint(EMULATOR_NUMBER, ")
                .append(tapPtVar).append(");\n");
        } else {
            code.append(INDENT5).append(INDENT).append("emuManager.tapAtPoint(EMULATOR_NUMBER, ")
                .append(varName).append(".getPoint());\n");
        }
    }

    // ========================================================================
    // Loop guard helpers
    // ========================================================================

    /**
     * Finds a back-edge in the list matching the given branch type.
     */
    private LoopDetector.BackEdge findBackEdge(List<LoopDetector.BackEdge> edges, boolean isFalseBranch) {
        for (LoopDetector.BackEdge be : edges) {
            if (be.isFalseBranch() == isFalseBranch) return be;
        }
        return null;
    }

    /**
     * Emits a loop-guarded state transition for non-branching nodes.
     * Used at the INDENT5 level (inside a case block, top level).
     */
    private void emitLoopGuardedTransition(StringBuilder code, TaskFlowNode node,
                                            LoopDetector.BackEdge backEdge,
                                            int backTarget, int forwardTarget) {
        int maxIter   = node.getParamAsInt("loopMaxIterations", 10);
        int delayMs   = node.getParamAsInt("loopDelayMs", 500);
        String action = node.getParam("loopExhaustedAction");
        boolean continueForward = "CONTINUE".equals(action);

        String counterVar = "__loopCount_" + backEdge.key();

        code.append(INDENT5).append(counterVar).append("++;\n");
        code.append(INDENT5).append("if (").append(counterVar).append(" > ").append(maxIter).append(") {\n");
        code.append(INDENT5).append(INDENT).append("logInfo(\"Loop limit reached (").append(maxIter).append(" iterations)\");\n");
        code.append(INDENT5).append(INDENT).append("__state = ").append(continueForward && forwardTarget > 0 ? forwardTarget : -1).append(";\n");
        code.append(INDENT5).append("} else {\n");
        if (delayMs > 0) {
            code.append(INDENT5).append(INDENT).append("sleepTask(").append(delayMs).append("L);\n");
        }
        code.append(INDENT5).append(INDENT).append("__state = ").append(backTarget).append(";\n");
        code.append(INDENT5).append("}\n");
    }

    /**
     * Emits a loop-guarded state transition inside a branch (if/else block).
     * Used at the INDENT5 + INDENT level (inside a branch body).
     */
    private void emitLoopGuardedTransitionInBranch(StringBuilder code, TaskFlowNode node,
                                                    LoopDetector.BackEdge backEdge,
                                                    int backTarget, int forwardTarget) {
        int maxIter   = node.getParamAsInt("loopMaxIterations", 10);
        int delayMs   = node.getParamAsInt("loopDelayMs", 500);
        String action = node.getParam("loopExhaustedAction");
        boolean continueForward = "CONTINUE".equals(action);

        String counterVar = "__loopCount_" + backEdge.key();

        code.append(INDENT5).append(INDENT).append(counterVar).append("++;\n");
        code.append(INDENT5).append(INDENT).append("if (").append(counterVar).append(" > ").append(maxIter).append(") {\n");
        code.append(INDENT5).append(INDENT).append(INDENT).append("logInfo(\"Loop limit reached (").append(maxIter).append(" iterations)\");\n");
        code.append(INDENT5).append(INDENT).append(INDENT).append("__state = ").append(continueForward && forwardTarget > 0 ? forwardTarget : -1).append(";\n");
        code.append(INDENT5).append(INDENT).append("} else {\n");
        if (delayMs > 0) {
            code.append(INDENT5).append(INDENT).append(INDENT).append("sleepTask(").append(delayMs).append("L);\n");
        }
        code.append(INDENT5).append(INDENT).append(INDENT).append("__state = ").append(backTarget).append(";\n");
        code.append(INDENT5).append(INDENT).append("}\n");
    }
}
