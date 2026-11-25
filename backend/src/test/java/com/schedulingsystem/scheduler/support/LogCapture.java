package com.schedulingsystem.scheduler.support;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.slf4j.LoggerFactory;

/** Collects what one class logs, so a test can check it. Use in try-with-resources. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> loggingClass) {
        this.logger = (Logger) LoggerFactory.getLogger(loggingClass);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> loggingClass) {
        return new LogCapture(loggingClass);
    }

    /** Every message logged so far, as "LEVEL text". */
    public List<String> lines() {
        return List.copyOf(appender.list).stream()
                .map(event -> event.getLevel() + " " + event.getFormattedMessage())
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
