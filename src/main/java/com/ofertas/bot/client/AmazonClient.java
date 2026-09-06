package com.ofertas.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Creators API, LwA credentials v3.1 (BR/NA). No PA-API v5 fallback. */
@Component
public class AmazonClient implements MarketplaceClient {
    private static final List<String> RESOURCES = List.of("itemInfo.title", "images.primary.medium",
            "browseNodeInfo.browseNodes", "offersV2.listings.price", "offersV2.listings.availability",
            "offersV2.listings.condition", "offersV2.listings.merchantInfo", "offersV2.listings.isBuyBoxWinner",
            "offersV2.listings.dealDetails", "offersV2.listings.type");
    private final BotProperties p; private final SourceHttp http; private final Clock clock;
    private String token; private Instant tokenExpires = Instant.EPOCH;
    public AmazonClient(BotProperties p, SourceHttp http, Clock clock) { this.p=p; this.http=http; this.clock=clock; }
    public Marketplace marketplace() { return Marketplace.AMAZON; }
    public BotProperties.Source settings() { return p.amazon(); }
    public void collect(Consumer<OfertaDTO> consumer) {
        requireAccess();
        var ids = settings().products().stream().map(BotProperties.Watch::id).distinct().toList();
        for (int i=0;i<ids.size();i+=10) {
            call("getItems", Map.of("itemIds",ids.subList(i,Math.min(i+10,ids.size())), "itemIdType","ASIN"))
                    .path("itemsResult").path("items").forEach(item -> parse(item).forEach(consumer));
            pause();
        }
        for (var keyword : settings().keywords()) {
            Map<String,Object> query = new HashMap<>(); query.put("keywords",keyword); query.put("itemCount",10);
            if (!settings().browseNodeId().isBlank()) query.put("browseNodeId",settings().browseNodeId());
            call("searchItems",query).path("searchResult").path("items").forEach(item -> parse(item).forEach(consumer));
            pause();
        }
    }
    private void pause() {
        try { Thread.sleep(settings().requestInterval().toMillis()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SourceFailure("INTERROMPIDO", Duration.ofSeconds(30)); }
    }
    public Optional<OfertaDTO> refresh(OfertaDTO c) {
        requireAccess();
        var response = call("getItems",Map.of("itemIds", List.of(c.productId()),"itemIdType","ASIN"));
        for (var item : response.path("itemsResult").path("items"))
            for (var offer : parse(item)) if (offer.key().equals(c.key())) return Optional.of(offer);
        return Optional.empty();
    }
    private void requireAccess() {
        if (!settings().enabled() || settings().clientId().isBlank() || settings().clientSecret().isBlank()
                || settings().partnerTag().isBlank())
            throw new SourceFailure("CREDENCIAIS_CREATORS_API_AUSENTES", Duration.ofMinutes(5));
    }
    private synchronized String accessToken() {
        if (clock.instant().isBefore(tokenExpires)) return token;
        var r=http.call("AMAZON_AUTH",Duration.ofSeconds(1),"POST","https://api.amazon.com/auth/o2/token",Map.of(),
                Map.of("grant_type","client_credentials","client_id",settings().clientId(),
                        "client_secret",settings().clientSecret(),"scope","creatorsapi::default"));
        token=r.path("access_token").asText();
        if (token.isBlank() || r.path("expires_in").asLong() <= 60) throw new SourceFailure("TOKEN_AMAZON_INVALIDO",Duration.ofMinutes(5));
        tokenExpires=clock.instant().plusSeconds(r.path("expires_in").asLong()-60);
        return token;
    }
    private JsonNode call(String operation, Map<String,Object> params) {
        Map<String,Object> body=new HashMap<>(params);
        body.put("marketplace","www.amazon.com.br"); body.put("partnerTag",settings().partnerTag());
        body.put("resources",RESOURCES); body.put("condition","New");
        try {
            var response = http.call("AMAZON",settings().requestInterval(),"POST","https://creatorsapi.amazon/catalog/v1/"+operation,
                    Map.of("Authorization","Bearer "+accessToken(),"x-marketplace","www.amazon.com.br"),body);
            if (response.path("errors").isArray() && !response.path("errors").isEmpty())
                throw new SourceFailure("ERRO_PARCIAL_CREATORS_API",Duration.ofMinutes(1));
            String container=operation.equals("getItems")?"itemsResult":"searchResult";
            if (!response.path(container).path("items").isArray())
                throw new SourceFailure("RESPOSTA_CREATORS_API_INVALIDA",Duration.ofMinutes(1));
            return response;
        } catch (SourceFailure e) {
            if (e.getMessage().equals("ACESSO_NEGADO")) { synchronized(this) { tokenExpires=Instant.EPOCH; } }
            throw e;
        }
    }
    public List<OfertaDTO> parse(JsonNode item) {
        List<OfertaDTO> result=new ArrayList<>();
        String asin=item.path("asin").asText();
        if (!asin.matches("[A-Z0-9]{10}")) return result;
        for (var listing:item.path("offersV2").path("listings")) {
            if (!listing.path("isBuyBoxWinner").asBoolean() || listing.path("violatesMAP").asBoolean()) continue;
            // Subscription, early access and other unsupported commercial contexts are excluded.
            String type=listing.path("type").asText("");
            if (!type.isBlank() && !Set.of("LIGHTNING_DEAL","LIGHTNINGDEAL").contains(type)) continue;
            var price=listing.path("price").path("money");
            if (!price.path("amount").isNumber() || price.path("amount").decimalValue().signum()<=0) continue;
            String seller=listing.path("merchantInfo").path("id").asText("");
            if (seller.isBlank()) continue; // Seller name is not a stable identity.
            String access=listing.path("dealDetails").path("accessType").asText("ALL");
            if (!Set.of("ALL","PRIME_EXCLUSIVE").contains(access)) continue;
            var regular=OfertaDTO.Terms.regular();
            var terms=new OfertaDTO.Terms(listing.path("condition").path("value").asText(),regular.payment(),
                    access.equals("PRIME_EXCLUSIVE"),"",false,null,"",null,null);
            var now=clock.instant(); var expires=now.plus(p.maxAge());
            String end=listing.path("dealDetails").path("endTime").asText("");
            String start=listing.path("dealDetails").path("startTime").asText("");
            try {
                if (!start.isBlank() && Instant.parse(start).isAfter(now)) continue;
                if (!end.isBlank() && Instant.parse(end).isBefore(expires)) expires=Instant.parse(end);
            } catch (java.time.format.DateTimeParseException e) { continue; }
            var ref=listing.path("price").path("savingBasis").path("money");
            var reference=ref.path("currency").asText().equals(price.path("currency").asText()) && ref.path("amount").isNumber()
                    ? ref.path("amount").decimalValue():null;
            String availability=listing.path("availability").path("type").asText().replace("_","");
            result.add(new OfertaDTO(marketplace(),asin,asin,seller,
                    item.path("browseNodeInfo").path("browseNodes").path(0).path("id").asText(""),
                    item.path("itemInfo").path("title").path("displayValue").asText(),price.path("amount").decimalValue(),
                    reference,price.path("currency").asText(),Set.of("INSTOCK","INSTOCKSCARCE").contains(availability),
                    item.path("detailPageURL").asText(),item.path("images").path("primary").path("medium").path("url").asText(""),terms,now,expires));
        }
        return result;
    }
}
