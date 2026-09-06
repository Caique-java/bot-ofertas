package com.ofertas.bot.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** One exact variant, seller and commercial condition, in BRL. */
public record OfertaDTO(Marketplace marketplace, String productId, String variant, String seller,
                        String category, String titulo, BigDecimal precoAtual, BigDecimal storeReference,
                        String currency, boolean available, String urlOriginal, String imageUrl,
                        Terms terms, Instant observedAt, Instant expiresAt) {
    public record Terms(String condition, String payment, boolean primeOnly, String coupon,
                        boolean couponVerified, Instant couponExpiresAt, String requirements,
                        Integer installments, BigDecimal installmentTotal) {
        public static Terms regular() {
            return new Terms("new", "Não informado pela fonte; confira na loja", false, "", false,
                    null, "", null, null);
        }
        public String identity() {
            var values=List.of(condition,payment,Boolean.toString(primeOnly),coupon,requirements,
                    Objects.toString(installments,""),installmentTotal==null?"":installmentTotal.stripTrailingZeros().toPlainString());
            StringBuilder result=new StringBuilder();
            values.forEach(v->result.append(v.length()).append(':').append(v));
            return result.toString();
        }
    }
    public OfertaDTO {
        Objects.requireNonNull(marketplace); Objects.requireNonNull(terms);
        Objects.requireNonNull(observedAt); Objects.requireNonNull(expiresAt);
        for (var v : List.of(productId, seller, titulo, currency, urlOriginal))
            if (v.isBlank()) throw new IllegalArgumentException("Oferta sem identificação ou conteúdo obrigatório");
        Objects.requireNonNull(variant); Objects.requireNonNull(category);
        if (precoAtual == null || precoAtual.signum() <= 0 || precoAtual.scale() > 2
                || precoAtual.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new IllegalArgumentException("Preço inválido");
        if (titulo.length() > 1000 || urlOriginal.length() > 2048 || seller.length() > 200)
            throw new IllegalArgumentException("Conteúdo excessivo");
    }
    public String key() {
        var parts = List.of(marketplace.name(), productId, variant, seller, currency, terms.identity());
        StringBuilder key = new StringBuilder();
        parts.forEach(v -> key.append(v.length()).append(':').append(v));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
