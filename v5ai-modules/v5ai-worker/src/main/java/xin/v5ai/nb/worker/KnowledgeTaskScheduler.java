package xin.v5ai.nb.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.service.IKnowledgeTaskService;

/**
 * Periodically picks up processable knowledge indexing tasks (PENDING plus FAILED
 * tasks with attempts left) and runs them through {@link KnowledgeIndexingService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTaskScheduler {

    private final IKnowledgeTaskService taskService;
    private final KnowledgeIndexingService indexingService;

    @Scheduled(fixedDelayString = "${v5ai.worker.scan-interval-ms:5000}", initialDelayString = "${v5ai.worker.initial-delay-ms:10000}")
    public void scanAndProcess() {
        recoverStaleTasks();
        var tasks = taskService.findProcessable(batchSize());
        for (var task : tasks) {
            try {
                indexingService.processTask(task.getId());
            } catch (Exception exception) {
                log.warn("knowledge indexing task {} failed: {}", task.getId(), exception.getMessage());
            }
        }
    }

    /**
     * 回收卡死的 PROCESSING 任务（进程崩溃/重启残留，如任务在处理中断开连接后一直停在 PROCESSING），
     * 否则这类任务永远不会再被 {@link #findProcessable} 拾取。
     */
    private void recoverStaleTasks() {
        try {
            int recovered = taskService.recoverStaleProcessing(staleMinutes());
            if (recovered > 0) {
                log.info("recovered {} stale PROCESSING knowledge tasks", recovered);
            }
        } catch (Exception exception) {
            log.warn("recover stale knowledge tasks failed: {}", exception.getMessage());
        }
    }

    @Value("${v5ai.worker.batch-size:5}")
    private int batchSize;

    private int batchSize() {
        return batchSize <= 0 ? 5 : batchSize;
    }

    /** 任务在 PROCESSING 状态停留超过该分钟数视为卡死并回收（默认 10 分钟，远大于单次扫描间隔）。 */
    private int staleMinutes() {
        return 10;
    }
}
