package com.todo.service;

import com.todo.dto.CreateTodoRequest;
import com.todo.dto.TodoResponse;
import com.todo.dto.UpdateDescriptionRequest;
import com.todo.entity.TodoItem;
import com.todo.entity.TodoItem.Status;
import com.todo.exception.ItemImmutableException;
import com.todo.exception.TodoNotFoundException;
import com.todo.repository.TodoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoService Unit Tests")
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    private static final Instant FUTURE = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant PAST   = Instant.now().minus(1, ChronoUnit.HOURS);

    private TodoItem itemWithStatus(Status status) {
        TodoItem item = TodoItem.create("Test task", FUTURE);
        item.setStatus(status);
        try {
            var idField = TodoItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(item, UUID.randomUUID());
            var createdField = TodoItem.class.getDeclaredField("createdAt");
            createdField.setAccessible(true);
            createdField.set(item, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return item;
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates item with future dueAt")
        void create_validRequest_returnsSavedItem() {
            TodoItem saved = itemWithStatus(Status.NOT_DONE);
            when(todoRepository.save(any())).thenReturn(saved);

            TodoResponse response = todoService.create(new CreateTodoRequest("Buy milk", FUTURE));

            assertThat(response.status()).isEqualTo(Status.NOT_DONE);
            assertThat(response.doneAt()).isNull();
            verify(todoRepository).save(any(TodoItem.class));
        }

        @Test
        @DisplayName("rejects dueAt in the past")
        void create_pastDueAt_throwsIllegalArgument() {
            assertThatThrownBy(() -> todoService.create(new CreateTodoRequest("Buy milk", PAST)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dueAt must be in the future");
        }
    }

    @Nested
    @DisplayName("markAsDone()")
    class MarkAsDone {

        @Test
        @DisplayName("marks NOT_DONE item as DONE and sets doneAt")
        void markAsDone_notDoneItem_becomesDone() {
            TodoItem item = itemWithStatus(Status.NOT_DONE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));
            when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TodoResponse response = todoService.markAsDone(item.getId());

            assertThat(response.status()).isEqualTo(Status.DONE);
            assertThat(response.doneAt()).isNotNull();
        }

        @Test
        @DisplayName("throws 409 when item is PAST_DUE")
        void markAsDone_pastDueItem_throwsImmutable() {
            TodoItem item = itemWithStatus(Status.PAST_DUE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> todoService.markAsDone(item.getId()))
                    .isInstanceOf(ItemImmutableException.class)
                    .hasMessageContaining("PAST_DUE");
        }

        @Test
        @DisplayName("throws 409 when item is already DONE")
        void markAsDone_doneItem_throwsImmutable() {
            TodoItem item = itemWithStatus(Status.DONE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> todoService.markAsDone(item.getId()))
                    .isInstanceOf(ItemImmutableException.class)
                    .hasMessageContaining("DONE");
        }

        @Test
        @DisplayName("throws 404 when item not found")
        void markAsDone_notFound_throwsNotFound() {
            UUID id = UUID.randomUUID();
            when(todoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> todoService.markAsDone(id))
                    .isInstanceOf(TodoNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateDescription()")
    class UpdateDescription {

        @Test
        @DisplayName("updates description of NOT_DONE item")
        void updateDescription_notDoneItem_succeeds() {
            TodoItem item = itemWithStatus(Status.NOT_DONE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));
            when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TodoResponse response = todoService.updateDescription(
                    item.getId(), new UpdateDescriptionRequest("Updated description"));

            assertThat(response.description()).isEqualTo("Updated description");
        }

        @Test
        @DisplayName("throws 409 when item is DONE (immutable)")
        void updateDescription_doneItem_throwsImmutable() {
            TodoItem item = itemWithStatus(Status.DONE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> todoService.updateDescription(
                    item.getId(), new UpdateDescriptionRequest("New description")))
                    .isInstanceOf(ItemImmutableException.class)
                    .hasMessageContaining("DONE");
        }

        @Test
        @DisplayName("throws 409 when item is PAST_DUE (immutable)")
        void updateDescription_pastDueItem_throwsImmutable() {
            TodoItem item = itemWithStatus(Status.PAST_DUE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> todoService.updateDescription(
                    item.getId(), new UpdateDescriptionRequest("New description")))
                    .isInstanceOf(ItemImmutableException.class)
                    .hasMessageContaining("PAST_DUE");
        }
    }

    @Nested
    @DisplayName("markAsNotDone()")
    class MarkAsNotDone {

        @Test
        @DisplayName("throws 409 for DONE item (immutable)")
        void markAsNotDone_doneItem_throwsImmutable() {
            TodoItem item = itemWithStatus(Status.DONE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> todoService.markAsNotDone(item.getId()))
                    .isInstanceOf(ItemImmutableException.class);
        }

        @Test
        @DisplayName("is idempotent for NOT_DONE item")
        void markAsNotDone_notDoneItem_idempotentNoOp() {
            TodoItem item = itemWithStatus(Status.NOT_DONE);
            when(todoRepository.findById(item.getId())).thenReturn(Optional.of(item));

            TodoResponse response = todoService.markAsNotDone(item.getId());
            assertThat(response.status()).isEqualTo(Status.NOT_DONE);
        }
    }

    @Nested
    @DisplayName("sweepPastDueItems()")
    class Sweep {

        @Test
        @DisplayName("calls bulkMarkPastDue with current time")
        void sweep_callsBulkUpdate() {
            when(todoRepository.bulkMarkPastDue(any(Instant.class))).thenReturn(3);
            todoService.sweepPastDueItems();
            verify(todoRepository).bulkMarkPastDue(any(Instant.class));
        }

        @Test
        @DisplayName("handles zero updated items gracefully")
        void sweep_noItemsToUpdate_noException() {
            when(todoRepository.bulkMarkPastDue(any(Instant.class))).thenReturn(0);
            assertThatNoException().isThrownBy(() -> todoService.sweepPastDueItems());
        }
    }

    @Nested
    @DisplayName("getAll()")
    class GetAll {

        @Test
        @DisplayName("default (includeAll=false) returns only NOT_DONE items")
        void getAll_defaultFilter_returnsNotDoneOnly() {
            TodoItem item = itemWithStatus(Status.NOT_DONE);
            when(todoRepository.findAllByStatus(Status.NOT_DONE)).thenReturn(List.of(item));

            List<TodoResponse> results = todoService.getAll(false);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(Status.NOT_DONE);
            verify(todoRepository).findAllByStatus(Status.NOT_DONE);
            verify(todoRepository, never()).findAll();
        }

        @Test
        @DisplayName("includeAll=true returns all items via findAll")
        void getAll_includeAll_returnsAllItems() {
            when(todoRepository.findAll()).thenReturn(List.of(
                    itemWithStatus(Status.NOT_DONE),
                    itemWithStatus(Status.DONE),
                    itemWithStatus(Status.PAST_DUE)
            ));

            List<TodoResponse> results = todoService.getAll(true);

            assertThat(results).hasSize(3);
            verify(todoRepository).findAll();
        }
    }
}
