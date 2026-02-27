package com.todo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.dto.CreateTodoRequest;
import com.todo.dto.UpdateDescriptionRequest;
import com.todo.entity.TodoItem;
import com.todo.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("TodoController Integration Tests")
class TodoControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TodoRepository todoRepository;

    private static final String BASE   = "/api/v1/todos";
    private static final Instant FUTURE = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant PAST   = Instant.now().minus(1, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/todos")
    class CreateTodo {

        @Test
        @DisplayName("returns 201 with full item body for valid request")
        void create_validRequest_returns201() throws Exception {
            postTodo(new CreateTodoRequest("Buy milk", FUTURE))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.description").value("Buy milk"))
                    .andExpect(jsonPath("$.status").value("NOT_DONE"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.dueAt").isNotEmpty())
                    .andExpect(jsonPath("$.doneAt").doesNotExist());
        }

        @Test
        @DisplayName("returns 400 for blank description")
        void create_blankDescription_returns400() throws Exception {
            postTodo(new CreateTodoRequest("  ", FUTURE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("blank")));
        }

        @Test
        @DisplayName("returns 400 when dueAt is in the past")
        void create_pastDueAt_returns400() throws Exception {
            postTodo(new CreateTodoRequest("Past task", PAST))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("returns 400 when dueAt is missing")
        void create_missingDueAt_returns400() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":\"Missing due date\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/todos")
    class GetTodos {

        @Test
        @DisplayName("returns only NOT_DONE items by default")
        void getAll_default_returnsNotDoneOnly() throws Exception {
            createItem("Task 1", FUTURE);
            patchDone(extractId(createItem("Task 2", FUTURE)));

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status").value("NOT_DONE"));
        }

        @Test
        @DisplayName("returns all items when all=true")
        void getAll_allTrue_returnsEverything() throws Exception {
            createItem("Task 1", FUTURE);
            patchDone(extractId(createItem("Task 2", FUTURE)));

            mockMvc.perform(get(BASE).param("all", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("returns empty list when no items exist")
        void getAll_empty_returnsEmptyList() throws Exception {
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/todos/{id}")
    class GetById {

        @Test
        @DisplayName("returns 200 with item details")
        void getById_exists_returns200() throws Exception {
            String id = extractId(createItem("My task", FUTURE));
            mockMvc.perform(get(BASE + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.description").value("My task"));
        }

        @Test
        @DisplayName("returns 404 for unknown id")
        void getById_notFound_returns404() throws Exception {
            mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/todos/{id}/done")
    class MarkDone {

        @Test
        @DisplayName("marks NOT_DONE item as DONE and sets doneAt")
        void markDone_notDoneItem_returns200WithDoneAt() throws Exception {
            String id = extractId(createItem("Task", FUTURE));
            patchDone(id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DONE"))
                    .andExpect(jsonPath("$.doneAt").isNotEmpty());
        }

        @Test
        @DisplayName("returns 409 for PAST_DUE item")
        void markDone_pastDueItem_returns409() throws Exception {
            TodoItem item = TodoItem.create("Overdue task", PAST);
            item.setStatus(TodoItem.Status.PAST_DUE);
            TodoItem saved = todoRepository.save(item);

            mockMvc.perform(patch(BASE + "/{id}/done", saved.getId()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("PAST_DUE")));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/todos/{id}/not-done")
    class MarkNotDone {

        @Test
        @DisplayName("returns 409 for DONE item (immutable)")
        void markNotDone_doneItem_returns409() throws Exception {
            String id = extractId(createItem("Task", FUTURE));
            patchDone(id);

            mockMvc.perform(patch(BASE + "/{id}/not-done", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("DONE")));
        }

        @Test
        @DisplayName("returns 200 for NOT_DONE item (idempotent)")
        void markNotDone_notDoneItem_returns200() throws Exception {
            String id = extractId(createItem("Task", FUTURE));
            mockMvc.perform(patch(BASE + "/{id}/not-done", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NOT_DONE"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/todos/{id}/description")
    class UpdateDescription {

        @Test
        @DisplayName("updates description of NOT_DONE item")
        void updateDescription_notDone_returns200() throws Exception {
            String id = extractId(createItem("Old description", FUTURE));
            mockMvc.perform(patch(BASE + "/{id}/description", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateDescriptionRequest("New description"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("New description"));
        }

        @Test
        @DisplayName("returns 409 for DONE item")
        void updateDescription_doneItem_returns409() throws Exception {
            String id = extractId(createItem("Task", FUTURE));
            patchDone(id);

            mockMvc.perform(patch(BASE + "/{id}/description", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateDescriptionRequest("New description"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("returns 400 for blank description")
        void updateDescription_blank_returns400() throws Exception {
            String id = extractId(createItem("Task", FUTURE));

            mockMvc.perform(patch(BASE + "/{id}/description", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateDescriptionRequest(""))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResultActions createItem(String description, Instant dueAt) throws Exception {
        return postTodo(new CreateTodoRequest(description, dueAt));
    }

    private ResultActions postTodo(CreateTodoRequest request) throws Exception {
        return mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions patchDone(String id) throws Exception {
        return mockMvc.perform(patch(BASE + "/{id}/done", id));
    }

    private String extractId(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                        actions.andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }
}
