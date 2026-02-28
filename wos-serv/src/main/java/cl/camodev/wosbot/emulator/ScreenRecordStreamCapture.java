package cl.camodev.wosbot.emulator;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screen capture via Android's built-in {@code screenrecord} command.
 *
 * <h3>Architecture</h3>
 * <pre>
 * adb exec-out screenrecord --output-format=h264 -
 *     → pipe H.264 to FFmpeg stdin
 *     → FFmpeg decodes to raw BGR24
 *     → reader thread publishes latest frame
 * </pre>
 *
 * <h3>Advantages over scrcpy</h3>
 * <ul>
 *   <li>No extra JAR to push — uses Android's built-in {@code screenrecord}</li>
 *   <li>No abstract-socket forwarding — uses {@code exec-out} binary pipe</li>
 *   <li>Simpler protocol — no handshake, no framing options</li>
 * </ul>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Android 5.0+ required for {@code --output-format=h264}</li>
 *   <li>3-minute maximum recording time (OS limit); this class auto-detects
 *       the stream end but does <b>not</b> restart automatically</li>
 *   <li>Typically 15-30 FPS (device dependent)</li>
 * </ul>
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>{@code lib/ffmpeg/ffmpeg.exe} — FFmpeg binary</li>
 *   <li>ADB binary (already used by the emulator layer)</li>
 * </ul>
 *
 * @author wosbot
 */
public class ScreenRecordStreamCapture implements VideoStreamCapture {

    private static final Logger logger = LoggerFactory.getLogger(ScreenRecordStreamCapture.class);

    // ── Constructor parameters ───────────────────────────────────────────────
    private final String adbPath;
    private final String deviceSerial;
    private final String ffmpegPath;
    private final int    width;
    private final int    height;
    private final int    bitRate;
    private final int    maxFps;
    private final int    frameSize;    // width * height * 3 (BGR24)

    // ── Runtime state ────────────────────────────────────────────────────────
    private volatile boolean running;
    private Process adbProcess;        // adb exec-out screenrecord
    private Process ffmpegProcess;     // FFmpeg H.264 → BGR24 decoder
    private Thread  pipeThread;        // adb stdout → FFmpeg stdin
    private Thread  readerThread;      // FFmpeg stdout → frame buffer

    // ── Frame buffer ─────────────────────────────────────────────────────────
    private final AtomicReference<byte[]> latestFrameBytes = new AtomicReference<>();
    private volatile long latestFrameTimestampNs;
    private volatile int  decodedFrameCount;

    // ── Diagnostics ──────────────────────────────────────────────────────────
    private volatile String lastError;

    // ======================================================================
    // Constructor
    // ======================================================================

    /**
     * Creates a new screenrecord-based capture (does not start anything yet).
     *
     * @param adbPath      Absolute path to {@code adb.exe}
     * @param deviceSerial ADB device serial (e.g. {@code "127.0.0.1:16384"})
     * @param ffmpegPath   Absolute path to {@code ffmpeg.exe}
     * @param width        Expected frame width  (e.g. 720)
     * @param height       Expected frame height (e.g. 1280)
     * @param bitRate      Video bit-rate in bps (e.g. 8_000_000)
     * @param maxFps       Hint — screenrecord may not honour this
     */
    public ScreenRecordStreamCapture(String adbPath, String deviceSerial,
                                      String ffmpegPath,
                                      int width, int height,
                                      int bitRate, int maxFps) {
        this.adbPath      = adbPath;
        this.deviceSerial = deviceSerial;
        this.ffmpegPath   = ffmpegPath;
        this.width        = width;
        this.height       = height;
        this.bitRate      = bitRate;
        this.maxFps       = maxFps;
        this.frameSize    = width * height * 3;
    }

    // ======================================================================
    // Public API
    // ======================================================================

    /**
     * Starts the streaming pipeline.
     *
     * @throws IOException          if any process fails to launch
     * @throws InterruptedException if interrupted while waiting
     */
    public synchronized void start() throws IOException, InterruptedException {
        if (running) return;

        validateFile(ffmpegPath, "ffmpeg");

        // 1. Start ADB screenrecord piping H.264 to stdout
        startScreenRecord();

        // 2. Give screenrecord a moment to initialise the encoder
        Thread.sleep(800);

        // Check if it died immediately (e.g. "Unable to get output buffers")
        if (!adbProcess.isAlive()) {
            int exitCode = adbProcess.exitValue();
            lastError = "screenrecord exited immediately (code=" + exitCode + ")";
            logger.warn("[screenrecord {}] {}", deviceSerial, lastError);
            throw new IOException(lastError);
        }

        // 3. Start FFmpeg decoder
        startFfmpegDecoder();

        // 4. Wire pipe & reader threads
        running = true;
        startPipeThread();
        startReaderThread();

        logger.info("ScreenRecordStreamCapture started: {}x{} @{}bps on {}",
                width, height, bitRate, deviceSerial);
    }

    /**
     * Returns the latest decoded frame as a BGR OpenCV Mat.
     * <b>Caller MUST release the returned Mat.</b>
     * Returns {@code null} if no frame has arrived since last call.
     */
    public Mat grabFrame() {
        byte[] data = latestFrameBytes.getAndSet(null);
        if (data == null) return null;
        Mat mat = new Mat(height, width, CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    /** Peeks at the latest frame without consuming it. */
    public Mat peekFrame() {
        byte[] data = latestFrameBytes.get();
        if (data == null) return null;
        Mat mat = new Mat(height, width, CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    /**
     * Blocks until a new frame is available or the timeout expires.
     *
     * @param timeoutMs Maximum wait in milliseconds
     * @return BGR Mat or {@code null} on timeout. <b>Caller MUST release.</b>
     */
    public Mat waitForFrame(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Mat frame = grabFrame();
            if (frame != null) return frame;
            try { Thread.sleep(1); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public boolean isRunning()                { return running; }
    public int     getDecodedFrameCount()     { return decodedFrameCount; }
    public long    getLatestFrameTimestampNs() { return latestFrameTimestampNs; }
    public String  getLastError()             { return lastError; }
    public int     getWidth()                 { return width; }
    public int     getHeight()                { return height; }

    public synchronized void stop() { close(); }

    @Override
    public synchronized void close() {
        if (!running) return;
        running = false;

        logger.info("Stopping ScreenRecordStreamCapture for {} (decoded {} frames)",
                deviceSerial, decodedFrameCount);

        interruptQuietly(pipeThread);
        interruptQuietly(readerThread);
        destroyQuietly(ffmpegProcess);
        destroyQuietly(adbProcess);

        logger.info("ScreenRecordStreamCapture stopped for {} (decoded {} frames)",
                deviceSerial, decodedFrameCount);
    }

    // ======================================================================
    // Internal — pipeline setup
    // ======================================================================

    private void validateFile(String path, String name) throws IOException {
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            throw new IOException(name + " not found at: " + path);
        }
    }

    /**
     * Launches {@code adb exec-out screenrecord --output-format=h264 --size WxH -}
     * which writes raw H.264 bytes to the process stdout.
     */
    private void startScreenRecord() throws IOException {
        // exec-out is binary-safe (no PTY mangling), unlike "adb shell"
        ProcessBuilder pb = new ProcessBuilder(
                adbPath, "-s", deviceSerial,
                "exec-out",
                "screenrecord",
                "--output-format=h264",
                "--size", width + "x" + height,
                "--bit-rate", String.valueOf(bitRate),
                "-"    // output to stdout
        );
        pb.redirectErrorStream(false);
        adbProcess = pb.start();

        // Log screenrecord stderr
        Thread stderrLog = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(adbProcess.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    logger.warn("[screenrecord-stderr {}] {}", deviceSerial, line);
                }
            } catch (IOException e) {
                if (running) logger.warn("[screenrecord {}] stderr read error: {}",
                        deviceSerial, e.getMessage());
            }
        }, "screenrecord-stderr-" + deviceSerial);
        stderrLog.setDaemon(true);
        stderrLog.start();

        logger.info("[screenrecord {}] Process launched ({}x{} @{}bps)",
                deviceSerial, width, height, bitRate);
    }

    /**
     * Starts FFmpeg to decode H.264 (from stdin) to raw BGR24 (to stdout).
     */
    private void startFfmpegDecoder() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                // ── Input demuxer flags ──
                "-fflags", "+genpts+nobuffer+discardcorrupt",
                "-flags", "low_delay",
                "-analyzeduration", "0",
                "-probesize", "32768",        // 32KB: SPS+PPS+IDR data
                "-fpsprobesize", "0",          // skip fps probing
                "-max_delay", "0",
                // ── Decoder flags (MUST be before -i to apply to H264 decoder) ──
                "-threads", "1",               // single thread: no frame-level buffering
                "-thread_type", "slice",       // slice parallelism only
                "-framerate", "30",
                "-f", "h264",
                "-i", "pipe:0",
                // ── Output flags ──
                "-f", "rawvideo",
                "-pix_fmt", "bgr24",
                "-fps_mode", "passthrough",
                "-flush_packets", "1",
                "-v", "warning",
                "pipe:1"
        );
        pb.redirectErrorStream(false);
        ffmpegProcess = pb.start();

        // Log FFmpeg stderr
        Thread stderrLog = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    logger.warn("[ffmpeg-screenrecord {}] {}", deviceSerial, line);
                }
            } catch (IOException e) {
                if (running) logger.warn("[ffmpeg-screenrecord {}] stderr read error: {}",
                        deviceSerial, e.getMessage());
            }
        }, "ffmpeg-screenrecord-stderr-" + deviceSerial);
        stderrLog.setDaemon(true);
        stderrLog.start();

    }

    // ======================================================================
    // Internal — background threads
    // ======================================================================

    /**
     * Pipes ADB exec-out stdout (H.264) → FFmpeg stdin.
     */
    private void startPipeThread() {
        pipeThread = new Thread(() -> {
            byte[] buffer = new byte[65536];
            long totalBytes = 0;
            try {
                InputStream  adbOut   = new BufferedInputStream(adbProcess.getInputStream(), 262144);
                OutputStream ffmpegIn = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 262144);
                
                int bytesRead;
                while (running && (bytesRead = adbOut.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    ffmpegIn.write(buffer, 0, bytesRead);
                    ffmpegIn.flush();
                }
            } catch (IOException e) {
                if (running) {
                    lastError = "Pipe error (screenrecord->ffmpeg): " + e.getMessage();
                    logger.warn("[screenrecord {}] {} (piped {} bytes)", deviceSerial, lastError, totalBytes);
                }
            }

            if (running) {
                if (totalBytes == 0) {
                    lastError = "screenrecord produced 0 bytes";
                    logger.warn("[screenrecord {}] {}", deviceSerial, lastError);
                }
                running = false;
            }
        }, "screenrecord-pipe-" + deviceSerial);
        pipeThread.setDaemon(true);
        pipeThread.start();
    }

    /**
     * Reads raw BGR24 frames from FFmpeg stdout and publishes them.
     */
    private void startReaderThread() {
        readerThread = new Thread(() -> {
            byte[] frameBuffer = new byte[frameSize];
            try {
                InputStream ffmpegOut = new BufferedInputStream(ffmpegProcess.getInputStream(), 262144);
                
                while (running) {
                    int totalRead = 0;
                    while (totalRead < frameSize) {
                        int n = ffmpegOut.read(frameBuffer, totalRead, frameSize - totalRead);
                        if (n == -1) return;
                        totalRead += n;
                    }

                    byte[] copy = new byte[frameSize];
                    System.arraycopy(frameBuffer, 0, copy, 0, frameSize);
                    latestFrameBytes.set(copy);
                    latestFrameTimestampNs = System.nanoTime();
                    decodedFrameCount++;
                }
            } catch (IOException e) {
                if (running) {
                    logger.warn("[ffmpeg-reader {}] Reader error: {}",
                            deviceSerial, e.getMessage());
                }
            }
        }, "screenrecord-frame-reader-" + deviceSerial);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private static void interruptQuietly(Thread t) {
        if (t != null && t.isAlive()) t.interrupt();
    }

    private static void destroyQuietly(Process p) {
        if (p != null) p.destroyForcibly();
    }
}
