package cl.camodev.wosbot.main;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.utiles.ImageSearchUtil;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.emulator.EmulatorType;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.TelegramBotService;

public class HeadlessApp {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessApp.class);

	public static void start(String[] args) {
		logger.info("Initializing Headless Bot...");

		// 1. Initialize external libraries
		try {
			ImageSearchUtil.loadNativeLibrary("/native/opencv/opencv_java4110.dll");
			logger.info("OpenCV native library loaded successfully.");
		} catch (IOException e) {
			logger.error("Failed to load OpenCV: ", e);
		}

		// 2. Initialize Emulator Manager paths
		initializeEmulatorManager();

		// 3. Initialize Telegram Bot manually
		initializeTelegramBot();

		boolean autostart = false;
		for (String arg : args) {
			if ("--autostart".equalsIgnoreCase(arg)) {
				autostart = true;
				break;
			}
		}

		if (autostart) {
			logger.info("Autostart flag detected. Starting automation...");
			ServScheduler.getServices().startBot();
		} else {
			logger.info("Headless bot ready. Waiting for Telegram commands to start automation...");
		}
	}

	private static void initializeTelegramBot() {
		HashMap<String, String> cfg = ServConfig.getServices().getGlobalConfig();
		if (cfg == null) {
			return;
		}

		boolean enabled = Boolean.parseBoolean(
				cfg.getOrDefault(EnumConfigurationKey.TELEGRAM_BOT_ENABLED_BOOL.name(), "false"));
		String token = cfg.getOrDefault(EnumConfigurationKey.TELEGRAM_BOT_TOKEN_STRING.name(), "");
		String chatIdStr = cfg.getOrDefault(EnumConfigurationKey.TELEGRAM_ALLOWED_CHAT_ID_STRING.name(), "");

		if (enabled && !token.isBlank()) {
			long chatId = chatIdStr.isBlank() ? 0L : Long.parseLong(chatIdStr);
			TelegramBotService.getInstance().start(token, chatId);
			logger.info("Telegram Bot Service started in Headless mode.");

			// Launch the background watcher to monitor the app process
			cl.camodev.wosbot.serv.impl.TelegramWatcherLauncher.startWatcherIfNotRunning();
		} else {
			logger.warn("Telegram integration is disabled or missing token. The headless bot might be unreachable unless autostart is used.");
		}
	}

	private static void initializeEmulatorManager() {
		HashMap<String, String> globalConfig = ServConfig.getServices().getGlobalConfig();

		if (globalConfig == null || globalConfig.isEmpty()) {
			globalConfig = new HashMap<>();
		}

		String savedActiveEmulator = globalConfig.get(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name());
		EmulatorType activeEmulator = null;
		if (savedActiveEmulator != null && !savedActiveEmulator.isEmpty()) {
			try {
				activeEmulator = EmulatorType.valueOf(savedActiveEmulator);
			} catch (IllegalArgumentException e) {
				// Ignore Invalid Enum constant
			}
		}
		boolean activeEmulatorValid = false;

		if (activeEmulator != null) {
			String activePath = globalConfig.get(activeEmulator.getConfigKey());
			if (activePath != null && new File(activePath).exists()) {
				activeEmulatorValid = true;
			} else {
				ServScheduler.getServices().saveEmulatorPath(activeEmulator.getConfigKey(), null);
			}
		}

		if (!activeEmulatorValid) {
			logger.warn("No valid active emulator configured. Automation might fail unless changed.");
		}
	}
}
