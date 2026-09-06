package com.ofertas.bot;
import com.ofertas.bot.client.*;
import com.ofertas.bot.model.*;
import com.ofertas.bot.repository.QueueRepository;
import com.ofertas.bot.scheduler.PromocaoScheduler;
import com.ofertas.bot.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PipelineTest {
    @Test void failingAmazonDoesNotStopMercadoLivre() {
        var amazon=mock(MarketplaceClient.class);var ml=mock(MarketplaceClient.class);
        when(amazon.marketplace()).thenReturn(Marketplace.AMAZON);when(ml.marketplace()).thenReturn(Marketplace.MERCADO_LIVRE);
        when(amazon.settings()).thenReturn(TestSupport.properties(true,false).amazon());
        when(ml.settings()).thenReturn(TestSupport.properties(true,false).mercadoLivre());
        doThrow(new SourceFailure("HTTP_503",Duration.ofSeconds(30))).when(amazon).collect(any());
        var queue=mock(QueueRepository.class);when(queue.startPoll(anyString(),any())).thenReturn(true);
        var scheduler=new PromocaoScheduler(List.of(amazon,ml),mock(PromocaoService.class),queue,mock(PublicationWorker.class));
        scheduler.collect(Marketplace.AMAZON);scheduler.collect(Marketplace.MERCADO_LIVRE);
        verify(queue).sourceFailure(eq("AMAZON"),eq("HTTP_503"),any());
        verify(queue).sourceSuccess(eq("MERCADO_LIVRE"),eq(0));
    }
    @Test void workerPersistsSuccessOnlyAfterTelegramReturns() {
        var queue=mock(QueueRepository.class);var source=mock(MarketplaceClient.class);
        var service=mock(PromocaoService.class);var telegram=mock(TelegramNotificationService.class);
        var offer=TestSupport.offer();var job=new QueueRepository.Job("job","claim",offer,1,"PRECO_ALVO",null,BigDecimal.ZERO);
        when(queue.claim()).thenReturn(Optional.of(job));when(source.marketplace()).thenReturn(offer.marketplace());
        when(source.settings()).thenReturn(TestSupport.properties(false,false).mercadoLivre());
        when(source.refresh(offer)).thenReturn(Optional.of(offer));
        var decision=new OfferEvaluator.Decision(true,"PRECO_ALVO",null,BigDecimal.ZERO);
        when(service.evaluate(offer)).thenReturn(decision);when(queue.beginSend(job,offer,decision)).thenReturn(true);
        when(telegram.send(eq(offer),anyString())).thenReturn(45L);
        var worker=new PublicationWorker(queue,List.of(source),TestSupport.properties(false,false),service,new MessageFormatter(),telegram,new SimpleMeterRegistry(),Clock.fixed(TestSupport.NOW,ZoneOffset.UTC));
        worker.runOnce();
        var order=inOrder(queue,telegram);
        order.verify(queue).claim();order.verify(telegram).validateChannel();order.verify(queue).beginSend(job,offer,decision);
        order.verify(telegram).send(eq(offer),anyString());order.verify(queue).complete(job,"SENT",45L);
    }
}
