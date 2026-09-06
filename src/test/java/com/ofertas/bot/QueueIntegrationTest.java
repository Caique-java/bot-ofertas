package com.ofertas.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import com.ofertas.bot.repository.QueueRepository;
import com.ofertas.bot.service.OfferEvaluator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.sql.Timestamp;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"bot.scheduling-enabled=false","logging.file.name=target/test.log","bot.dry-run=true",
    "bot.amazon.enabled=false","bot.mercado-livre.enabled=false","spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/bot_ofertas_test}",
    "spring.datasource.username=${TEST_DB_USER:bot_test}","spring.datasource.password=${TEST_DB_PASSWORD:}"})
class QueueIntegrationTest {
    @Autowired QueueRepository queue;
    @Autowired JdbcTemplate db;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper mapper;
    @Autowired BotProperties p;
    @Autowired Clock clock;
    final OfferEvaluator.Decision approval=new OfferEvaluator.Decision(true,"PRECO_ALVO",null,BigDecimal.ZERO);
    @BeforeEach void clear() { db.execute("TRUNCATE offer_queue,publication_attempt,price_observation,channel_state,source_state CASCADE"); }
    OfertaDTO offer(String id,String price) { return TestSupport.offer(id,new BigDecimal(price),OfertaDTO.Terms.regular(),true,clock.instant()); }
    @Test void duplicateRemainsSuppressedAfterRepositoryRecreationButRealDropCanRepost() {
        var o=offer("MLB1","100");
        assertThat(queue.enqueue(o,approval)).isEqualTo("ENFILEIRADA");
        var j=queue.claim().orElseThrow();queue.complete(j,"PREVIEW",null);
        var restarted=new QueueRepository(db,tx,mapper,p,clock);
        assertThat(restarted.enqueue(o,approval)).isEqualTo("DUPLICADA");
        assertThat(restarted.enqueue(offer("MLB1","94"),approval)).isEqualTo("ENFILEIRADA");
        assertThat(db.queryForObject("SELECT repost_reason FROM offer_queue WHERE status='PENDING'",String.class)).isEqualTo("REPUBLICACAO_QUEDA");
    }
    @Test void claimsAreExclusiveAcrossConcurrentWorkers() throws Exception {
        queue.enqueue(offer("MLB1","80"),approval);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return queue.claim();});
            var b=pool.submit(()->{gate.await();return queue.claim();});gate.countDown();
            assertThat((a.get().isPresent()?1:0)+(b.get().isPresent()?1:0)).isEqualTo(1);
        }
    }
    @Test void enqueueIsAtomicUnderConcurrency() throws Exception {
        var o=offer("MLB1","80");
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new java.util.ArrayList<Callable<String>>();
            for(int i=0;i<8;i++) tasks.add(()->queue.enqueue(o,approval));
            pool.invokeAll(tasks).forEach(f->{try{f.get();}catch(Exception e){throw new RuntimeException(e);}});
        }
        assertThat(db.queryForObject("SELECT count(*) FROM offer_queue",Long.class)).isEqualTo(1);
    }
    @Test void interruptedSendIsQuarantinedNotRetried() {
        var o=offer("MLB1","80");queue.enqueue(o,approval);var j=queue.claim().orElseThrow();
        assertThat(queue.beginSend(j,o,approval)).isTrue();
        db.update("UPDATE offer_queue SET lease_until=?",Timestamp.from(clock.instant().minusSeconds(1)));
        assertThat(queue.claim()).isEmpty();
        assertThat(db.queryForObject("SELECT status FROM offer_queue",String.class)).isEqualTo("UNCERTAIN");
        assertThat(queue.enqueue(o,approval)).isEqualTo("DUPLICADA_OU_INCERTA");
    }
    @Test void checkingLeaseCanRecoverWithoutFencingViolation() {
        var o=offer("MLB1","80");queue.enqueue(o,approval);var old=queue.claim().orElseThrow();
        db.update("UPDATE offer_queue SET lease_until=?",Timestamp.from(clock.instant().minusSeconds(1)));
        var fresh=queue.claim().orElseThrow();
        assertThat(fresh.token()).isNotEqualTo(old.token());
        assertThat(queue.beginSend(old,o,approval)).isFalse();
        assertThat(queue.beginSend(fresh,o,approval)).isTrue();
    }
    @Test void confirmationStoresMessageAndAttemptResultTogether() {
        var o=offer("MLB1","80");queue.enqueue(o,approval);var j=queue.claim().orElseThrow();queue.beginSend(j,o,approval);
        assertThat(db.queryForObject("SELECT message_id FROM offer_queue",Long.class)).isNull();
        queue.complete(j,"SENT",123L);
        assertThat(db.queryForObject("SELECT message_id FROM offer_queue",Long.class)).isEqualTo(123);
        assertThat(db.queryForObject("SELECT result FROM publication_attempt",String.class)).isEqualTo("SENT");
    }
    @Test void rateLimitDelaysEveryJobInTheChannel() {
        var a=offer("MLB1","80");queue.enqueue(a,approval);var j=queue.claim().orElseThrow();queue.beginSend(j,a,approval);
        queue.fail(j,"TELEGRAM_429",false,true,Duration.ofSeconds(300));
        var b=offer("MLB2","80");queue.enqueue(b,approval);var second=queue.claim().orElseThrow();
        assertThat(queue.beginSend(second,b,approval)).isFalse();
        assertThat(db.queryForObject("SELECT next_send FROM channel_state",Timestamp.class).toInstant()).isAfter(clock.instant().plusSeconds(290));
    }
    @Test void expiresBeforePublicationAndKeepsQueueBounded() {
        queue.enqueue(offer("MLB1","80"),approval);
        db.update("UPDATE offer_queue SET expires_at=?",Timestamp.from(clock.instant().minusSeconds(1)));
        assertThat(queue.claim()).isEmpty();
        assertThat(db.queryForObject("SELECT status FROM offer_queue",String.class)).isEqualTo("EXPIRED");
    }
    @Test void liveModeDoesNotConsumeDryRunJobs() {
        queue.enqueue(offer("MLB1","80"),approval);
        var live=new QueueRepository(db,tx,mapper,TestSupport.properties(false,false),clock);
        assertThat(live.claim()).isEmpty();
    }
    @Test void erasesAmazonCacheBeforeItBecomesLongTermHistory() {
        queue.enqueue(offer("MLB1","80"),approval);
        db.update("UPDATE offer_queue SET marketplace='AMAZON',created_at=?",Timestamp.from(clock.instant().minus(Duration.ofHours(24))));
        queue.cleanup();
        assertThat(db.queryForObject("SELECT payload FROM offer_queue",String.class)).isNull();
        assertThat(db.queryForObject("SELECT price FROM offer_queue",BigDecimal.class)).isNull();
    }
    @Test void capsRetriesAndDoesNotImmediatelyReenqueuePermanentFailure() {
        var o=offer("MLB1","80");queue.enqueue(o,approval);
        for(int i=0;i<p.maxAttempts();i++) {
            var j=queue.claim().orElseThrow();queue.fail(j,"HTTP_503",false,true,Duration.ZERO);
            db.update("UPDATE offer_queue SET next_attempt=?",Timestamp.from(clock.instant().minusSeconds(1)));
        }
        assertThat(queue.claim()).isEmpty();
        assertThat(db.queryForObject("SELECT status FROM offer_queue",String.class)).isEqualTo("FAILED");
        assertThat(db.queryForObject("SELECT message_id FROM offer_queue",Long.class)).isNull();
        assertThat(queue.enqueue(o,approval)).isEqualTo("DUPLICADA");
    }
    @Test void runsCollectionEvaluationQueueAndRevalidatedPreviewWithoutTelegram() throws Exception {
        var wire=org.mockito.Mockito.mock(com.ofertas.bot.client.SourceHttp.class);
        var telegram=org.mockito.Mockito.mock(com.ofertas.bot.service.TelegramNotificationService.class);
        var settings=new BotProperties.Source(true,Duration.ofMinutes(5),Duration.ofSeconds(1),"unit-access","","","",true,false,
                java.util.List.of(new BotProperties.Watch("MLB123","","42",new BigDecimal("90"))),java.util.List.of(),"");
        var base=TestSupport.properties(true,false);
        var config=new BotProperties(true,false,base.maxAge(),base.repostAfter(),base.repostDropPercent(),base.queueCapacity(),base.maxAttempts(),
                base.minMessageInterval(),base.maxMessagesPerHour(),base.telegram(),base.filters(),settings,base.amazon());
        var response=mapper.readTree("""
                {"id":"MLB123","title":"Produto real do contrato de teste","price":80,"currency_id":"BRL",
                 "seller_id":42,"status":"active","available_quantity":1,"condition":"new","category_id":"MLB1",
                 "permalink":"https://www.mercadolivre.com.br/MLB123","variations":[]}
                """);
        org.mockito.Mockito.when(wire.call(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(response);
        var client=new com.ofertas.bot.client.MercadoLivreClient(config,wire,clock);
        var meters=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var service=new com.ofertas.bot.service.PromocaoService(queue,new OfferEvaluator(config),config,clock,
                new com.ofertas.bot.util.AffiliateLinkConverter(),meters);
        client.collect(service::processarOferta);
        assertThat(db.queryForObject("SELECT status FROM offer_queue",String.class)).isEqualTo("PENDING");
        var worker=new com.ofertas.bot.service.PublicationWorker(queue,java.util.List.of(client),config,service,
                new com.ofertas.bot.service.MessageFormatter(),telegram,meters,clock);
        worker.runOnce();
        assertThat(db.queryForObject("SELECT status FROM offer_queue",String.class)).isEqualTo("PREVIEW");
        org.mockito.Mockito.verifyNoInteractions(telegram);
    }
}
