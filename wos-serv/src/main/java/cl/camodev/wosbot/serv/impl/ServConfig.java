package cl.camodev.wosbot.serv.impl;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.wosbot.almac.entity.Config;
import cl.camodev.wosbot.almac.entity.Profile;
import cl.camodev.wosbot.almac.entity.TpConfig;
import cl.camodev.wosbot.almac.repo.ConfigRepository;
import cl.camodev.wosbot.almac.repo.IConfigRepository;
import cl.camodev.wosbot.almac.repo.IProfileRepository;
import cl.camodev.wosbot.almac.repo.ProfileRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpConfigEnum;
import cl.camodev.wosbot.ot.DTOProfiles;

public class ServConfig {

	private static ServConfig instance;
	private static final Logger logger = LoggerFactory.getLogger(ServConfig.class);

	private IConfigRepository iConfigRepository = ConfigRepository.getRepository();
	private IProfileRepository iProfileRepository = ProfileRepository.getRepository();

	private ServConfig() {

	}

	public static ServConfig getServices() {
		if (instance == null) {
			instance = new ServConfig();
			instance.initializeGlobalConfigs();
		}
		return instance;
	}

	private void initializeGlobalConfigs() {
		List<Config> existingConfigs = iConfigRepository.getGlobalConfigs();
		TpConfig globalTpConfig = iConfigRepository.getTpConfig(TpConfigEnum.GLOBAL_CONFIG);

		if (globalTpConfig == null) {
			logger.warn("GLOBAL_CONFIG TpConfig not found, cannot seed default configurations");
			return;
		}

		for (EnumConfigurationKey key : EnumConfigurationKey.values()) {
			// Some configs like EXPERT_* or CITY_* might be profile-specific,
			// but things like MUMU_PATH_STRING, CURRENT_EMULATOR_STRING, etc., are global.
			// Let's seed them if they are completely missing from the global table.
			boolean exists = existingConfigs != null && existingConfigs.stream()
					.anyMatch(c -> c.getKey().equals(key.name()));

			if (!exists && key.getDefaultValue() != null) {
				Config config = new Config();
				config.setKey(key.name());
				config.setValue(key.getDefaultValue());
				config.setTpConfig(globalTpConfig);
				iConfigRepository.addConfig(config);
				logger.info("Seeded default global configuration: {} = {}", key.name(), key.getDefaultValue());
			}
		}
	}

	public HashMap<String, String> getGlobalConfig() {
		List<Config> configs = iConfigRepository.getGlobalConfigs();

		if (configs == null || configs.isEmpty()) {
			return null;
		}

		HashMap<String, String> globalConfig = new HashMap<>();
		for (Config config : configs) {
			globalConfig.put(config.getKey(), config.getValue());
		}
		return globalConfig;
	}

	/**
	 * Updates a specific profile configuration value both in memory and in the
	 * database
	 * If the configuration doesn't exist, it will be created automatically
	 *
	 * @param profile The profile to update
	 * @param key     The configuration key to update
	 * @param value   The new value to set
	 * @return true if the update was successful, false otherwise
	 */
	public boolean updateProfileConfig(DTOProfiles profile, EnumConfigurationKey key, String value) {
		try {
			// Update the profile configuration in memory
			profile.setConfig(key, value);

			// Persist to database - get all configs for this profile and find the specific
			// one
			List<Config> profileConfigs = iConfigRepository.getProfileConfigs(profile.getId());
			Config config = profileConfigs.stream()
					.filter(c -> c.getKey().equalsIgnoreCase(key.name()))
					.findFirst()
					.orElse(null);

			if (config != null) {
				// Configuration exists, update it
				config.setValue(value);
				boolean saved = iConfigRepository.saveConfig(config);

				if (saved) {
					logger.info("Configuration {} updated to: {}", key.name(), value);
					// Notify UI that profile data has changed
					ServProfiles.getServices().notifyProfileDataChange(profile);
				} else {
					logger.warn("Failed to persist configuration {}", key.name());
				}

				return saved;
			} else {
				// Configuration doesn't exist, create it
				logger.info("Configuration {} not found, creating new entry", key.name());

				// Get the TpConfig for profile configurations
				TpConfig tpConfig = iConfigRepository.getTpConfig(TpConfigEnum.PROFILE_CONFIG);
				if (tpConfig == null) {
					logger.error("Could not find PROFILE_CONFIG type in database");
					return false;
				}

				// Get the Profile entity from the database
				Profile profileEntity = iProfileRepository.getProfileById(profile.getId());
				if (profileEntity == null) {
					logger.error("Could not find profile with ID {} in database", profile.getId());
					return false;
				}

				// Create new configuration
				Config newConfig = new Config(profileEntity, tpConfig, key.name(), value);
				boolean created = iConfigRepository.addConfig(newConfig);

				if (created) {
					logger.info("Configuration {} created with value: {}", key.name(), value);
					// Notify UI that profile data has changed
					ServProfiles.getServices().notifyProfileDataChange(profile);
				} else {
					logger.warn("Failed to create configuration {}", key.name());
				}

				return created;
			}

		} catch (Exception e) {
			logger.error("Error updating configuration {}: {}", key.name(), e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Creates or updates a global (non-profile) configuration value.
	 *
	 * @param key   The configuration key to update
	 * @param value The new value to set
	 * @return true if the operation was successful, false otherwise
	 */
	public boolean updateGlobalConfig(EnumConfigurationKey key, String value) {
		try {
			List<Config> configs = iConfigRepository.getGlobalConfigs();
			Config config = configs != null
					? configs.stream().filter(c -> c.getKey().equals(key.name())).findFirst().orElse(null)
					: null;

			if (config == null) {
				TpConfig tpConfig = iConfigRepository.getTpConfig(TpConfigEnum.GLOBAL_CONFIG);
				if (tpConfig == null) {
					logger.error("Could not find GLOBAL_CONFIG type in database");
					return false;
				}
				config = new Config();
				config.setKey(key.name());
				config.setValue(value);
				config.setTpConfig(tpConfig);
				boolean created = iConfigRepository.addConfig(config);
				if (created) {
					logger.info("Global configuration {} created with value: {}", key.name(), value);
				}
				return created;
			} else {
				config.setValue(value);
				boolean saved = iConfigRepository.saveConfig(config);
				if (saved) {
					logger.info("Global configuration {} updated to: {}", key.name(), value);
				}
				return saved;
			}
		} catch (Exception e) {
			logger.error("Error updating global configuration {}: {}", key.name(), e.getMessage(), e);
			return false;
		}
	}

}
