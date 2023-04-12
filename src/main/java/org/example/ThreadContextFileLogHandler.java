package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.ThreadContextMapFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Handles logs using an {@link Appender} that writes to a file in the given workingDir.
 *
 * The appender will only handle logs if {@link org.apache.logging.log4j.ThreadContext}
 * contains {@link #LOG_THREAD_CONTEXT_KEY}=fileId. You don't need to manage this, but just
 * keep in mind that you should run your code with {@link #runWithThreadContext}.
 * The code run there will capture logs into the file.
 *
 * Use a unique fileId if you may have multiple files in parallel.
 */
public class ThreadContextFileLogHandler implements ThreadContextLogHandler {

    private static final Logger log = LogManager.getLogger(ThreadContextFileLogHandler.class);

    public static final String LOG_THREAD_CONTEXT_KEY = "file-log-id"; // we might parametrize this
    public static final String END_OF_LOG = "END OF LOG";

    private final Appender appender;
    private final String filename;
    private final String fileId;

    public ThreadContextFileLogHandler(String workingDir, String fileId) {
        this.fileId = fileId;
        this.filename = workingDir + "/" + fileId + ".log";
        this.appender = createAppender();
        this.appender.start();
        addAppender();
    }

    public String getFileName() {
        return filename;
    }

    @Override
    public <T> T runWithThreadContext(Supplier<T> supplier) {
        try (var ignore = CloseableThreadContext.put(LOG_THREAD_CONTEXT_KEY, fileId)) {
            var result = supplier.get();
            log.info("{} for logger {}", END_OF_LOG, fileId); // special log to signal the end of logs for this appender
            return result;
        }
    }

    @Override
    public void runWithThreadContext(Runnable runnable) {
        runWithThreadContext(() -> {
            runnable.run();
            return null; // dummy result
        });
    }

    public void stop() {
        flushAppender();
        removeAppender();
        appender.stop();
    }

    private void addAppender() {
        // https://logging.apache.org/log4j/2.x/manual/customconfig.html
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();
    }


    private void removeAppender() {
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        config.getRootLogger().removeAppender(appender.getName());
        context.updateLoggers();
    }

    /**
     * We tried flushing a FileAppender, but for some reason this hack stopped working:
     * https://stackoverflow.com/a/71081005/1121497
     */
    protected void flushAppender() {
        var myAppender = (CustomFileAppender) appender;
        synchronized (appender) {
            log.info("Check if appender {} finished", fileId);
            if (!myAppender.isFinished()) {
                log.info("Appender {} not finished yet, so wait", fileId);
                try {
                    myAppender.wait(20 * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (!myAppender.isFinished()) {
                    throw new IllegalStateException("The appender " + fileId + " takes too much to finish");
                }
                log.info("Appender {} finished", fileId);
            }
        }
    }

    private Appender createAppender() {
        log.info("Creating custom appender that will write logs to {}", filename);
        return new CustomFileAppender(fileId, filename, new ThreadContextMapFilter(
                Map.of(LOG_THREAD_CONTEXT_KEY, List.of(fileId)),
                // the following arguments are not used if there's only one key/value
                true, null, null), createLayout());
    }

    /**
     * This is a simple file appender.
     * To detect when the appender has finished and can be removed, the appender expects a final
     * special {@link LogEvent} containing {@link #END_OF_LOG}. When received, it sets {@link #isFinished()} and calls
     * {@link Object#notifyAll()}), so whoever interested in this flag can {@link Object#wait()} for it.
     */
    private static class CustomFileAppender extends AbstractAppender {

        private final String filename;
        private final Filter filter;
        private final FileWriter writer;
        private boolean finished;

        public boolean isFinished() {
            return finished;
        }

        protected CustomFileAppender(String name, String filename, Filter filter, Layout<? extends Serializable> layout) {
            super(name, null, layout, false, null);
            this.filename = filename;
            this.filter = filter;
            try {
                this.writer = new FileWriter(filename);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void append(LogEvent event) {
            var formattedMessage = event.getMessage().getFormattedMessage();
            if (filter.filter(event) != Filter.Result.DENY) {
                try {
                    if (formattedMessage.startsWith(END_OF_LOG)) {
                        // this special event should be the last one, and it's not written in the file
                        writer.close();
                        synchronized (this) {
                            finished = true;
                            this.notifyAll();
                        }
                        return;
                    }
                    var formattedEvent = getLayout().toSerializable(event).toString();
                    writer.append(formattedEvent);
                } catch (IOException e) {
                    throw new RuntimeException("Error writing to file " + filename, e);
                }
            } else {
                // TODO To detect if appender is actually receiving events
                System.out.println("Ignored log: " + formattedMessage);
            }
        }
    }

    /** Override if you want to use a different {@link Layout} */
    protected Layout<String> createLayout() {
        return PatternLayout.newBuilder()
                .withPattern("%d{ISO8601_OFFSET_DATE_TIME_HHCMM} [%t] %-5level %c{1.} %X{rid} %msg%n%throwable")
                .build();
    }
}
