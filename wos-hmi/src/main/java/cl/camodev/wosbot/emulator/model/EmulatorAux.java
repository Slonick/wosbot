package cl.camodev.wosbot.emulator.model;

import cl.camodev.wosbot.emulator.EmulatorType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class EmulatorAux

{
	private final EmulatorType emulatorType;
	private final SimpleStringProperty name;
	private final SimpleStringProperty path;
	private final SimpleBooleanProperty active;

	public EmulatorAux(EmulatorType emulatorType, String path) {
		this.emulatorType = emulatorType;
		this.name = new SimpleStringProperty(emulatorType.getDisplayName());
		this.path = new SimpleStringProperty(path);
		this.active = new SimpleBooleanProperty(false);
	}

	public EmulatorType getEmulatorType() {
		return emulatorType;
	}

	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
	}

	public String getPath() {
		return path.get();
	}

	public void setPath(String path) {
		this.path.set(path);
	}

	public StringProperty pathProperty() {
		return path;
	}

	public boolean isActive() {
		return active.get();
	}

	public void setActive(boolean active) {
		this.active.set(active);
	}

	public BooleanProperty activeProperty() {
		return active;
	}
}