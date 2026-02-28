package cl.camodev.wosbot.emulator;

import org.opencv.core.Mat;

/**
 * Common interface for video stream capture implementations.
 * <p>
 * Allows {@code ScrcpyStreamCapture} and {@code ScreenRecordStreamCapture}
 * to be used interchangeably by task code.
 */
public interface VideoStreamCapture extends AutoCloseable {

    /** Grabs the latest frame (consuming it). Returns {@code null} if none available. */
    Mat grabFrame();

    /** Blocks until a frame arrives or timeout. Returns {@code null} on timeout. */
    Mat waitForFrame(long timeoutMs);

    /** @return {@code true} if the stream is still active */
    boolean isRunning();

    /** @return Number of frames decoded since start */
    int getDecodedFrameCount();

    /** @return Diagnostic error message, or {@code null} */
    String getLastError();

    /** Stops the capture and releases resources. */
    void stop();

    /** Same as {@link #stop()} — for {@link AutoCloseable}. */
    @Override
    void close();
}
