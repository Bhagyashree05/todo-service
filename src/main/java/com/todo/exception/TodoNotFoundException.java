package com.todo.exception;

import com.todo.entity.TodoItem;

import java.util.UUID;

/** Thrown when a to-do item cannot be found by ID. Maps to HTTP 404. */
public class TodoNotFoundException extends RuntimeException {
    public TodoNotFoundException(UUID id) {
        super("Todo item not found: " + id);
    }
}
