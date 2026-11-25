package com.schedulingsystem.scheduler.schedule;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findAllByOrderByCreatedAtDesc();
}
