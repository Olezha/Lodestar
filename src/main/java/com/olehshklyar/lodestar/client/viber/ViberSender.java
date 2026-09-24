package com.olehshklyar.lodestar.client.viber;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ViberSender(
        @JsonProperty("name") String name
) {}
