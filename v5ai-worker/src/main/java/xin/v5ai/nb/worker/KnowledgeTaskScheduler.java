package xin.v5ai.nb.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.KnowledgeTaskRepository;

/**
 * Periodically picks up processable knowledge indexing tasks (PENDING plus FAILED
 * tasks with attempts left) and runs them through {@link KnowledgeIndexingService}.
 */
@Component
public class KnowledgeTaskScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeTaskScheduler.class);

    private final KnowledgeTaskRepository taskRepository;
    private final KnowledgeIndexingService indexingService;

    public KnowledgeTaskScheduler(KnowledgeTaskRepository taskRepository, KnowledgeIndexingService indexingService) {
        this.taskRepository = taskRepository;
        this.indexingService = indexingService;
    }

    @Scheduled(fixedDelayString = "${v5ai.worker.scan-interval-ms:5000}", initialDelayString = "${v5ai.worker.initial-delay-ms:10000}")
    public void scanAndProcess() {
        var tasks = taskRepository.findProcessable(batchSize());
        for (var task : tasks) {
            try {
                indexingService.processTask(task.id());
            } catch (Exception exception) {
                LOG.warn("knowledge indexing task {} failed: {}", task.id(), exception.getMessage());
            }
        }
    }

    @Value("${v5ai.worker.batch-size:5}")
    private int batchSize;

    private int batchSize() {
        return batchSize <= 0 ? 5 : batchSize;
    }
}
