package cl.camodev.wosbot.emulator;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

public enum EmulatorType {
	// @formatter:off
    MUMU("MuMuPlayer", EnumConfigurationKey.MUMU_PATH_STRING.name(), "MuMuManager.exe","C:\\Program Files\\Netease\\MuMuPlayer\\nx_main\\"),
    MEMU("MEmu Player", EnumConfigurationKey.MEMU_PATH_STRING.name(), "memuc.exe","C:\\Program Files\\Microvirt\\MEmu\\"),
    LDPLAYER("LDPlayer", EnumConfigurationKey.LDPLAYER_PATH_STRING.name(), "ldconsole.exe","C:\\LDPlayer\\LDPlayer9\\");
	    // @formatter:on

	private final String displayName;
	private final String configKey;
	private final String executableName;
	private final String defaultPath;

	EmulatorType(String displayName, String configKey, String executableName, String defaultPath) {
		this.displayName = displayName;
		this.configKey = configKey;
		this.executableName = executableName;
		this.defaultPath = defaultPath;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getConfigKey() {
		return configKey;
	}

	public String getExecutableName() {
		return executableName;
	}

	public String getDefaultPath() {
		return defaultPath + executableName;
	}
}
