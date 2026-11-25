package com.schedulingsystem.scheduler.task;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only: tasks can be listed, never created or changed. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRegistry taskRegistry;

    public TaskController(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @GetMapping
    public List<TaskDefinition> list() {
        return taskRegistry.definitions();
    }
}
