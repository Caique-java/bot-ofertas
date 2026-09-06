package com.ofertas.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@ConfigurationProperties("bot")
public record BotProperties(
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("true") boolean schedulingEnabled,
        @DefaultValue("15m") Duration maxAge,
        @DefaultValue("12h") Duration repostAfter,
        @DefaultValue("5") BigDecimal repostDropPercent,
        @DefaultValue("500") int queueCapacity,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration minMessageInterval,
        @DefaultValue("20") int maxMessagesPerHour,
        @DefaultValue Telegram telegram,
        @DefaultValue Filters filters,
        @DefaultValue Source mercadoLivre,
        @DefaultValue Source amazon) {
    public record Telegram(@DefaultValue("") String token, @DefaultValue("") String chatId,
                           @DefaultValue("false") boolean paused) {}
    public record Filters(@DefaultValue("0.01") BigDecimal minPrice,
                          @DefaultValue("1000000") BigDecimal maxPrice,
                          @DefaultValue("10") BigDecimal minDiscountPercent,
                          @DefaultValue("false") boolean allowStoreReference,
                          @DefaultValue("false") boolean allowPrime,
                          Set<String> allowedSellers,
                          Set<String> blockedSellers,
                          Set<String> excludedCategories,
                          List<String> excludedTerms) {
        public Filters {
            allowedSellers=allowedSellers==null?Set.of():Set.copyOf(allowedSellers);
            blockedSellers=blockedSellers==null?Set.of():Set.copyOf(blockedSellers);
            excludedCategories=excludedCategories==null?Set.of():Set.copyOf(excludedCategories);
            excludedTerms=excludedTerms==null?List.of():List.copyOf(excludedTerms);
        }
    }
    public record Source(@DefaultValue("false") boolean enabled,
                         @DefaultValue("5m") Duration interval,
                         @DefaultValue("2s") Duration requestInterval,
                         @DefaultValue("") String accessToken,
                         @DefaultValue("") String clientId,
                         @DefaultValue("") String clientSecret,
                         @DefaultValue("") String partnerTag,
                         @DefaultValue("false") boolean publicationAuthorized,
                         @DefaultValue("false") boolean retainHistory,
                         List<Watch> products,
                         List<String> keywords,
                         @DefaultValue("") String browseNodeId) {
        public Source {
            products=products==null?List.of():List.copyOf(products);
            keywords=keywords==null?List.of():List.copyOf(keywords);
        }
    }
    public record Watch(String id, @DefaultValue("") String variant,
                        @DefaultValue("") String seller, BigDecimal targetPrice) {}
}
