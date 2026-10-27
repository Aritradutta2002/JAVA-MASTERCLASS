package ratelimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Token bucket with atomic consume and explicit retry timing. */
public final class RateLimiter {
    public interface Policy { Decision consume(String key, long cost, Instant now); }
    public record Decision(boolean allowed, long remaining, Duration retryAfter) {
        public static Decision allow(long remaining) { return new Decision(true, remaining, Duration.ZERO); }
        public static Decision reject(long remaining, Duration retryAfter) { return new Decision(false, remaining, retryAfter); }
    }

    public static final class TokenBucketPolicy implements Policy {
        private final long capacity;
        private final double refillPerNano;
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
        private final Clock clock;
        public TokenBucketPolicy(long capacity, double refillPerSecond, Clock clock) {
            if (capacity <= 0 || refillPerSecond <= 0) throw new IllegalArgumentException("Invalid policy");
            this.capacity=capacity; this.refillPerNano=refillPerSecond/1_000_000_000d; this.clock=Objects.requireNonNull(clock);
        }
        @Override public Decision consume(String key, long cost, Instant now) {
            if (cost <= 0 || cost > capacity) throw new IllegalArgumentException("Invalid cost");
            Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
            synchronized (bucket) {
                long tokens = bucket.refill(now, refillPerNano, capacity);
                if (tokens < cost) {
                    long needed = cost - tokens;
                    Duration retry = Duration.ofNanos(Math.max(1, (long)Math.ceil(needed/refillPerNano)));
                    return Decision.reject(tokens, retry);
                }
                bucket.tokens = tokens - cost;
                return Decision.allow(bucket.tokens);
            }
        }
        private static final class Bucket { long tokens; long lastNanos; Bucket(long tokens, Instant now) { this.tokens=tokens; this.lastNanos=now.getEpochSecond()*1_000_000_000L+now.getNano(); }
            long refill(Instant now, double rate, long capacity) { long nowNanos=now.getEpochSecond()*1_000_000_000L+now.getNano(); long elapsed=Math.max(0,nowNanos-lastNanos); lastNanos=nowNanos; tokens=Math.min(capacity,(long)Math.min(capacity,tokens+elapsed*rate)); return tokens; } }
    }

    public static void main(String[] args) {
        Clock clock = Clock.systemUTC();
        RateLimiter.Policy policy = new TokenBucketPolicy(2, 1, clock);
        Instant now = clock.instant();
        System.out.println(policy.consume("u1", 1, now));
        System.out.println(policy.consume("u1", 1, now));
        System.out.println(policy.consume("u1", 1, now));
    }
}
