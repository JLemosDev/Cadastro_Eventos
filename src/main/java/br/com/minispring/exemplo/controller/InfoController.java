package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import br.com.minispring.framework.web.HttpResult;

@RestController
public final class InfoController {
    private final String appName;

    public InfoController(@Value("app.name") String appName) { this.appName = appName; }

    @Route(method = "GET", path = "/")
    public Map<String, String> info() { return Map.of("application", appName, "framework", "Mini Spring", "server", "Jetty"); }

    @Route(method = "GET", path = "/health")
    public Map<String, String> health() { return Map.of("status", "UP"); }

    @Route(method = "GET", path = "/painel")
    public HttpResult painel() throws IOException {
        return asset("eventos.html", "text/html");
    }

    @Route(method = "GET", path = "/assets/{name}")
    public HttpResult resource(@PathVariable("name") String name) throws IOException {
        if (!java.util.Set.of("eventos.css", "eventos.js", "theme.js", "terminal.js", "api.js").contains(name))
            throw new br.com.minispring.framework.web.HttpException(404, "Arquivo não encontrado");
        return asset(name, name.endsWith(".css") ? "text/css" : "text/javascript");
    }

    private HttpResult asset(String name, String contentType) throws IOException {
        try (var input = InfoController.class.getResourceAsStream("/static/" + name)) {
            if (input == null) throw new IOException("Interface não encontrada");
            return new HttpResult(200, new String(input.readAllBytes(), StandardCharsets.UTF_8),
                    Map.of("Content-Type", contentType, "Cache-Control", "no-cache"));
        }
    }
}
