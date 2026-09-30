package net.swofty.commons.redis;

import net.swofty.redisapi.api.RedisAPI;
import org.tinylog.Logger;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class RedisWatchdog {
    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(3);

    private static ScheduledExecutorService scheduler;
    private static RedisAPI watchedApi;

    private RedisWatchdog() {
    }

    public static synchronized void start(String componentOwner) {
        RedisAPI api = RedisAPI.getInstance();
        if (api == null) {
            Logger.warn("AtlasRedisAPI watchdog not started for {}: RedisAPI instance is unavailable", componentOwner);
            return;
        }

        if (scheduler != null && !scheduler.isShutdown() && watchedApi == api) {
            return;
        }

        stop();
        watchedApi = api;
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "atlas-redis-watchdog-" + componentOwner);
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                () -> check(api, componentOwner),
                CHECK_INTERVAL.toSeconds(),
                CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );
        Logger.info("AtlasRedisAPI Pub/Sub watchdog started for {}", componentOwner);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        scheduler = null;
        watchedApi = null;
    }

    private static void check(RedisAPI api, String componentOwner) {
        if (RedisAPI.getInstance() != api) {
            return;
        }

        Thread subscriberThread = api.getSubscriberThread();
        if (subscriberThread != null && subscriberThread.isAlive()) {
            return;
        }

        try {
            Logger.warn(
                    "AtlasRedisAPI subscriber thread is not alive for {}; restarting listeners",
                    componentOwner
            );
            api.startListeners();

            Thread restartedThread = api.getSubscriberThread();
            if (restartedThread == null || !restartedThread.isAlive()) {
                Logger.warn("AtlasRedisAPI listener restart for {} did not create a live subscriber thread", componentOwner);
            } else {
                Logger.info("AtlasRedisAPI subscriber listeners restarted for {}", componentOwner);
            }
        } catch (Throwable throwable) {
            Logger.error(throwable, "AtlasRedisAPI listener restart failed for {}", componentOwner);
        }
    }
}
