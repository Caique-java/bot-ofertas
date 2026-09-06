package com.ofertas.bot.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import com.ofertas.bot.service.OfferEvaluator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Short SQL transactions; network calls never execute while a database row is locked. */
@Repository
public class QueueRepository {
    private final JdbcTemplate db; private final TransactionTemplate tx;
    private final ObjectMapper json; private final BotProperties p; private final Clock clock;
    public QueueRepository(JdbcTemplate db, TransactionTemplate tx, ObjectMapper json, BotProperties p, Clock clock) {
        this.db=db; this.tx=tx; this.json=json; this.p=p; this.clock=clock;
    }
    public record Job(String id, String token, OfertaDTO offer, int attempts, String reason,
                      BigDecimal reference, BigDecimal priority) {}
    private Timestamp ts(Instant t) { return Timestamp.from(t); }
    private void guard() { db.queryForObject("SELECT id FROM bot_guard WHERE id=1 FOR UPDATE",Integer.class); }
    public String channel() { return p.telegram().chatId().isBlank() ? "preview" : p.telegram().chatId(); }
    public boolean reserveSourceRequest(String source, Duration interval) {
        db.update("INSERT INTO source_state(source) VALUES (?) ON CONFLICT DO NOTHING",source);
        return db.update("UPDATE source_state SET next_request=? WHERE source=? AND next_request<=?",
                ts(clock.instant().plus(interval)),source,ts(clock.instant())) == 1;
    }
    public boolean startPoll(String source, Duration interval) {
        db.update("INSERT INTO source_state(source) VALUES (?) ON CONFLICT DO NOTHING",source);
        return db.update("UPDATE source_state SET next_poll=? WHERE source=? AND next_poll<=?",
                ts(clock.instant().plus(interval)),source,ts(clock.instant())) == 1;
    }
    public void sourceSuccess(String source, int evaluated) {
        db.update("UPDATE source_state SET last_success=?, last_error=NULL, failures=0, evaluated=evaluated+? WHERE source=?",
                ts(clock.instant()),evaluated,source);
    }
    public void sourceFailure(String source, String code, Duration retry) {
        var failures = db.queryForObject("SELECT failures FROM source_state WHERE source=?",Integer.class,source);
        long backoff=Math.min(1800L,30L * (1L << Math.min(failures == null ? 0 : failures,6)));
        var delay = Duration.ofSeconds(backoff + java.util.concurrent.ThreadLocalRandom.current().nextLong(10));
        if (retry.compareTo(delay)>0) delay=retry;
        db.update("UPDATE source_state SET last_error=?, failures=failures+1, next_poll=GREATEST(next_poll,?) WHERE source=?",
                code,ts(clock.instant().plus(delay)),source);
    }
    public BigDecimal observedReference(OfertaDTO o) {
        // Lowest observed price in a comparable 30-day window; excludes current instant.
        if (o.marketplace()!=Marketplace.MERCADO_LIVRE || !p.mercadoLivre().retainHistory()) return null;
        return db.queryForObject("SELECT min(price) FROM price_observation WHERE offer_key=? AND observed_at>=? AND observed_at<?",
                BigDecimal.class,o.key(),ts(clock.instant().minus(Duration.ofDays(30))),ts(o.observedAt()));
    }
    public void observe(OfertaDTO o) {
        if (o.marketplace()==Marketplace.MERCADO_LIVRE && p.mercadoLivre().retainHistory() && o.available())
            db.update("INSERT INTO price_observation(offer_key,price,observed_at) VALUES(?,?,?)",o.key(),o.precoAtual(),ts(o.observedAt()));
    }
    public String enqueue(OfertaDTO o, OfferEvaluator.Decision decision) {
        return tx.execute(status -> {
            guard();
            var existing = db.queryForObject("SELECT count(*) FROM offer_queue WHERE channel=? AND dry_run=? AND offer_key=? AND status IN ('PENDING','CHECKING','SENDING','UNCERTAIN')",
                    Long.class,channel(),p.dryRun(),o.key());
            if (existing != null && existing>0) return "DUPLICADA_OU_INCERTA";
            var history = db.queryForList("SELECT price,completed_at,status FROM offer_queue WHERE channel=? AND dry_run=? AND offer_key=? AND status IN ('SENT','PREVIEW','FAILED') ORDER BY completed_at DESC LIMIT 1",
                    channel(),p.dryRun(),o.key());
            String reason = "PRIMEIRA_PUBLICACAO";
            if (!history.isEmpty()) {
                var row=history.get(0); var last=((Timestamp)row.get("completed_at")).toInstant();
                var price=(BigDecimal)row.get("price");
                if (last.plus(p.repostAfter()).isAfter(clock.instant())) {
                    if ("FAILED".equals(row.get("status")) || price==null
                            || OfferEvaluator.discount(price,o.precoAtual()).compareTo(p.repostDropPercent())<0) return "DUPLICADA";
                    reason="REPUBLICACAO_QUEDA";
                } else reason="REPUBLICACAO_INTERVALO";
            }
            Long size=db.queryForObject("SELECT count(*) FROM offer_queue WHERE status IN ('PENDING','CHECKING','SENDING','UNCERTAIN')",Long.class);
            if (size!=null && size>=p.queueCapacity()) return "FILA_CHEIA";
            db.update("INSERT INTO offer_queue(id,offer_key,marketplace,channel,dry_run,payload,price,reference_price,priority,reason,repost_reason,status,created_at,expires_at,next_attempt) VALUES(?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,?)",
                    UUID.randomUUID().toString(),o.key(),o.marketplace().name(),channel(),p.dryRun(),encode(o),o.precoAtual(),decision.reference(),
                    decision.discount(),decision.reason(),reason,ts(clock.instant()),ts(o.expiresAt()),ts(clock.instant()));
            return "ENFILEIRADA";
        });
    }
    public Optional<Job> claim() {
        return tx.execute(status -> {
            guard(); recover();
            var jobs=db.query("SELECT * FROM offer_queue WHERE channel=? AND dry_run=? AND status='PENDING' AND next_attempt<=? AND expires_at>? ORDER BY priority DESC,created_at LIMIT 1 FOR UPDATE",
                    (r,n)->new Job(r.getString("id"),UUID.randomUUID().toString(),decode(r.getString("payload")),r.getInt("attempts")+1,
                            r.getString("reason"),r.getBigDecimal("reference_price"),r.getBigDecimal("priority")),channel(),p.dryRun(),ts(clock.instant()),ts(clock.instant()));
            if (jobs.isEmpty()) return Optional.empty();
            var j=jobs.get(0);
            db.update("UPDATE offer_queue SET status='CHECKING',attempts=attempts+1,claim_token=?,lease_until=? WHERE id=?",
                    j.token(),ts(clock.instant().plusSeconds(90)),j.id());
            return Optional.of(j);
        });
    }
    private void recover() {
        var now=ts(clock.instant());
        db.update("UPDATE offer_queue SET status='UNCERTAIN',last_error='PROCESSO_INTERROMPIDO',completed_at=? WHERE status='SENDING' AND lease_until<=?",now,now);
        db.update("UPDATE publication_attempt SET result='UNCERTAIN' WHERE result='SENDING' AND started_at<?",ts(clock.instant().minusSeconds(90)));
        db.update("UPDATE offer_queue SET status='PENDING',claim_token=NULL WHERE status='CHECKING' AND lease_until<=?",now);
        db.update("UPDATE offer_queue SET status='EXPIRED',completed_at=? WHERE status='PENDING' AND (expires_at<=? OR attempts>=?)",now,now,p.maxAttempts());
    }
    public boolean beginSend(Job j, OfertaDTO fresh, OfferEvaluator.Decision decision) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            guard();
            db.update("INSERT INTO channel_state(channel) VALUES(?) ON CONFLICT DO NOTHING",channel());
            var next=db.queryForObject("SELECT next_send FROM channel_state WHERE channel=?",Timestamp.class,channel());
            long count=db.queryForObject("SELECT count(*) FROM publication_attempt WHERE channel=? AND started_at>?",Long.class,channel(),ts(clock.instant().minusSeconds(3600)));
            if (next.toInstant().isAfter(clock.instant()) || count>=p.maxMessagesPerHour()) {
                db.update("UPDATE offer_queue SET status='PENDING',attempts=attempts-1,next_attempt=? WHERE id=? AND claim_token=? AND status='CHECKING'",
                        ts(clock.instant().plusSeconds(30)),j.id(),j.token());
                return false;
            }
            int changed=db.update("UPDATE offer_queue SET status='SENDING',payload=?,price=?,reference_price=?,priority=?,lease_until=? WHERE id=? AND claim_token=? AND status='CHECKING' AND lease_until>? AND expires_at>?",
                    encode(fresh),fresh.precoAtual(),decision.reference(),decision.discount(),ts(clock.instant().plusSeconds(90)),j.id(),j.token(),ts(clock.instant()),ts(clock.instant()));
            if (changed!=1) return false;
            db.update("UPDATE channel_state SET next_send=? WHERE channel=?",ts(clock.instant().plus(p.minMessageInterval())),channel());
            db.update("INSERT INTO publication_attempt(id,job_id,channel,started_at,result) VALUES(?,?,?,?,'SENDING')",
                    j.token(),j.id(),channel(),ts(clock.instant()));
            return true;
        }));
    }
    public void preview(Job j, OfertaDTO fresh, OfferEvaluator.Decision decision) {
        tx.executeWithoutResult(status -> {
            db.update("UPDATE offer_queue SET payload=?,price=?,reference_price=?,priority=?,reason=? WHERE id=? AND claim_token=? AND status='CHECKING'",
                    encode(fresh),fresh.precoAtual(),decision.reference(),decision.discount(),decision.reason(),j.id(),j.token());
            complete(j,"PREVIEW",null);
        });
    }
    public void complete(Job j, String state, Long messageId) {
        tx.executeWithoutResult(status -> {
            db.update("UPDATE offer_queue SET status=?,message_id=?,completed_at=?,last_error=NULL WHERE id=? AND claim_token=? AND status IN ('CHECKING','SENDING','UNCERTAIN')",
                    state,messageId,ts(clock.instant()),j.id(),j.token());
            db.update("UPDATE publication_attempt SET result=?,message_id=? WHERE id=?",state,messageId,j.token());
        });
    }
    public void fail(Job j, String code, boolean uncertain, boolean retryable, Duration retryAfter) {
        tx.executeWithoutResult(status -> {
            String state=uncertain?"UNCERTAIN":retryable && j.attempts()<p.maxAttempts()?"PENDING":"FAILED";
            var delay=Duration.ofSeconds(Math.min(900,5L*(1L << Math.min(j.attempts(),8)))
                    +java.util.concurrent.ThreadLocalRandom.current().nextLong(5));
            if (retryAfter.compareTo(delay)>0) delay=retryAfter;
            db.update("UPDATE offer_queue SET status=?,last_error=?,next_attempt=?,completed_at=? WHERE id=? AND claim_token=? AND status IN ('CHECKING','SENDING')",
                    state,code,ts(clock.instant().plus(delay)),ts(clock.instant()),j.id(),j.token());
            db.update("UPDATE publication_attempt SET result=? WHERE id=?",code,j.token());
            // A Telegram 429 is a channel-wide cooldown, not merely a delay for this job.
            if (code.equals("TELEGRAM_429")) db.update("UPDATE channel_state SET next_send=GREATEST(next_send,?) WHERE channel=?",
                    ts(clock.instant().plus(retryAfter)),channel());
        });
    }
    public void cleanup() {
        tx.executeWithoutResult(status -> {
            guard(); recover();
            db.update("DELETE FROM price_observation WHERE observed_at<?",ts(clock.instant().minus(Duration.ofDays(30))));
            // Amazon data is cache only; scrub all content (including numeric prices) before 24h.
            db.update("UPDATE offer_queue SET payload=NULL,price=NULL,reference_price=NULL WHERE marketplace='AMAZON' AND created_at<?",
                    ts(clock.instant().minus(Duration.ofHours(23))));
            db.update("DELETE FROM offer_queue WHERE status NOT IN ('PENDING','CHECKING','SENDING','UNCERTAIN') AND created_at<?",
                    ts(clock.instant().minus(Duration.ofDays(30))));
        });
    }
    public Map<String,Object> status() {
        return Map.of("sources",db.queryForList("SELECT source,last_success,last_error,failures,evaluated FROM source_state"),
                "queue",db.queryForList("SELECT status,count(*) AS total,min(created_at) AS oldest FROM offer_queue WHERE channel=? AND dry_run=? GROUP BY status",channel(),p.dryRun()),
                "dryRun",p.dryRun());
    }
    private String encode(OfertaDTO o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalArgumentException("Oferta não serializável"); }
    }
    private OfertaDTO decode(String value) {
        try { return json.readValue(value,OfertaDTO.class); } catch (Exception e) { throw new IllegalArgumentException("Oferta persistida inválida"); }
    }
}
