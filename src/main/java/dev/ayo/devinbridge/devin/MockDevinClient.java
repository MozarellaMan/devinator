package dev.ayo.devinbridge.devin;

import dev.ayo.devinbridge.domain.DevinStatus;
import dev.ayo.devinbridge.domain.StatusSnapshot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stateful fake Devin, enabled via {@code MOCK_DEVIN=true}.
 * <p>Each session advances one step per {@link #getStatus} call: {@code working} ->
 * {@code working} -> {@code finished} (with a fake PR url attached on the finishing
 * call). Session ids are locally minted, monotonically increasing strings so runs are
 * easy to eyeball in logs.
 */
public final class MockDevinClient implements DevinClient {

    private final AtomicLong idGenerator = new AtomicLong();
    private final Map<String, AtomicInteger> pollCounts = new ConcurrentHashMap<>();

    @Override
    public String createSession(String prompt, String repo) {
        String sessionId = "mock-session-" + idGenerator.incrementAndGet();
        pollCounts.put(sessionId, new AtomicInteger(0));
        return sessionId;
    }

    @Override
    public StatusSnapshot getStatus(String sessionId) {
        int step = pollCounts.computeIfAbsent(sessionId, _ -> new AtomicInteger(0))
                .getAndIncrement();

        // Two "working" polls to make the dashboard's Running state visible for a
        // beat, then finish with a fake PR on the third and every poll after.
        if (step < 2) {
            return new StatusSnapshot(sessionId, DevinStatus.WORKING, null);
        }
        String prUrl = "https://github.com/mock-org/mock-repo/pull/" + Math.abs(sessionId.hashCode() % 1000);
        return new StatusSnapshot(sessionId, DevinStatus.FINISHED, prUrl);
    }

    @Override
    public void terminateSession(String sessionId) {
        // No-op: mock sessions jump straight Running -> Completed via getStatus above,
        // so pollPrOpenSession (the only caller of terminateSession) never runs for them.
    }
}
