package com.ofertas.bot.client;
import java.time.Duration;
public class SourceFailure extends RuntimeException {
    private final Duration retryAfter;
    public SourceFailure(String code, Duration retryAfter) { super(code); this.retryAfter = retryAfter; }
    public Duration retryAfter() { return retryAfter; }
}
