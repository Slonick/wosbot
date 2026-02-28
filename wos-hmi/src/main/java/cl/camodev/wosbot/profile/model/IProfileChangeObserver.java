package cl.camodev.wosbot.profile.model;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

public interface IProfileChangeObserver {

	public void notifyProfileChange(EnumConfigurationKey key, Object value);

}
