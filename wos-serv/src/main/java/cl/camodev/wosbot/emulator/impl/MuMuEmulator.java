package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;

import cl.camodev.wosbot.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MuMuEmulator extends Emulator {
	private static final Logger logger = LoggerFactory.getLogger(MuMuEmulator.class);

	public MuMuEmulator(String consolePath) {
		super(consolePath);
	}

	@Override
	protected String getDeviceSerial(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "MuMuManager.exe", "adb", "-v", emulatorNumber, "connect" };
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());
			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			String host = "127.0.0.1";
			String port = "";
			
			while ((line = reader.readLine()) != null) {
				if (line.contains("\"adb_host\"")) {
					host = line.split(":")[1].replaceAll("[\" ,]", "").trim();
				} else if (line.contains("\"adb_port\"")) {
					port = line.split(":")[1].replaceAll("[, ]", "").trim();
				}
			}
			process.waitFor(TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS);
			
			if (!port.isEmpty()) {
				return host + ":" + port;
			}
		} catch (Exception e) {
			logger.error("Error getting MuMu adb port from manager, falling back to default.", e);
		}

		// Fallback for older MuMu versions
		int fallbackPort = 16384 + (Integer.parseInt(emulatorNumber) * 32);
		return "127.0.0.1:" + fallbackPort;
	}

	@Override
	public void launchEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "MuMuManager.exe", "api", "-v", emulatorNumber, "launch_player" };
		executeCommand(command);
        logger.info("MuMu launched at index {}", emulatorNumber);
	}

	@Override
	public void closeEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "MuMuManager.exe", "api", "-v", emulatorNumber, "shutdown_player" };
		executeCommand(command);
        logger.info("MuMu closed at index {}", emulatorNumber);
	}

	@Override
	public boolean isRunning(String emulatorNumber) {
		try {
			String[] command = { consolePath + File.separator + "MuMuManager.exe", "api", "-v", emulatorNumber, "player_state" };
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());

			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.contains("state=start_finished")) {
					return true;
				}
			}

			process.waitFor();
		} catch (IOException | InterruptedException e) {
			logger.error("Error checking if emulator is running", e);
		}
		return false;
	}

	@Override
	public boolean verifyAndApplySettings(String emulatorNumber) {
		logger.info("Verifying and applying settings for MuMu index {}", emulatorNumber);
		boolean wasRunning = isRunning(emulatorNumber);

		// Apply static config file injections via MuMuManager setting command
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

		logger.info("Setting Native Resolution (720x1280) and DPI (320) via MuMuManager for MuMu index {}", emulatorNumber);
		// Resolution mode "custom" allows overriding with specific resolution settings
		String[] customModifyMode = { consolePath + File.separator + "MuMuManager.exe", "setting", "--vmindex", emulatorNumber, "--key", "resolution_mode", "--value", "custom" };
		executeCommand(customModifyMode);

		String[] customModifyConfig = { 
			consolePath + File.separator + "MuMuManager.exe", "setting", "--vmindex", emulatorNumber, 
			"--key", "resolution_width.custom", "--value", "720",
			"--key", "resolution_height.custom", "--value", "1280",
			"--key", "resolution_dpi.custom", "--value", "320" 
		};
		executeCommand(customModifyConfig);
		
		// Set ADB language injection
		logger.info("Starting MuMu index {} to apply language settings via ADB...", emulatorNumber);
		launchEmulator(emulatorNumber);

		try {
			// Give MuMu time to boot
			Thread.sleep(15000); 

			IDevice device = findDevice(emulatorNumber);
			if (device != null) {
				logger.info("Setting locale to en-US for MuMu index {}", emulatorNumber);
				device.executeShellCommand("setprop persist.sys.locale en-US; stop; sleep 2; start", new NullOutputReceiver(), 10000, TimeUnit.MILLISECONDS);
				
				logger.info("Settings applied successfully for MuMu index {}", emulatorNumber);
				Thread.sleep(10000); // Give android time to restart UI
			} else {
				logger.warn("Could not connect to device via ADB to set Language on MuMu index {}", emulatorNumber);
			}
		} catch (Exception e) {
			logger.error("Failed to apply Language via ADB for MuMu index {}", emulatorNumber, e);
		}

		return true;
	}

	private void executeCommand(String[] command) {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());
			Process process = pb.start();
			process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Command execution interrupted", e);
		} catch (IOException e) {
			logger.error("Error executing command", e);
		}
	}
}
