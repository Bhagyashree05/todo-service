package com.todo.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request body for POST /api/v1/todos.
 * Both fields are mandatory — a to-do without a description or deadline is not valid.
 */
public record CreateTodoRequest(

        @NotBlank(message = "description must not be blank")
        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description,

        @NotNull(message = "dueAt is required")
        @Future(message = "dueAt must be in the future")
        Instant dueAt

) {}
