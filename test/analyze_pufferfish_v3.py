"""
Pufferfish velocity analysis v3.
Only tracks RIGHT-FACING fish (no template flip).
Clean unidirectional tracks, no bounce complications.
"""

import cv2
import numpy as np
import os
import csv
import sys
from pathlib import Path

FRAMES_DIR = r"c:\Users\parad\Desktop\New folder\wosbot\analysis_frames"
TEMPLATE_PATH = r"c:\Users\parad\Desktop\New folder\wosbot\wos-serv\src\main\resources\templates\fishingtoornament\pufferfishRight.png"
OUTPUT_CSV = r"c:\Users\parad\Desktop\New folder\wosbot\pufferfish_tracking_v3.csv"
ANNOTATED_DIR = r"c:\Users\parad\Desktop\New folder\wosbot\analysis_frames\annotated_puffer_v3"
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


def load_frames(frames_dir):
    return sorted(Path(frames_dir).glob("frame_*.png"))


def detect_fish_right_only(frame, template, threshold):
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    th, tw = template.shape[:2]
    result = cv2.matchTemplate(gray, template, cv2.TM_CCOEFF_NORMED)
    locations = np.where(result >= threshold)
    detections = []
    for pt_y, pt_x in zip(*locations):
        score = result[pt_y, pt_x]
        cx = pt_x + tw // 2
        cy = pt_y + th // 2
        if UI_TOP_Y <= cy <= PLAY_BOT_Y:
            detections.append((cx, cy, tw, th, float(score)))
    return nms(detections, merge_dist=40)


def nms(detections, merge_dist=40):
    if not detections:
        return []
    dets = sorted(detections, key=lambda d: d[4], reverse=True)
    kept = []
    for d in dets:
        if not any(np.hypot(d[0] - k[0], d[1] - k[1]) < merge_dist for k in kept):
            kept.append(d)
    return kept


class FishTrack:
    def __init__(self, track_id, cx, cy, frame_idx):
        self.track_id = track_id
        self.positions = [(frame_idx, cx, cy)]
        self.last_cx = cx
        self.last_cy = cy
        self.last_frame = frame_idx
        self.lost_count = 0
        self.alive = True

    def update(self, cx, cy, frame_idx):
        self.positions.append((frame_idx, cx, cy))
        self.last_cx = cx
        self.last_cy = cy
        self.last_frame = frame_idx
        self.lost_count = 0

    def mark_lost(self):
        self.lost_count += 1
        if self.lost_count > 8:
            self.alive = False

    def predicted_pos(self, frame_idx):
        if len(self.positions) < 2:
            return self.last_cx, self.last_cy
        f1, x1, y1 = self.positions[-1]
        f0, x0, y0 = self.positions[-2]
        df = f1 - f0
        if df == 0:
            return x1, y1
        vx = (x1 - x0) / df
        vy = (y1 - y0) / df
        dt = frame_idx - f1
        return x1 + vx * dt, y1 + vy * dt


def track_fish(all_detections):
    tracks = []
    next_id = 0
    for frame_idx, dets in all_detections:
        active = [t for t in tracks if t.alive]
        pairs = []
        for di, d in enumerate(dets):
            for ti, t in enumerate(active):
                px, py = t.predicted_pos(frame_idx)
                dist = np.hypot(d[0] - px, d[1] - py)
                pairs.append((dist, ti, di))
        pairs.sort()
        used_tracks = set()
        used_dets = set()
        for dist, ti, di in pairs:
            if ti in used_tracks or di in used_dets:
                continue
            if dist < MAX_MATCH_DIST:
                active[ti].update(dets[di][0], dets[di][1], frame_idx)
                used_tracks.add(ti)
                used_dets.add(di)
        for ti, t in enumerate(active):
            if ti not in used_tracks:
                t.mark_lost()
        for di, d in enumerate(dets):
            if di not in used_dets:
                tracks.append(FishTrack(next_id, d[0], d[1], frame_idx))
                next_id += 1
    return tracks


def linear_regression_velocity(track):
    if len(track.positions) < 3:
        return None
    frames = np.array([p[0] for p in track.positions], dtype=float)
    xs = np.array([p[1] for p in track.positions], dtype=float)
    ys = np.array([p[2] for p in track.positions], dtype=float)

    A = np.vstack([frames, np.ones(len(frames))]).T
    vx, bx = np.linalg.lstsq(A, xs, rcond=None)[0]
    res_x = xs - (vx * frames + bx)
    ss_res_x = np.sum(res_x ** 2)
    ss_tot_x = np.sum((xs - np.mean(xs)) ** 2)
    r2_x = 1 - ss_res_x / ss_tot_x if ss_tot_x > 0 else 0

    vy, by = np.linalg.lstsq(A, ys, rcond=None)[0]
    res_y = ys - (vy * frames + by)
    ss_res_y = np.sum(res_y ** 2)
    ss_tot_y = np.sum((ys - np.mean(ys)) ** 2)
    r2_y = 1 - ss_res_y / ss_tot_y if ss_tot_y > 0 else 0

    return vx, vy, r2_x, r2_y


def annotate_frames(frame_files, all_detections, tracks, out_dir, template_shape, every_n=3):
    os.makedirs(out_dir, exist_ok=True)
    track_lookup = {}
    for t in tracks:
        if len(t.positions) < 5:
            continue
        for f, x, y in t.positions:
            track_lookup.setdefault(f, []).append((t.track_id, x, y))
    th, tw = template_shape
    hw, hh = tw // 2, th // 2
    for frame_idx, dets in all_detections:
        if frame_idx % every_n != 0:
            continue
        frame = cv2.imread(str(frame_files[frame_idx]))
        if frame is None:
            continue
        for d in dets:
            cv2.circle(frame, (d[0], d[1]), 4, (255, 255, 255), 1)
        if frame_idx in track_lookup:
            for tid, cx, cy in track_lookup[frame_idx]:
                color = COLORS[tid % len(COLORS)]
                cv2.rectangle(frame, (cx - hw, cy - hh), (cx + hw, cy + hh), color, 2)
                cv2.putText(frame, f"P{tid}", (cx - hw, cy - hh - 5),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)
        cv2.putText(frame, f"Frame {frame_idx}", (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
        cv2.imwrite(os.path.join(out_dir, f"ann_{frame_idx:04d}.png"), frame)
    print(f"Annotated frames written to {out_dir}")


def main():
    print("Loading pufferfish template (RIGHT-FACING ONLY)...")
    template = cv2.imread(TEMPLATE_PATH, cv2.IMREAD_GRAYSCALE)
    if template is None:
        print(f"ERROR: Could not load template from {TEMPLATE_PATH}")
        sys.exit(1)
    print(f"Template size: {template.shape[1]}x{template.shape[0]} px")

    frame_files = load_frames(FRAMES_DIR)
    print(f"Found {len(frame_files)} frames")

    print("Running template matching (right-facing only)...")
    all_detections = []
    for idx, fpath in enumerate(frame_files):
        frame = cv2.imread(str(fpath))
        if frame is None:
            continue
        dets = detect_fish_right_only(frame, template, THRESHOLD)
        all_detections.append((idx, dets))
        if (idx + 1) % 50 == 0:
            total_d = sum(len(d) for _, d in all_detections)
            print(f"  {idx+1}/{len(frame_files)} — {total_d} detections")

    total_d = sum(len(d) for _, d in all_detections)
    print(f"Total right-facing detections: {total_d}")

    print("Tracking...")
    tracks = track_fish(all_detections)
    good_tracks = [t for t in tracks if len(t.positions) >= 5]
    print(f"Total tracks: {len(tracks)} | With >=5 pts: {len(good_tracks)}")

    # Linear regression per track
    print("\n" + "=" * 80)
    print("LINEAR REGRESSION VELOCITY PER TRACK (right-facing only)")
    print("=" * 80)

    all_vx = []
    all_vy = []
    csv_rows = []

    for t in good_tracks:
        result = linear_regression_velocity(t)
        if result is None:
            continue
        vx, vy, r2_x, r2_y = result
        span = t.positions[-1][0] - t.positions[0][0]
        print(f"\nTrack {t.track_id}: {len(t.positions)} pts, frames {t.positions[0][0]}-{t.positions[-1][0]} (span={span})")
        print(f"  Start: ({t.positions[0][1]}, {t.positions[0][2]})  End: ({t.positions[-1][1]}, {t.positions[-1][2]})")
        print(f"  vx = {vx:+.4f} px/frame (R²={r2_x:.6f})")
        print(f"  vy = {vy:+.4f} px/frame (R²={r2_y:.6f})")
        print(f"  |vx| = {abs(vx):.4f} px/frame = {abs(vx)*FPS:.2f} px/sec")
        print(f"  |vy| = {abs(vy):.4f} px/frame = {abs(vy)*FPS:.2f} px/sec")

        if r2_y > 0.95 and r2_x > 0.90 and len(t.positions) >= 8:
            all_vx.append(vx)
            all_vy.append(vy)

        csv_rows.append({
            'track_id': t.track_id, 'n_points': len(t.positions),
            'frame_start': t.positions[0][0], 'frame_end': t.positions[-1][0],
            'start_x': t.positions[0][1], 'start_y': t.positions[0][2],
            'end_x': t.positions[-1][1], 'end_y': t.positions[-1][2],
            'vx': round(vx, 6), 'vy': round(vy, 6),
            'abs_vx': round(abs(vx), 6), 'abs_vy': round(abs(vy), 6),
            'r2_x': round(r2_x, 6), 'r2_y': round(r2_y, 6),
        })

    if csv_rows:
        with open(OUTPUT_CSV, 'w', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=csv_rows[0].keys())
            writer.writeheader()
            writer.writerows(csv_rows)
        print(f"\nWrote {len(csv_rows)} tracks to {OUTPUT_CSV}")

    print("\nAnnotating frames...")
    annotate_frames(frame_files, all_detections, tracks, ANNOTATED_DIR, template.shape, every_n=3)

    # Final stats
    print("\n" + "=" * 80)
    print("AGGREGATE (high R² on both axes, >=8 pts)")
    print("=" * 80)
    if all_vx:
        abs_vx = [abs(v) for v in all_vx]
        abs_vy = [abs(v) for v in all_vy]
        print(f"\nUsing {len(all_vx)} high-confidence tracks")
        print(f"\n  |vx| values: {[round(v,4) for v in abs_vx]}")
        print(f"  |vx| mean={np.mean(abs_vx):.4f}  median={np.median(abs_vx):.4f}  std={np.std(abs_vx):.4f}")
        print(f"\n  |vy| values: {[round(v,4) for v in abs_vy]}")
        print(f"  |vy| mean={np.mean(abs_vy):.4f}  median={np.median(abs_vy):.4f}  std={np.std(abs_vy):.4f}")

        med_vx = np.median(abs_vx)
        med_vy = np.median(abs_vy)
        ms_per_frame = 1000.0 / FPS

        print("\n" + "=" * 80)
        print("╔══════════════════════════════════════════════════════════════╗")
        print("║   PUFFERFISH CONSTANT VELOCITY — RIGHT-FACING ONLY         ║")
        print("╠══════════════════════════════════════════════════════════════╣")
        print(f"║  |vx| = {med_vx:.4f} px/frame  = {med_vx*FPS:7.2f} px/sec             ║")
        print(f"║  |vy| = {med_vy:.4f} px/frame  = {med_vy*FPS:7.2f} px/sec           ║")
        print(f"║                                                              ║")
        print(f"║  vx = {med_vx/ms_per_frame:.6f} px/ms                                ║")
        print(f"║  vy = {med_vy/ms_per_frame:.6f} px/ms                                ║")
        print("╚══════════════════════════════════════════════════════════════╝")
    else:
        print("No high-confidence tracks found.")
        print("All tracks with >=5 pts:")
        for t in good_tracks:
            result = linear_regression_velocity(t)
            if result:
                vx, vy, r2_x, r2_y = result
                print(f"  T{t.track_id}: |vx|={abs(vx):.4f} |vy|={abs(vy):.4f} R²_x={r2_x:.4f} R²_y={r2_y:.4f} n={len(t.positions)}")


if __name__ == "__main__":
    main()
