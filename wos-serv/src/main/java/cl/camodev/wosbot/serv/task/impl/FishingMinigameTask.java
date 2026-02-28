package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.task.DelayedTask;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fishing Minigame Task — Predictive Constant-Velocity (PCV) Algorithm
 *
 * <p>
 * Automates the fishing minigame in Whiteout Survival. Fish have empirically
 * measured <b>constant</b> horizontal velocities (px/ms):
 * <ul>
 *   <li>Pufferfish: 0.030 (1.0 px/frame)</li>
 *   <li>Small fish: 0.017 (0.57 px/frame)</li>
 *   <li>Striped:    0.060 (2.0 px/frame)</li>
 *   <li>Redfish:    0.237 (8.0 px/frame)</li>
 * </ul>
 * All fish scroll upward at 0.690 px/ms (23 px/frame) — the hook-fish
 * relative vertical speed. Fish bounce perfectly off screen edges (vx inverts).
 *
 * <p>
 * <b>Algorithm:</b> Template matching is performed every N ticks to detect new
 * fish and correct positional drift. Between scans, fish positions are predicted
 * using constant-velocity physics with wall bouncing. Danger zones are computed
 * from predicted future positions, and the hook is steered into safe gaps.
 */
public class FishingMinigameTask extends DelayedTask {

    // ── Game area ──────────────────────────────────────────────────────────
    private static final int SCREEN_W   = 720;
    private static final int UI_TOP_Y   = 170;
    private static final int PLAY_BOT_Y = 1200;

    // ── Hook geometry ──────────────────────────────────────────────────────
    private static final int HOOK_W = 18;
    private static final int HOOK_H = 19;

    // ── Constant fish horizontal velocities (px/ms, absolute) ──────────────
    private static final float VX_PUFFER = 0.030f;   // 1.0 px/frame @ 30fps
    private static final float VX_SMALL  = 0.017f;   // 0.57 px/frame
    private static final float VX_STRIPE = 0.060f;   // 2.0 px/frame
    private static final float VX_RED    = 0.237f;   // 8.0 px/frame

    // ── Vertical scroll speed (hook-fish relative, all types) ──────────────
    private static final float VY_SCROLL = 0.690f;   // 23 px/frame

    // ── Fish bounding-box sizes (from templates) ───────────────────────────
    private static final int PUFFER_W = 105, PUFFER_H = 49;
    private static final int REDFISH_W = 52, REDFISH_H = 10;
    private static final int SMALL_W  = 24,  SMALL_H  = 12;
    private static final int STRIPE_W = 36,  STRIPE_H = 41;

    // ── Detection thresholds (0–100) ───────────────────────────────────────
    private static final double THR_HOOK   = 80.0;
    private static final double THR_PUFFER = 72.0;
    private static final double THR_RED    = 75.0;
    private static final double THR_SMALL  = 72.0;
    private static final double THR_STRIPE = 72.0;

    // ── Algorithm tuning ───────────────────────────────────────────────────
    /** Fish ahead to consider for danger zones. */
    private static final int LOOKAHEAD_FISH = 3;
    /** Max template hits per fish type per scan. */
    private static final int MAX_FISH_PER_TYPE = 8;
    /** Game loop tick interval (ms). */
    private static final long TICK_MS = 50L;
    /** Padding added to each side of a danger zone (px). */
    private static final int MARGIN_PX = 5;
    /** Safety timeout for the entire minigame (ms). */
    private static final long MAX_DURATION_MS = 30_000L;
    /** Minimum displacement before issuing a swipe (px). */
    private static final int SWIPE_DEADZONE_PX = 6;
    /** Template scan interval (every N ticks) for drift correction + new fish. */
    private static final int SCAN_INTERVAL = 10;
    /** Max pixel distance to associate a detection with an existing track. */
    private static final float TRACK_MATCH_DIST = 60.0f;
    /** Remove single-detection tracks after this many consecutive missed scans. */
    private static final int MAX_MISSED_SCANS = 10;

    // ── Runtime state ──────────────────────────────────────────────────────
    private final List<TrackedFish> trackedFish = new ArrayList<>();
    private int nextTrackId = 0;

    public FishingMinigameTask(cl.camodev.wosbot.ot.DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected boolean acceptsInjections() {
        return false;
    }

    // =========================================================================
    // MAIN GAME LOOP
    // =========================================================================

    @Override
    protected void execute() {
        logInfo("Fishing Minigame: PCV algorithm started.");
        trackedFish.clear();
        nextTrackId = 0;

        long start = System.currentTimeMillis();
        int ticks = 0;

        while (System.currentTimeMillis() - start < MAX_DURATION_MS) {
            checkPreemption();
            long now = System.currentTimeMillis();

            // ── 1. Physics prediction for all tracked fish ───────────────
            for (TrackedFish f : trackedFish) {
                f.predict(now);
            }

            // ── 2. Prune fish that scrolled off the top ──────────────────
            trackedFish.removeIf(f -> f.cy < UI_TOP_Y - f.h);

            // ── 3. Capture one screenshot ────────────────────────────────
            DTORawImage raw = emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

            // ── 4. Locate hook (every tick for responsive control) ────────
            DTOImageSearchResult hookResult = emuManager.searchTemplate(
                    EMULATOR_NUMBER, raw, EnumTemplates.FISHING_HOOK,
                    new DTOPoint(0, UI_TOP_Y),
                    new DTOPoint(SCREEN_W, PLAY_BOT_Y), THR_HOOK);

            if (hookResult == null || !hookResult.isFound()) {
                logDebug("Hook not detected — game may have ended.");
                break;
            }

            int hookCx = hookResult.getPoint().getX();
            int hookCy = hookResult.getPoint().getY();

            // ── 5. Periodic template scan ────────────────────────────────
            if (ticks % SCAN_INTERVAL == 0) {
                List<FishDetection> detections = scanForFish(raw, hookCy);
                reconcileTracks(detections, now);
            }

            // ── 6. Danger zones from predicted positions ─────────────────
            List<int[]> dangers = computeDangerZones(hookCx, hookCy);

            // ── 7. Pick safe target ──────────────────────────────────────
            int targetX = chooseSafeTarget(hookCx, dangers);

            // ── 8. Swipe if needed ───────────────────────────────────────
            if (Math.abs(targetX - hookCx) > SWIPE_DEADZONE_PX) {
                int swipeY = Math.min(hookCy + 60, PLAY_BOT_Y - 10);
                emuManager.executeSwipe(EMULATOR_NUMBER,
                        new DTOPoint(hookCx, swipeY),
                        new DTOPoint(targetX, swipeY));
                logDebug(String.format("Tick %d | hook=(%d,%d) -> %d | tracked=%d | zones=%d",
                        ticks, hookCx, hookCy, targetX, trackedFish.size(), dangers.size()));
            }

            ticks++;
            long elapsed = System.currentTimeMillis() - now;
            if (elapsed < TICK_MS) {
                sleepTask(TICK_MS - elapsed);
            }
        }

        logInfo("Fishing Minigame completed after " + ticks + " ticks.");
        reschedule(LocalDateTime.now().plusHours(6));
    }

    // =========================================================================
    // PERIODIC TEMPLATE SCANNING
    // =========================================================================

    /**
     * Scans for all fish types below the hook using template matching.
     * Only right-facing templates are used; detected fish are assigned
     * positive vx. Wall bounces (vx inversion) are handled by physics.
     */
    private List<FishDetection> scanForFish(DTORawImage raw, int hookCy) {
        List<FishDetection> result = new ArrayList<>();
        int searchTopY = Math.max(UI_TOP_Y, hookCy - PUFFER_H);

        result.addAll(scanType(raw, EnumTemplates.FISHING_PUFFERFISH,
                THR_PUFFER, PUFFER_W, PUFFER_H, VX_PUFFER, searchTopY, "puffer"));
        result.addAll(scanType(raw, EnumTemplates.FISHING_REDFISH,
                THR_RED, REDFISH_W, REDFISH_H, VX_RED, searchTopY, "red"));
        result.addAll(scanType(raw, EnumTemplates.FISHING_SMALLFISH,
                THR_SMALL, SMALL_W, SMALL_H, VX_SMALL, searchTopY, "small"));
        result.addAll(scanType(raw, EnumTemplates.FISHING_STRIPEFISH,
                THR_STRIPE, STRIPE_W, STRIPE_H, VX_STRIPE, searchTopY, "stripe"));

        return result;
    }

    private List<FishDetection> scanType(DTORawImage raw, EnumTemplates tmpl,
            double threshold, int w, int h, float absVx, int topY, String label) {
        List<FishDetection> out = new ArrayList<>();
        List<DTOImageSearchResult> hits = emuManager.searchTemplates(
                EMULATOR_NUMBER, raw, tmpl,
                new DTOPoint(0, topY),
                new DTOPoint(SCREEN_W, PLAY_BOT_Y),
                threshold, MAX_FISH_PER_TYPE);

        if (hits == null) return out;
        for (DTOImageSearchResult hit : hits) {
            if (hit.isFound()) {
                FishDetection d = new FishDetection();
                d.label = label;
                d.cx = hit.getPoint().getX();
                d.cy = hit.getPoint().getY();
                d.w = w;
                d.h = h;
                d.absVx = absVx;
                out.add(d);
            }
        }
        return out;
    }

    // =========================================================================
    // TRACK RECONCILIATION
    // =========================================================================

    /**
     * Matches template detections to existing physics-tracked fish.
     * <ul>
     *   <li>Matched tracks: correct positional drift, confirm direction.</li>
     *   <li>Unmatched detections: spawn new tracks (right-facing, vx &gt; 0).</li>
     *   <li>Unmatched tracks: increment miss counter; remove stale
     *       single-detection tracks. Confirmed tracks persist (they may be
     *       left-facing after a bounce and temporarily undetectable).</li>
     * </ul>
     */
    private void reconcileTracks(List<FishDetection> detections, long now) {
        boolean[] detUsed   = new boolean[detections.size()];
        boolean[] tkMatched = new boolean[trackedFish.size()];

        // Build candidate pairs sorted by distance (greedy nearest-neighbour)
        List<int[]> pairs = new ArrayList<>();
        for (int ti = 0; ti < trackedFish.size(); ti++) {
            TrackedFish t = trackedFish.get(ti);
            for (int di = 0; di < detections.size(); di++) {
                FishDetection d = detections.get(di);
                if (!t.label.equals(d.label)) continue;
                float dist = (float) Math.hypot(t.cx - d.cx, t.cy - d.cy);
                if (dist < TRACK_MATCH_DIST) {
                    pairs.add(new int[]{ti, di, (int) (dist * 100)});
                }
            }
        }
        pairs.sort(Comparator.comparingInt(a -> a[2]));

        for (int[] p : pairs) {
            int ti = p[0], di = p[1];
            if (tkMatched[ti] || detUsed[di]) continue;

            TrackedFish t = trackedFish.get(ti);
            FishDetection d = detections.get(di);

            // Drift correction: snap to detected position
            t.cx = d.cx;
            t.cy = d.cy;
            // Right-facing template matched → fish is facing right now
            t.vx = d.absVx;
            t.lastPredictMs = now;
            t.missedScans = 0;
            t.scanCount++;

            tkMatched[ti] = true;
            detUsed[di]   = true;
        }

        // Age unmatched tracks
        for (int ti = 0; ti < trackedFish.size(); ti++) {
            if (!tkMatched[ti]) {
                trackedFish.get(ti).missedScans++;
            }
        }

        // Remove stale single-detection tracks (probable false positives).
        // Confirmed tracks (scanCount > 1) are kept — they may be left-facing
        // after a wall bounce and temporarily invisible to right-facing templates.
        trackedFish.removeIf(t -> t.missedScans > MAX_MISSED_SCANS && t.scanCount <= 1);

        // Spawn new tracks for unmatched detections
        for (int di = 0; di < detections.size(); di++) {
            if (detUsed[di]) continue;
            FishDetection d = detections.get(di);

            TrackedFish t = new TrackedFish();
            t.id            = nextTrackId++;
            t.label         = d.label;
            t.cx            = d.cx;
            t.cy            = d.cy;
            t.vx            = d.absVx;   // right-facing → positive vx
            t.absVx         = d.absVx;
            t.w             = d.w;
            t.h             = d.h;
            t.lastPredictMs = now;
            t.missedScans   = 0;
            t.scanCount     = 1;

            trackedFish.add(t);
        }
    }

    // =========================================================================
    // DANGER ZONE COMPUTATION
    // =========================================================================

    /**
     * Computes danger zones for the nearest threats below the hook using
     * predicted positions from constant-velocity physics.
     */
    private List<int[]> computeDangerZones(int hookCx, int hookCy) {
        List<TrackedFish> below = new ArrayList<>();
        for (TrackedFish f : trackedFish) {
            if (f.cy > hookCy) below.add(f);
        }
        below.sort(Comparator.comparingDouble(f -> f.cy));

        List<int[]> dangers = new ArrayList<>();
        int considered = 0;
        for (TrackedFish f : below) {
            if (considered >= LOOKAHEAD_FISH) break;
            int[] zone = computeDangerZone(f, hookCy);
            if (zone != null) {
                dangers.add(zone);
                considered++;
            }
        }
        return dangers;
    }

    /**
     * Computes the horizontal danger zone for a single tracked fish.
     * Projects the fish's X position over the time window when the hook's
     * Y band will overlap the fish's Y band, using constant-velocity physics
     * with wall bouncing.
     *
     * @return {dangerLeft, dangerRight} in screen pixels, or null if not a threat.
     */
    private int[] computeDangerZone(TrackedFish f, int hookCy) {
        float fishTop    = f.cy - f.h / 2.0f;
        float fishBottom = f.cy + f.h / 2.0f;
        float hookBottom = hookCy + HOOK_H / 2.0f;
        float hookTop    = hookCy - HOOK_H / 2.0f;

        // Time window (ms) when hook Y-band overlaps fish Y-band
        float tArrive = (fishTop - hookBottom) / VY_SCROLL;
        float tDepart = (fishBottom - hookTop) / VY_SCROLL;

        if (tDepart <= 0) return null;   // already passed
        tArrive = Math.max(0, tArrive);

        // Sample fish X at several points to catch mid-window bounces
        float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE;
        int samples = 5;
        for (int i = 0; i <= samples; i++) {
            float t = tArrive + (tDepart - tArrive) * i / samples;
            float x = projectX(f.cx, f.vx, f.w, t);
            xMin = Math.min(xMin, x);
            xMax = Math.max(xMax, x);
        }

        float halfPad = f.w / 2.0f + HOOK_W / 2.0f + MARGIN_PX;
        int dangerLeft  = Math.max(0, (int) (xMin - halfPad));
        int dangerRight = Math.min(SCREEN_W, (int) (xMax + halfPad));

        return new int[]{dangerLeft, dangerRight};
    }

    /**
     * Projects a fish's center X forward by {@code timeMs} milliseconds,
     * reflecting perfectly off screen edges. Pure function — does not modify
     * any tracked fish state.
     */
    private static float projectX(float x, float vx, int fishW, float timeMs) {
        if (vx == 0 || timeMs <= 0) return x;

        float remaining = timeMs;
        float curX  = x;
        float curVx = vx;
        float halfW = fishW / 2.0f;
        float leftWall  = halfW;
        float rightWall = SCREEN_W - halfW;

        int maxBounces = 100;
        while (remaining > 0 && maxBounces-- > 0) {
            if (curVx > 0) {
                float ttb = (rightWall - curX) / curVx;
                if (ttb < 0) { curVx = -curVx; continue; }
                if (ttb >= remaining) return curX + curVx * remaining;
                curX = rightWall;
                remaining -= ttb;
                curVx = -curVx;
            } else {
                float ttb = (curX - leftWall) / (-curVx);
                if (ttb < 0) { curVx = -curVx; continue; }
                if (ttb >= remaining) return curX + curVx * remaining;
                curX = leftWall;
                remaining -= ttb;
                curVx = -curVx;
            }
        }
        return curX;
    }

    // =========================================================================
    // SAFE TARGET SELECTION
    // =========================================================================

    /**
     * Finds the safest X for the hook given a list of danger zones.
     * <ol>
     *   <li>Computes safe gaps between and around danger zones.</li>
     *   <li>If hook is already in a safe gap, stay put.</li>
     *   <li>Otherwise move to the nearest gap centre.</li>
     * </ol>
     */
    private int chooseSafeTarget(int hookCx, List<int[]> dangers) {
        int minX = HOOK_W / 2;
        int maxX = SCREEN_W - HOOK_W / 2;

        if (dangers.isEmpty()) return hookCx;

        List<int[]> sorted = new ArrayList<>(dangers);
        sorted.sort(Comparator.comparingInt(d -> d[0]));

        List<int[]> gaps = new ArrayList<>();
        int cursor = minX;
        for (int[] d : sorted) {
            if (d[0] > cursor) gaps.add(new int[]{cursor, d[0]});
            cursor = Math.max(cursor, d[1]);
        }
        if (cursor < maxX) gaps.add(new int[]{cursor, maxX});

        if (gaps.isEmpty()) {
            logDebug("No safe gap — falling back to screen centre.");
            return SCREEN_W / 2;
        }

        for (int[] g : gaps) {
            if (hookCx >= g[0] && hookCx <= g[1]) return hookCx;
        }

        int bestTarget = SCREEN_W / 2;
        int bestDist   = Integer.MAX_VALUE;
        for (int[] g : gaps) {
            int centre = (g[0] + g[1]) / 2;
            int dist   = Math.abs(centre - hookCx);
            if (dist < bestDist) { bestDist = dist; bestTarget = centre; }
        }
        return bestTarget;
    }

    // =========================================================================
    // INNER DATA CLASSES
    // =========================================================================

    /** Raw template detection from a single scan frame. */
    private static class FishDetection {
        String label;
        int cx, cy;
        int w, h;
        float absVx;
    }

    /**
     * Physics-tracked fish. Position is predicted between template scans
     * using constant-velocity kinematics with perfect wall bouncing.
     */
    private static class TrackedFish {
        int    id;
        String label;
        float  cx, cy;           // predicted screen-coordinate position
        float  vx;               // signed horizontal velocity (px/ms, + = right)
        float  absVx;            // unsigned speed constant for this fish type
        int    w, h;
        long   lastPredictMs;
        int    missedScans;      // consecutive scans without a template match
        int    scanCount;        // total number of scans that confirmed this track

        /**
         * Advances position to {@code nowMs} using constant-velocity physics.
         * Y scrolls upward at {@link #VY_SCROLL}; X bounces off screen walls.
         */
        void predict(long nowMs) {
            long dt = nowMs - lastPredictMs;
            if (dt <= 0) return;

            // Vertical: scroll upward at constant rate
            cy -= VY_SCROLL * dt;

            // Horizontal: constant speed with wall bouncing
            float remaining = dt;
            float halfW     = w / 2.0f;
            float leftWall  = halfW;
            float rightWall = SCREEN_W - halfW;

            int safety = 100;
            while (remaining > 0 && vx != 0 && safety-- > 0) {
                if (vx > 0) {
                    float ttb = (rightWall - cx) / vx;
                    if (ttb < 0) { vx = -vx; continue; }
                    if (ttb >= remaining) { cx += vx * remaining; break; }
                    cx = rightWall;
                    remaining -= ttb;
                    vx = -vx;
                } else {
                    float ttb = (cx - leftWall) / (-vx);
                    if (ttb < 0) { vx = -vx; continue; }
                    if (ttb >= remaining) { cx += vx * remaining; break; }
                    cx = leftWall;
                    remaining -= ttb;
                    vx = -vx;
                }
            }

            lastPredictMs = nowMs;
        }
    }
}
