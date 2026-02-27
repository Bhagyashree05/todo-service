package com.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/todos/{id}/description.
 * Only the description field is updatable via this endpoint.
 */
public record UpdateDescriptionRequest(

        @NotBlank(message = "description must not be blank")
        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description

) {}
