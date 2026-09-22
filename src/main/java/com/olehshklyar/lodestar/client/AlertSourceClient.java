package com.olehshklyar.lodestar.client;

import com.olehshklyar.lodestar.dto.AlertEvent;

import java.util.List;

public interface AlertSourceClient {
    List<AlertEvent> fetchLatestEvents();
}
