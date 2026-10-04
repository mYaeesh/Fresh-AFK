package dev.afkmod;

import dev.afkmod.client.AfkController;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.testlab.ConfigOverrides;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class AfkModClient implements ClientModInitializer {
	public static final String MOD_ID = "afkmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static AfkConfig config = new AfkConfig();
	/** The Test Lab's temporary, in-memory overrides (empty outside a test). */
	private static final ConfigOverrides OVERRIDES = new ConfigOverrides();

	/**
	 * The config the mod runs with: the saved config, plus the Test Lab's overrides while a test runs (then a separate
	 * in-memory copy). Never save this object; the GUI edits and saves {@link #savedConfig()}.
	 */
	public static AfkConfig config() {
		return OVERRIDES.effective(config);
	}

	/** The persistent settings (what {@code config/afkmod.json} holds); the settings screen edits and saves this. */
	public static AfkConfig savedConfig() {
		return config;
	}

	public static ConfigOverrides overrides() {
		return OVERRIDES;
	}

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(AfkConfig.FILE_NAME);
	}

	@Override
	public void onInitializeClient() {
		Path file = configFile();
		try {
			config = AfkConfig.load(file);
			LOGGER.info("Loaded config from {}", file);
		} catch (IOException e) {
			LOGGER.error("Could not read or write {}, using defaults", file, e);
			config = new AfkConfig();
		}
		AfkController.init();
	}
}
