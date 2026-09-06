package com.ofertas.bot;
import com.ofertas.bot.model.*;
import com.ofertas.bot.service.*;
import com.ofertas.bot.util.AffiliateLinkConverter;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class OfferRulesTest {
    final OfferEvaluator evaluator=new OfferEvaluator(TestSupport.properties(true,false));
    @Test void computesDiscountWithExplicitConservativeRounding() {
        assertThat(OfferEvaluator.discount(new BigDecimal("3"),new BigDecimal("2"))).isEqualByComparingTo("33.33");
        assertThat(OfferEvaluator.discount(BigDecimal.ZERO,BigDecimal.ONE)).isZero();
        assertThat(OfferEvaluator.discount(BigDecimal.ONE,BigDecimal.TEN)).isZero();
    }
    @Test void storeReferenceDoesNotProveAnObservedDrop() {
        assertThat(evaluator.evaluate(TestSupport.offer(),null,null,TestSupport.NOW).approved()).isFalse();
        assertThat(evaluator.evaluate(TestSupport.offer(),null,new BigDecimal("100"),TestSupport.NOW).reason()).isEqualTo("QUEDA_OBSERVADA");
    }
    @Test void targetApprovesButStillChecksStockAndFreshness() {
        assertThat(evaluator.evaluate(TestSupport.offer(),new BigDecimal("80"),null,TestSupport.NOW).approved()).isTrue();
        var out=TestSupport.offer("MLB1",new BigDecimal("80"),OfertaDTO.Terms.regular(),false,TestSupport.NOW);
        assertThat(evaluator.evaluate(out,BigDecimal.valueOf(100),null,TestSupport.NOW).reason()).isEqualTo("SEM_ESTOQUE");
        assertThat(evaluator.evaluate(TestSupport.offer(),BigDecimal.valueOf(100),null,TestSupport.NOW.plusSeconds(901)).reason()).isEqualTo("DADOS_VENCIDOS");
    }
    @Test void trackingParametersDoNotChangeIdentityButPaymentDoes() {
        var o=TestSupport.offer();
        assertThat(TestSupport.copy(o).url(o.urlOriginal()+"?utm_source=x").key()).isEqualTo(o.key());
        var pix=new OfertaDTO.Terms("new","Pix",false,"",false,null,"",null,null);
        assertThat(TestSupport.offer("MLB123",o.precoAtual(),pix,true,TestSupport.NOW).key()).isNotEqualTo(o.key());
    }
    @Test void rejectsUnknownAndExpiredCoupons() {
        var t=new OfertaDTO.Terms("new","Pix",false,"CUPOM",false,TestSupport.NOW.plusSeconds(60),"Acima de R$ 50",null,null);
        assertThat(evaluator.evaluate(TestSupport.offer("MLB1",BigDecimal.TEN,t,true,TestSupport.NOW),BigDecimal.valueOf(50),null,TestSupport.NOW).approved()).isFalse();
        t=new OfertaDTO.Terms("new","Pix",false,"CUPOM",true,TestSupport.NOW.minusSeconds(1),"Acima de R$ 50",null,null);
        assertThat(evaluator.evaluate(TestSupport.offer("MLB1",BigDecimal.TEN,t,true,TestSupport.NOW),BigDecimal.valueOf(50),null,TestSupport.NOW).approved()).isFalse();
    }
    @Test void requiresConsistentInstallmentTotal() {
        var t=new OfertaDTO.Terms("new","Cartão",false,"",false,null,"",10,new BigDecimal("90"));
        assertThat(evaluator.evaluate(TestSupport.offer("MLB1",new BigDecimal("100"),t,true,TestSupport.NOW),new BigDecimal("150"),null,TestSupport.NOW).reason()).isEqualTo("PARCELAMENTO_INVALIDO");
    }
    @Test void escapesHtmlAndIdentifiesReference() {
        var formatter=new MessageFormatter();
        var d=new OfferEvaluator.Decision(true,"REFERENCIA_DA_LOJA",new BigDecimal("100"),new BigDecimal("20"));
        var text=formatter.format(TestSupport.offer(),d);
        assertThat(text).contains("&lt;novo&gt; &amp; original","informada pela loja","CEP","03/09/2026").doesNotContain("<novo>");
    }
    @Test void validatesExactHostsAndAmazonTagWithoutInventingAffiliation() {
        var links=new AffiliateLinkConverter();
        assertThatThrownBy(()->links.validar("https://mercadolivre.com.br.evil.test/x",Marketplace.MERCADO_LIVRE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->links.validar("https://www.amazon.com.br/dp/B012345678?tag=wrong",Marketplace.AMAZON,"right")).isInstanceOf(IllegalArgumentException.class);
        assertThat(links.validar("https://www.mercadolivre.com.br/item?x=1",Marketplace.MERCADO_LIVRE,"")).endsWith("?x=1");
        assertThat(links.imagemPermitida("http://localhost/file")).isFalse();
    }
}
