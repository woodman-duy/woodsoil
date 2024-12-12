package com.plantssoil.webhook.core.impl;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.plantssoil.common.config.ConfigFactory;
import com.plantssoil.common.config.IConfiguration;
import com.plantssoil.common.config.LettuceConfiguration;
import com.plantssoil.common.httpclient.IHttpPoster;
import com.plantssoil.common.httpclient.IHttpResponse;
import com.plantssoil.webhook.core.IDataGroup;
import com.plantssoil.webhook.core.IEngineFactory;
import com.plantssoil.webhook.core.IEvent;
import com.plantssoil.webhook.core.IWebhook;
import com.plantssoil.webhook.core.IWebhookPoster;
import com.plantssoil.webhook.core.Message;
import com.plantssoil.webhook.core.exception.EngineException;
import com.plantssoil.webhook.core.logging.WebhookLoggingHandler;

/**
 * The webhook url poster, which with retry logics:
 * <ul>
 * <li>The first call webhook url: success(HttpResponse status code is 20x) -
 * end the process, fail - go to the retry process in next step</li>
 * <li>The first retry webhook url 5 seconds later: success(HttpResponse status
 * code is 20x) - end the process, fail - go to the next retry process in next
 * step</li>
 * <li>The second retry webhook url 30 seconds later: end the process whatever
 * success(HttpResponse status code is 20x) or fail</li>
 * </ul>
 * 
 * @author danialdy
 * @Date 27 Nov 2024 11:24:20 am
 */
public class WebhookPoster implements IWebhookPoster {
    private final static int PAGE_SIZE = 50;
    private static volatile IWebhookPoster instance;
    private int corePoolSize, maximumPoolSize, workQueueCapacity, retryQueueCapacity5, retryQueueCapacity30;
    private ExecutorService executor;
    private ScheduledExecutorService retryScheduler; // scheduler every 5 seconds
    private RetryWebhookQueue retryWebhooks5; // retry webhook queue 5 seconds delay
    private RetryWebhookQueue retryWebhooks30; // retry webhook queue 30 seconds delay
    private ThreadFactory webhookExecutorThreadFactory = new NamedThreadFactory("Webhook-Poster"); // create the ThreadFactory to name threads for Main Executor
    private ThreadFactory retrySchedulerThreadFactory = new NamedThreadFactory("Retry-Scheduler"); // create the ThreadFactory to name threads for Retry
                                                                                                   // Scheduler

    class NamedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String factoryName) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = factoryName + "-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    private WebhookPoster() {
        // initial configuration
        initialConfiguration();
        // initial thread pool for webhook poster
        initialExecutor();
        // initial retry queues (5 secs & 30 secs)
        initialRetryQueues();
        // initial retry scheduler 5 seconds delay, don't use ScheduledExecutorService
        // because ScheduledExecutorService will start threads when the schedule start.
        // 5 seconds & 30 seconds retry queue will be checked every 5 seconds
        initialRetryScheduler();
    }

    private void initialConfiguration() {
        IConfiguration configuration = ConfigFactory.getInstance().getConfiguration();
        this.corePoolSize = configuration.getInt(LettuceConfiguration.WEBHOOK_ENGINE_CORE_POOL_SIZE, 100);
        this.maximumPoolSize = configuration.getInt(LettuceConfiguration.WEBHOOK_ENGINE_MAXIMUM_POOL_SIZE, 200);
        this.workQueueCapacity = configuration.getInt(LettuceConfiguration.WEBHOOK_ENGINE_WORK_QUEUE_CAPACITY, Integer.MAX_VALUE);
        this.retryQueueCapacity5 = configuration.getInt(LettuceConfiguration.WEBHOOK_ENGINE_RETRY_QUEUE_CAPACITY5, Integer.MAX_VALUE);
        this.retryQueueCapacity30 = configuration.getInt(LettuceConfiguration.WEBHOOK_ENGINE_RETRY_QUEUE_CAPACITY30, Integer.MAX_VALUE);
    }

    private void initialExecutor() {
        this.executor = new ThreadPoolExecutor(this.corePoolSize, this.maximumPoolSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(this.workQueueCapacity),
                this.webhookExecutorThreadFactory);
    }

    private void initialRetryQueues() {
        this.retryWebhooks5 = new RetryWebhookQueue(this.retryQueueCapacity5);
        this.retryWebhooks30 = new RetryWebhookQueue(this.retryQueueCapacity30);
    }

    private void initialRetryScheduler() {
        this.retryScheduler = Executors.newSingleThreadScheduledExecutor(this.retrySchedulerThreadFactory);
        this.retryScheduler.scheduleAtFixedRate(() -> {
            retryWebhook5();
            retryWebhook30();
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void retryWebhook5() {
        List<RetryWebhookTask> list = this.retryWebhooks5.webhookTasksTimeUp();
        for (RetryWebhookTask task : list) {
            try {
                this.executor.submit(() -> {
                    try {
                        IHttpResponse response = post(task.getMessage(), task.getWebhook());
                        // the response code should be 20x, indicates call webhook url successfully
                        // otherwise, put the message into retry queue, which will retry 30 seconds
                        // later
                        if (!(response.getStatusCode() >= 200 && response.getStatusCode() < 210)) {
                            this.retryWebhooks30.add(task.getMessage(), task.getWebhook(), System.currentTimeMillis() + 30 * 1000);
                        }
                    } catch (Exception e) {
                        // retry after 30 seconds if exception happens
                        this.retryWebhooks30.add(task.getMessage(), task.getWebhook(), System.currentTimeMillis() + 30 * 1000);
                    }
                });
            } catch (Exception e) {
                // retry after 30 seconds if exception happens
                this.retryWebhooks30.add(task.getMessage(), task.getWebhook(), System.currentTimeMillis() + 30 * 1000);
            }
        }
    }

    private void retryWebhook30() {
        List<RetryWebhookTask> list = this.retryWebhooks30.webhookTasksTimeUp();
        for (RetryWebhookTask task : list) {
            try {
                this.executor.submit(() -> {
                    // this is the last try, just post webhook url
                    // no more further process, no matter success or not
                    try {
                        post(task.getMessage(), task.getWebhook());
                    } catch (Exception e) {
                        // discard the exception and the failed task, no need retry any more
                    }
                });
            } catch (Exception e) {
                // discard the exception and the failed task, no need retry any more
            }
        }
    }

    /**
     * Get the webhook poster singleton instance
     * 
     * @return webhook poster singleton instance
     */
    public static IWebhookPoster getInstance() {
        if (instance == null) {
            synchronized (WebhookPoster.class) {
                if (instance == null) {
                    // create poster instance (use proxy to AOP logging)
                    WebhookPoster poster = new WebhookPoster();
                    instance = (IWebhookPoster) WebhookLoggingHandler.createProxy(poster);
                }
            }
        }
        return instance;
    }

    @Override
    public void postWebhook(final Message message, final IWebhook webhook) {
        // if event is not subscribed, not need post webhook
        if (!eventSubscried(message, webhook)) {
            return;
        }

        try {
            this.executor.submit(() -> {
                postWebhook1(message, webhook);
            });
        } catch (Exception ex) {
            // return the message back to queue, if exception happens when thread pool full
            // and queue full
            IEngineFactory.getFactoryInstance().getEngine().trigger(message);
        }
    }

    private boolean eventSubscried(Message message, IWebhook webhook) {
        boolean subscribed = false;
        int page = 0;

        List<IEvent> events = webhook.findSubscribedEvents(page, PAGE_SIZE);
        while (events != null && events.size() > 0) {
            for (IEvent event : events) {
                if (event.getEventType().equals(message.getEventType())) {
                    subscribed = true;
                    break;
                }
            }
            if (events.size() < PAGE_SIZE) {
                break;
            }
            page++;
            events = webhook.findSubscribedEvents(page, PAGE_SIZE);
        }
        return subscribed;
    }

    private void postWebhook1(Message message, IWebhook webhook) {
        try {
            IHttpResponse response = post(message, webhook);
            // the response code should be 20x, indicates call webhook url successfully
            // otherwise, put the message into retry queue, which will retry 5 seconds later
            if (!(response.getStatusCode() >= 200 && response.getStatusCode() < 210)) {
                this.retryWebhooks5.add(message, webhook, System.currentTimeMillis() + 5 * 1000);
            }
        } catch (Exception e) {
            this.retryWebhooks5.add(message, webhook, System.currentTimeMillis() + 5 * 1000);
        }
    }

    private IHttpResponse post(Message message, IWebhook webhook) {
        IHttpPoster poster = IHttpPoster
                .createInstance(com.plantssoil.common.httpclient.IHttpPoster.SecurityStrategy.valueOf(webhook.getSecurityStrategy().name()));
        if (message.getDataGroup() != null) {
            // get access token from data group (if supported)
            IDataGroup dataGroup = webhook.findSubscribedDataGroup(message.getDataGroup());
            if (dataGroup != null) {
                poster.setAccessToken(dataGroup.getAccessToken());
            } else {
                throw new EngineException(EngineException.BUSINESS_EXCEPTION_CODE_20003, String.format(
                        "Webhook (webhookId: %s, url: %s) does not subscribe event (%s) from publisher (publisherId: %s, version: %s, dataGroup: %s), or data group (%s) does not exist!",
                        webhook.getWebhookId(), webhook.getWebhookUrl(), message.getEventType(), webhook.getPublisherId(), webhook.getPublisherVersion(),
                        message.getDataGroup(), message.getDataGroup()));
            }
        } else {
            // get access token from webhook
            poster.setAccessToken(webhook.getAccessToken());
        }
        poster.setCharset(message.getCharset());
        poster.setMediaType(message.getContentType());
        IHttpResponse r = poster.post(webhook.getWebhookUrl(), webhook.getCustomizedHeaders(), message.getRequestId(), message.getPayload());
        return r;
    }
}