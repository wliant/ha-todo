package com.hatodo.data;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataListUserStateRepository
        extends JpaRepository<DataListUserState, DataListUserStateId> {

    @Query("SELECT s FROM DataListUserState s WHERE s.list.id = :listId AND s.user.id = :userId")
    Optional<DataListUserState> find(@Param("listId") UUID listId, @Param("userId") UUID userId);
}
