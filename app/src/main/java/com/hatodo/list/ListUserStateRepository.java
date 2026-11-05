package com.hatodo.list;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListUserStateRepository extends JpaRepository<ListUserState, ListUserStateId> {

    @Query("SELECT s FROM ListUserState s WHERE s.list.id = :listId AND s.user.id = :userId")
    Optional<ListUserState> find(@Param("listId") UUID listId, @Param("userId") UUID userId);

    /** The sidebar's pinned section (R27), newest pin first. */
    @Query("""
            SELECT s.list FROM ListUserState s
            WHERE s.user.id = :userId AND s.pinnedAt IS NOT NULL
            AND EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = s.list AND a.user.id = :userId)
            ORDER BY s.pinnedAt DESC
            """)
    List<TodoList> findPinned(@Param("userId") UUID userId);

    /**
     * The sidebar's recent section (R28). Pinned lists are excluded so the
     * sidebar never shows the same list twice.
     */
    @Query("""
            SELECT s.list FROM ListUserState s
            WHERE s.user.id = :userId AND s.lastInteractedAt IS NOT NULL AND s.pinnedAt IS NULL
            AND EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = s.list AND a.user.id = :userId)
            ORDER BY s.lastInteractedAt DESC
            """)
    List<TodoList> findRecent(@Param("userId") UUID userId, org.springframework.data.domain.Pageable limit);

    /**
     * Stamps recency without reading first, and without touching the row at all
     * when it was touched recently.
     *
     * <p>The condition on the UPDATE is what keeps this off the hot path: a
     * second visit inside the window is a no-op at the storage layer, taking no
     * row lock and writing no WAL. Rows are keyed per (list, member), so two
     * members opening the same list never contend.
     */
    @Modifying
    @Query(value = """
            INSERT INTO list_user_state (list_id, user_id, last_interacted_at)
            VALUES (:listId, :userId, now())
            ON CONFLICT (list_id, user_id) DO UPDATE SET last_interacted_at = now()
            WHERE list_user_state.last_interacted_at IS NULL
               OR list_user_state.last_interacted_at < now() - make_interval(mins => :windowMinutes)
            """, nativeQuery = true)
    void touch(@Param("listId") UUID listId,
               @Param("userId") UUID userId,
               @Param("windowMinutes") int windowMinutes);
}
