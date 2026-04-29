package cl.camodev.wosbot.main;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.utiles.PlatformPaths;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.TelegramBotService;

public class HeadlessApp {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessApp.class);

	public static void start(String[] args) {
		logger.info("Initializing Headless Bot...");

		// 1. Initialize external libraries
		try {
			PlatformPaths.loadOpenCvNativeLibrary();
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

		String activePath = globalConfig.get(EnumConfigurationKey.MUMU_PATH_STRING.name());
		if (activePath == null || !new File(activePath).exists()) {
			logger.warn("No valid active emulator configured. Automation might fail unless changed.");
		}
	}
}
