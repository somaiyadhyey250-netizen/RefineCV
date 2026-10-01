package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single-process fixed-window limiter for resume analysis requests.
 *
 * Keep this interface small so it can be replaced by a shared implementation
 * if RefineCV is later deployed across multiple application instances.
 */
@Component
public class InMemoryAnalysisRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final int maxTrackedClients;
    private final Map<String, Window> windows = new HashMap<>();

    public InMemoryAnalysisRateLimiter(
            @Value("${refinecv.analysis.rate-limit.max-requests:5}") int maxRequests,
            @Value("${refinecv.analysis.rate-limit.window:15m}") Duration window,
            @Value("${refinecv.analysis.rate-limit.max-tracked-clients:10000}") int maxTrackedClients
    ) {
        if (maxRequests < 1 || window.isZero() || window.isNegative() || maxTrackedClients < 1) {
            throw new IllegalArgumentException("Rate-limit settings must be positive.");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.maxTrackedClients = maxTrackedClients;
        if (windowMillis < 1) {
            throw new IllegalArgumentException("Rate-limit window must be at least one millisecond.");
        }
    }

    /**
     * Records one request for the supplied network client key, returning false
     * when the client's fixed window is exhausted or tracking capacity is full.
     */
    public synchronized boolean tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Window window = windows.get(clientKey);
        if (window != null && now - window.startedAtMillis >= windowMillis) {
            windows.remove(clientKey);
            window = null;
        }

        if (window == null) {
            removeExpiredWindows(now);
            if (windows.size() >= maxTrackedClients) {
                return false;
            }
            windows.put(clientKey, new Window(now, 1));
            return true;
        }

        if (window.requestCount >= maxRequests) {
            return false;
        }
        window.requestCount++;
        return true;
    }

    private void removeExpiredWindows(long now) {
        Iterator<Window> iterator = windows.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().startedAtMillis >= windowMillis) {
                iterator.remove();
            }
        }
    }

    private static final class Window {
        private final long startedAtMillis;
        private int requestCount;

        private Window(long startedAtMillis, int requestCount) {
            this.startedAtMillis = startedAtMillis;
            this.requestCount = requestCount;
        }
    }
}
