package dsa.systemdesign.ratelimiter;

/**
 * Token Bucket Rate Limiter
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Algorithm: Tokens added at fixed rate, requests consume tokens
 * Allows bursts up to bucket capacity
 */
public class TokenBucket {
    
    private final long capacity;
    private final long refillRate; // tokens per second
    private long tokens;
    private long lastRefillTime;
    
    public TokenBucket(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTime = System.currentTimeMillis();
    }
    
    public synchronized boolean allowRequest(long tokensNeeded) {
        refill();
        
        if (tokens >= tokensNeeded) {
            tokens -= tokensNeeded;
            return true;
        }
        
        return false;
    }
    
    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastRefillTime;
        long tokensToAdd = (elapsedTime * refillRate) / 1000;
        
        tokens = Math.min(capacity, tokens + tokensToAdd);
        lastRefillTime = now;
    }
    
    public long getAvailableTokens() {
        refill();
        return tokens;
    }
    
    public static void main(String[] args) throws InterruptedException {
        // 10 tokens capacity, 2 tokens per second
        TokenBucket limiter = new TokenBucket(10, 2);
        
        // Burst of 5 requests
        for (int i = 0; i < 5; i++) {
            System.out.println("Request " + i + ": " + 
                limiter.allowRequest(1));
        }
        
        Thread.sleep(3000); // Wait 3 seconds
        
        // Should have ~6 tokens now
        System.out.println("After 3s: " + limiter.allowRequest(1));
    }
}

