package br.com.minispring.framework.web;

import java.util.Map;

/** Controller declara a resposta sem depender da API Servlet. */
public record HttpResult(int status, Object body, Map<String, String> headers) {
    public HttpResult { headers = Map.copyOf(headers); }
    public static HttpResult ok(Object body) { return new HttpResult(200, body, Map.of()); }

    public static HttpResult created(String location, Object body) {
        return new HttpResult(201, body, Map.of("Location", location));
    }

    public static HttpResult noContent() { return new HttpResult(204, null, Map.of()); }
}
