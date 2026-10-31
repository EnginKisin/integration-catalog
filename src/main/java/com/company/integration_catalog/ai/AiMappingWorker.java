package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Component @Slf4j @ConditionalOnProperty(name="catalog.ai.enabled", havingValue="true")
public class AiMappingWorker {
    private final AiJobTransactions transactions;
    private final AiMappingService mappings;
    private final AiProperties properties;
    private final ExecutorService executor;
    private final AtomicBoolean busy=new AtomicBoolean();

    public AiMappingWorker(AiJobTransactions transactions,AiMappingService mappings,AiProperties properties,
            @Qualifier("aiMappingExecutor") ExecutorService executor) {
        this.transactions=transactions; this.mappings=mappings; this.properties=properties; this.executor=executor;
    }

    @Scheduled(fixedDelayString="${catalog.ai.poll-ms:1000}")
    public void poll() {
        if (!busy.compareAndSet(false,true)) return;
        try { executor.execute(this::process); }
        catch (java.util.concurrent.RejectedExecutionException ex) { busy.set(false); }
    }

    private void process() {
        try {
            var job=transactions.claim();
            if (job==null) return;
            try {
                if (!job.provider().equals(properties.getProvider()) || !job.model().equals(properties.getModel()))
                    throw new PayloadValidationException(503,"AI_CONFIGURATION_CHANGED","Provider configuration changed; create a new job");
                transactions.finish(job.id(),mappings.provider().suggest(job.input()),null,null);
            } catch (PayloadValidationException ex) {
                transactions.finish(job.id(),null,ex.getCode(),ex.getMessage());
            } catch (Exception ex) {
                // Exceptions from HTTP/model parsers may contain user payloads: log only the job ID.
                log.warn("AI mapping job failed id={}",job.id());
                transactions.finish(job.id(),null,"AI_INTERNAL_ERROR","Suggestion generation failed; manual mapping is available");
            }
        } catch (Exception ex) {
            log.warn("AI mapping worker could not process queue");
        } finally { busy.set(false); }
    }
}
