package org.example;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.logging.log4j.CloseableThreadContext;

/**
 * Handles logs depending on the thread context.
 * Run code using {@link #runWithThreadContext}, so the logs written there are handled.
 *
 * Threads do not inherit ThreadContext by default (https://stackoverflow.com/a/70717962/1121497)
 * (it's better not to inherit, since threads may be reused).
 * So, if you run threads, you should pass the thread context to them, for example using
 * {@link CloseableThreadContext#putAll(Map)}.
 * Otherwise, the logs written inside the threads will not be handled.
 * You may use the convenience methods {@link #runWith} for that.
 * Make sure you get the threadContext from the parent thread, not from the child thread.
 */
public interface ThreadContextLogHandler {

    void runWithThreadContext(Runnable runnable);

    <T> T runWithThreadContext(Supplier<T> supplier);

    static void runWith(Map<String, String> threadContext, Runnable runnable) {
        try (var ignore = CloseableThreadContext.putAll(threadContext)) {
            runnable.run();
        }
    }

    static <T> T runWith(Map<String, String> threadContext, Supplier<T> supplier) {
        try (var ignore = CloseableThreadContext.putAll(threadContext)) {
            return supplier.get();
        }
    }
}
