package com.schedulingsystem.scheduler.task;

import java.util.Map;

/**
 * A predefined unit of work that can be scheduled. Tasks are read-only: they exist as code.
 * To add a task, add one Spring bean that implements this interface.
 */
public interface Task {

    TaskDefinition definition();

    /**
     * Runs the task.
     *
     * @param parameters the values saved with the schedule, already checked against
     *                   {@link #definition()}: required ones are present and every value has the
     *                   declared type (STRING is a {@link String}, NUMBER is a {@link Number})
     */
    void execute(Map<String, Object> parameters) throws Exception;
}
