package dsa.systemdesign.realworld;

import java.util.*;

/**
 * Encode and Decode TinyURL (LeetCode 535) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Design URL shortening service
 */
public class TinyURL {
    
    // Approach 1: Using counter
    class Codec1 {
        private Map<String, String> longToShort = new HashMap<>();
        private Map<String, String> shortToLong = new HashMap<>();
        private int counter = 0;
        private static final String BASE_URL = "http://tinyurl.com/";
        
        public String encode(String longUrl) {
            if (longToShort.containsKey(longUrl)) {
                return longToShort.get(longUrl);
            }
            
            String shortUrl = BASE_URL + counter++;
            longToShort.put(longUrl, shortUrl);
            shortToLong.put(shortUrl, longUrl);
            return shortUrl;
        }
        
        public String decode(String shortUrl) {
            return shortToLong.get(shortUrl);
        }
    }
    
    // Approach 2: Using random string
    class Codec2 {
        private Map<String, String> shortToLong = new HashMap<>();
        private Map<String, String> longToShort = new HashMap<>();
        private static final String BASE_URL = "http://tinyurl.com/";
        private static final String CHARS = 
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private Random random = new Random();
        
        public String encode(String longUrl) {
            if (longToShort.containsKey(longUrl)) {
                return longToShort.get(longUrl);
            }
            
            String shortUrl;
            do {
                shortUrl = BASE_URL + generateRandomString();
            } while (shortToLong.containsKey(shortUrl));
            
            shortToLong.put(shortUrl, longUrl);
            longToShort.put(longUrl, shortUrl);
            return shortUrl;
        }
        
        public String decode(String shortUrl) {
            return shortToLong.get(shortUrl);
        }
        
        private String generateRandomString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            }
            return sb.toString();
        }
    }
    
    // Approach 3: Using Base62 encoding
    class Codec3 {
        private Map<String, String> shortToLong = new HashMap<>();
        private int counter = 0;
        private static final String BASE_URL = "http://tinyurl.com/";
        private static final String CHARS = 
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        
        public String encode(String longUrl) {
            String shortUrl = BASE_URL + toBase62(counter++);
            shortToLong.put(shortUrl, longUrl);
            return shortUrl;
        }
        
        public String decode(String shortUrl) {
            return shortToLong.get(shortUrl);
        }
        
        private String toBase62(int num) {
            StringBuilder sb = new StringBuilder();
            while (num > 0) {
                sb.append(CHARS.charAt(num % 62));
                num /= 62;
            }
            return sb.reverse().toString();
        }
    }
    
    public static void main(String[] args) {
        TinyURL.Codec2 codec = new TinyURL().new Codec2();
        
        String url = "https://leetcode.com/problems/design-tinyurl";
        String shortUrl = codec.encode(url);
        System.out.println("Short URL: " + shortUrl);
        System.out.println("Decoded: " + codec.decode(shortUrl));
    }
}

