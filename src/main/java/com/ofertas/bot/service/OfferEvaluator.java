package com.ofertas.bot.service;

import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.OfertaDTO;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Component
public class OfferEvaluator {
    private final BotProperties p;
    public OfferEvaluator(BotProperties p) { this.p = p; }
    public record Decision(boolean approved, String reason, BigDecimal reference, BigDecimal discount) {}
    public static BigDecimal discount(BigDecimal reference, BigDecimal price) {
        if (reference == null || reference.signum() <= 0 || price.compareTo(reference) >= 0) return BigDecimal.ZERO;
        return reference.subtract(price).multiply(BigDecimal.valueOf(100)).divide(reference, 2, RoundingMode.DOWN);
    }
    public Decision evaluate(OfertaDTO o, BigDecimal target, BigDecimal observedReference, Instant now) {
        var f = p.filters();
        if (!o.available()) return reject("SEM_ESTOQUE");
        if (!o.expiresAt().isAfter(now) || o.observedAt().isAfter(now.plusSeconds(5))
                || o.observedAt().isBefore(now.minus(p.maxAge()))) return reject("DADOS_VENCIDOS");
        if (!"BRL".equals(o.currency())) return reject("MOEDA_NAO_SUPORTADA");
        if (o.precoAtual().compareTo(f.minPrice()) < 0 || o.precoAtual().compareTo(f.maxPrice()) > 0)
            return reject("FAIXA_PRECO");
        if (f.blockedSellers().contains(o.seller()) || (!f.allowedSellers().isEmpty()
                && !f.allowedSellers().contains(o.seller()))) return reject("VENDEDOR");
        if (f.excludedCategories().contains(o.category()) || f.excludedTerms().stream()
                .anyMatch(t -> o.titulo().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT)))) return reject("EXCLUSAO");
        var t = o.terms();
        if (!"new".equalsIgnoreCase(t.condition())) return reject("CONDICAO_NAO_SUPORTADA");
        if (t.primeOnly() && !f.allowPrime()) return reject("EXCLUSIVO_PRIME");
        if (!t.coupon().isBlank() && (!t.couponVerified() || t.couponExpiresAt() == null
                || !t.couponExpiresAt().isAfter(now) || t.requirements().isBlank())) return reject("CUPOM_NAO_VERIFICADO");
        if (t.installments() != null && (t.installments() < 1 || t.installmentTotal() == null
                || t.installmentTotal().compareTo(o.precoAtual()) < 0)) return reject("PARCELAMENTO_INVALIDO");
        if (target != null && o.precoAtual().compareTo(target) <= 0)
            return new Decision(true, "PRECO_ALVO", null, BigDecimal.ZERO);
        var percent = discount(observedReference, o.precoAtual());
        if (percent.compareTo(f.minDiscountPercent()) >= 0)
            return new Decision(true, "QUEDA_OBSERVADA", observedReference, percent);
        percent = discount(o.storeReference(), o.precoAtual());
        if (f.allowStoreReference() && percent.compareTo(f.minDiscountPercent()) >= 0)
            return new Decision(true, "REFERENCIA_DA_LOJA", o.storeReference(), percent);
        return reject("SEM_CRITERIO_DE_OFERTA");
    }
    private Decision reject(String reason) { return new Decision(false, reason, null, BigDecimal.ZERO); }
}
