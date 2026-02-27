package com.todo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "todo_items")
@Getter
@Setter
@NoArgsConstructor
public class TodoItem {

    public enum Status {
        NOT_DONE, DONE, PAST_DUE
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant dueAt;

    @Column
    private Instant doneAt;

    /**
     * Optimistic locking version field.
     * Prevents lost updates on concurrent PATCH requests.
     * JPA raises OptimisticLockException on stale update → mapped to 409 by GlobalExceptionHandler.
     */
    @Version
    private Long version;

    @PrePersist
    private void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = Status.NOT_DONE;
        }
    }

    /** Convenience factory */
    public static TodoItem create(String description, Instant dueAt) {
        TodoItem item = new TodoItem();
        item.setDescription(description);
        item.setDueAt(dueAt);
        return item;
    }
}
