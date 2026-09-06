package com.ofertas.bot.service;

import com.ofertas.bot.client.JsonHttp;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import com.ofertas.bot.util.AffiliateLinkConverter;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class TelegramNotificationService {
    private final BotProperties p; private final JsonHttp http; private final AffiliateLinkConverter links;
    private final Clock clock; private Instant permissionsUntil=Instant.EPOCH;
    public TelegramNotificationService(BotProperties p, JsonHttp http, AffiliateLinkConverter links, Clock clock) {
        this.p=p; this.http=http; this.links=links; this.clock=clock;
    }
    public static class DeliveryFailure extends RuntimeException {
        public final boolean uncertain, retryable, photoRejected;
        public final Duration retryAfter;
        public DeliveryFailure(String code, boolean uncertain, boolean retryable, boolean photoRejected, Duration retryAfter) {
            super(code); this.uncertain=uncertain; this.retryable=retryable; this.photoRejected=photoRejected; this.retryAfter=retryAfter;
        }
    }
    public synchronized void validateChannel() {
        if (p.dryRun() || clock.instant().isBefore(permissionsUntil)) return;
        var me=request("getMe",Map.of());
        var chat=request("getChat",Map.of("chat_id",p.telegram().chatId()));
        var member=request("getChatMember",Map.of("chat_id",p.telegram().chatId(),"user_id",me.path("id").asLong()));
        if (!chat.path("type").asText().equals("channel") || !member.path("status").asText().equals("administrator")
                || !member.path("can_post_messages").asBoolean())
            throw new DeliveryFailure("PERMISSAO_CANAL",false,false,false,Duration.ZERO);
        permissionsUntil=clock.instant().plusSeconds(300);
    }
    public long send(OfertaDTO offer, String message) {
        if (p.dryRun()) throw new IllegalStateException("Publicação desativada em dry-run");
        String link=links.validar(offer.urlOriginal(),offer.marketplace(),p.amazon().partnerTag());
        Map<String,Object> payload=new HashMap<>();
        payload.put("chat_id",p.telegram().chatId()); payload.put("parse_mode","HTML");
        payload.put("reply_markup",Map.of("inline_keyboard",List.of(List.of(Map.of("text","🛒 Ver na loja","url",link)))));
        // Amazon image redistribution by Telegram is not assumed to be authorized by a caching license.
        boolean photo=offer.marketplace()!=Marketplace.AMAZON && links.imagemPermitida(offer.imageUrl()) && message.length()<=1024;
        if (photo) {
            payload.put("photo",offer.imageUrl());payload.put("caption",message);
            try { return messageId(request("sendPhoto",payload)); }
            catch (DeliveryFailure e) {
                if (!e.photoRejected) throw e;
                payload.remove("photo");payload.remove("caption");
            }
        }
        payload.put("text",message);payload.put("link_preview_options",Map.of("is_disabled",true));
        return messageId(request("sendMessage",payload));
    }
    private long messageId(com.fasterxml.jackson.databind.JsonNode r) {
        if (!r.path("message_id").canConvertToLong() || r.path("message_id").asLong()<=0)
            throw new DeliveryFailure("RESPOSTA_INCERTA",true,false,false,Duration.ZERO);
        return r.path("message_id").asLong();
    }
    private com.fasterxml.jackson.databind.JsonNode request(String method, Map<String,Object> body) {
        final JsonHttp.Response r;
        try { r=http.call("POST","https://api.telegram.org/bot"+p.telegram().token()+"/"+method,Map.of(),body); }
        catch (JsonHttp.TransportFailure e) { throw new DeliveryFailure("RESPOSTA_INCERTA",true,false,false,Duration.ZERO); }
        if (r.status()==200 && r.body().path("ok").asBoolean()) return r.body().path("result");
        if (!r.body().has("ok")) throw new DeliveryFailure("RESPOSTA_INCERTA",true,false,false,Duration.ZERO);
        int code=r.body().path("error_code").asInt(r.status());
        long retry=r.body().path("parameters").path("retry_after").asLong(30);
        // Only definite image-related rejection is eligible for text fallback.
        String description=r.body().path("description").asText("").toLowerCase(Locale.ROOT);
        boolean photoError=method.equals("sendPhoto") && code==400
                && (description.contains("failed to get http url content") || description.contains("wrong file identifier")
                || description.contains("photo_invalid") || description.contains("image_process_failed")
                || description.contains("wrong type of the web page content"));
        throw new DeliveryFailure("TELEGRAM_"+code,false,code==429 || code>=500,photoError,Duration.ofSeconds(Math.max(1,retry)));
    }
}
