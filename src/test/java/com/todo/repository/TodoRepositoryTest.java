package com.todo.repository;

import com.todo.entity.TodoItem;
import com.todo.entity.TodoItem.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("TodoRepository Integration Tests")
class TodoRepositoryTest {

    @Autowired
    private TodoRepository todoRepository;

    private static final Instant FUTURE = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant PAST   = Instant.now().minus(1, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
    }


    @Test
    @DisplayName("findAllByStatus(NOT_DONE) returns only NOT_DONE items")
    void findAllByStatus_notDone_returnsOnlyNotDone() {
        TodoItem notDone  = save(Status.NOT_DONE, FUTURE);
        TodoItem done     = save(Status.DONE, FUTURE);
        TodoItem pastDue  = save(Status.PAST_DUE, PAST);

        List<TodoItem> results = todoRepository.findAllByStatus(Status.NOT_DONE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(notDone.getId());
    }

    @Test
    @DisplayName("findAllByStatus(DONE) returns only DONE items")
    void findAllByStatus_done_returnsOnlyDone() {
        save(Status.NOT_DONE, FUTURE);
        TodoItem done = save(Status.DONE, FUTURE);

        List<TodoItem> results = todoRepository.findAllByStatus(Status.DONE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(done.getId());
    }

    @Test
    @DisplayName("findAllByStatus returns empty list when none match")
    void findAllByStatus_noMatch_returnsEmpty() {
        save(Status.NOT_DONE, FUTURE);
        assertThat(todoRepository.findAllByStatus(Status.PAST_DUE)).isEmpty();
    }


    @Test
    @DisplayName("bulkMarkPastDue flips overdue NOT_DONE items to PAST_DUE")
    void bulkMarkPastDue_overdueNotDone_becomePastDue() {
        TodoItem overdue = save(Status.NOT_DONE, PAST);
        TodoItem future  = save(Status.NOT_DONE, FUTURE);

        int updated = todoRepository.bulkMarkPastDue(Instant.now());

        assertThat(updated).isEqualTo(1);
        assertThat(todoRepository.findById(overdue.getId()).get().getStatus())
                .isEqualTo(Status.PAST_DUE);
        assertThat(todoRepository.findById(future.getId()).get().getStatus())
                .isEqualTo(Status.NOT_DONE);
    }

    @Test
    @DisplayName("bulkMarkPastDue does NOT affect DONE items")
    void bulkMarkPastDue_doneItem_notAffected() {
        TodoItem done = save(Status.DONE, PAST);

        int updated = todoRepository.bulkMarkPastDue(Instant.now());

        assertThat(updated).isEqualTo(0);
        assertThat(todoRepository.findById(done.getId()).get().getStatus())
                .isEqualTo(Status.DONE);
    }

    @Test
    @DisplayName("bulkMarkPastDue returns 0 when nothing is overdue")
    void bulkMarkPastDue_nothingOverdue_returnsZero() {
        save(Status.NOT_DONE, FUTURE);
        assertThat(todoRepository.bulkMarkPastDue(Instant.now())).isEqualTo(0);
    }


    @Test
    @DisplayName("findById returns empty Optional for unknown id")
    void findById_unknownId_returnsEmpty() {
        Optional<TodoItem> result = todoRepository.findById(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById returns item when it exists")
    void findById_existingId_returnsItem() {
        TodoItem saved = save(Status.NOT_DONE, FUTURE);
        Optional<TodoItem> result = todoRepository.findById(saved.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getDescription()).isEqualTo(saved.getDescription());
    }


    private TodoItem save(Status status, Instant dueAt) {
        TodoItem item = TodoItem.create("Task " + UUID.randomUUID(), dueAt);
        item.setStatus(status);
        return todoRepository.saveAndFlush(item);
    }
}
