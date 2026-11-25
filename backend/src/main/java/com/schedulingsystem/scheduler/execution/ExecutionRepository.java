package com.schedulingsystem.scheduler.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    List<Execution> findByScheduleIdOrderByStartedAtDesc(UUID scheduleId);

    Optional<Execution> findFirstByScheduleIdOrderByStartedAtDesc(UUID scheduleId);

    /** The newest run of every schedule that has run at least once, in one query. */
    @Query(value = "SELECT DISTINCT ON (schedule_id) * FROM executions ORDER BY schedule_id, started_at DESC",
            nativeQuery = true)
    List<Execution> findNewestOfEachSchedule();

    /** Deletes a schedule's runs except its {@code keep} newest ones. */
    @Modifying
    @Query(value = """
            DELETE FROM executions
            WHERE schedule_id = :scheduleId
              AND id NOT IN (SELECT id FROM executions
                             WHERE schedule_id = :scheduleId
                             ORDER BY started_at DESC
                             LIMIT :keep)
            """, nativeQuery = true)
    int deleteAllButNewest(UUID scheduleId, int keep);
}
