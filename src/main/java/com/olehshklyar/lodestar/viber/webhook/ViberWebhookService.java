package com.olehshklyar.lodestar.viber.webhook;

import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackEvent;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackResponse;

public interface ViberWebhookService {

    ViberCallbackResponse processEvent(ViberCallbackEvent event);
}
