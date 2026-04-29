package cl.camodev.wosbot.emulator;

import java.io.File;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

public enum EmulatorType {
    MUMU("MuMuPlayer", EnumConfigurationKey.MUMU_PATH_STRING.name(), "mumutool",
            "/Applications/MuMuPlayer Pro.app/Contents/MacOS");

    private final String displayName;
    private final String configKey;
    private final String executableName;
    private final String defaultDirectory;

    EmulatorType(String displayName, String configKey, String executableName, String defaultDirectory) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.executableName = executableName;
        this.defaultDirectory = defaultDirectory;
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

    public String getDefaultDirectory() {
        return defaultDirectory;
    }

    public String getDefaultPath() {
        return defaultDirectory + File.separator + executableName;
    }
}
