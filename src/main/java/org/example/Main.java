package org.example;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Package e.g. with:
 * mvn clean package
 *
 * Then check that custom appender works (run the app and see that the generated log file contains some logs):
 * java -cp target/log4j-test-1.0-SNAPSHOT.jar org.example.Main
 *
 * Note that the hacks in the pom.xml file are not needed if you just use separate jars:
 * java -cp "target/original-log4j-test-1.0-SNAPSHOT.jar:$HOME/.m2/repository/org/apache/logging/log4j/log4j-core/2.17.2/log4j-core-2.17.2.jar:$HOME/.m2/repository/org/apache/logging/log4j/log4j-api/2.17.2/log4j-api-2.17.2.jar" org.example.Main
 *
 * Note: you can run `java -Dlog4j.debug=true -cp ...` for more info.
 */
public class Main {

    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Hello world!");

        var logId = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        log.info("We will generate a log file named {}.log", logId);
        var logHandler = new ThreadContextFileLogHandler(".", logId);

        log.info("This log should NOT be included");
        logHandler.runWithThreadContext(() -> {
            // These are the logs that should be included in the log file
            log.info("OK - This log should be INCLUDED");
            log.info("OK - This log also should be INCLUDED");
        });
        log.info("Also, this log should NOT be included either");
    }
}
