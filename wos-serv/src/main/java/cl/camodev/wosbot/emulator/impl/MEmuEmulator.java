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

public class MEmuEmulator extends Emulator {
	private static final Logger logger = LoggerFactory.getLogger(MEmuEmulator.class);

	public MEmuEmulator(String consolePath) {
		super(consolePath);
	}

	@Override
	protected String getDeviceSerial(String emulatorNumber) {
		// MEmu uses the format 127.0.0.1:XXXX where XXXX = 21503 + (emulatorNumber * 10)
		return "127.0.0.1:" + (21503 + Integer.parseInt(emulatorNumber) * 10);
	}


	@Override
	public void launchEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "memuc", "start", "-i", emulatorNumber };
		executeCommand(command);
		logger.info("MEmu launched at index " + emulatorNumber);
	}

	@Override
	public void closeEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "memuc", "stop", "-i", emulatorNumber };
		executeCommand(command);
		logger.info("MEmu closed at index " + emulatorNumber);
	}

	@Override
	public boolean isRunning(String emulatorNumber) {
		try {
			String[] command = { consolePath + File.separator + "memuc", "isvmrunning", "-i", emulatorNumber };
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());

			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				if (!line.equals("Not Running")) {
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
		logger.info("Verifying and applying settings for MEmu index {}", emulatorNumber);
		boolean wasRunning = isRunning(emulatorNumber);

		// MEmu settings modification via memuc usually requires the VM to be stopped
		if (wasRunning) {
			logger.info("Stopping MEmu index {} to apply core settings...", emulatorNumber);
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

		logger.info("Setting MEmu index {} Resolution to 720x1280 and DPI to 320", emulatorNumber);
		String[] configs = {
			"is_customed_resolution", "1",
			"resolution_width", "720",
			"resolution_height", "1280",
			"vbox_dpi", "320"
		};
		
		for (int i = 0; i < configs.length; i += 2) {
			String[] cmd = { consolePath + File.separator + "memuc", "setconfig", "-i", emulatorNumber, configs[i], configs[i+1] };
			executeCommand(cmd);
		}

		// Relaunch to apply Language via ADB
		logger.info("Starting MEmu index {} to apply Language and ADB settings...", emulatorNumber);
		launchEmulator(emulatorNumber);
		
		try {
			// Give emulator time to boot up to receive ADB commands
			Thread.sleep(15000);
			IDevice device = findDevice(emulatorNumber);
			if (device != null) {
				logger.info("Setting locale to en-US for MEmu index {}", emulatorNumber);
				try {
					device.executeShellCommand("setprop persist.sys.locale en-US; stop; sleep 2; start", new NullOutputReceiver(), 10000, TimeUnit.MILLISECONDS);
					logger.info("Language applied successfully for MEmu index {}", emulatorNumber);
					Thread.sleep(10000); // Give android time to restart UI
				} catch (Exception e) {
					logger.error("Failed to apply Language setting via ADB for MEmu", e);
				}
			} else {
				logger.warn("Could not connect to device via ADB to set language on MEmu index {}", emulatorNumber);
            }
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return true;
	}

	private void executeCommand(String[] command) {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());
			Process process = pb.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			logger.error("Error executing command", e);
		}
	}
}
