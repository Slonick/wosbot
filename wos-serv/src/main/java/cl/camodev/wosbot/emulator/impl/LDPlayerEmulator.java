package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;

import cl.camodev.wosbot.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LDPlayerEmulator extends Emulator {

    private static final Logger logger = LoggerFactory.getLogger(LDPlayerEmulator.class);
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    public LDPlayerEmulator(String consolePath) {
        super(consolePath);
    }

    @Override
    protected String getDeviceSerial(String emulatorNumber) {
        // LDPlayer exposes ADB on port 5555 + (index * 2)
        // Using 127.0.0.1:PORT format (consistent with MuMu/MEmu) so both
        // ddmlib auto-discovery AND adb connect recovery work correctly.
        // Note: The old emulator-XXXX format used the console port (5554),
        // which worked for auto-discovery but failed on adb connect recovery
        // because adb connect needs the ADB port (5555), not the console port.
        int port = 5555 + (Integer.parseInt(emulatorNumber) * 2);
        return "127.0.0.1:" + port;
    }

    @Override
    public void launchEmulator(String emulatorNumber) {
        String[] command = { consolePath + File.separator + "ldconsole.exe", "launch", "--index", emulatorNumber };
        executeCommand(command);
        logger.info("LDPlayer launched at index {}", emulatorNumber);
    }

    @Override
    public void closeEmulator(String emulatorNumber) {
        String[] command = { consolePath + File.separator + "ldconsole.exe", "quit", "--index", emulatorNumber };
        executeCommand(command);
        logger.info("LDPlayer closed at index {}", emulatorNumber);
    }

    @Override
    public boolean isRunning(String emulatorNumber) {
        try {
            String[] command = { consolePath + File.separator + "ldconsole.exe", "isrunning", "--index", emulatorNumber };
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(consolePath));

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("LDPlayer isRunning check timed out for index {}", emulatorNumber);
                return false;
            }

            return line != null && line.trim().equalsIgnoreCase("running");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("LDPlayer isRunning check interrupted for index {}", emulatorNumber);
        } catch (IOException e) {
            logger.error("Error checking if LDPlayer emulator is running at index {}", emulatorNumber, e);
        }
        return false;
    }

    @Override
    public boolean verifyAndApplySettings(String emulatorNumber) {
        logger.info("Verifying and applying settings for LDPlayer index {}", emulatorNumber);
        boolean wasRunning = isRunning(emulatorNumber);

        // Settings that require emulator to be stopped: Resolution, DPI, and ADB
        if (wasRunning) {
            logger.info("Stopping LDPlayer index {} to apply core settings...", emulatorNumber);
            closeEmulator(emulatorNumber);
            try {
                // Wait for emulator to stop properly
                for (int i = 0; i < 15; i++) {
                    if (!isRunning(emulatorNumber)) break;
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // Apply Resolution + DPI using ldconsole modify
        logger.info("Setting LDPlayer index {} Resolution to 720x1280 and DPI to 320", emulatorNumber);
        String[] modifyCommand = { consolePath + File.separator + "ldconsole.exe", "modify", "--index", emulatorNumber, "--resolution", "720,1280,320" };
        executeCommand(modifyCommand);

        // Apply ADB Debugging in vms/config/leidianX.config
        logger.info("Ensuring ADB Debugging is enabled for LDPlayer index {}", emulatorNumber);
        File configFile = new File(consolePath + File.separator + "vms" + File.separator + "config" + File.separator + "leidian" + emulatorNumber + ".config");
        if (configFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (content.contains("\"basicSettings.adbDebug\": 0") || content.contains("\"basicSettings.adbDebug\":0")) {
                    content = content.replaceAll("\"basicSettings\\.adbDebug\"\\s*:\\s*0", "\"basicSettings.adbDebug\": 1");
                    java.nio.file.Files.write(configFile.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else if (!content.contains("\"basicSettings.adbDebug\"")) {
                    content = content.replaceAll("}$", ",\n    \"basicSettings.adbDebug\": 1\n}");
                    java.nio.file.Files.write(configFile.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                logger.error("Failed to update config file for LDPlayer index {}", emulatorNumber, e);
            }
        } else {
            logger.warn("LDPlayer config file not found for index {}: {}", emulatorNumber, configFile.getAbsolutePath());
        }

        // Now we need it running to set language via ADB
        logger.info("Starting LDPlayer index {} to apply Language settings...", emulatorNumber);
        launchEmulator(emulatorNumber);
        
        try {
            // Give emulator time to establish ADB
            Thread.sleep(15000); 
            IDevice device = findDevice(emulatorNumber);
            if (device != null) {
                logger.info("Setting locale to en-US for LDPlayer index {}", emulatorNumber);
                try {
                	device.executeShellCommand("setprop persist.sys.locale en-US; stop; sleep 2; start", new NullOutputReceiver(), 10000, TimeUnit.MILLISECONDS);
                	logger.info("Language applied successfully for LDPlayer index {}", emulatorNumber);
                	Thread.sleep(10000); // Give android time to restart UI
                } catch (Exception e) {
                	logger.error("Failed to apply Language setting via ADB", e);
                }
            } else {
                logger.warn("Could not connect to device via ADB to set language on LDPlayer index {}", emulatorNumber);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // If it wasn't initially running, maybe we leave it as running, 
        // since starting task will need it running anyway.
        return true;
    }

    private void executeCommand(String[] command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(consolePath));
            Process process = pb.start();
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("LDPlayer command timed out and was killed: {}", String.join(" ", command));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("LDPlayer command interrupted: {}", String.join(" ", command));
        } catch (IOException e) {
            logger.error("Error executing LDPlayer command: {}", String.join(" ", command), e);
        }
    }
}
