package cl.camodev.utiles;

import java.io.File;
import java.io.IOException;

import nu.pattern.OpenCV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlatformPaths {

    private static final Logger logger = LoggerFactory.getLogger(PlatformPaths.class);
    private static final String OPENCV_LIBRARY_NAME = "opencv_java4110";
    private static volatile boolean openCvLoaded;

    private PlatformPaths() {
    }

    public static boolean isWindows() {
        return osName().contains("win");
    }

    public static boolean isMac() {
        return osName().contains("mac");
    }

    public static String adbExecutableName() {
        return executableName("adb");
    }

    public static String ffmpegExecutableName() {
        return executableName("ffmpeg");
    }

    public static String executableName(String baseName) {
        return isWindows() ? baseName + ".exe" : baseName;
    }

    public static String resolveBundledAdbPath(String consolePath) {
        String resolved = findFirstExisting(
                "lib/adb/" + adbExecutableName(),
                "wos-hmi/lib/adb/" + adbExecutableName());
        if (resolved != null) {
            return resolved;
        }

        if (consolePath != null && !consolePath.isBlank()) {
            File consoleAdb = new File(consolePath, adbExecutableName());
            if (consoleAdb.isFile()) {
                return consoleAdb.getAbsolutePath();
            }
        }

        String adbOnPath = findExecutableOnPath(adbExecutableName());
        if (adbOnPath != null) {
            return adbOnPath;
        }

        if (consolePath != null && !consolePath.isBlank()) {
            return new File(consolePath, adbExecutableName()).getAbsolutePath();
        }
        return adbExecutableName();
    }

    public static String resolveBundledFfmpegPath() {
        String resolved = findFirstExisting(
                "lib/ffmpeg/" + ffmpegExecutableName(),
                "wos-hmi/lib/ffmpeg/" + ffmpegExecutableName());
        if (resolved != null) {
            return resolved;
        }

        return findExecutableOnPath(ffmpegExecutableName());
    }

    public static void configureTesseractNativePath() {
        String configuredPath = firstNonBlank(
                System.getProperty("wosbot.tesseract.libdir"),
                System.getenv("TESSERACT_LIB_DIR"));

        String libraryDir = configuredPath;
        if (libraryDir == null && isMac()) {
            File brewDir = new File("/opt/homebrew/opt/tesseract/lib");
            if (brewDir.isDirectory()) {
                libraryDir = brewDir.getAbsolutePath();
            }
        }

        if (libraryDir == null) {
            return;
        }

        String existing = System.getProperty("jna.library.path");
        if (existing == null || existing.isBlank()) {
            System.setProperty("jna.library.path", libraryDir);
        } else if (!existing.contains(libraryDir)) {
            System.setProperty("jna.library.path", libraryDir + File.pathSeparator + existing);
        }
    }

    public static synchronized void loadOpenCvNativeLibrary() throws IOException {
        if (openCvLoaded) {
            return;
        }

        try {
            OpenCV.loadLocally();
            logger.info("OpenCV native library loaded from bundled Maven dependency.");
            openCvLoaded = true;
            return;
        } catch (Throwable e) {
            logger.info("Bundled OpenCV loader unavailable, falling back to manual resolution: {}", e.getMessage());
        }

        String bundledResource = getBundledOpenCvResourcePath();
        if (bundledResource != null) {
            ImageSearchUtil.loadNativeLibrary(bundledResource);
            openCvLoaded = true;
            return;
        }

        String configuredPath = firstNonBlank(
                System.getProperty("wosbot.opencv.lib"),
                System.getenv("OPENCV_LIB_PATH"));
        if (configuredPath != null) {
            System.load(new File(configuredPath).getAbsolutePath());
            logger.info("OpenCV native library loaded from configured path: {}", configuredPath);
            openCvLoaded = true;
            return;
        }

        try {
            System.loadLibrary(OPENCV_LIBRARY_NAME);
            logger.info("OpenCV native library loaded from java.library.path: {}", OPENCV_LIBRARY_NAME);
            openCvLoaded = true;
            return;
        } catch (UnsatisfiedLinkError ignored) {
            // Fall through to a clearer error message below.
        }

        throw new IOException("OpenCV native library not found for " + System.getProperty("os.name")
                + ". Set -Dwosbot.opencv.lib=/absolute/path/to/" + libraryFileName()
                + " or define OPENCV_LIB_PATH, or install OpenCV so " + OPENCV_LIBRARY_NAME
                + " is available via java.library.path.");
    }

    private static String getBundledOpenCvResourcePath() {
        String[] candidates;
        if (isWindows()) {
            candidates = new String[] { "/native/opencv/opencv_java4110.dll" };
        } else if (isMac()) {
            candidates = new String[] { "/native/opencv/libopencv_java4110.dylib" };
        } else {
            candidates = new String[] { "/native/opencv/libopencv_java4110.so" };
        }

        for (String candidate : candidates) {
            if (PlatformPaths.class.getResource(candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String libraryFileName() {
        if (isWindows()) {
            return "opencv_java4110.dll";
        }
        if (isMac()) {
            return "libopencv_java4110.dylib";
        }
        return "libopencv_java4110.so";
    }

    private static String findFirstExisting(String... relativePaths) {
        String cwd = System.getProperty("user.dir");
        for (String relativePath : relativePaths) {
            File candidate = new File(cwd, relativePath);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static String findExecutableOnPath(String executableName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }

        for (String pathEntry : pathEnv.split(File.pathSeparator)) {
            if (pathEntry == null || pathEntry.isBlank()) {
                continue;
            }
            File candidate = new File(pathEntry, executableName);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase();
    }
}
