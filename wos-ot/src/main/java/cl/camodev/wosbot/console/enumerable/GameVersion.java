package cl.camodev.wosbot.console.enumerable;

public enum GameVersion {
    GLOBAL("Global", "com.gof.global"),
    CHINA("China", "com.gof.china");

    private final String displayName;
    private final String packageName;

    GameVersion(String displayName, String packageName) {
        this.displayName = displayName;
        this.packageName = packageName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPackageName() {
        return packageName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

