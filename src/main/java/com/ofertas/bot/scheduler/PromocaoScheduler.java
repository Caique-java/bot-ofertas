package com.ofertas.bot.scheduler;

import com.ofertas.bot.client.*;
import com.ofertas.bot.model.Marketplace;
import com.ofertas.bot.repository.QueueRepository;
import com.ofertas.bot.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name="bot.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class PromocaoScheduler {
    private static final Logger log=LoggerFactory.getLogger(PromocaoScheduler.class);
    private final List<MarketplaceClient> sources; private final PromocaoService service;
    private final QueueRepository queue; private final PublicationWorker worker;
    public PromocaoScheduler(List<MarketplaceClient> sources,PromocaoService service,QueueRepository queue,PublicationWorker worker) {
        this.sources=sources;this.service=service;this.queue=queue;this.worker=worker;
    }
    @Scheduled(fixedDelayString="${bot.mercado-livre.interval:5m}",initialDelayString="5s")
    public void mercadoLivre() { collect(Marketplace.MERCADO_LIVRE); }
    @Scheduled(fixedDelayString="${bot.amazon.interval:5m}",initialDelayString="7s")
    public void amazon() { collect(Marketplace.AMAZON); }
    public void collect(Marketplace marketplace) {
        var source=sources.stream().filter(s->s.marketplace()==marketplace).findFirst().orElseThrow();
        if (!source.settings().enabled() || !queue.startPoll(marketplace.name(),source.settings().interval())) return;
        AtomicInteger evaluated=new AtomicInteger();
        try {
            source.collect(o->{ service.processarOferta(o);evaluated.incrementAndGet(); });
            queue.sourceSuccess(marketplace.name(),evaluated.get());
            log.info("Coleta concluída: fonte={} avaliados={}",marketplace,evaluated.get());
        } catch (SourceFailure e) {
            queue.sourceFailure(marketplace.name(),e.getMessage(),e.retryAfter());
            log.warn("Coleta indisponível: fonte={} motivo={}",marketplace,e.getMessage());
        } catch (RuntimeException e) {
            queue.sourceFailure(marketplace.name(),"DADOS_OU_PERSISTENCIA_INVALIDOS",Duration.ofMinutes(1));
            log.error("Coleta interrompida: fonte={} tipo={}",marketplace,e.getClass().getSimpleName());
        }
    }
    @Scheduled(fixedDelayString="5s",initialDelayString="10s")
    public void publish() { worker.runOnce(); }
    @Scheduled(fixedDelayString="10m",initialDelayString="1s")
    public void cleanup() { queue.cleanup(); }
}
