package moe.seikimo.laudiolin;

import lombok.Data;
import lombok.SneakyThrows;
import moe.seikimo.laudiolin.utils.EncodingUtils;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;

@Data
public final class Config {
    private static Config instance = new Config();

    /**
     * @return The configuration instance.
     */
    public static Config get() {
        return Config.instance;
    }

    /**
     * Loads the configuration from a file.
     */
    @SneakyThrows
    public static void load() {
        var configFile = new File("config.json");

        if (!configFile.exists()) {
            // Save this configuration.
            Config.save();
        } else {
            // Load the configuration.
            Config.instance = EncodingUtils.jsonDecode(
                    new FileReader(configFile), Config.class);

            // Check if the configuration is null.
            if (Config.instance == null) {
                Config.instance = new Config();
            }
        }
    }

    /**
     * Saves the plugin configuration.
     */
    @SneakyThrows
    public static void save() {
        var configFile = new File("config.json");

        // Save the configuration.
        var json = EncodingUtils.jsonEncode(Config.instance);
        Files.write(configFile.toPath(), json.getBytes());
    }

    private int port = 3000;
    private String mongoUri = "mongodb://localhost:27017";
    private String storagePath = "files";

    public Spotify spotify = new Spotify();

    @Data
    public static class Spotify {
        private String clientId;
        private String clientSecret;
    }
}
