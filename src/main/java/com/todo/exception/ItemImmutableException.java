package com.todo.exception;

import com.todo.entity.TodoItem;

import java.util.UUID;

/**
 * Thrown when a mutation is attempted on an item whose status is PAST_DUE or DONE.
 * Both statuses are immutable per the requirements and design decisions.
 * Maps to HTTP 409 Conflict.
 */
public class ItemImmutableException extends RuntimeException {
    public ItemImmutableException(UUID id, TodoItem.Status status) {
        super("Todo item " + id + " is " + status + " and cannot be modified.");
    }
}
