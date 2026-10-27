package com.akashic.containerclickguard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class Log {
    private static final Logger LOGGER = LogManager.getLogger("AkashicContainerClickGuard");

    private Log() {
    }

    static void info(String message) {
        LOGGER.info(message);
    }

    static void warn(String message) {
        LOGGER.warn(message);
    }

    static void error(String message, Throwable t) {
        LOGGER.error(message, t);
    }
}
