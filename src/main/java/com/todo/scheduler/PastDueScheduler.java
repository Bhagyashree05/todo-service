package com.todo.scheduler;

import com.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background sweeper that marks overdue items as PAST_DUE.
 *
 * Uses fixedDelayString (not fixedRate) — the next run starts only after the
 * previous one completes. This prevents overlapping sweeps if a run takes longer
 * than expected (e.g. under DB load).
 *
 * The interval is configurable via application.properties:
 *   todo.scheduler.past-due-sweep-delay-ms=60000
 */
@Component
@RequiredArgsConstructor
public class PastDueScheduler {

    private static final Logger log = LoggerFactory.getLogger(PastDueScheduler.class);

    private final TodoService todoService;

    @Scheduled(fixedDelayString = "${todo.scheduler.past-due-sweep-delay-ms:60000}")
    public void sweepPastDueItems() {
        log.debug("Past-due scheduler sweep triggered");
        try {
            todoService.sweepPastDueItems();
        } catch (Exception e) {
            log.error("Past-due scheduler sweep failed | error={}", e.getMessage(), e);
        }
    }
}
