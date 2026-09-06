package com.ofertas.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ofertas.bot.repository.QueueRepository;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Map;

@Component
public class SourceHttp {
    private final JsonHttp http;
    private final QueueRepository repository;
    public SourceHttp(JsonHttp http, QueueRepository repository) { this.http = http; this.repository = repository; }
    public JsonNode call(String source, Duration interval, String method, String url, Map<String,String> headers, Object body) {
        if (!repository.reserveSourceRequest(source, interval))
            throw new SourceFailure("QUOTA_LOCAL", interval);
        final JsonHttp.Response r;
        try { r = http.call(method, url, headers, body); }
        catch (JsonHttp.TransportFailure e) { throw new SourceFailure("FALHA_REDE", Duration.ofSeconds(30)); }
        if (r.status() >= 200 && r.status() < 300) return r.body();
        long retry = r.headers().firstValue("Retry-After").map(v -> {
            try { return Long.parseLong(v); } catch (NumberFormatException e) { return 60L; }
        }).orElse(60L);
        String code = r.status() == 401 || r.status() == 403 ? "ACESSO_NEGADO" : "HTTP_" + r.status();
        throw new SourceFailure(code, Duration.ofSeconds(Math.max(1, retry)));
    }
}
