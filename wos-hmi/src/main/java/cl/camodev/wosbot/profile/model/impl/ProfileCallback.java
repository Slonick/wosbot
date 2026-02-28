package cl.camodev.wosbot.profile.model.impl;

import java.util.List;

import cl.camodev.wosbot.ot.DTOProfiles;

@FunctionalInterface
public interface ProfileCallback {
	void onProfilesLoaded(List<DTOProfiles> profiles);
}