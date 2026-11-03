package dsa.systemdesign.ratelimiter;

import java.util.*;

/**
 * Sliding Window Rate Limiter
 * FAANG Frequency: High (Amazon, Google)
 * 
 * Algorithm: Track requests in sliding time window
 * More accurate than fixed window
 */
public class SlidingWindowRateLimiter {
    
    private final int maxRequests;
    private final long windowSizeMs;
    private final Queue<Long> requestTimestamps;
    
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeMs) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
        this.requestTimestamps = new LinkedList<>();
    }
    
    public synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();
        
        // Remove old requests outside window
        while (!requestTimestamps.isEmpty() && 
               now - requestTimestamps.peek() >= windowSizeMs) {
            requestTimestamps.poll();
        }
        
        if (requestTimestamps.size() < maxRequests) {
            requestTimestamps.offer(now);
            return true;
        }
        
        return false;
    }
    
    // Sliding Window Log with HashMap (for multiple users)
    static class SlidingWindowLog {
        private final int maxRequests;
        private final long windowSizeMs;
        private final Map<String, Queue<Long>> userRequests;
        
        public SlidingWindowLog(int maxRequests, long windowSizeMs) {
            this.maxRequests = maxRequests;
            this.windowSizeMs = windowSizeMs;
            this.userRequests = new HashMap<>();
        }
        
        public synchronized boolean allowRequest(String userId) {
            long now = System.currentTimeMillis();
            
            userRequests.putIfAbsent(userId, new LinkedList<>());
            Queue<Long> timestamps = userRequests.get(userId);
            
            // Remove old requests
            while (!timestamps.isEmpty() && 
                   now - timestamps.peek() >= windowSizeMs) {
                timestamps.poll();
            }
            
            if (timestamps.size() < maxRequests) {
                timestamps.offer(now);
                return true;
            }
            
            return false;
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        // Allow 3 requests per 5 seconds
        SlidingWindowRateLimiter limiter = 
            new SlidingWindowRateLimiter(3, 5000);
        
        for (int i = 0; i < 5; i++) {
            System.out.println("Request " + i + ": " + 
                limiter.allowRequest());
            Thread.sleep(1000);
        }
    }
}

