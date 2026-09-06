package com.ofertas.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Restricted to explicit listings authorized for the application's access token. */
@Component
public class MercadoLivreClient implements MarketplaceClient {
    private final BotProperties p; private final SourceHttp http; private final Clock clock;
    public MercadoLivreClient(BotProperties p, SourceHttp http, Clock clock) { this.p=p; this.http=http; this.clock=clock; }
    public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }
    public BotProperties.Source settings() { return p.mercadoLivre(); }
    public void collect(Consumer<OfertaDTO> consumer) {
        requireAccess();
        for (var watch : settings().products()) {
            fetch(watch.id(), watch.variant()).ifPresent(consumer);
            pause();
        }
    }
    private void pause() {
        try { Thread.sleep(settings().requestInterval().toMillis()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SourceFailure("INTERROMPIDO", Duration.ofSeconds(30)); }
    }
    public Optional<OfertaDTO> refresh(OfertaDTO candidate) {
        requireAccess(); return fetch(candidate.productId(), candidate.variant());
    }
    private void requireAccess() {
        if (!settings().enabled() || settings().accessToken().isBlank())
            throw new SourceFailure("TOKEN_MERCADO_LIVRE_AUSENTE", Duration.ofMinutes(5));
    }
    private Optional<OfertaDTO> fetch(String id, String variant) {
        if (!id.matches("MLB[0-9]+")) throw new SourceFailure("ID_MERCADO_LIVRE_INVALIDO", Duration.ofMinutes(5));
        var root = http.call("MERCADO_LIVRE", settings().requestInterval(), "GET",
                "https://api.mercadolibre.com/items/" + id,
                Map.of("Authorization", "Bearer " + settings().accessToken()), null);
        if (!root.path("id").asText().equals(id))
            throw new SourceFailure("RESPOSTA_MERCADO_LIVRE_INVALIDA",Duration.ofMinutes(5));
        return parse(root, variant);
    }
    public Optional<OfertaDTO> parse(JsonNode root, String variant) {
        JsonNode selected = root;
        if (root.path("variations").isArray() && !root.path("variations").isEmpty()) {
            // Never apply a generic or lowest price to an unspecified color/size.
            selected = null;
            for (var v : root.path("variations")) if (v.path("id").asText().equals(variant)) selected = v;
            if (selected == null) return Optional.empty();
        } else if (!variant.isBlank()) return Optional.empty();
        if (!selected.path("price").isNumber() || root.path("seller_id").asText().isBlank()) return Optional.empty();
        var price = selected.path("price").decimalValue();
        if (price.signum() <= 0) return Optional.empty();
        boolean available = root.path("status").asText().equals("active") && selected.path("available_quantity").asInt() > 0;
        var now = clock.instant();
        var regular = OfertaDTO.Terms.regular();
        var terms = new OfertaDTO.Terms(root.path("condition").asText(), regular.payment(), false, "", false, null, "", null, null);
        // Reference belongs to the exact selected variant only.
        BigDecimal reference = selected.path("original_price").isNumber() ? selected.path("original_price").decimalValue() : null;
        return Optional.of(new OfertaDTO(marketplace(), root.path("id").asText(), variant,
                root.path("seller_id").asText(), root.path("category_id").asText(), root.path("title").asText(),
                price, reference, root.path("currency_id").asText(), available, root.path("permalink").asText(),
                root.path("pictures").path(0).path("secure_url").asText(""), terms, now, now.plus(p.maxAge())));
    }
}
