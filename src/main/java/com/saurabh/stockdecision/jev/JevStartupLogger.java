package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.config.TypeSafeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * States at startup whether decisions will be AI-evaluated or use the basic calculation only.
 */
@Component
class JevStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(JevStartupLogger.class);

    private final TypeSafeProperties props;

    JevStartupLogger(TypeSafeProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logJevStatus() {
        if (props.enabled()) {
            log.info("Jev enabled: url={}{}, model={}, timeout={}",
                    props.baseUrl(), JevConfig.SYSTEM_ONE_PATH, props.model(), props.timeout());
        } else {
            log.warn("Jev disabled: TYPESAFE_API_KEY is not set. Decisions use the basic calculation only.");
        }
    }
}
