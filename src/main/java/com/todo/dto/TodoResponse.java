package com.todo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.todo.entity.TodoItem;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body returned by all to-do endpoints.
 * doneAt is omitted from JSON when null (item not yet completed).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodoResponse(

        UUID id,
        String description,
        TodoItem.Status status,
        Instant createdAt,
        Instant dueAt,
        Instant doneAt

) {
    /** Maps a TodoItem entity to the API response shape. */
    public static TodoResponse from(TodoItem item) {
        return new TodoResponse(
                item.getId(),
                item.getDescription(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getDueAt(),
                item.getDoneAt()
        );
    }
}
