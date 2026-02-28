package cl.camodev.wosbot.emulator;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance screen capture using scrcpy-server's H.264 video stream,
 * decoded in real-time by a local FFmpeg subprocess.
 *
 * <h3>Architecture</h3>
 * <pre>
 * [Device]  scrcpy-server  →  H.264 over ADB-forwarded TCP socket
 *     → [Java]  reads socket, pipes to FFmpeg stdin
 *     → [FFmpeg] decodes H.264 to raw BGR24
 *     → [Java]  reads FFmpeg stdout, wraps in OpenCV Mat
 * </pre>
 *
 * <h3>Expected performance</h3>
 * <ul>
 *   <li>30–60 FPS (depends on device encoder &amp; configured max_fps)</li>
 *   <li>10–40 ms capture-to-frame latency</li>
 *   <li>5–10× faster than ADB screencap (~200-350 ms per frame)</li>
 * </ul>
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>{@code lib/scrcpy/scrcpy-server} — scrcpy-server v2.4+ JAR
 *       (download from <a href="https://github.com/Genymobile/scrcpy/releases">scrcpy releases</a>)</li>
 *   <li>{@code lib/ffmpeg/ffmpeg.exe} — FFmpeg binary
 *       (download from <a href="https://www.gyan.dev/ffmpeg/builds/">gyan.dev</a> or
 *       <a href="https://github.com/BtbN/FFmpeg-Builds/releases">BtbN builds</a>)</li>
 * </ul>
 *
 * @author wosbot
 */
public class ScrcpyStreamCapture implements VideoStreamCapture {

    private static final Logger logger = LoggerFactory.getLogger(ScrcpyStreamCapture.class);

    // ── scrcpy-server config ─────────────────────────────────────────────────
    private static final String SCRCPY_DEVICE_PATH = "/data/local/tmp/scrcpy-server.jar";
    private static final String SCRCPY_VERSION     = "2.7";
    private static final long   CONNECT_TIMEOUT_MS = 10_000L;

    // ── Constructor parameters ───────────────────────────────────────────────
    private final String adbPath;
    private final String deviceSerial;
    private final String scrcpyServerPath;
    private final String ffmpegPath;
    private final int    width;
    private final int    height;
    private final int    bitRate;
    private final int    maxFps;
    private final int    frameSize;          // width * height * 3 (BGR24)

    // ── Runtime state ────────────────────────────────────────────────────────
    private volatile boolean running;
    private Process  adbServerProcess;       // adb shell running scrcpy-server
    private Process  ffmpegProcess;          // local FFmpeg decoder
    private Socket   videoSocket;            // TCP connection to forwarded port
    private Thread   pipeThread;             // socket → FFmpeg stdin
    private Thread   readerThread;           // FFmpeg stdout → frame buffer
    private int      localPort;              // ADB-forwarded local TCP port
    private int      scid;                   // scrcpy server connection ID

    // ── Frame buffer ─────────────────────────────────────────────────────────
    private final AtomicReference<byte[]> latestFrameBytes = new AtomicReference<>();
    private volatile long latestFrameTimestampNs;
    private volatile int  decodedFrameCount;

    // ── Diagnostics ──────────────────────────────────────────────────────────
    private volatile String lastServerLine;   // last line from scrcpy-server (for error context)
    private volatile String lastError;

    // ======================================================================
    // Constructor
    // ======================================================================

    /**
     * Creates a new stream capture instance (does not start anything yet).
     *
     * @param adbPath           Absolute path to {@code adb.exe}
     * @param deviceSerial      ADB device serial (e.g. {@code "127.0.0.1:16416"})
     * @param scrcpyServerPath  Absolute path to scrcpy-server JAR file
     * @param ffmpegPath        Absolute path to {@code ffmpeg.exe}
     * @param width             Expected frame width  (e.g. 720)
     * @param height            Expected frame height (e.g. 1280)
     * @param bitRate           Video bit-rate in bps (e.g. 4_000_000 for 4 Mbps)
     * @param maxFps            Maximum frames per second (e.g. 60)
     */
    public ScrcpyStreamCapture(String adbPath, String deviceSerial,
                                String scrcpyServerPath, String ffmpegPath,
                                int width, int height, int bitRate, int maxFps) {
        this.adbPath          = adbPath;
        this.deviceSerial     = deviceSerial;
        this.scrcpyServerPath = scrcpyServerPath;
        this.ffmpegPath       = ffmpegPath;
        this.width            = width;
        this.height           = height;
        this.bitRate          = bitRate;
        this.maxFps           = maxFps;
        this.frameSize        = width * height * 3;   // BGR24
    }

    // ======================================================================
    // Public API
    // ======================================================================

    /**
     * Starts the full streaming pipeline.
     *
     * @throws IOException          if any process or socket fails
     * @throws InterruptedException if the calling thread is interrupted
     */
    public synchronized void start() throws IOException, InterruptedException {
        if (running) return;

        // Validate binaries exist
        validateFile(scrcpyServerPath, "scrcpy-server");
        validateFile(ffmpegPath, "ffmpeg");

        scid = Math.abs(deviceSerial.hashCode()) & 0x7FFFFFFF;
        if (scid == 0) scid = 1;   // 0 means "no scid" in scrcpy

        // 1. Push scrcpy-server to device
        pushServerToDevice();

        // 2. Forward a local port → device abstract socket
        localPort = setupPortForward();
        logger.info("[scrcpy {}] ADB forward on port {}", deviceSerial, localPort);

        // 3. Start scrcpy-server on device
        startScrcpyServer();

        // 4. Start FFmpeg NOW so it is fully loaded while we wait for the server.
        //    FFmpeg blocks on stdin until the pipe thread writes data — no race.
        startFfmpegDecoder();

        // 5. Wait for server to initialise encoder before connecting.
        //    Too short → server accepts socket but sends 0 bytes.
        Thread.sleep(1000);

        if (!adbServerProcess.isAlive()) {
            int exitCode = adbServerProcess.exitValue();
            String ctx = lastServerLine != null ? lastServerLine : "(no output)";
            lastError = "scrcpy-server exited immediately (code=" + exitCode + "): " + ctx;
            logger.warn("[scrcpy {}] {}", deviceSerial, lastError);
            destroyQuietly(ffmpegProcess);
            removePortForward();
            throw new IOException(lastError);
        }

        // 6. Connect to forwarded port
        connectToVideoSocket();

        // 7. Wire background threads — data starts flowing immediately
        running = true;
        startPipeThread();
        startReaderThread();

        logger.info("ScrcpyStreamCapture started: {}x{} @{}fps {}bps on {}",
                width, height, maxFps, bitRate, deviceSerial);
    }

    /**
     * Returns the latest decoded frame as a BGR OpenCV Mat.
     * <p>
     * <b>The caller MUST release the returned Mat.</b>
     * Returns {@code null} if no new frame has arrived since the last call.
     */
    public Mat grabFrame() {
        byte[] data = latestFrameBytes.getAndSet(null);
        if (data == null) return null;

        Mat mat = new Mat(height, width, CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    /**
     * Peeks at the latest frame <b>without</b> consuming it.
     */
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
     * @param timeoutMs Maximum time to wait (milliseconds)
     * @return Latest frame as BGR Mat, or {@code null} on timeout.
     *         <b>Caller MUST release the returned Mat.</b>
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

    /** @return {@code true} if the streaming pipeline is active */
    public boolean isRunning()                  { return running; }

    /** @return Total number of frames decoded since {@link #start()} */
    public int    getDecodedFrameCount()        { return decodedFrameCount; }

    /** @return System.nanoTime() timestamp of the most recent frame */
    public long   getLatestFrameTimestampNs()   { return latestFrameTimestampNs; }

    /** @return Diagnostic error message from the last failure, or null */
    public String getLastError()                { return lastError; }

    /** @return Expected frame width */
    public int    getWidth()                    { return width; }

    /** @return Expected frame height */
    public int    getHeight()                   { return height; }

    /**
     * Stops the streaming pipeline and releases all resources.
     */
    public synchronized void stop() {
        close();
    }

    @Override
    public synchronized void close() {
        if (!running) return;
        running = false;

        logger.info("Stopping ScrcpyStreamCapture for {} (decoded {} frames)",
                deviceSerial, decodedFrameCount);

        // 1. Interrupt background threads
        interruptQuietly(pipeThread);
        interruptQuietly(readerThread);

        // 2. Close video socket (unblocks the pipe thread)
        closeQuietly(videoSocket);

        // 3. Kill FFmpeg
        destroyQuietly(ffmpegProcess);

        // 4. Kill scrcpy-server
        destroyQuietly(adbServerProcess);

        // 5. Remove ADB port forward
        removePortForward();

        logger.info("ScrcpyStreamCapture stopped for {} (decoded {} frames)",
                deviceSerial, decodedFrameCount);
    }

    // ======================================================================
    // Static helpers — check if binaries are available
    // ======================================================================

    /**
     * Resolves the scrcpy-server JAR path relative to the current working dir.
     * Matches both exact names and versioned names (e.g. {@code scrcpy-server-v2.7}).
     *
     * @return Absolute path, or {@code null} if not found
     */
    public static String findScrcpyServer() {
        String[] exactCandidates = {
            "lib/scrcpy/scrcpy-server",
            "lib/scrcpy/scrcpy-server.jar",
            "wos-hmi/lib/scrcpy/scrcpy-server",
            "wos-hmi/lib/scrcpy/scrcpy-server.jar",
        };
        String exact = findFirstExisting(exactCandidates);
        if (exact != null) return exact;

        String cwd = System.getProperty("user.dir");
        String[] searchDirs = { "lib/scrcpy", "wos-hmi/lib/scrcpy" };
        for (String dir : searchDirs) {
            File folder = new File(cwd, dir);
            if (!folder.isDirectory()) continue;
            File[] matches = folder.listFiles(
                f -> f.isFile() && f.getName().startsWith("scrcpy-server"));
            if (matches != null && matches.length > 0) {
                java.util.Arrays.sort(matches,
                    (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                return matches[0].getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Resolves the FFmpeg executable path relative to the current working dir.
     *
     * @return Absolute path, or {@code null} if not found
     */
    public static String findFfmpeg() {
        String[] candidates = {
            "lib/ffmpeg/ffmpeg.exe",
            "wos-hmi/lib/ffmpeg/ffmpeg.exe",
            "lib/ffmpeg/ffmpeg",
            "wos-hmi/lib/ffmpeg/ffmpeg",
        };
        return findFirstExisting(candidates);
    }

    /**
     * Checks whether both scrcpy-server and FFmpeg binaries are available.
     */
    public static boolean isStreamCaptureAvailable() {
        return findScrcpyServer() != null && findFfmpeg() != null;
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
     * Pushes the scrcpy-server JAR to the device via ADB.
     */
    private void pushServerToDevice() throws IOException, InterruptedException {
        logger.info("[scrcpy {}] Pushing scrcpy-server to device...", deviceSerial);
        ProcessBuilder pb = new ProcessBuilder(
                adbPath, "-s", deviceSerial,
                "push", scrcpyServerPath, SCRCPY_DEVICE_PATH);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = readAll(p.getInputStream());
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("Failed to push scrcpy-server (exit=" + exit + "): " + output);
        }
        logger.info("[scrcpy {}] Push OK: {}", deviceSerial, output.trim());
    }

    /**
     * Sets up ADB TCP port forwarding. Uses {@code tcp:0} so ADB picks a free port.
     */
    private int setupPortForward() throws IOException, InterruptedException {
        String socketName = String.format("scrcpy_%08x", scid);
        ProcessBuilder pb = new ProcessBuilder(
                adbPath, "-s", deviceSerial,
                "forward", "tcp:0", "localabstract:" + socketName);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = readAll(p.getInputStream()).trim();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("ADB forward failed (exit=" + exit + "): " + output);
        }
        int port = Integer.parseInt(output);
        logger.info("[scrcpy {}] Forward tcp:{} -> localabstract:{}", deviceSerial, port, socketName);
        return port;
    }

    /**
     * Starts scrcpy-server on the device via {@code adb shell app_process}.
     * <p>
     * Uses {@code raw_stream=true} to disable all protocol framing so the
     * socket delivers a raw H.264 bitstream suitable for FFmpeg.
     * <p>
     * Does NOT force a specific encoder — lets scrcpy auto-select the best
     * available H.264 encoder on the device (MuMu/emulators often lack
     * hardware encoders, but the software {@code c2.android.avc.encoder}
     * or {@code OMX.google.h264.encoder} should work if present).
     */
    private void startScrcpyServer() throws IOException {

        ProcessBuilder pb = new ProcessBuilder(
                adbPath, "-s", deviceSerial, "shell",
                "CLASSPATH=" + SCRCPY_DEVICE_PATH,
                "app_process", "/",
                "com.genymobile.scrcpy.Server", SCRCPY_VERSION,
                "tunnel_forward=true",
                "video=true",
                "audio=false",
                "control=false",
                "max_size=" + Math.max(width, height),
                "video_bit_rate=" + bitRate,
                "max_fps=" + maxFps,
                "video_codec=h264",
                "video_codec_options=i-frame-interval:int=1",
                // No video_encoder specified — let scrcpy auto-select
                "raw_stream=true",
                "send_device_meta=false",
                "send_frame_meta=false",
                "send_dummy_byte=false",
                "send_codec_meta=false",
                "scid=" + String.format("%x", scid)
        );

        pb.redirectErrorStream(true);
        adbServerProcess = pb.start();

        // Drain server stdout to prevent blocking; track last line for error context
        Thread serverLog = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(adbServerProcess.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lastServerLine = line;
                    logger.info("[scrcpy-server {}] {}", deviceSerial, line);
                }
            } catch (IOException e) {
                if (running) logger.debug("[scrcpy-server {}] log reader ended: {}", deviceSerial, e.getMessage());
            }
        }, "scrcpy-log-" + deviceSerial);
        serverLog.setDaemon(true);
        serverLog.start();

        logger.info("[scrcpy {}] Server launched (scid={}, socket=scrcpy_{}).",
                deviceSerial, scid, String.format("%08x", scid));
    }

    /**
     * Connects to the ADB-forwarded TCP port with retries.
     */
    private void connectToVideoSocket() throws IOException {
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
        IOException lastConnectError = null;

        while (System.currentTimeMillis() < deadline) {
            if (!adbServerProcess.isAlive()) {
                String ctx = lastServerLine != null ? lastServerLine : "(no output)";
                lastError = "scrcpy-server died before socket connect: " + ctx;
                throw new IOException(lastError);
            }
            try {
                videoSocket = new Socket("127.0.0.1", localPort);
                videoSocket.setTcpNoDelay(true);
                videoSocket.setSoTimeout(5000);
                logger.info("[scrcpy {}] Connected on port {}", deviceSerial, localPort);
                return;
            } catch (IOException e) {
                lastConnectError = e;
                try { Thread.sleep(100); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while connecting to video socket", ie);
                }
            }
        }
        throw new IOException("Timeout connecting to scrcpy video socket on port " + localPort, lastConnectError);
    }

    /**
     * Starts the FFmpeg decoder subprocess with low-latency flags.
     */
    private void startFfmpegDecoder() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                // ── Input demuxer flags ──
                "-fflags", "+genpts+nobuffer+discardcorrupt",
                "-flags", "low_delay",
                "-analyzeduration", "0",
                "-probesize", "32768",        // 32KB: SPS+PPS+IDR data
                "-fpsprobesize", "0",          // skip fps probing — we set framerate explicitly
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

        // Log FFmpeg stderr at WARN level for visibility
        Thread stderrLog = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    logger.warn("[ffmpeg {}] {}", deviceSerial, line);
                }
            } catch (IOException e) {
                if (running) logger.warn("[ffmpeg {}] stderr reader error: {}", deviceSerial, e.getMessage());
            }
        }, "ffmpeg-stderr-" + deviceSerial);
        stderrLog.setDaemon(true);
        stderrLog.start();

    }

    // ======================================================================
    // Internal — background threads
    // ======================================================================

    /**
     * Pipe thread: reads H.264 bytes from the video socket and writes them
     * to FFmpeg's stdin.
     */
    private void startPipeThread() {
        pipeThread = new Thread(() -> {
            byte[] buffer = new byte[65536];
            long totalBytesRead = 0;
            long pipeStartMs = System.currentTimeMillis();
            try {
                InputStream  socketIn = new BufferedInputStream(videoSocket.getInputStream(), 262144);
                // Write directly — no BufferedOutputStream; Phase 1 already batches
                OutputStream ffmpegIn = ffmpegProcess.getOutputStream();

                // Phase 1: Accumulate initial data so FFmpeg receives SPS+PPS
                // together with IDR frame data in one write.  Without this,
                // the tiny 32-byte SPS+PPS fragment alone is not enough for
                // FFmpeg to initialise the decoder.
                final int INIT_THRESHOLD = 131072;   // 128 KB
                ByteArrayOutputStream initBuf = new ByteArrayOutputStream(INIT_THRESHOLD);
                while (running && initBuf.size() < INIT_THRESHOLD) {
                    int bytesRead = socketIn.read(buffer);
                    if (bytesRead == -1) break;
                    initBuf.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                if (initBuf.size() > 0) {
                    ffmpegIn.write(initBuf.toByteArray());
                    ffmpegIn.flush();
                    logger.info("[scrcpy {}] Phase 1 flushed {} bytes to FFmpeg ({}ms after pipe start)",
                            deviceSerial, initBuf.size(),
                            System.currentTimeMillis() - pipeStartMs);
                }

                // Phase 2: Normal streaming — write-through, flush every chunk
                long phase2Start = System.currentTimeMillis();
                long phase2Bytes = 0;
                long lastThroughputLogMs = phase2Start;
                int bytesRead;
                while (running && (bytesRead = socketIn.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    phase2Bytes += bytesRead;
                    ffmpegIn.write(buffer, 0, bytesRead);
                    ffmpegIn.flush();
                    // Log throughput every 5 seconds for warmup diagnostics
                    long now = System.currentTimeMillis();
                    if (now - lastThroughputLogMs >= 5000) {
                        long elapsedSec = Math.max(1, (now - phase2Start) / 1000);
                        logger.info("[scrcpy {}] Phase 2: {}KB piped in {}s ({}KB/s)",
                                deviceSerial, phase2Bytes / 1024, elapsedSec, phase2Bytes / 1024 / elapsedSec);
                        lastThroughputLogMs = now;
                    }
                }
            } catch (IOException e) {
                if (running) {
                    lastError = "Pipe error (socket->ffmpeg): " + e.getMessage();
                    logger.warn("[scrcpy {}] {} (piped {} bytes)",
                            deviceSerial, lastError, totalBytesRead);
                }
            }

            if (running) {
                if (totalBytesRead == 0) {
                    lastError = "Socket closed immediately — server sent 0 bytes.";
                    logger.warn("[scrcpy {}] {}", deviceSerial, lastError);
                }
                running = false;
            }
        }, "scrcpy-pipe-" + deviceSerial);
        pipeThread.setDaemon(true);
        pipeThread.start();
    }

    /**
     * Reader thread: reads raw BGR24 frames from FFmpeg's stdout and stores
     * each frame in the atomic buffer.
     */
    private void startReaderThread() {
        readerThread = new Thread(() -> {
            byte[] frameBuffer = new byte[frameSize];
            long readerStartMs = System.currentTimeMillis();
            boolean firstByteLogged = false;
            try {
                InputStream ffmpegOut = new BufferedInputStream(ffmpegProcess.getInputStream(), 262144);
                
                while (running) {
                    int totalRead = 0;
                    while (totalRead < frameSize) {
                        int n = ffmpegOut.read(frameBuffer, totalRead, frameSize - totalRead);
                        if (n == -1) return;
                        totalRead += n;
                        if (!firstByteLogged) {
                            logger.info("[scrcpy {}] FFmpeg first output byte at {}ms after reader start",
                                    deviceSerial, System.currentTimeMillis() - readerStartMs);
                            firstByteLogged = true;
                        }
                    }

                    byte[] copy = new byte[frameSize];
                    System.arraycopy(frameBuffer, 0, copy, 0, frameSize);
                    latestFrameBytes.set(copy);
                    latestFrameTimestampNs = System.nanoTime();
                    decodedFrameCount++;

                    // Log the first frame timing for warmup diagnostics
                    if (decodedFrameCount == 1) {
                        logger.info("[scrcpy {}] First frame decoded ({}ms after reader start)",
                                deviceSerial, System.currentTimeMillis() - readerStartMs);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logger.warn("[ffmpeg-reader {}] Reader error: {}",
                            deviceSerial, e.getMessage());
                }
            }
        }, "scrcpy-frame-reader-" + deviceSerial);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    // ======================================================================
    // Internal — cleanup helpers
    // ======================================================================

    private void removePortForward() {
        try {
            Process p = new ProcessBuilder(
                    adbPath, "-s", deviceSerial,
                    "forward", "--remove", "tcp:" + localPort)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception e) {
            logger.warn("Failed to remove ADB port forward for {}: {}", deviceSerial, e.toString());
        }
    }

    private static void interruptQuietly(Thread t) {
        if (t != null && t.isAlive()) t.interrupt();
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    private static void destroyQuietly(Process p) {
        if (p != null) p.destroyForcibly();
    }

    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String findFirstExisting(String[] candidates) {
        String cwd = System.getProperty("user.dir");
        for (String rel : candidates) {
            File f = new File(cwd, rel);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
}
