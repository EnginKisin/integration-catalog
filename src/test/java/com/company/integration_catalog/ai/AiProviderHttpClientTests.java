package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiProviderHttpClientTests {
    private final HttpClient client=mock(HttpClient.class);
    private final AiProviderHttpClient http=new AiProviderHttpClient(new ObjectMapper(),client);
    private HttpRequest request(Duration timeout) {
        return HttpRequest.newBuilder(URI.create("https://example.test/api")).timeout(timeout).GET().build();
    }

    @Test void expiredDeadlineCancelsTheOutstandingHttpFuture() {
        CompletableFuture<HttpResponse<byte[]>> pending=new CompletableFuture<>();
        doReturn(pending).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> http.exchange(request(Duration.ofMillis(5)),status -> unexpectedStatus()))
                .isInstanceOfSatisfying(PayloadValidationException.class,ex -> assertThat(ex.getCode()).isEqualTo("AI_TIMEOUT"));
        assertThat(pending.isCancelled()).isTrue();
    }

    @Test @SuppressWarnings("unchecked") void oversizedStreamIsCancelledBeforeItCanBeBuffered() {
        Flow.Subscription subscription=mock(Flow.Subscription.class);
        doAnswer(call -> {
            HttpResponse.BodyHandler<byte[]> handler=call.getArgument(1);
            var subscriber=handler.apply(mock(HttpResponse.ResponseInfo.class));
            subscriber.onSubscribe(subscription);
            subscriber.onNext(List.of(ByteBuffer.wrap(new byte[262145])));
            return subscriber.getBody().toCompletableFuture();
        }).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> http.exchange(request(Duration.ofSeconds(1)),status -> unexpectedStatus()))
                .isInstanceOfSatisfying(PayloadValidationException.class,ex -> assertThat(ex.getCode()).isEqualTo("AI_UNAVAILABLE"));
        verify(subscription).cancel();
    }

    private PayloadValidationException unexpectedStatus() {
        return new PayloadValidationException(503,"UNEXPECTED_STATUS","Unexpected HTTP status");
    }
}
