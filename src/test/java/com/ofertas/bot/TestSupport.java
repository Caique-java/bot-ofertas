package com.ofertas.bot;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
final class TestSupport {
    static final Instant NOW=Instant.parse("2026-09-03T20:00:00Z");
    static BotProperties properties(boolean dry, boolean store) {
        var source=new BotProperties.Source(true,Duration.ofMinutes(5),Duration.ofSeconds(2),"unit-access",
                "unit-client","unit-secret","unit-20",true,false,List.of(),List.of(),"");
        return new BotProperties(dry,false,Duration.ofMinutes(15),Duration.ofHours(12),new BigDecimal("5"),500,5,
                Duration.ofSeconds(30),20,new BotProperties.Telegram("unit-token","-1001234567",false),
                new BotProperties.Filters(new BigDecimal("0.01"),new BigDecimal("1000000"),new BigDecimal("10"),store,false,
                        Set.of(),Set.of(),Set.of(),List.of()),source,source);
    }
    static OfertaDTO offer() { return offer("MLB123",new BigDecimal("80.00"),OfertaDTO.Terms.regular(),true,NOW); }
    static OfertaDTO offer(String id,BigDecimal price,OfertaDTO.Terms terms,boolean available,Instant time) {
        return new OfertaDTO(Marketplace.MERCADO_LIVRE,id,"","42","MLB1","Produto <novo> & original",price,
                new BigDecimal("100.00"),"BRL",available,"https://produto.mercadolivre.com.br/"+id,
                "https://http2.mlstatic.com/image.jpg",terms,time,time.plusSeconds(900));
    }
    static OfferCopy copy(OfertaDTO o) { return new OfferCopy(o); }
    record OfferCopy(OfertaDTO o) {
        OfertaDTO url(String url) { return new OfertaDTO(o.marketplace(),o.productId(),o.variant(),o.seller(),o.category(),o.titulo(),o.precoAtual(),o.storeReference(),o.currency(),o.available(),url,o.imageUrl(),o.terms(),o.observedAt(),o.expiresAt()); }
    }
}
