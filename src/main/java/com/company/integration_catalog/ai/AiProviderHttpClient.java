package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.IntFunction;

final class AiProviderHttpClient {
    private final ObjectMapper json;
    private final HttpClient client;

    AiProviderHttpClient(ObjectMapper json,HttpClient client) {
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(262144).build());
        this.client=client;
    }

    JsonNode exchange(HttpRequest request,IntFunction<PayloadValidationException> statusError) {
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            pending=client.sendAsync(request,ignored -> new LimitedBody(262144));
            HttpResponse<byte[]> response=pending.get(request.timeout().orElseThrow().toMillis(),TimeUnit.MILLISECONDS);
            if (response.statusCode()!=200) throw statusError.apply(response.statusCode());
            JsonNode parsed=json.readTree(response.body());
            if (parsed==null || !parsed.isObject())
                throw error("AI_INVALID_OUTPUT","Model returned an invalid mapping result");
            return parsed;
        } catch (TimeoutException | HttpTimeoutException ex) {
            throw error("AI_TIMEOUT","AI request timed out; manual mapping is available");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw error("AI_INTERRUPTED","AI request interrupted");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof HttpTimeoutException)
                throw error("AI_TIMEOUT","AI request timed out; manual mapping is available");
            throw error("AI_UNAVAILABLE","AI provider is unreachable or returned an oversized response");
        } catch (java.io.IOException ex) {
            throw error("AI_INVALID_OUTPUT","Model returned an invalid mapping result");
        } finally {
            if (pending!=null && !pending.isDone()) pending.cancel(true);
        }
    }

    static PayloadValidationException error(String code,String message) {
        return new PayloadValidationException(503,code,message);
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private final long maximum;
        private Flow.Subscription subscription;
        private long bytes;
        private boolean failed;
        LimitedBody(long maximum) { this.maximum=maximum; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; delegate.onSubscribe(subscription); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer buffer:buffers) bytes+=buffer.remaining();
            if (bytes>maximum) {
                failed=true; subscription.cancel(); delegate.onError(new IllegalStateException("Provider response too large"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
