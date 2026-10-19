/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.antibot;

import com.google.common.base.Ticker;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Limit the number of requests with a maximum size. Each requests expire after the specified time making it available
 * for another request.
 */
public class TickingRateLimiter implements RateLimiter {

    private final Ticker ticker;

    // amount of milliseconds to expire
    private final long expireTime;

    // total request limit
    private final int requestLimit;

    private final Deque<TimeRecord> records;
    private int totalRequests;

    public TickingRateLimiter(Ticker ticker, int maxLimit, long expireTime) {
        this.ticker = ticker;

        this.requestLimit = maxLimit;
        this.expireTime = expireTime;

        records = new ArrayDeque<>(10);
    }

    /**
     * Ask if access is allowed. If so register the request.
     *
     * @return true if allowed - false otherwise without any side effects
     */
    @Override
    public boolean tryAcquire() {
        // current time millis is not monotonic - it can jump back depending on user choice or NTP
        long nowMilli = ticker.read() / 1_000_000;
        synchronized (this) {
            // having synchronized will limit the amount of concurrency a lot
            TimeRecord oldest;
            while ((oldest = records.peekFirst()) != null && oldest.hasExpired(nowMilli)) {
                records.pop();
                totalRequests -= oldest.getRequestCount();
            }

            // total requests reached block any further requests
            if (totalRequests >= requestLimit) {
                return false;
            }

            TimeRecord latest = records.peekLast();
            if (latest == null) {
                // empty list - add new record
                records.add(new TimeRecord(nowMilli, expireTime));
                totalRequests++;
                return true;
            }

            int res = latest.compareTo(nowMilli);
            if (res <= 0) {
                // same window or earlier record — treat as same-window either
                // way (a clock jump back must not crash the limiter)
                latest.hit();
                totalRequests++;
                return true;
            }

            // now is one minute newer
            records.add(new TimeRecord(nowMilli, expireTime));
            totalRequests++;
            return true;
        }
    }

    private static class TimeRecord implements Comparable<TimeRecord> {

        private final long firstMinuteRecord;
        private final long expireTime;
        private int count;

        TimeRecord(long firstMinuteRecord, long expireTime) {
            this.firstMinuteRecord = firstMinuteRecord;
            this.expireTime = expireTime;
            this.count = 1;
        }

        public void hit() {
            count++;
        }

        public int getRequestCount() {
            return count;
        }

        public boolean hasExpired(long now) {
            return firstMinuteRecord + expireTime <= now;
        }

        public int compareTo(long other) {
            if (other < firstMinuteRecord) {
                // other is earlier
                return -1;
            }

            if (other > firstMinuteRecord + expireTime) {
                return +1;
            }

            return 0;
        }

        @Override
        public int compareTo(TimeRecord other) {
            return compareTo(other.firstMinuteRecord);
        }
    }
}
