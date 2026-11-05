package com.hatodo.recurrence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {

    @Query("SELECT r FROM RecurrenceRule r WHERE r.list.id = :listId ORDER BY r.position ASC")
    List<RecurrenceRule> findForList(@Param("listId") UUID listId);

    @Query("SELECT COALESCE(MAX(r.position), -1) FROM RecurrenceRule r WHERE r.list.id = :listId")
    int highestPosition(@Param("listId") UUID listId);

    /**
     * The rules a run might owe something.
     *
     * <p>Paused and spent rules are excluded here rather than inside the loop,
     * so a household that has switched everything off costs one scan.
     */
    @Query("""
            SELECT r FROM RecurrenceRule r
            WHERE r.paused = FALSE
              AND r.startsOn <= :through
              AND (r.endsOn IS NULL OR r.endsOn >= :from)
            ORDER BY r.list.id ASC, r.position ASC
            """)
    List<RecurrenceRule> findDue(@Param("from") LocalDate from,
                                 @Param("through") LocalDate through);

    @Query("SELECT r FROM RecurrenceRule r WHERE r.list.id = :listId AND r.paused = FALSE")
    List<RecurrenceRule> findActiveForList(@Param("listId") UUID listId);

    /** Whether a cost choice is one a rule has already picked. */
    @Query("SELECT COUNT(v) > 0 FROM RecurrenceCostValue v WHERE v.choice.id = :choiceId")
    boolean usesChoice(@Param("choiceId") UUID choiceId);

    /** Used when a grant is revoked: a rule cannot keep assigning to somebody gone. */
    @Query("SELECT r FROM RecurrenceRule r WHERE r.list.id = :listId AND r.assignee.id = :userId")
    List<RecurrenceRule> findAssignedTo(@Param("listId") UUID listId,
                                        @Param("userId") UUID userId);
}
