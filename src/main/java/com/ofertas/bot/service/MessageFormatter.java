package com.ofertas.bot.service;

import com.ofertas.bot.model.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class MessageFormatter {
    public static String escape(String text) {
        if (text==null) return "";
        return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String clip(String s, int max) {
        return s.codePointCount(0,s.length())>max ? s.substring(0,s.offsetByCodePoints(0,max))+"…" : s;
    }
    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value.setScale(2,RoundingMode.HALF_UP));
    }
    public String format(OfertaDTO o, OfferEvaluator.Decision d) {
        var msg=new StringBuilder("🔥 <b>").append(escape(clip(o.titulo(),180))).append("</b>\n\n");
        if (d.reference()!=null) msg.append("💰 Referência ").append(d.reason().equals("QUEDA_OBSERVADA")?"observada pelo bot":"informada pela loja")
                .append(": ").append(money(d.reference())).append('\n');
        msg.append("✅ Por: <b>").append(money(o.precoAtual())).append("</b>\n");
        if (d.discount().signum()>0) msg.append("🏷️ ").append(d.discount().toPlainString()).append("% sobre essa referência\n");
        msg.append("💳 ").append(escape(o.terms().payment())).append('\n');
        if (o.terms().installments()!=null) msg.append("Parcelamento: ").append(o.terms().installments()).append("x; total ")
                .append(money(o.terms().installmentTotal())).append('\n');
        if (o.terms().primeOnly()) msg.append("Exclusivo para assinantes Prime.\n");
        if (!o.terms().coupon().isBlank()) msg.append("🎟️ ").append(escape(o.terms().coupon())).append(" — ")
                .append(escape(clip(o.terms().requirements(),180))).append('\n');
        msg.append("🛒 ").append(o.marketplace()==Marketplace.AMAZON?"Amazon":"Mercado Livre").append('\n');
        if (!o.variant().isBlank()) msg.append("Variação: ").append(escape(o.variant())).append('\n');
        msg.append("Vendedor: ").append(escape(o.seller())).append('\n');
        msg.append("Consultado em ").append(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'BRT'")
                .withZone(ZoneId.of("America/Sao_Paulo")).format(o.observedAt())).append('\n');
        msg.append("\nFrete e condições finais: consulte a loja para seu CEP.\nPreço e disponibilidade podem mudar.");
        if (o.marketplace()==Marketplace.AMAZON) {
            msg.append("\n\nComo participante do Programa de Associados da Amazon, sou remunerado pelas compras qualificadas efetuadas.");
            msg.append("\nOs preços e a disponibilidade dos produtos estão corretos na data/horário indicados e poderão sofrer alterações. ")
                    .append("Quaisquer informações de preço e disponibilidade exibidas na Amazon no momento da compra serão aplicáveis à compra desse produto.");
            msg.append("\nO conteúdo exibido é originado da Amazon. É exibido como se encontra e poderá ser modificado ou excluído a qualquer momento.");
        }
        return msg.toString();
    }
}
