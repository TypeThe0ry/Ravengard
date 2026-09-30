package net.swofty.type.generic.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackManagerTest {
    @Test
    void completedPackDoesNotInvokeTimeoutFallback() {
        var fallback = new AtomicInteger();

        assertTrue(ResourcePackManager.awaitResolution(
                CompletableFuture.completedFuture(null), 1, TimeUnit.SECONDS,
                fallback::incrementAndGet));
        assertEquals(0, fallback.get());
    }

    @Test
    void timeoutInvokesFallbackExactlyOnce() {
        var fallback = new AtomicInteger();

        assertFalse(ResourcePackManager.awaitResolution(
                new CompletableFuture<>(), 1, TimeUnit.MILLISECONDS,
                fallback::incrementAndGet));
        assertEquals(1, fallback.get());
    }

    @Test
    void timeoutFallbackCanCompleteThePendingPackFuture() {
        var pendingPack = new CompletableFuture<Void>();

        assertFalse(ResourcePackManager.awaitResolution(
                new CompletableFuture<>(), 1, TimeUnit.MILLISECONDS,
                () -> pendingPack.complete(null)));
        assertTrue(pendingPack.isDone());
    }

    @Test
    void interruptionRestoresInterruptFlagAndInvokesFallback() throws Exception {
        var fallback = new AtomicInteger();
        var interrupted = new CompletableFuture<Boolean>();
        Thread thread = new Thread(() -> {
            ResourcePackManager.awaitResolution(
                    new CompletableFuture<>(), 1, TimeUnit.MINUTES,
                    fallback::incrementAndGet);
            interrupted.complete(Thread.currentThread().isInterrupted());
        });
        thread.start();
        thread.interrupt();
        thread.join(2_000);

        assertTrue(interrupted.get(1, TimeUnit.SECONDS));
        assertEquals(1, fallback.get());
    }
}
