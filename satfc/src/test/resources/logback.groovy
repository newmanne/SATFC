appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{0} - %msg%n"
    }
}

logger("ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt", TRACE)
root(INFO, ["CONSOLE"])