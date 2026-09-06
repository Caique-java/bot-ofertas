package com.ofertas.bot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofertas.bot.client.*;
import com.ofertas.bot.repository.QueueRepository;
import org.junit.jupiter.api.Test;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class SourceHttpTest {
    @Test void quotaAndTimeoutFailuresAreReportedWithoutCredentials() throws Exception {
        var wire=mock(JsonHttp.class);var queue=mock(QueueRepository.class);
        var client=new SourceHttp(wire,queue);
        when(queue.reserveSourceRequest(anyString(),any())).thenReturn(true);
        when(wire.call(anyString(),anyString(),anyMap(),any())).thenReturn(new JsonHttp.Response(429,
                new ObjectMapper().readTree("{}"),HttpHeaders.of(Map.of("Retry-After",List.of("90")),(a,b)->true)));
        assertThatThrownBy(()->client.call("AMAZON",Duration.ofSeconds(2),"GET","https://example.test",Map.of(),null))
                .isInstanceOfSatisfying(SourceFailure.class,e->assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(90)));
        when(wire.call(anyString(),anyString(),anyMap(),any())).thenThrow(new JsonHttp.TransportFailure());
        assertThatThrownBy(()->client.call("AMAZON",Duration.ofSeconds(2),"GET","https://example.test",Map.of(),null))
                .hasMessage("FALHA_REDE");
    }
    @Test void localQuotaPreventsNetworkRequest() {
        var wire=mock(JsonHttp.class);var queue=mock(QueueRepository.class);
        var client=new SourceHttp(wire,queue);
        when(queue.reserveSourceRequest(anyString(),any())).thenReturn(false);
        assertThatThrownBy(()->client.call("AMAZON",Duration.ofSeconds(2),"GET","https://example.test",Map.of(),null)).hasMessage("QUOTA_LOCAL");
        verifyNoInteractions(wire);
    }
}
