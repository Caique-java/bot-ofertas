package com.ofertas.bot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class AppConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Bean String validatedConfiguration(BotProperties p) {
        if (p.maxAge().isNegative() || p.maxAge().isZero() || p.maxAge().compareTo(Duration.ofHours(1)) > 0
                || p.queueCapacity() < 1 || p.maxAttempts() < 1 || p.maxAttempts() > 10
                || p.maxMessagesPerHour() < 1 || p.minMessageInterval().compareTo(Duration.ofSeconds(1)) < 0
                || p.repostAfter().isNegative() || p.repostDropPercent().signum() <= 0
                || p.repostDropPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0
                || p.filters().minPrice().signum() <= 0
                || p.filters().maxPrice().compareTo(p.filters().minPrice()) < 0
                || p.filters().minDiscountPercent().signum() <= 0
                || p.filters().minDiscountPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Limites de bot inválidos; consulte application.yml");
        if (!p.dryRun() && (!p.telegram().chatId().matches("-100[0-9]+") || p.telegram().token().isBlank()))
            throw new IllegalArgumentException("Envio exige TELEGRAM_TOKEN e TELEGRAM_CHAT_ID numérico de canal");
        for (var s : java.util.List.of(p.amazon(), p.mercadoLivre())) {
            if (s.interval().compareTo(Duration.ofSeconds(30)) < 0
                    || s.requestInterval().compareTo(Duration.ofSeconds(1)) < 0
                    || s.requestInterval().compareTo(Duration.ofSeconds(10)) > 0
                    || s.products().size() > 200 || s.keywords().size() > 20)
                throw new IllegalArgumentException("Intervalos ou quantidade de produtos inválidos");
            for (var w : s.products()) if (w.id() == null || w.id().isBlank()
                    || (w.targetPrice() != null && w.targetPrice().signum() <= 0))
                throw new IllegalArgumentException("Produto monitorado inválido");
            if (s.enabled() && !p.dryRun() && !s.publicationAuthorized())
                throw new IllegalArgumentException("Confirme a autorização da fonte para publicar no canal configurado");
        }
        if (!p.mercadoLivre().keywords().isEmpty() || !p.mercadoLivre().browseNodeId().isBlank())
            throw new IllegalArgumentException("Busca global Mercado Livre não homologada; configure produtos por ID");
        for (var w:p.amazon().products()) if (!w.id().matches("[A-Z0-9]{10}"))
            throw new IllegalArgumentException("ASIN Amazon inválido");
        for (var w:p.mercadoLivre().products()) if (!w.id().matches("MLB[0-9]+"))
            throw new IllegalArgumentException("ID Mercado Livre inválido");
        if (p.amazon().retainHistory())
            throw new IllegalArgumentException("Histórico Amazon desabilitado; cache tem retenção limitada");
        return "validated";
    }
}
