package com.schedulingsystem.scheduler;

import com.schedulingsystem.scheduler.support.RecordingTask;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Adds one extra task to the integration-test application, exactly the way a real one is added: as a bean. */
@TestConfiguration(proxyBeanMethods = false)
class TestOnlyTaskConfiguration {

    @Bean
    RecordingTask recordingTask() {
        return new RecordingTask();
    }
}
