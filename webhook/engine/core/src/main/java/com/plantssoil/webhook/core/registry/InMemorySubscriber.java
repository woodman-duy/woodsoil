package com.plantssoil.webhook.core.registry;

import java.util.ArrayList;
import java.util.List;

import com.plantssoil.webhook.core.ISubscriber;
import com.plantssoil.webhook.core.IWebhook;

/**
 * The in-memory implementation of ISubscriber<br/>
 * All data will be lost when JVM shutdown<br/>
 * It's only for demonstration purpose, SHOULD AVOID be used in production
 * environment<br/>
 * 
 * @author danialdy
 * @Date 2 Jan 2025 5:09:49 pm
 */
public class InMemorySubscriber implements ISubscriber {
    private String subscriberId;
    private List<IWebhook> webhooks = new ArrayList<>();

    @Override
    public void setSubscriberId(String subscriberId) {
        this.subscriberId = subscriberId;
    }

    @Override
    public void addWebhook(IWebhook webhook) {
        this.webhooks.add(webhook);
    }

    @Override
    public void updateWebhook(IWebhook webhook) {
        int index = 0;
        boolean found = false;
        for (IWebhook w : this.webhooks) {
            if (webhook.getWebhookId().equals(w.getWebhookId())) {
                found = true;
            }
            index++;
        }
        if (found) {
            this.webhooks.remove(index);
            this.webhooks.add(webhook);
        }
    }

    @Override
    public void removeWebhook(IWebhook webhook) {
        int index = 0;
        boolean found = false;
        for (IWebhook w : this.webhooks) {
            if (webhook.getWebhookId().equals(w.getWebhookId())) {
                found = true;
            }
            index++;
        }
        if (found) {
            this.webhooks.remove(index);
        }
    }

    @Override
    public String getSubscriberId() {
        return this.subscriberId;
    }

    @Override
    public List<IWebhook> findWebhooks(int page, int pageSize) {
        List<IWebhook> list = new ArrayList<>();
        int beginIndex = page * pageSize;
        int endIndex = beginIndex + pageSize;

        for (int i = beginIndex; i < this.webhooks.size() && i < endIndex; i++) {
            list.add(this.webhooks.get(i));
        }
        return list;
    }

}
