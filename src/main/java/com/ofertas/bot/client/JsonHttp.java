package com.ofertas.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.ByteArrayOutputStream;

/** Never propagates URL, headers or remote response text in exceptions (Telegram URLs contain tokens). */
@Component
public class JsonHttp {
    private final HttpClient client;
    private final ObjectMapper mapper;
    public JsonHttp(HttpClient client, ObjectMapper mapper) { this.client = client; this.mapper = mapper; }
    public record Response(int status, JsonNode body, HttpHeaders headers) {}
    public static class TransportFailure extends RuntimeException {
        public TransportFailure() { super("Resposta HTTP ausente ou inválida"); }
    }
    public Response call(String method, String url, Map<String, String> headers, Object body) {
        CompletableFuture<HttpResponse<String>> future = null;
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
            headers.forEach(builder::header);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            future = client.sendAsync(builder.build(), info -> new LimitedBody(url.startsWith("https://creatorsapi.amazon/") ? 40 * 1024 : 2 * 1024 * 1024));
            var response = future.get(20, TimeUnit.SECONDS);
            var json = mapper.readTree(response.body());
            if (json == null) throw new TransportFailure();
            return new Response(response.statusCode(), json, response.headers());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new TransportFailure();
        } catch (Exception e) { throw new TransportFailure(); }
        finally { if (future != null && !future.isDone()) future.cancel(true); }
    }
    private static class LimitedBody implements HttpResponse.BodySubscriber<String> {
        final CompletableFuture<String> result = new CompletableFuture<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final int limit;
        Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        public CompletionStage<String> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var b : buffers) {
                if (bytes.size() + b.remaining() > limit) {
                    subscription.cancel(); result.completeExceptionally(new TransportFailure()); return;
                }
                byte[] chunk = new byte[b.remaining()]; b.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable t) { result.completeExceptionally(new TransportFailure()); }
        public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
    }
}
