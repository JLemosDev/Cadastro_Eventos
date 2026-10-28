package br.com.minispring.framework.context;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Precedência: -Dpropriedade > VARIAVEL_AMBIENTE > application.properties.
 */
public final class AppConfig {
    private final Properties properties;
    private final Map<String, String> environment;

    public AppConfig(Properties properties, Map<String, String> environment) {

        this.properties = new Properties();
        this.properties.putAll(properties);
        this.environment = Map.copyOf(environment);
    }

    public static AppConfig load() {
        var properties = new Properties();

        try (var input = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler application.properties", e);
        }
        return new AppConfig(properties, System.getenv());
    }

    public String get(String key) {

        String value = System.getProperty(key);
        if (value == null) value = environment.get(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (value == null) value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("Configuração ausente: " + key);
        return value;
    }
}
