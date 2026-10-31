package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.ExecutorService;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.mockito.Mockito.*;

class AiMappingWorkerTests {
    private final AiJobTransactions transactions=mock(AiJobTransactions.class);
    private final AiMappingService mappings=mock(AiMappingService.class);
    private final MappingSuggestionProvider gemini=mock(MappingSuggestionProvider.class);
    private final ExecutorService executor=mock(ExecutorService.class);
    private final AiProperties properties=AiTestProperties.defaults();
    private final Input input=new Input(11L,12L,"revision",List.of(),List.of(),List.of());
    private final Suggestions result=new Suggestions(List.of(),List.of(),List.of(),List.of());
    private AiMappingWorker worker;

    @BeforeEach void setup() {
        properties.setProvider("gemini"); properties.setModel("gemini-3.5-flash-lite");
        doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return null; }).when(executor).execute(any());
        when(transactions.claim()).thenReturn(new AiJobTransactions.Claimed("job","gemini",properties.getModel(),input));
        when(mappings.provider()).thenReturn(gemini); when(gemini.suggest(input)).thenReturn(result);
        worker=new AiMappingWorker(transactions,mappings,properties,executor);
    }

    @Test void configuredProviderUsesTheExistingAsyncCompletionContract() {
        worker.poll();
        verify(gemini).suggest(input); verify(transactions).finish("job",result,null,null);
    }
    @Test void rateLimitFinishesTheJobWithoutRetryOrFallback() {
        when(gemini.suggest(input)).thenThrow(new PayloadValidationException(503,"AI_RATE_LIMITED","Gemini quota reached"));
        worker.poll();
        verify(gemini,times(1)).suggest(input);
        verify(transactions).finish("job",null,"AI_RATE_LIMITED","AI provider quota or rate limit was reached");
        verify(mappings,times(1)).provider();
    }
    @Test void queuedOllamaWorkIsNeverSentToGeminiAfterConfigurationChanges() {
        when(transactions.claim()).thenReturn(new AiJobTransactions.Claimed("job","ollama","qwen3.5:4b",input));
        worker.poll();
        verify(transactions).finish("job",null,"AI_CONFIGURATION_CHANGED","AI provider configuration changed; create a new job");
        verify(mappings,never()).provider(); verifyNoInteractions(gemini);
    }
    @Test void contentBlocksUseTheCatalogMessageWithoutExposingProviderDetails() {
        when(gemini.suggest(input)).thenThrow(new PayloadValidationException(503,
                "AI_CONTENT_BLOCKED", "sensitive upstream explanation"));
        worker.poll();
        verify(transactions).finish("job", null, "AI_CONTENT_BLOCKED",
                "AI provider blocked suggestion generation for this content");
    }
    @Test void unexpectedExceptionsDoNotExposeKeysOrPayloadsInStoredErrors() {
        when(gemini.suggest(input)).thenThrow(new IllegalStateException("secret-key and private sample value"));
        worker.poll();
        verify(transactions).finish("job",null,"AI_INTERNAL_ERROR","Suggestion generation failed; manual mapping is available");
    }
}
