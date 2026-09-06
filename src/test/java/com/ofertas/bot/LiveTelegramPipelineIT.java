package com.ofertas.bot;

import com.ofertas.bot.client.MarketplaceClient;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.Marketplace;
import com.ofertas.bot.model.OfertaDTO;
import com.ofertas.bot.repository.QueueRepository;
import com.ofertas.bot.service.MessageFormatter;
import com.ofertas.bot.service.PromocaoService;
import com.ofertas.bot.service.PublicationWorker;
import com.ofertas.bot.service.TelegramNotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validação manual e opt-in do caminho Java -> PostgreSQL -> fila -> Telegram.
 *
 * O nome termina em IT para que o Maven/Surefire não execute este teste durante
 * o build normal. No IntelliJ, ele só é habilitado com a frase de confirmação
 * documentada em docs/VALIDACAO.md.
 */
@SpringBootTest(properties = {
        "bot.scheduling-enabled=false",
        "bot.dry-run=false",
        "bot.filters.allow-store-reference=true",
        "spring.config.import=",
        "logging.file.name=target/live-telegram-pipeline.log"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_LIVE_TELEGRAM_PIPELINE_TEST",
        matches = "CONFIRMO_UM_ENVIO_NO_CANAL_PRIVADO")
class LiveTelegramPipelineIT {
    private static final String PRODUCT_ID = "MLB900000000001";
    private static final String CONFIRMATION = "CONFIRMO_UM_ENVIO_NO_CANAL_PRIVADO";

    @Autowired QueueRepository queue;
    @Autowired JdbcTemplate db;
    @Autowired BotProperties properties;
    @Autowired PromocaoService service;
    @Autowired MessageFormatter formatter;
    @Autowired TelegramNotificationService telegram;
    @Autowired MeterRegistry metrics;
    @Autowired Clock clock;

    @Test
    void sendsOneControlledOfferAndPersistsTelegramConfirmation() {
        // IntelliJ can be configured to deactivate JUnit conditions. Keep a
        // second, in-method gate so that such a configuration still cannot send.
        assertThat(System.getenv("RUN_LIVE_TELEGRAM_PIPELINE_TEST"))
                .as("Confirmação explícita obrigatória para permitir um envio real")
                .isEqualTo(CONFIRMATION);

        long active = db.queryForObject("""
                SELECT count(*) FROM offer_queue
                WHERE channel=? AND dry_run=false
                  AND status IN ('PENDING','CHECKING','SENDING','UNCERTAIN')
                """, Long.class, queue.channel());
        assertThat(active)
                .as("A fila live precisa estar vazia para impedir o envio de outro trabalho")
                .isZero();

        Instant now = clock.instant();
        OfertaDTO offer = new OfertaDTO(
                Marketplace.MERCADO_LIVRE,
                PRODUCT_ID,
                "",
                "VALIDACAO_LOCAL",
                "TESTE",
                "[TESTE CONTROLADO] Pipeline Java, banco, fila e Telegram",
                new BigDecimal("80.00"),
                new BigDecimal("100.00"),
                "BRL",
                true,
                "https://www.mercadolivre.com.br/",
                "",
                OfertaDTO.Terms.regular(),
                now,
                now.plus(properties.maxAge()));

        String outcome = service.processarOferta(offer);
        assertThat(outcome)
                .as("A oferta controlada deve entrar na fila")
                .isIn("ENFILEIRADA", "DUPLICADA");

        if (outcome.equals("ENFILEIRADA")) {
            MarketplaceClient controlledSource = controlledSource(offer);
            new PublicationWorker(queue, List.of(controlledSource), properties, service,
                    formatter, telegram, metrics, clock).runOnce();
        }

        var row = db.queryForMap("""
                SELECT status,message_id FROM offer_queue
                WHERE channel=? AND dry_run=false AND offer_key=?
                ORDER BY created_at DESC LIMIT 1
                """, queue.channel(), offer.key());
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(((Number) row.get("message_id")).longValue()).isPositive();

        assertThat(service.processarOferta(offer))
                .as("A mesma oferta não pode ser enfileirada novamente")
                .isEqualTo("DUPLICADA");
    }

    private MarketplaceClient controlledSource(OfertaDTO offer) {
        var base = properties.mercadoLivre();
        var enabled = new BotProperties.Source(true, base.interval(), base.requestInterval(),
                "validacao-local", "", "", "", true, false,
                List.of(), List.of(), "");
        return new MarketplaceClient() {
            public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }
            public BotProperties.Source settings() { return enabled; }
            public void collect(Consumer<OfertaDTO> consumer) { consumer.accept(offer); }
            public Optional<OfertaDTO> refresh(OfertaDTO candidate) {
                return candidate.key().equals(offer.key()) ? Optional.of(offer) : Optional.empty();
            }
        };
    }
}
