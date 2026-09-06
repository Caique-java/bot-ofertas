package com.ofertas.bot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofertas.bot.client.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConnectorTest {
    final ObjectMapper json=new ObjectMapper();
    final Clock clock=Clock.fixed(TestSupport.NOW,ZoneOffset.UTC);
    @Test void mercadoLivreRequiresAnExactVariantAndUsesItsPrice() throws Exception {
        var client=new MercadoLivreClient(TestSupport.properties(true,false),mock(SourceHttp.class),clock);
        var item=json.readTree("""
                {"id":"MLB123","title":"Camisa","seller_id":42,"category_id":"MLB1","price":10,
                 "currency_id":"BRL","status":"active","condition":"new","permalink":"https://www.mercadolivre.com.br/item",
                 "variations":[{"id":11,"price":25,"available_quantity":3},{"id":12,"price":40,"available_quantity":0}]}
                """);
        assertThat(client.parse(item,"")).isEmpty();
        assertThat(client.parse(item,"11").orElseThrow().precoAtual()).isEqualByComparingTo("25");
        assertThat(client.parse(item,"12").orElseThrow().available()).isFalse();
    }
    @Test void amazonReadsCreatorsOffersV2AndDoesNotAssumePrimeOrSeller() throws Exception {
        var client=new AmazonClient(TestSupport.properties(true,false),mock(SourceHttp.class),clock);
        var item=json.readTree("""
                {"asin":"B012345678","detailPageURL":"https://www.amazon.com.br/dp/B012345678?tag=unit-20",
                 "itemInfo":{"title":{"displayValue":"Produto"}},
                 "offersV2":{"listings":[{"isBuyBoxWinner":true,"merchantInfo":{"id":"SELLER"},
                   "condition":{"value":"New"},"availability":{"type":"IN_STOCK"},
                   "dealDetails":{"accessType":"PRIME_EXCLUSIVE"},
                   "price":{"money":{"amount":50.99,"currency":"BRL"},"savingBasis":{"money":{"amount":70,"currency":"BRL"}}}}]}}
                """);
        var offer=client.parse(item).get(0);
        assertThat(offer.precoAtual()).isEqualByComparingTo("50.99");
        assertThat(offer.terms().primeOnly()).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode)item.path("offersV2").path("listings").get(0).path("merchantInfo")).remove("id");
        assertThat(client.parse(item)).isEmpty();
    }
}
