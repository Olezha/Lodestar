package com.olehshklyar.lodestar.client;

import com.olehshklyar.lodestar.client.live.LiveAlertSourceClient;
import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Primary delegating client that dynamically routes calls to LiveAlertSourceClient
 * or SyntheticAlertSourceClient based on runtime configuration.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class DelegatingAlertSourceClient implements AlertSourceClient {

    private final AlertSourceProperties properties;
    private final SyntheticAlertSourceClient syntheticClient;
    private final LiveAlertSourceClient liveClient;

    @Override
    public List<AlertEvent> fetchLatestEvents() {
        if (properties.isLive()) {
            if (properties.alertsInUaToken() == null || properties.alertsInUaToken().isBlank()) {
                log.warn("ALERT_SOURCE_TYPE=live, but ALERTS_IN_UA_TOKEN is empty. Falling back to synthetic generator.");
                return syntheticClient.fetchLatestEvents();
            }
            log.debug("Delegating alert polling to LiveAlertSourceClient");
            return liveClient.fetchLatestEvents();
        }

        log.debug("Delegating alert polling to SyntheticAlertSourceClient");
        return syntheticClient.fetchLatestEvents();
    }
}
