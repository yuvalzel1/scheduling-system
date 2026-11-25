package com.schedulingsystem.scheduler.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.schedulingsystem.scheduler.IntegrationTest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.quartz.CronTrigger;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

class ScheduleApiIntegrationTests extends IntegrationTest {

    private static final Instant IN_ONE_HOUR = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

    @Nested
    class Listing {

        @Test
        void isEmptyAtFirst() {
            ApiResponse response = get(SCHEDULES);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.<List<Object>>read("$")).isEmpty();
        }

        @Test
        void showsCreatedSchedulesNewestFirst() {
            UUID first = create(logSchedule(EVERY_15_MINUTES));
            UUID second = create(logSchedule(CRON_EVERY_5_MINUTES));

            assertThat(get(SCHEDULES).<List<String>>read("$[*].id")).containsExactly(second.toString(), first.toString());
        }
    }

    @Nested
    class Creating {

        @Test
        void savesAOneTimeScheduleWithOneAlarmAtThatMoment() throws SchedulerException {
            ApiResponse response = post(SCHEDULES, logSchedule(oneTimeAt(IN_ONE_HOUR)));

            assertThat(response.status()).isEqualTo(201);
            UUID id = UUID.fromString(response.read("$.id"));
            assertThat(response.location()).isEqualTo(SCHEDULES + "/" + id);
            assertThat(response.<Map<String, Object>>read("$.timing"))
                    .isEqualTo(Map.of("type", "ONE_TIME", "runAt", IN_ONE_HOUR.toString()));

            SimpleTrigger alarm = (SimpleTrigger) onlyAlarmOf(id);
            assertThat(alarm.getStartTime().toInstant()).isEqualTo(IN_ONE_HOUR);
            assertThat(alarm.getRepeatCount()).isZero();
        }

        @Test
        void savesAnIntervalScheduleWithOneRepeatingAlarm() throws SchedulerException {
            Instant before = Instant.now();
            ApiResponse response = post(SCHEDULES, logSchedule(EVERY_15_MINUTES));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.<Map<String, Object>>read("$.timing"))
                    .isEqualTo(Map.of("type", "INTERVAL", "every", 15, "unit", "MINUTES"));

            SimpleTrigger alarm = (SimpleTrigger) onlyAlarmOf(UUID.fromString(response.read("$.id")));
            assertThat(alarm.getRepeatInterval()).isEqualTo(Duration.ofMinutes(15).toMillis());
            assertThat(alarm.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
            assertThat(alarm.getNextFireTime().toInstant())
                    .as("the first run is one full gap after saving")
                    .isBetween(before.plus(Duration.ofMinutes(15)).minusSeconds(1), Instant.now().plus(Duration.ofMinutes(15)));
        }

        @Test
        void savesAWeeklyScheduleWithOneCronAlarmInTheSchedulesTimeZone() throws SchedulerException {
            ApiResponse response = post(SCHEDULES, logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.<Map<String, Object>>read("$.timing"))
                    .isEqualTo(Map.of("type", "WEEKLY", "days", List.of("MONDAY", "WEDNESDAY"), "time", "08:30"));

            CronTrigger alarm = (CronTrigger) onlyAlarmOf(UUID.fromString(response.read("$.id")));
            assertThat(alarm.getCronExpression()).isEqualTo("0 30 8 ? * MON,WED");
            assertThat(alarm.getTimeZone().getID()).isEqualTo("Europe/Berlin");
        }

        @Test
        void savesACronScheduleWithOneCronAlarm() throws SchedulerException {
            ApiResponse response = post(SCHEDULES, logSchedule(CRON_EVERY_5_MINUTES));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.<Map<String, Object>>read("$.timing"))
                    .isEqualTo(Map.of("type", "CRON", "expression", "0 0/5 * * * ?"));

            CronTrigger alarm = (CronTrigger) onlyAlarmOf(UUID.fromString(response.read("$.id")));
            assertThat(alarm.getCronExpression()).isEqualTo("0 0/5 * * * ?");
            assertThat(alarm.getTimeZone().getID()).isEqualTo("Europe/Berlin");
        }

        @Test
        void returnsTheWholeSchedule() {
            ApiResponse response = post(SCHEDULES, logSchedule(EVERY_15_MINUTES));

            assertThat(response.<String>read("$.taskKey")).isEqualTo("log");
            assertThat(response.<String>read("$.timeZone")).isEqualTo("Europe/Berlin");
            assertThat(response.<Map<String, Object>>read("$.parameters")).isEqualTo(Map.of("message", "hello"));
            assertThat(Instant.parse(response.read("$.createdAt"))).isBetween(Instant.now().minusSeconds(30), Instant.now());
            assertThat(response.<String>read("$.updatedAt")).isEqualTo(response.<String>read("$.createdAt"));
        }

        @Test
        void readsBackExactlyWhatTheCreateAnswerShowed() {
            ApiResponse created = post(SCHEDULES, logSchedule(oneTimeAt(IN_ONE_HOUR)));

            assertThat(get(SCHEDULES + "/" + created.<String>read("$.id")).body()).isEqualTo(created.body());
        }

        @Test
        void keepsANumberInputAsANumber() {
            UUID id = create(schedule("db-query", EVERY_15_MINUTES, """
                    {"limit": 5}"""));

            assertThat(get(SCHEDULES + "/" + id).<Object>read("$.parameters.limit")).isEqualTo(5);
        }

        @Test
        void acceptsATaskWithNoInputsGiven() {
            ApiResponse response = post(SCHEDULES, """
                    {"taskKey": "db-query", "timing": %s, "timeZone": "Europe/Berlin"}""".formatted(EVERY_15_MINUTES));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.<Map<String, Object>>read("$.parameters")).isEmpty();
        }

        @Test
        void dropsAnOptionalInputThatIsEmpty() {
            UUID id = create(schedule("email", EVERY_15_MINUTES, """
                    {"to": "ada@example.com", "subject": "Hi", "body": ""}"""));

            assertThat(get(SCHEDULES + "/" + id).<Map<String, Object>>read("$.parameters"))
                    .isEqualTo(Map.of("to", "ada@example.com", "subject", "Hi"));
        }

        @Test
        void treatsTheSameWeekdayTwiceAsOnceAndSortsTheDays() {
            UUID id = create(logSchedule("""
                    {"type": "WEEKLY", "days": ["FRIDAY", "MONDAY", "FRIDAY"], "time": "00:00"}"""));

            ApiResponse saved = get(SCHEDULES + "/" + id);
            assertThat(saved.<List<String>>read("$.timing.days")).containsExactly("MONDAY", "FRIDAY");
            assertThat(saved.<String>read("$.timing.time")).isEqualTo("00:00");
        }

        @Test
        void ignoresFieldsThatBelongToAnotherTimingKind() {
            UUID id = create(logSchedule("""
                    {"type": "CRON", "expression": "0 0 12 * * ?", "every": 15, "unit": "MINUTES", "days": ["MONDAY"]}"""));

            assertThat(get(SCHEDULES + "/" + id).<Map<String, Object>>read("$.timing"))
                    .isEqualTo(Map.of("type", "CRON", "expression", "0 0 12 * * ?"));
            assertThat(timingColumns(id))
                    .containsEntry("interval_value", null)
                    .containsEntry("interval_unit", null)
                    .containsEntry("weekly_days", null);
        }

        @Test
        void usesUtcWhenNoTimeZoneIsSent() throws SchedulerException {
            ApiResponse response = post(SCHEDULES, """
                    {"taskKey": "log", "timing": %s, "parameters": {"message": "hello"}}""".formatted(CRON_EVERY_5_MINUTES));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.<String>read("$.timeZone")).isEqualTo("UTC");
            assertThat(((CronTrigger) onlyAlarmOf(UUID.fromString(response.read("$.id")))).getTimeZone().getID())
                    .isEqualTo("UTC");
        }

        @Test
        void makesOneAlarmPerSchedule() throws SchedulerException {
            create(logSchedule(oneTimeAt(IN_ONE_HOUR)));
            create(logSchedule(EVERY_15_MINUTES));
            create(logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));
            create(logSchedule(CRON_EVERY_5_MINUTES));

            assertThat(totalJobs()).isEqualTo(4);
            assertThat(totalAlarms()).isEqualTo(4);
        }
    }

    @Nested
    class Reading {

        @Test
        void returnsOneSchedule() {
            UUID id = create(logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));

            ApiResponse response = get(SCHEDULES + "/" + id);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.<String>read("$.id")).isEqualTo(id.toString());
            assertThat(response.<String>read("$.taskKey")).isEqualTo("log");
            assertThat(response.<String>read("$.timing.type")).isEqualTo("WEEKLY");
        }
    }

    @Nested
    class Editing {

        @Test
        void changesOnlyTheInputs() throws SchedulerException {
            UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));

            ApiResponse response = put(SCHEDULES + "/" + id, schedule("log", CRON_EVERY_5_MINUTES, """
                    {"message": "a new message"}"""));

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.<String>read("$.id")).isEqualTo(id.toString());
            assertThat(get(SCHEDULES + "/" + id).<String>read("$.parameters.message")).isEqualTo("a new message");
            assertThat(((CronTrigger) onlyAlarmOf(id)).getCronExpression()).isEqualTo("0 0/5 * * * ?");
        }

        @Test
        void changesTheTimingWithinTheSameKind() throws SchedulerException {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            ApiResponse response = put(SCHEDULES + "/" + id, logSchedule("""
                    {"type": "INTERVAL", "every": 2, "unit": "HOURS"}"""));

            assertThat(response.status()).isEqualTo(200);
            assertThat(get(SCHEDULES + "/" + id).<Map<String, Object>>read("$.timing"))
                    .isEqualTo(Map.of("type", "INTERVAL", "every", 2, "unit", "HOURS"));
            assertThat(((SimpleTrigger) onlyAlarmOf(id)).getRepeatInterval()).isEqualTo(Duration.ofHours(2).toMillis());
        }

        @Test
        void changesToAnotherTimingKindAndEmptiesTheOldColumns() throws SchedulerException {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            ApiResponse response = put(SCHEDULES + "/" + id, logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));

            assertThat(response.status()).isEqualTo(200);
            assertThat(get(SCHEDULES + "/" + id).<String>read("$.timing.type")).isEqualTo("WEEKLY");
            assertThat(timingColumns(id))
                    .containsEntry("timing_type", "WEEKLY")
                    .containsEntry("interval_value", null)
                    .containsEntry("interval_unit", null)
                    .containsEntry("weekly_days", "MONDAY,WEDNESDAY");
            assertThat(((CronTrigger) onlyAlarmOf(id)).getCronExpression()).isEqualTo("0 30 8 ? * MON,WED");
        }

        @Test
        void changesToAnotherTaskAndChecksTheInputsAgainstTheNewTask() throws SchedulerException {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            ApiResponse rejected = put(SCHEDULES + "/" + id, schedule("email", EVERY_15_MINUTES, """
                    {"message": "hello"}"""));
            assertThat(rejected.status()).isEqualTo(400);
            assertThat(rejected.errorFields())
                    .containsExactlyInAnyOrder("parameters.to", "parameters.subject", "parameters.message");

            ApiResponse accepted = put(SCHEDULES + "/" + id, schedule("email", EVERY_15_MINUTES, """
                    {"to": "ada@example.com", "subject": "Hi"}"""));
            assertThat(accepted.status()).isEqualTo(200);
            assertThat(get(SCHEDULES + "/" + id).<String>read("$.taskKey")).isEqualTo("email");
            assertThat(alarmsOf(id)).hasSize(1);
        }

        @Test
        void changesTheTimeZoneOfTheAlarm() throws SchedulerException {
            UUID id = create(logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));

            put(SCHEDULES + "/" + id, logSchedule(MONDAY_AND_WEDNESDAY_AT_0830).replace("Europe/Berlin", "Asia/Tokyo"));

            assertThat(((CronTrigger) onlyAlarmOf(id)).getTimeZone().getID()).isEqualTo("Asia/Tokyo");
        }

        @Test
        void updatesTheChangeTimeButNotTheCreationTime() throws InterruptedException {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            String createdAt = get(SCHEDULES + "/" + id).read("$.createdAt");
            Thread.sleep(20);

            put(SCHEDULES + "/" + id, logSchedule(CRON_EVERY_5_MINUTES));

            ApiResponse saved = get(SCHEDULES + "/" + id);
            assertThat(saved.<String>read("$.createdAt")).isEqualTo(createdAt);
            assertThat(Instant.parse(saved.read("$.updatedAt"))).isAfter(Instant.parse(createdAt));
        }

        @Test
        void neverLeavesMoreThanOneAlarmHoweverOftenItIsEdited() throws SchedulerException {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            put(SCHEDULES + "/" + id, logSchedule(CRON_EVERY_5_MINUTES));
            put(SCHEDULES + "/" + id, logSchedule(oneTimeAt(IN_ONE_HOUR)));
            put(SCHEDULES + "/" + id, logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));

            assertThat(alarmsOf(id)).hasSize(1);
            assertThat(totalJobs()).isEqualTo(1);
            assertThat(totalAlarms()).isEqualTo(1);
        }

        @Test
        void leavesTheOldScheduleAndAlarmUntouchedWhenTheEditIsRejected() throws SchedulerException {
            UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));
            String before = get(SCHEDULES + "/" + id).body();

            ApiResponse response = put(SCHEDULES + "/" + id, logSchedule("""
                    {"type": "CRON", "expression": "this is not cron"}"""));

            assertThat(response.status()).isEqualTo(400);
            assertThat(get(SCHEDULES + "/" + id).body()).isEqualTo(before);
            assertThat(((CronTrigger) onlyAlarmOf(id)).getCronExpression()).isEqualTo("0 0/5 * * * ?");
        }
    }

    @Nested
    class Deleting {

        @Test
        void removesTheRowAndTheAlarm() throws SchedulerException {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            ApiResponse response = delete(SCHEDULES + "/" + id);

            assertThat(response.status()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
            assertThat(get(SCHEDULES + "/" + id).status()).isEqualTo(404);
            assertThat(scheduleRepository.count()).isZero();
            assertThat(totalJobs()).isZero();
            assertThat(totalAlarms()).isZero();
        }

        @Test
        void leavesOtherSchedulesAlone() throws SchedulerException {
            UUID kept = create(logSchedule(EVERY_15_MINUTES));
            UUID removed = create(logSchedule(CRON_EVERY_5_MINUTES));

            delete(SCHEDULES + "/" + removed);

            assertThat(get(SCHEDULES).<List<String>>read("$[*].id")).containsExactly(kept.toString());
            assertThat(alarmsOf(kept)).hasSize(1);
        }
    }

    @Nested
    class RejectingBadInput {

        static Stream<Arguments> badRequests() {
            String past = oneTimeAt(Instant.now().minusSeconds(60));
            return Stream.of(
                    // task and its inputs
                    bad("unknown task", schedule("no-such-task", EVERY_15_MINUTES, "{}"), "taskKey"),
                    bad("no task", """
                            {"timing": %s, "parameters": {}}""".formatted(EVERY_15_MINUTES), "taskKey"),
                    bad("required input missing", schedule("log", EVERY_15_MINUTES, "{}"), "parameters.message"),
                    bad("required input is null", schedule("log", EVERY_15_MINUTES, """
                            {"message": null}"""), "parameters.message"),
                    bad("required input is only spaces", schedule("log", EVERY_15_MINUTES, """
                            {"message": "   "}"""), "parameters.message"),
                    bad("number where text is expected", schedule("log", EVERY_15_MINUTES, """
                            {"message": 5}"""), "parameters.message"),
                    bad("text where a number is expected", schedule("db-query", EVERY_15_MINUTES, """
                            {"limit": "ten"}"""), "parameters.limit"),
                    bad("input the task does not know", schedule("log", EVERY_15_MINUTES, """
                            {"message": "hello", "colour": "red"}"""), "parameters.colour"),
                    // one time
                    bad("one-time in the past", logSchedule(past), "timing.runAt"),
                    bad("one-time without a date", logSchedule("""
                            {"type": "ONE_TIME"}"""), "timing.runAt"),
                    bad("one-time with a broken date", logSchedule("""
                            {"type": "ONE_TIME", "runAt": "next tuesday"}"""), "timing.runAt"),
                    // every X
                    bad("every 0", logSchedule("""
                            {"type": "INTERVAL", "every": 0, "unit": "MINUTES"}"""), "timing.every"),
                    bad("every -5", logSchedule("""
                            {"type": "INTERVAL", "every": -5, "unit": "MINUTES"}"""), "timing.every"),
                    bad("every 1.5", logSchedule("""
                            {"type": "INTERVAL", "every": 1.5, "unit": "HOURS"}"""), "timing.every"),
                    bad("every 'often'", logSchedule("""
                            {"type": "INTERVAL", "every": "often", "unit": "HOURS"}"""), "timing.every"),
                    bad("every without a number", logSchedule("""
                            {"type": "INTERVAL", "unit": "HOURS"}"""), "timing.every"),
                    bad("unknown unit", logSchedule("""
                            {"type": "INTERVAL", "every": 5, "unit": "FORTNIGHTS"}"""), "timing.unit"),
                    bad("no unit", logSchedule("""
                            {"type": "INTERVAL", "every": 5}"""), "timing.unit"),
                    // weekly
                    bad("weekly with no days", logSchedule("""
                            {"type": "WEEKLY", "days": [], "time": "08:30"}"""), "timing.days"),
                    bad("weekly without days", logSchedule("""
                            {"type": "WEEKLY", "time": "08:30"}"""), "timing.days"),
                    bad("weekly with an unknown day", logSchedule("""
                            {"type": "WEEKLY", "days": ["FUNDAY"], "time": "08:30"}"""), "timing.days"),
                    bad("weekly at 25:00", logSchedule("""
                            {"type": "WEEKLY", "days": ["MONDAY"], "time": "25:00"}"""), "timing.time"),
                    bad("weekly without a time", logSchedule("""
                            {"type": "WEEKLY", "days": ["MONDAY"]}"""), "timing.time"),
                    // cron
                    bad("cron that is not cron", logSchedule("""
                            {"type": "CRON", "expression": "every now and then"}"""), "timing.expression"),
                    bad("5-part Unix cron", logSchedule("""
                            {"type": "CRON", "expression": "*/5 * * * *"}"""), "timing.expression"),
                    bad("cron that can never fire", logSchedule("""
                            {"type": "CRON", "expression": "0 0 0 1 1 ? 2020"}"""), "timing.expression"),
                    bad("empty cron", logSchedule("""
                            {"type": "CRON", "expression": ""}"""), "timing.expression"),
                    // timing in general
                    bad("unknown timing kind", logSchedule("""
                            {"type": "WHENEVER"}"""), "timing"),
                    bad("timing without a kind", logSchedule("""
                            {"every": 5, "unit": "MINUTES"}"""), "timing"),
                    bad("no timing", """
                            {"taskKey": "log", "parameters": {"message": "hello"}}""", "timing"),
                    // time zone
                    bad("unknown time zone", logSchedule(EVERY_15_MINUTES).replace("Europe/Berlin", "Mars/Olympus_Mons"),
                            "timeZone"));
        }

        private static Arguments bad(String what, String json, String expectedField) {
            return Arguments.of(what, json, expectedField);
        }

        @ParameterizedTest(name = "{0} -> error on {2}")
        @MethodSource("badRequests")
        void onCreate(String what, String json, String expectedField) throws SchedulerException {
            ApiResponse response = post(SCHEDULES, json);

            assertThat(response.status()).as(response.body()).isEqualTo(400);
            assertThat(response.contentType()).startsWith("application/problem+json");
            assertThat(response.<String>read("$.title")).isEqualTo("Validation failed");
            assertThat(response.errorFields()).containsExactly(expectedField);
            assertThat(response.<List<String>>read("$.errors[*].message")).allSatisfy(m -> assertThat(m).isNotBlank());

            assertThat(scheduleRepository.count()).as("nothing is saved").isZero();
            assertThat(totalJobs()).as("no alarm is set").isZero();
        }

        @ParameterizedTest(name = "{0} -> error on {2}")
        @MethodSource("badRequests")
        void onEdit(String what, String json, String expectedField) {
            UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));
            String before = get(SCHEDULES + "/" + id).body();

            ApiResponse response = put(SCHEDULES + "/" + id, json);

            assertThat(response.status()).as(response.body()).isEqualTo(400);
            assertThat(response.errorFields()).containsExactly(expectedField);
            assertThat(get(SCHEDULES + "/" + id).body()).as("the schedule is unchanged").isEqualTo(before);
        }

        @Test
        void explainsTheQuartzFormatForAUnixCron() {
            ApiResponse response = post(SCHEDULES, logSchedule("""
                    {"type": "CRON", "expression": "*/5 * * * *"}"""));

            assertThat(response.<List<String>>read("$.errors[*].message")).singleElement().asString()
                    .contains("Unix cron", "6 or 7 parts", "starts with seconds");
        }

        @Test
        void reportsSeveralProblemsInOneAnswer() {
            ApiResponse response = post(SCHEDULES, schedule("log", """
                    {"type": "INTERVAL", "every": 0, "unit": "MINUTES"}""", "{}"));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.errorFields()).containsExactlyInAnyOrder("parameters.message", "timing.every");
        }

        @ParameterizedTest
        @MethodSource("brokenBodies")
        void rejectsABodyThatIsNotJson(String body) throws SchedulerException {
            ApiResponse response = post(SCHEDULES, body);

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.contentType()).startsWith("application/problem+json");
            assertThat(response.<String>read("$.title")).isEqualTo("Malformed request");
            assertThat(totalJobs()).isZero();
        }

        static Stream<String> brokenBodies() {
            return Stream.of("{\"taskKey\": \"log\",", "not json at all", "");
        }
    }

    @Nested
    class UnknownIds {

        private final String unknown = SCHEDULES + "/" + UUID.randomUUID();

        @Test
        void readingGivesNotFound() {
            assertNotFound(get(unknown));
        }

        @Test
        void editingGivesNotFound() {
            assertNotFound(put(unknown, logSchedule(EVERY_15_MINUTES)));
        }

        @Test
        void deletingGivesNotFound() {
            assertNotFound(delete(unknown));
        }

        @Test
        void deletingTwiceGivesNotFoundTheSecondTime() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            assertThat(delete(SCHEDULES + "/" + id).status()).isEqualTo(204);
            assertNotFound(delete(SCHEDULES + "/" + id));
        }

        @Test
        void anIdThatIsNotAnIdIsABadRequest() {
            ApiResponse response = get(SCHEDULES + "/not-a-uuid");

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.contentType()).startsWith("application/problem+json");
        }

        private void assertNotFound(ApiResponse response) {
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.contentType()).startsWith("application/problem+json");
            assertThat(response.<String>read("$.title")).isEqualTo("Not found");
            assertThat(response.<Integer>read("$.status")).isEqualTo(404);
            assertThat(response.<String>read("$.detail")).startsWith("No schedule with id ");
        }
    }

    @Nested
    class ErrorShape {

        @Test
        void validationErrorsFollowTheProblemDetailsStandard() {
            ApiResponse response = post(SCHEDULES, schedule("log", EVERY_15_MINUTES, "{}"));

            assertThat(response.<Map<String, Object>>read("$"))
                    .containsEntry("title", "Validation failed")
                    .containsEntry("status", 400)
                    .containsEntry("detail", "One or more fields are not valid.")
                    .containsEntry("instance", SCHEDULES)
                    .containsEntry("errors", List.of(Map.of("field", "parameters.message", "message", "Message is required")));
        }
    }

    private Trigger onlyAlarmOf(UUID id) throws SchedulerException {
        List<? extends Trigger> alarms = alarmsOf(id);
        assertThat(alarms).as("exactly one alarm for schedule %s", id).hasSize(1);
        return alarms.getFirst();
    }

    private Map<String, Object> timingColumns(UUID id) {
        return jdbcClient
                .sql("SELECT timing_type, interval_value, interval_unit, weekly_days, cron_expression FROM schedules WHERE id = :id")
                .param("id", id)
                .query()
                .singleRow();
    }
}
