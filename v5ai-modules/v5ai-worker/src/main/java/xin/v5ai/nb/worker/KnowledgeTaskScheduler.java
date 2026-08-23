package xin.v5ai.nb.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.repository.KnowledgeTaskRepository;

/**
 * Periodically picks up processable knowledge indexing tasks (PENDING plus FAILED
 * tasks with attempts left) and runs them through {@link KnowledgeIndexingService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTaskScheduler {

    private final KnowledgeTaskRepository taskRepository;
    private final KnowledgeIndexingService indexingService;

    @Scheduled(fixedDelayString = "${v5ai.worker.scan-interval-ms:5000}", initialDelayString = "${v5ai.worker.initial-delay-ms:10000}")
    public void scanAndProcess() {
        var tasks = taskRepository.findProcessable(batchSize());
        for (var task : tasks) {
            try {
                indexingService.processTask(task.getId());
            } catch (Exception exception) {
                log.warn("knowledge indexing task {} failed: {}", task.getId(), exception.getMessage());
            }
        }
    }

    @Value("${v5ai.worker.batch-size:5}")
    private int batchSize;

    private int batchSize() {
        return batchSize <= 0 ? 5 : batchSize;
    }
}
