package com.todo.repository;

import com.todo.entity.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TodoRepository extends JpaRepository<TodoItem, UUID> {

    /** Returns only NOT_DONE items — default list view. */
    List<TodoItem> findAllByStatus(TodoItem.Status status);

    /**
     * Bulk update: flip all NOT_DONE items whose dueAt is in the past to PAST_DUE.
     * Called by the scheduled sweeper every 60s.
     * A single bulk UPDATE is used rather than loading entities into memory,
     * which is efficient and correct even at scale.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TodoItem t
            SET t.status = 'PAST_DUE'
            WHERE t.status = 'NOT_DONE'
              AND t.dueAt < :now
            """)
    int bulkMarkPastDue(@Param("now") Instant now);
}
