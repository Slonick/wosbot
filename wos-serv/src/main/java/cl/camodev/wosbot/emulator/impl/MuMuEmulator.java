package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;

import cl.camodev.wosbot.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MuMuEmulator extends Emulator {
	private static final Logger logger = LoggerFactory.getLogger(MuMuEmulator.class);
	private static final int COMMAND_TIMEOUT_SECONDS = 15;
	private static final long BACKEND_READY_CACHE_MS = 30_000L;
	private static final Pattern JSON_NUMBER_FIELD = Pattern.compile(
			"(?i)[\"']?(customAdbPort|adbPort|adb_port|port|adbPortNumber)[\"']?\\s*[:=]\\s*[\"']?(\\d{4,5})");
	private static final Pattern JSON_STRING_FIELD = Pattern.compile(
			"(?i)[\"']?(adbHost|host|ip|ipAddress)[\"']?\\s*[:=]\\s*[\"']([^\"'\\s,}]+)");
	private static final Pattern RUNNING_STATE_PATTERN = Pattern.compile(
			"(?i)[\"']?(state|status|playerState|vmState)[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)");
	private volatile long lastBackendReadyAtMs;

	public MuMuEmulator(String consolePath) {
		super(consolePath);
	}

	@Override
	protected String getDeviceSerial(String emulatorNumber) {
		return getMacDeviceSerial(emulatorNumber);
	}

	@Override
	public void launchEmulator(String emulatorNumber) {
		ensureMuMuAppRunning();
		waitForMuMuToolBackend();
		executeCommand(new String[] { getMuMuExecutablePath(), "open", emulatorNumber });
        logger.info("MuMu launched at index {}", emulatorNumber);
	}

	@Override
	public void closeEmulator(String emulatorNumber) {
		executeCommand(new String[] { getMuMuExecutablePath(), "close", emulatorNumber });
        logger.info("MuMu closed at index {}", emulatorNumber);
	}

	@Override
	public boolean isRunning(String emulatorNumber) {
		return isRunningOnMac(emulatorNumber);
	}

	@Override
	public boolean verifyAndApplySettings(String emulatorNumber) {
		logger.info("Verifying and applying settings for MuMu index {}", emulatorNumber);
		boolean wasRunning = isRunning(emulatorNumber);

		if (wasRunning) {
			logger.info("Stopping MuMu index {} to apply core settings...", emulatorNumber);
			closeEmulator(emulatorNumber);
			try {
				for (int i = 0; i < 15; i++) {
					if (!isRunning(emulatorNumber)) break;
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		String settings = "{\"resolutionWidthHeight\":\"720x1280\","
				+ "\"resolutionDPI\":320,"
				+ "\"usingNormalADBPort\":true,"
				+ "\"customAdbPort\":" + getMacAdbPort(emulatorNumber) + "}";
		executeCommand(new String[] { getMuMuExecutablePath(), "config", emulatorNumber, "--setting", settings });

		logger.info("Starting MuMu index {} to apply language settings via ADB...", emulatorNumber);
		launchEmulator(emulatorNumber);

		try {
			Thread.sleep(15000);

			IDevice device = findDevice(emulatorNumber);
			if (device != null) {
				logger.info("Setting locale to en-US for MuMu index {}", emulatorNumber);
				device.executeShellCommand("setprop persist.sys.locale en-US; stop; sleep 2; start",
						new NullOutputReceiver(), 10000, TimeUnit.MILLISECONDS);

				logger.info("Settings applied successfully for MuMu index {}", emulatorNumber);
				Thread.sleep(10000);
			} else {
				logger.warn("Could not connect to device via ADB to set Language on MuMu index {}", emulatorNumber);
			}
		} catch (Exception e) {
			logger.error("Failed to apply Language via ADB for MuMu index {}", emulatorNumber, e);
		}

		return true;
	}

	private String getMacDeviceSerial(String emulatorNumber) {
		waitForMuMuToolBackend();
		String output = runCommandForOutput(new String[] { getMuMuExecutablePath(), "info", emulatorNumber });
		String host = extractStringField(output, "127.0.0.1");
		Integer port = extractPort(output);
		if (port == null) {
			port = getMacAdbPort(emulatorNumber);
			logger.warn("Could not parse MuMu macOS ADB port for device {}. Falling back to configured port {}.",
					emulatorNumber, port);
		}
		return host + ":" + port;
	}

	private boolean isRunningOnMac(String emulatorNumber) {
		waitForMuMuToolBackend();
		String output = runCommandForOutput(new String[] { getMuMuExecutablePath(), "info", emulatorNumber });
		if (output.isBlank()) {
			return false;
		}

		Matcher stateMatcher = RUNNING_STATE_PATTERN.matcher(output);
		while (stateMatcher.find()) {
			String state = stateMatcher.group(2).toLowerCase();
			if (state.contains("run") || state.contains("boot") || state.contains("open") || state.contains("online")) {
				return true;
			}
			if (state.contains("close") || state.contains("stop") || state.contains("shutdown") || state.contains("offline")) {
				return false;
			}
		}

		String normalized = output.toLowerCase();
		if (normalized.contains("running") || normalized.contains("opened") || normalized.contains("online")) {
			return true;
		}
		if (normalized.contains("closed") || normalized.contains("stopped") || normalized.contains("shutdown")) {
			return false;
		}

		return false;
	}

	private int getMacAdbPort(String emulatorNumber) {
		return 16384 + (Integer.parseInt(emulatorNumber) * 32);
	}

	private Integer extractPort(String output) {
		if (output == null) {
			return null;
		}

		Matcher matcher = JSON_NUMBER_FIELD.matcher(output);
		while (matcher.find()) {
			String fieldName = matcher.group(1).toLowerCase();
			int port = Integer.parseInt(matcher.group(2));
			if (fieldName.contains("adb")) {
				return port;
			}
		}

		matcher.reset();
		if (matcher.find()) {
			return Integer.parseInt(matcher.group(2));
		}
		return null;
	}

	private String extractStringField(String output, String fallback) {
		if (output == null) {
			return fallback;
		}

		Matcher matcher = JSON_STRING_FIELD.matcher(output);
		return matcher.find() ? matcher.group(2) : fallback;
	}

	private String runCommandForOutput(String[] command) {
		try {
			ProcessBuilder pb = createProcessBuilder(command);
			Process process = pb.start();
			String output;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
				 BufferedReader errorReader = new BufferedReader(
						 new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				StringBuilder buffer = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					buffer.append(line).append('\n');
				}
				while ((line = errorReader.readLine()) != null) {
					buffer.append(line).append('\n');
				}
				output = buffer.toString();
			}

			boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				logger.warn("MuMu command timed out: {}", String.join(" ", command));
				return "";
			}
			return output;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("MuMu command interrupted: {}", String.join(" ", command));
		} catch (IOException e) {
			logger.error("Error executing MuMu command: {}", String.join(" ", command), e);
		}
		return "";
	}

	private void executeCommand(String[] command) {
		try {
			ProcessBuilder pb = createProcessBuilder(command);
			Process process = pb.start();
			boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				logger.warn("MuMu command timed out and was killed: {}", String.join(" ", command));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Command execution interrupted", e);
		} catch (IOException e) {
			logger.error("Error executing command", e);
		}
	}

	private ProcessBuilder createProcessBuilder(String[] command) {
		ProcessBuilder pb = new ProcessBuilder(command);
		File workingDirectory = new File(consolePath);
		pb.directory(workingDirectory.isDirectory() ? workingDirectory : workingDirectory.getParentFile());
		return pb;
	}

	private void ensureMuMuAppRunning() {
		String appBundlePath = "/Applications/MuMuPlayer Pro.app";
		try {
			Process process = new ProcessBuilder("open", "-g", appBundlePath).start();
			process.waitFor(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			logger.warn("Could not start MuMuPlayer Pro app bundle via open: {}", e.getMessage());
			try {
				Process process = new ProcessBuilder("/Applications/MuMuPlayer Pro.app/Contents/MacOS/MuMuPlayer Pro")
						.start();
				process.waitFor(10, TimeUnit.SECONDS);
			} catch (Exception inner) {
				logger.warn("Could not start MuMuPlayer Pro binary directly: {}", inner.getMessage());
			}
		}
	}

	private void waitForMuMuToolBackend() {
		long now = System.currentTimeMillis();
		if (lastBackendReadyAtMs > 0 && (now - lastBackendReadyAtMs) < BACKEND_READY_CACHE_MS) {
			return;
		}

		ensureMuMuAppRunning();

		for (int i = 0; i < 10; i++) {
			String output = runCommandForOutput(new String[] { getMuMuExecutablePath(), "port" }).trim();
			if (!output.isBlank() && !output.toLowerCase().contains("invalidport")) {
				lastBackendReadyAtMs = System.currentTimeMillis();
				logger.info("MuMu backend is ready. Server port response: {}", output);
				return;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		logger.warn("MuMu backend did not become ready in time; mumutool still reports invalid port.");
	}

	private String getMuMuExecutablePath() {
		return consolePath + File.separator + "mumutool";
	}
}
