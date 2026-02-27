package com.todo.controller;

import com.todo.dto.CreateTodoRequest;
import com.todo.dto.TodoResponse;
import com.todo.dto.UpdateDescriptionRequest;
import com.todo.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for to-do item management.
 * Base path: /api/v1/todos
 *
 * Each mutation endpoint has a dedicated URL (/done, /not-done, /description)
 * rather than a generic PATCH with a body flag. This makes the intent explicit
 * and removes ambiguity about what is updatable via which endpoint.
 */
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private static final Logger log = LoggerFactory.getLogger(TodoController.class);

    private final TodoService todoService;

    /**
     * POST /api/v1/todos
     * Creates a new to-do item. Returns 201 Created with the full item body.
     */
    @PostMapping
    public ResponseEntity<TodoResponse> create(
            @Valid @RequestBody CreateTodoRequest request) {
        log.debug("POST /api/v1/todos | dueAt={}", request.dueAt());
        TodoResponse response = todoService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/todos
     * Returns NOT_DONE items by default. Pass ?all=true to retrieve all items.
     */
    @GetMapping
    public ResponseEntity<List<TodoResponse>> getAll(
            @RequestParam(name = "all", defaultValue = "false") boolean all) {
        log.debug("GET /api/v1/todos | all={}", all);
        return ResponseEntity.ok(todoService.getAll(all));
    }

    /**
     * GET /api/v1/todos/{id}
     * Returns the full detail of a specific item. Returns 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> getById(@PathVariable UUID id) {
        log.debug("GET /api/v1/todos/{}", id);
        return ResponseEntity.ok(todoService.getById(id));
    }

    /**
     * PATCH /api/v1/todos/{id}/description
     * Updates the description of a NOT_DONE item. Returns 409 if DONE or PAST_DUE.
     */
    @PatchMapping("/{id}/description")
    public ResponseEntity<TodoResponse> updateDescription(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDescriptionRequest request) {
        log.debug("PATCH /api/v1/todos/{}/description", id);
        return ResponseEntity.ok(todoService.updateDescription(id, request));
    }

    /**
     * PATCH /api/v1/todos/{id}/done
     * Marks a NOT_DONE item as DONE. Sets doneAt. Returns 409 if already DONE or PAST_DUE.
     */
    @PatchMapping("/{id}/done")
    public ResponseEntity<TodoResponse> markAsDone(@PathVariable UUID id) {
        log.debug("PATCH /api/v1/todos/{}/done", id);
        return ResponseEntity.ok(todoService.markAsDone(id));
    }

    /**
     * PATCH /api/v1/todos/{id}/not-done
     * Per design decision: DONE and PAST_DUE items are immutable → 409 Conflict.
     * Only NOT_DONE items reach this successfully (idempotent no-op).
     */
    @PatchMapping("/{id}/not-done")
    public ResponseEntity<TodoResponse> markAsNotDone(@PathVariable UUID id) {
        log.debug("PATCH /api/v1/todos/{}/not-done", id);
        return ResponseEntity.ok(todoService.markAsNotDone(id));
    }
}
