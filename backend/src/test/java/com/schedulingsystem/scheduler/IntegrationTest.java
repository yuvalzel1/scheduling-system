package com.schedulingsystem.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.schedulingsystem.scheduler.schedule.ScheduleRepository;
import com.schedulingsystem.scheduler.schedule.quartz.QuartzScheduleRegistrar;
import com.schedulingsystem.scheduler.support.RecordingTask;
import com.schedulingsystem.scheduler.task.builtin.LogTask;

import org.junit.jupiter.api.AfterEach;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base for tests that run the whole application against a real PostgreSQL (Testcontainers) and a
 * real Quartz scheduler.
 *
 * <p>Everything that shapes the Spring context (properties, spies) is declared here and nowhere
 * else, so all integration tests share one context, one database and one scheduler. Two contexts
 * would mean two schedulers firing the same alarms.
 */
@SpringBootTest(properties = {
        // Tests create alarms that fire within a couple of seconds, so the scheduler must look for
        // new alarms more often than in production (see the note in application.yml).
        "spring.quartz.properties.org.quartz.scheduler.idleWaitTime=1000"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestOnlyTaskConfiguration.class})
public abstract class IntegrationTest {

    protected static final String SCHEDULES = "/api/schedules";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected ScheduleRepository scheduleRepository;

    @Autowired
    protected Scheduler scheduler;

    @Autowired
    protected JdbcClient jdbcClient;

    /** A task defined only in test code; see {@link RecordingTask}. */
    @Autowired
    protected RecordingTask recordingTask;

    /** Lets tests see when the Log Task really ran, and make it fail on purpose. */
    @MockitoSpyBean
    protected LogTask logTask;

    /** Lets tests make the Quartz step fail, to prove that nothing is left half-done. */
    @MockitoSpyBean
    protected QuartzScheduleRegistrar registrar;

    @AfterEach
    void removeAllSchedulesAndAlarms() throws SchedulerException {
        scheduler.clear();
        scheduleRepository.deleteAll();
        recordingTask.forgetRuns();
    }

    // ---- requests -------------------------------------------------------------------------

    protected ApiResponse get(String path) {
        return exchange(mvc.get().uri(path));
    }

    protected ApiResponse post(String path, String json) {
        return exchange(mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ApiResponse put(String path, String json) {
        return exchange(mvc.put().uri(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ApiResponse delete(String path) {
        return exchange(mvc.delete().uri(path));
    }

    /** Creates a schedule through the API and returns its id. Fails the test if it is rejected. */
    protected UUID create(String json) {
        ApiResponse response = post(SCHEDULES, json);
        assertThat(response.status()).as("create should succeed, body: %s", response.body()).isEqualTo(201);
        return UUID.fromString(response.read("$.id"));
    }

    private static ApiResponse exchange(MockMvcTester.MockMvcRequestBuilder request) {
        MockHttpServletResponse response = request.exchange().getResponse();
        try {
            return new ApiResponse(response.getStatus(), response.getContentType(), response.getHeader("Location"),
                    response.getContentAsString(StandardCharsets.UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public record ApiResponse(int status, String contentType, String location, String body) {

        public <T> T read(String jsonPath) {
            return JsonPath.read(body, jsonPath);
        }

        public List<String> errorFields() {
            return read("$.errors[*].field");
        }
    }

    // ---- request bodies -------------------------------------------------------------------

    protected static final String EVERY_15_MINUTES = """
            {"type": "INTERVAL", "every": 15, "unit": "MINUTES"}""";

    protected static final String MONDAY_AND_WEDNESDAY_AT_0830 = """
            {"type": "WEEKLY", "days": ["MONDAY", "WEDNESDAY"], "time": "08:30"}""";

    protected static final String CRON_EVERY_5_MINUTES = """
            {"type": "CRON", "expression": "0 0/5 * * * ?"}""";

    protected static String oneTimeAt(Instant moment) {
        return """
                {"type": "ONE_TIME", "runAt": "%s"}""".formatted(moment);
    }

    /** A Log Task schedule in the Berlin time zone with the message "hello". */
    protected static String logSchedule(String timingJson) {
        return schedule("log", timingJson, """
                {"message": "hello"}""");
    }

    protected static String schedule(String taskKey, String timingJson, String parametersJson) {
        return """
                {"taskKey": "%s", "timing": %s, "timeZone": "Europe/Berlin", "parameters": %s}"""
                .formatted(taskKey, timingJson, parametersJson);
    }

    // ---- looking into Quartz --------------------------------------------------------------

    /** The alarms Quartz holds for one schedule. There should only ever be zero or one. */
    protected List<? extends Trigger> alarmsOf(UUID scheduleId) throws SchedulerException {
        return scheduler.getTriggersOfJob(QuartzScheduleRegistrar.jobKey(scheduleId));
    }

    protected int totalAlarms() throws SchedulerException {
        return scheduler.getTriggerKeys(GroupMatcher.anyGroup()).size();
    }

    protected int totalJobs() throws SchedulerException {
        return scheduler.getJobKeys(GroupMatcher.anyGroup()).size();
    }
}
