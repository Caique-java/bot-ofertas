package com.ofertas.bot.service;

import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import com.ofertas.bot.repository.QueueRepository;
import com.ofertas.bot.util.AffiliateLinkConverter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Clock;

@Service
public class PromocaoService {
    private final QueueRepository queue; private final OfferEvaluator evaluator;
    private final BotProperties p; private final Clock clock; private final AffiliateLinkConverter links;
    private final MeterRegistry metrics;
    public PromocaoService(QueueRepository queue, OfferEvaluator evaluator, BotProperties p, Clock clock,
                           AffiliateLinkConverter links, MeterRegistry metrics) {
        this.queue=queue;this.evaluator=evaluator;this.p=p;this.clock=clock;this.links=links;this.metrics=metrics;
    }
    public BigDecimal target(OfertaDTO o) {
        var source=o.marketplace()==Marketplace.AMAZON?p.amazon():p.mercadoLivre();
        return source.products().stream().filter(w->w.id().equals(o.productId())
                && (w.variant().isBlank() || w.variant().equals(o.variant()))
                && (w.seller().isBlank() || w.seller().equals(o.seller())))
                .map(BotProperties.Watch::targetPrice).filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
    }
    public OfferEvaluator.Decision evaluate(OfertaDTO o) {
        links.validar(o.urlOriginal(),o.marketplace(),p.amazon().partnerTag());
        return evaluator.evaluate(o,target(o),queue.observedReference(o),clock.instant());
    }
    public String processarOferta(OfertaDTO o) {
        var decision=evaluate(o);
        String outcome=decision.approved()?queue.enqueue(o,decision):decision.reason();
        queue.observe(o);
        metrics.counter("bot.offers","result",outcome,"source",o.marketplace().name()).increment();
        return outcome;
    }
}
