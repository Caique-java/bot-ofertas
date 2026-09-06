package com.ofertas.bot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofertas.bot.client.JsonHttp;
import com.ofertas.bot.service.TelegramNotificationService;
import com.ofertas.bot.util.AffiliateLinkConverter;
import org.junit.jupiter.api.*;
import java.net.http.HttpHeaders;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class TelegramTest {
    final JsonHttp http=mock(JsonHttp.class);
    final TelegramNotificationService service=new TelegramNotificationService(TestSupport.properties(false,false),http,new AffiliateLinkConverter(),Clock.fixed(TestSupport.NOW,ZoneOffset.UTC));
    JsonHttp.Response response(int code,String json) throws Exception { return new JsonHttp.Response(code,new ObjectMapper().readTree(json),HttpHeaders.of(Map.of(),(a,b)->true)); }
    @Test void persistsOnlyPositiveMessageIdentifier() throws Exception {
        when(http.call(anyString(),anyString(),anyMap(),any())).thenReturn(response(200,"{\"ok\":true,\"result\":{\"message_id\":123}}"));
        assertThat(service.send(TestSupport.offer(),"Oferta")).isEqualTo(123);
    }
    @Test void handles429WithoutPhotoFallbackAndRespectsRetryAfter() throws Exception {
        when(http.call(anyString(),anyString(),anyMap(),any())).thenReturn(response(429,"{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":75}}"));
        assertThatThrownBy(()->service.send(TestSupport.offer(),"Oferta")).isInstanceOfSatisfying(TelegramNotificationService.DeliveryFailure.class,e->{assertThat(e.retryAfter).isEqualTo(Duration.ofSeconds(75));assertThat(e.retryable).isTrue();});
        verify(http,times(1)).call(anyString(),anyString(),anyMap(),any());
    }
    @Test void lostResponseIsUncertainAndNeverFallsBack() {
        when(http.call(anyString(),anyString(),anyMap(),any())).thenThrow(new JsonHttp.TransportFailure());
        assertThatThrownBy(()->service.send(TestSupport.offer(),"Oferta")).isInstanceOfSatisfying(TelegramNotificationService.DeliveryFailure.class,e->assertThat(e.uncertain).isTrue());
        verify(http,times(1)).call(anyString(),anyString(),anyMap(),any());
    }
    @Test void fallsBackOnlyAfterDefinitePhotoRejection() throws Exception {
        when(http.call(anyString(),anyString(),anyMap(),any())).thenReturn(
                response(400,"{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: failed to get HTTP URL content\"}"),
                response(200,"{\"ok\":true,\"result\":{\"message_id\":456}}"));
        assertThat(service.send(TestSupport.offer(),"Oferta")).isEqualTo(456);
        verify(http).call(eq("POST"),endsWith("/sendMessage"),anyMap(),any());
    }
    @Test void temporaryAndPermanentErrorsAreDifferent() throws Exception {
        when(http.call(anyString(),anyString(),anyMap(),any())).thenReturn(response(500,"{\"ok\":false,\"error_code\":500}"),response(403,"{\"ok\":false,\"error_code\":403}"));
        assertThatThrownBy(()->service.send(TestSupport.offer(),"x")).isInstanceOfSatisfying(TelegramNotificationService.DeliveryFailure.class,e->assertThat(e.retryable).isTrue());
        assertThatThrownBy(()->service.send(TestSupport.offer(),"x")).isInstanceOfSatisfying(TelegramNotificationService.DeliveryFailure.class,e->assertThat(e.retryable).isFalse());
    }
    @Test void dryRunCannotCallTelegram() {
        var dry=new TelegramNotificationService(TestSupport.properties(true,false),http,new AffiliateLinkConverter(),Clock.systemUTC());
        assertThatThrownBy(()->dry.send(TestSupport.offer(),"x")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(http);
    }
    @Test void validatesChannelPostingPermission() throws Exception {
        when(http.call(anyString(),anyString(),anyMap(),any())).thenReturn(
                response(200,"{\"ok\":true,\"result\":{\"id\":1}}"),
                response(200,"{\"ok\":true,\"result\":{\"type\":\"channel\"}}"),
                response(200,"{\"ok\":true,\"result\":{\"status\":\"administrator\",\"can_post_messages\":false}}"));
        assertThatThrownBy(service::validateChannel).hasMessage("PERMISSAO_CANAL");
    }
}
