package com.ofertas.bot.service;

import com.ofertas.bot.client.*;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.repository.QueueRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.List;

@Service
public class PublicationWorker {
    private static final Logger log=LoggerFactory.getLogger(PublicationWorker.class);
    private final QueueRepository queue; private final List<MarketplaceClient> sources; private final BotProperties p;
    private final PromocaoService service; private final MessageFormatter formatter;
    private final TelegramNotificationService telegram; private final MeterRegistry metrics; private final Clock clock;
    public PublicationWorker(QueueRepository queue,List<MarketplaceClient> sources,BotProperties p,PromocaoService service,
                             MessageFormatter formatter,TelegramNotificationService telegram,MeterRegistry metrics,Clock clock) {
        this.queue=queue;this.sources=sources;this.p=p;this.service=service;this.formatter=formatter;this.telegram=telegram;this.metrics=metrics;this.clock=clock;
    }
    public void runOnce() {
        if (p.telegram().paused()) return;
        queue.claim().ifPresent(this::publish);
    }
    private void publish(QueueRepository.Job job) {
        boolean sending=false;
        try {
            var source=sources.stream().filter(s->s.marketplace()==job.offer().marketplace() && s.settings().enabled()).findFirst();
            if (source.isEmpty()) { queue.complete(job,"EXPIRED",null); return; }
            var fresh=source.get().refresh(job.offer());
            if (fresh.isEmpty() || !fresh.get().key().equals(job.offer().key())
                    || fresh.get().precoAtual().compareTo(job.offer().precoAtual())>0
                    || !job.offer().expiresAt().isAfter(clock.instant())) {
                queue.complete(job,"EXPIRED",null);return;
            }
            var decision=service.evaluate(fresh.get());
            // Preserve the earlier observed comparison if the collection just recorded the new price.
            if (!decision.approved() && "QUEDA_OBSERVADA".equals(job.reason())) {
                decision=new OfferEvaluator(p).evaluate(fresh.get(),service.target(fresh.get()),job.reference(),clock.instant());
            }
            if (!decision.approved()) { queue.complete(job,"EXPIRED",null);return; }
            String message=formatter.format(fresh.get(),decision);
            if (message.length()>4096) { queue.fail(job,"MENSAGEM_LONGA",false,false,Duration.ZERO);return; }
            if (p.dryRun()) {
                queue.preview(job,fresh.get(),decision);
                log.info("Prévia preparada: job={} fonte={}",job.id(),fresh.get().marketplace());return;
            }
            if (!source.get().settings().publicationAuthorized()) {
                queue.fail(job,"FONTE_NAO_AUTORIZADA",false,false,Duration.ZERO);return;
            }
            telegram.validateChannel();
            if (!queue.beginSend(job,fresh.get(),decision)) return;
            sending=true;
            long id=telegram.send(fresh.get(),message);
            queue.complete(job,"SENT",id);
            metrics.counter("bot.publications","result","sent").increment();
            log.info("Publicação confirmada: job={} message_id={}",job.id(),id);
        } catch (TelegramNotificationService.DeliveryFailure e) {
            queue.fail(job,e.getMessage(),sending && e.uncertain,e.retryable || !sending,e.retryAfter);
            metrics.counter("bot.publications","result",sending && e.uncertain?"uncertain":"failed").increment();
        } catch (SourceFailure e) {
            queue.fail(job,"REVALIDACAO_"+e.getMessage(),false,true,e.retryAfter());
        } catch (RuntimeException e) {
            // If persistence fails after send, leave SENDING for recovery to UNCERTAIN.
            log.error("Falha no job={}, etapa={}, tipo={}",job.id(),sending?"publicacao":"revalidacao",e.getClass().getSimpleName());
            if (!sending) queue.fail(job,"VALIDACAO_FALHOU",false,false,Duration.ZERO);
        }
    }
}
