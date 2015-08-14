import ch.qos.logback.classic.Level

def ROOT_LOG_LEVEL_PROPERTY = System.getProperty("SATFC.root.log.level")
def ROOT_LOG_LEVEL = ROOT_LOG_LEVEL_PROPERTY == null ? Level.INFO : Level.valueOf(ROOT_LOG_LEVEL_PROPERTY)
println "Setting root log level to $ROOT_LOG_LEVEL"

appender("FILE", FileAppender) {
    file = "SATFC.log"
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{0} - %msg%n"
    }
}

appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{0} - %msg%n"
    }
}

root(ROOT_LOG_LEVEL, ["CONSOLE", "FILE"])