"""
Striped fish velocity analysis.
Only tracks RIGHT-FACING fish (no template flip).
"""

import cv2
import numpy as np
import os
import csv
import sys
from pathlib import Path

FRAMES_DIR = r"c:\Users\parad\Desktop\New folder\wosbot\analysis_frames"
TEMPLATE_PATH = r"c:\Users\parad\Desktop\New folder\wosbot\wos-serv\src\main\resources\templates\fishingtoornament\stripefishRight.png"
OUTPUT_CSV = r"c:\Users\parad\Desktop\New folder\wosbot\stripefish_tracking.csv"
ANNOTATED_DIR = r"c:\Users\parad\Desktop\New folder\wosbot\analysis_frames\annotated_stripe"
THRESHOLD = 0.72
SCREEN_W = 720
UI_TOP_Y = 170
PLAY_BOT_Y = 1200
FPS = 30.0
MAX_MATCH_DIST = 80

COLORS = [
    (0, 255, 0), (255, 0, 0), (0, 0, 255), (255, 255, 0),
    (255, 0, 255), (0, 255, 255), (128, 255, 0), (255, 128, 0),
    (0, 128, 255), (255, 0, 128), (128, 0, 255), (0, 255, 128),
]

def load_frames(d): return sorted(Path(d).glob("frame_*.png"))

def detect_fish_right_only(frame, template, threshold):
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    th, tw = template.shape[:2]
    result = cv2.matchTemplate(gray, template, cv2.TM_CCOEFF_NORMED)
    locations = np.where(result >= threshold)
    dets = []
    for pt_y, pt_x in zip(*locations):
        score = result[pt_y, pt_x]
        cx, cy = pt_x + tw // 2, pt_y + th // 2
        if UI_TOP_Y <= cy <= PLAY_BOT_Y:
            dets.append((cx, cy, tw, th, float(score)))
    return nms(dets, 35)

def nms(dets, merge_dist):
    if not dets: return []
    dets = sorted(dets, key=lambda d: d[4], reverse=True)
    kept = []
    for d in dets:
        if not any(np.hypot(d[0]-k[0], d[1]-k[1]) < merge_dist for k in kept):
            kept.append(d)
    return kept

class FishTrack:
    def __init__(self, tid, cx, cy, fi):
        self.track_id = tid; self.positions = [(fi, cx, cy)]
        self.last_cx = cx; self.last_cy = cy; self.last_frame = fi
        self.lost_count = 0; self.alive = True
    def update(self, cx, cy, fi):
        self.positions.append((fi, cx, cy)); self.last_cx = cx; self.last_cy = cy
        self.last_frame = fi; self.lost_count = 0
    def mark_lost(self):
        self.lost_count += 1
        if self.lost_count > 8: self.alive = False
    def predicted_pos(self, fi):
        if len(self.positions) < 2: return self.last_cx, self.last_cy
        f1, x1, y1 = self.positions[-1]; f0, x0, y0 = self.positions[-2]
        df = f1 - f0
        if df == 0: return x1, y1
        dt = fi - f1; return x1 + (x1-x0)/df*dt, y1 + (y1-y0)/df*dt

def track_fish(all_dets):
    tracks = []; nid = 0
    for fi, dets in all_dets:
        active = [t for t in tracks if t.alive]
        pairs = []
        for di, d in enumerate(dets):
            for ti, t in enumerate(active):
                px, py = t.predicted_pos(fi)
                pairs.append((np.hypot(d[0]-px, d[1]-py), ti, di))
        pairs.sort(); ut = set(); ud = set()
        for dist, ti, di in pairs:
            if ti in ut or di in ud: continue
            if dist < MAX_MATCH_DIST: active[ti].update(dets[di][0], dets[di][1], fi); ut.add(ti); ud.add(di)
        for ti, t in enumerate(active):
            if ti not in ut: t.mark_lost()
        for di, d in enumerate(dets):
            if di not in ud: tracks.append(FishTrack(nid, d[0], d[1], fi)); nid += 1
    return tracks

def linreg(track):
    if len(track.positions) < 3: return None
    frames = np.array([p[0] for p in track.positions], dtype=float)
    xs = np.array([p[1] for p in track.positions], dtype=float)
    ys = np.array([p[2] for p in track.positions], dtype=float)
    A = np.vstack([frames, np.ones(len(frames))]).T
    vx, _ = np.linalg.lstsq(A, xs, rcond=None)[0]; vy, _ = np.linalg.lstsq(A, ys, rcond=None)[0]
    r2 = lambda actual, pred: 1 - np.sum((actual-pred)**2)/np.sum((actual-np.mean(actual))**2) if np.sum((actual-np.mean(actual))**2) > 0 else 0
    r2_x = r2(xs, vx*frames + np.linalg.lstsq(A, xs, rcond=None)[0][1] + (vx*frames - vx*frames))
    # Recompute properly
    res_x = np.linalg.lstsq(A, xs, rcond=None); res_y = np.linalg.lstsq(A, ys, rcond=None)
    vx, bx = res_x[0]; vy, by = res_y[0]
    pred_x = vx*frames+bx; pred_y = vy*frames+by
    ss_tot_x = np.sum((xs-np.mean(xs))**2); ss_tot_y = np.sum((ys-np.mean(ys))**2)
    r2_x = 1-np.sum((xs-pred_x)**2)/ss_tot_x if ss_tot_x>0 else 0
    r2_y = 1-np.sum((ys-pred_y)**2)/ss_tot_y if ss_tot_y>0 else 0
    return vx, vy, r2_x, r2_y

def annotate(frame_files, all_dets, tracks, out_dir, tshape, every_n=3):
    os.makedirs(out_dir, exist_ok=True)
    tl = {}
    for t in tracks:
        if len(t.positions) < 5: continue
        for f, x, y in t.positions: tl.setdefault(f, []).append((t.track_id, x, y))
    th, tw = tshape; hw, hh = tw//2, th//2
    for fi, dets in all_dets:
        if fi % every_n != 0: continue
        frame = cv2.imread(str(frame_files[fi]))
        if frame is None: continue
        for d in dets: cv2.circle(frame, (d[0], d[1]), 4, (255,255,255), 1)
        if fi in tl:
            for tid, cx, cy in tl[fi]:
                c = COLORS[tid % len(COLORS)]
                cv2.rectangle(frame, (cx-hw, cy-hh), (cx+hw, cy+hh), c, 2)
                cv2.putText(frame, f"S{tid}", (cx-hw, cy-hh-5), cv2.FONT_HERSHEY_SIMPLEX, 0.5, c, 2)
        cv2.putText(frame, f"Frame {fi}", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255,255,255), 2)
        cv2.imwrite(os.path.join(out_dir, f"ann_{fi:04d}.png"), frame)
    print(f"Annotated frames written to {out_dir}")

def main():
    print("Loading striped fish template (RIGHT-FACING ONLY)...")
    template = cv2.imread(TEMPLATE_PATH, cv2.IMREAD_GRAYSCALE)
    if template is None: print(f"ERROR: {TEMPLATE_PATH}"); sys.exit(1)
    print(f"Template size: {template.shape[1]}x{template.shape[0]} px")

    frame_files = load_frames(FRAMES_DIR)
    print(f"Found {len(frame_files)} frames")

    print("Running template matching (right-facing only)...")
    all_dets = []
    for idx, fp in enumerate(frame_files):
        frame = cv2.imread(str(fp))
        if frame is None: continue
        dets = detect_fish_right_only(frame, template, THRESHOLD)
        all_dets.append((idx, dets))
        if (idx+1) % 50 == 0:
            print(f"  {idx+1}/{len(frame_files)} — {sum(len(d) for _,d in all_dets)} detections")

    print(f"Total right-facing detections: {sum(len(d) for _,d in all_dets)}")

    print("Tracking...")
    tracks = track_fish(all_dets)
    good = [t for t in tracks if len(t.positions) >= 5]
    print(f"Total tracks: {len(tracks)} | With >=5 pts: {len(good)}")

    print("\n" + "="*80)
    print("LINEAR REGRESSION VELOCITY PER TRACK (right-facing only)")
    print("="*80)

    all_vx, all_vy, csv_rows = [], [], []

    for t in good:
        r = linreg(t)
        if r is None: continue
        vx, vy, r2_x, r2_y = r
        span = t.positions[-1][0] - t.positions[0][0]
        print(f"\nTrack {t.track_id}: {len(t.positions)} pts, frames {t.positions[0][0]}-{t.positions[-1][0]} (span={span})")
        print(f"  Start: ({t.positions[0][1]}, {t.positions[0][2]})  End: ({t.positions[-1][1]}, {t.positions[-1][2]})")
        print(f"  vx = {vx:+.4f} px/frame (R²={r2_x:.6f})")
        print(f"  vy = {vy:+.4f} px/frame (R²={r2_y:.6f})")
        print(f"  |vx| = {abs(vx):.4f} px/frame = {abs(vx)*FPS:.2f} px/sec")
        print(f"  |vy| = {abs(vy):.4f} px/frame = {abs(vy)*FPS:.2f} px/sec")

        if r2_y > 0.95 and r2_x > 0.90 and len(t.positions) >= 8:
            all_vx.append(vx); all_vy.append(vy)

        csv_rows.append({'track_id': t.track_id, 'n_points': len(t.positions),
            'frame_start': t.positions[0][0], 'frame_end': t.positions[-1][0],
            'start_x': t.positions[0][1], 'start_y': t.positions[0][2],
            'end_x': t.positions[-1][1], 'end_y': t.positions[-1][2],
            'vx': round(vx,6), 'vy': round(vy,6),
            'abs_vx': round(abs(vx),6), 'abs_vy': round(abs(vy),6),
            'r2_x': round(r2_x,6), 'r2_y': round(r2_y,6)})

    if csv_rows:
        with open(OUTPUT_CSV, 'w', newline='') as f:
            w = csv.DictWriter(f, fieldnames=csv_rows[0].keys()); w.writeheader(); w.writerows(csv_rows)
        print(f"\nWrote {len(csv_rows)} tracks to {OUTPUT_CSV}")

    print("\nAnnotating frames...")
    annotate(frame_files, all_dets, tracks, ANNOTATED_DIR, template.shape, every_n=3)

    print("\n" + "="*80)
    print("AGGREGATE (high R² on both axes, >=8 pts)")
    print("="*80)
    if all_vx:
        abs_vx = [abs(v) for v in all_vx]; abs_vy = [abs(v) for v in all_vy]
        print(f"\nUsing {len(all_vx)} high-confidence tracks")
        print(f"\n  |vx| values: {[round(v,4) for v in abs_vx]}")
        print(f"  |vx| mean={np.mean(abs_vx):.4f}  median={np.median(abs_vx):.4f}  std={np.std(abs_vx):.4f}")
        print(f"\n  |vy| values: {[round(v,4) for v in abs_vy]}")
        print(f"  |vy| mean={np.mean(abs_vy):.4f}  median={np.median(abs_vy):.4f}  std={np.std(abs_vy):.4f}")
        med_vx = np.median(abs_vx); med_vy = np.median(abs_vy); ms = 1000.0/FPS
        print("\n" + "="*80)
        print("╔══════════════════════════════════════════════════════════════╗")
        print("║    STRIPED FISH CONSTANT VELOCITY — RIGHT-FACING ONLY      ║")
        print("╠══════════════════════════════════════════════════════════════╣")
        print(f"║  |vx| = {med_vx:.4f} px/frame  = {med_vx*FPS:7.2f} px/sec             ║")
        print(f"║  |vy| = {med_vy:.4f} px/frame  = {med_vy*FPS:7.2f} px/sec           ║")
        print(f"║                                                              ║")
        print(f"║  vx = {med_vx/ms:.6f} px/ms                                ║")
        print(f"║  vy = {med_vy/ms:.6f} px/ms                                ║")
        print("╚══════════════════════════════════════════════════════════════╝")
    else:
        print("No high-confidence tracks found.")
        print("All tracks with >=5 pts:")
        for t in good:
            r = linreg(t)
            if r: vx,vy,r2x,r2y = r; print(f"  S{t.track_id}: |vx|={abs(vx):.4f} |vy|={abs(vy):.4f} R²_x={r2x:.4f} R²_y={r2y:.4f} n={len(t.positions)}")

if __name__ == "__main__":
    main()
