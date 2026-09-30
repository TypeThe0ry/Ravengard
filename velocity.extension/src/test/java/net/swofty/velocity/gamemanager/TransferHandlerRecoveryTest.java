package net.swofty.velocity.gamemanager;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferHandlerRecoveryTest {

    @Test
    void allowsOnlyOneCrashRecoveryPerPlayer() throws Exception {
        UUID player = UUID.randomUUID();
        int attempts = 16;
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);

        for (int i = 0; i < attempts; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    start.await();
                    if (CrashRecoveryGate.begin(player)) {
                        accepted.incrementAndGet();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(1, accepted.get());
        assertFalse(CrashRecoveryGate.begin(player));

        CrashRecoveryGate.clear(player);
        assertTrue(CrashRecoveryGate.begin(player));
        CrashRecoveryGate.clear(player);
    }
}
