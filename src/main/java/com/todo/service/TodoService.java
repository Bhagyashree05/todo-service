package com.todo.service;

import com.todo.dto.CreateTodoRequest;
import com.todo.dto.TodoResponse;
import com.todo.dto.UpdateDescriptionRequest;
import com.todo.entity.TodoItem;
import com.todo.entity.TodoItem.Status;
import com.todo.exception.ItemImmutableException;
import com.todo.exception.TodoNotFoundException;
import com.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for the to-do service.
 *
 * State machine:
 *   NOT_DONE  →  DONE         (markAsDone)
 *   NOT_DONE  →  PAST_DUE     (scheduler sweep or on-read guard)
 *   DONE      →  [immutable]
 *   PAST_DUE  →  [immutable]
 *
 * Both DONE and PAST_DUE items are fully locked from mutation (design decision:
 * keeps the state machine simple and preserves the doneAt audit trail).
 */
@Service
@RequiredArgsConstructor
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoRepository todoRepository;

    @Transactional
    public TodoResponse create(CreateTodoRequest request) {
        log.info("Creating todo item | description.length={} dueAt={}",
                request.description().length(), request.dueAt());

        if (!request.dueAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("dueAt must be in the future");
        }

        TodoItem item = TodoItem.create(request.description(), request.dueAt());
        TodoItem saved = todoRepository.save(item);

        log.info("Todo item created | id={} dueAt={}", saved.getId(), saved.getDueAt());
        return TodoResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TodoResponse> getAll(boolean includeAll) {
        log.debug("Fetching todo items | includeAll={}", includeAll);

        List<TodoItem> items = includeAll
                ? todoRepository.findAll()
                : todoRepository.findAllByStatus(Status.NOT_DONE);

        List<TodoResponse> responses = items.stream()
                .map(this::applyOnReadPastDueGuard)
                .map(TodoResponse::from)
                .toList();

        log.debug("Returned {} todo item(s) | includeAll={}", responses.size(), includeAll);
        return responses;
    }

    @Transactional(readOnly = true)
    public TodoResponse getById(UUID id) {
        log.debug("Fetching todo item | id={}", id);
        TodoItem item = findOrThrow(id);
        return TodoResponse.from(applyOnReadPastDueGuard(item));
    }


    @Transactional
    public TodoResponse updateDescription(UUID id, UpdateDescriptionRequest request) {
        log.info("Updating description | id={}", id);
        TodoItem item = findOrThrow(id);
        guardImmutable(item);

        item.setDescription(request.description());
        TodoItem saved = todoRepository.save(item);

        log.info("Description updated | id={}", id);
        return TodoResponse.from(saved);
    }

    @Transactional
    public TodoResponse markAsDone(UUID id) {
        log.info("Marking as DONE | id={}", id);
        TodoItem item = findOrThrow(id);
        guardImmutable(item);

        item.setStatus(Status.DONE);
        item.setDoneAt(Instant.now());
        TodoItem saved = todoRepository.save(item);

        log.info("Item marked DONE | id={} doneAt={}", id, saved.getDoneAt());
        return TodoResponse.from(saved);
    }


    @Transactional
    public TodoResponse markAsNotDone(UUID id) {
        log.info("Marking as NOT_DONE | id={}", id);
        TodoItem item = findOrThrow(id);
        guardImmutable(item);

        log.info("Item already NOT_DONE (no-op) | id={}", id);
        return TodoResponse.from(item);
    }


    @Transactional
    public void sweepPastDueItems() {
        int updated = todoRepository.bulkMarkPastDue(Instant.now());
        if (updated > 0) {
            log.info("Past-due sweep completed | itemsUpdated={}", updated);
        } else {
            log.debug("Past-due sweep completed | itemsUpdated=0");
        }
    }


    private TodoItem findOrThrow(UUID id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Todo item not found | id={}", id);
                    return new TodoNotFoundException(id);
                });
    }

    private void guardImmutable(TodoItem item) {
        if (item.getStatus() == Status.PAST_DUE || item.getStatus() == Status.DONE) {
            log.warn("Mutation attempted on immutable item | id={} status={}",
                    item.getId(), item.getStatus());
            throw new ItemImmutableException(item.getId(), item.getStatus());
        }
    }

    private TodoItem applyOnReadPastDueGuard(TodoItem item) {
        if (item.getStatus() == Status.NOT_DONE && item.getDueAt().isBefore(Instant.now())) {
            log.info("On-read past-due correction | id={} dueAt={}", item.getId(), item.getDueAt());
            item.setStatus(Status.PAST_DUE);
            return todoRepository.save(item);
        }
        return item;
    }
}
