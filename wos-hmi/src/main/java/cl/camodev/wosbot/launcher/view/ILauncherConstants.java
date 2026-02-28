package cl.camodev.wosbot.launcher.view;

public interface ILauncherConstants {

	String GLOBAL_CSS = "/styles/style.css";

	static String getCssPath() {
		return ILauncherConstants.class.getResource(GLOBAL_CSS).toExternalForm();
	}

}
