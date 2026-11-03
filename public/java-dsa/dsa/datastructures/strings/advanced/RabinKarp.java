package dsa.datastructures.strings.advanced;
/**
 * RABIN-KARP ALGORITHM
 * 
 * Rolling Hash for Pattern Matching - O(n+m) average
 * 
 * Key Concept: Hash function with rolling property
 * hash(s[i+1..i+m]) can be computed from hash(s[i..i+m-1]) in O(1)
 * 
 * CP Applications:
 * - Multiple pattern matching
 * - Substring search
 * - String hashing problems
 * - Plagiarism detection
 * 
 * Used heavily in Codeforces, CodeChef, AtCoder
 */

import java.util.*;

public class RabinKarp {
    
    private static final int BASE = 256;      // Number of characters
    private static final int MOD = 1000000007; // Prime modulus
    
    /**
     * Single pattern matching
     * Time: O(n+m) average, O(nm) worst
     */
    public List<Integer> search(String text, String pattern) {
        int n = text.length();
        int m = pattern.length();
        
        if (m > n) return new ArrayList<>();
        
        List<Integer> result = new ArrayList<>();
        
        // Compute hash of pattern and first window
        long patternHash = 0;
        long textHash = 0;
        long h = 1; // BASE^(m-1) % MOD
        
        // Compute h = BASE^(m-1) % MOD
        for (int i = 0; i < m - 1; i++) {
            h = (h * BASE) % MOD;
        }
        
        // Compute initial hashes
        for (int i = 0; i < m; i++) {
            patternHash = (BASE * patternHash + pattern.charAt(i)) % MOD;
            textHash = (BASE * textHash + text.charAt(i)) % MOD;
        }
        
        // Slide pattern over text
        for (int i = 0; i <= n - m; i++) {
            // Check if hashes match
            if (patternHash == textHash) {
                // Verify character by character (to handle collisions)
                if (text.substring(i, i + m).equals(pattern)) {
                    result.add(i);
                }
            }
            
            // Calculate hash for next window
            if (i < n - m) {
                textHash = (BASE * (textHash - text.charAt(i) * h) + text.charAt(i + m)) % MOD;
                
                // Handle negative hash
                if (textHash < 0) {
                    textHash += MOD;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Multiple pattern matching (VERY USEFUL IN CP!)
     * Find all occurrences of multiple patterns
     */
    public Map<String, List<Integer>> searchMultiple(String text, List<String> patterns) {
        Map<String, List<Integer>> results = new HashMap<>();
        
        // Group patterns by length for efficiency
        Map<Integer, List<String>> byLength = new HashMap<>();
        for (String pattern : patterns) {
            byLength.computeIfAbsent(pattern.length(), k -> new ArrayList<>()).add(pattern);
            results.put(pattern, new ArrayList<>());
        }
        
        // Process each length group
        for (Map.Entry<Integer, List<String>> entry : byLength.entrySet()) {
            int m = entry.getKey();
            List<String> patternsOfLength = entry.getValue();
            
            // Compute hashes for all patterns of this length
            Set<Long> patternHashes = new HashSet<>();
            Map<Long, List<String>> hashToPatterns = new HashMap<>();
            
            for (String pattern : patternsOfLength) {
                long hash = computeHash(pattern);
                patternHashes.add(hash);
                hashToPatterns.computeIfAbsent(hash, k -> new ArrayList<>()).add(pattern);
            }
            
            // Slide window over text
            int n = text.length();
            if (m > n) continue;
            
            long textHash = computeHash(text.substring(0, m));
            long h = power(BASE, m - 1, MOD);
            
            for (int i = 0; i <= n - m; i++) {
                if (patternHashes.contains(textHash)) {
                    // Verify matches
                    String window = text.substring(i, i + m);
                    if (hashToPatterns.containsKey(textHash)) {
                        for (String pattern : hashToPatterns.get(textHash)) {
                            if (window.equals(pattern)) {
                                results.get(pattern).add(i);
                            }
                        }
                    }
                }
                
                // Roll hash
                if (i < n - m) {
                    textHash = rollHash(textHash, text.charAt(i), text.charAt(i + m), h);
                }
            }
        }
        
        return results;
    }
    
    /**
     * CP Problem: Longest duplicate substring
     * Using binary search + rolling hash
     */
    public String longestDuplicateSubstring(String s) {
        int n = s.length();
        int left = 1, right = n;
        String result = "";
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            String dup = searchDuplicate(s, mid);
            
            if (dup != null) {
                result = dup;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return result;
    }
    
    private String searchDuplicate(String s, int len) {
        int n = s.length();
        Map<Long, List<Integer>> seen = new HashMap<>();
        
        long hash = computeHash(s.substring(0, len));
        long h = power(BASE, len - 1, MOD);
        seen.computeIfAbsent(hash, k -> new ArrayList<>()).add(0);
        
        for (int i = 1; i <= n - len; i++) {
            hash = rollHash(hash, s.charAt(i - 1), s.charAt(i + len - 1), h);
            
            if (seen.containsKey(hash)) {
                // Check for actual match (collision handling)
                String current = s.substring(i, i + len);
                for (int start : seen.get(hash)) {
                    if (s.substring(start, start + len).equals(current)) {
                        return current;
                    }
                }
            }
            
            seen.computeIfAbsent(hash, k -> new ArrayList<>()).add(i);
        }
        
        return null;
    }
    
    /**
     * CP Problem: Count distinct substrings
     * Using rolling hash
     */
    public int countDistinctSubstrings(String s) {
        int n = s.length();
        Set<Long> hashes = new HashSet<>();
        
        for (int len = 1; len <= n; len++) {
            long hash = computeHash(s.substring(0, len));
            long h = power(BASE, len - 1, MOD);
            hashes.add(hash);
            
            for (int i = 1; i <= n - len; i++) {
                hash = rollHash(hash, s.charAt(i - 1), s.charAt(i + len - 1), h);
                hashes.add(hash);
            }
        }
        
        return hashes.size();
    }
    
    // Helper: Compute hash of string
    private long computeHash(String s) {
        long hash = 0;
        for (char c : s.toCharArray()) {
            hash = (hash * BASE + c) % MOD;
        }
        return hash;
    }
    
    // Helper: Roll hash forward
    private long rollHash(long oldHash, char oldChar, char newChar, long h) {
        long hash = (BASE * (oldHash - oldChar * h) + newChar) % MOD;
        if (hash < 0) hash += MOD;
        return hash;
    }
    
    // Helper: Fast exponentiation
    private long power(long base, int exp, long mod) {
        long result = 1;
        base %= mod;
        while (exp > 0) {
            if (exp % 2 == 1) {
                result = (result * base) % mod;
            }
            base = (base * base) % mod;
            exp /= 2;
        }
        return result;
    }
    
    public static void main(String[] args) {
        RabinKarp rk = new RabinKarp();
        
        // Test 1: Single pattern
        String text1 = "AABAACAADAABAABA";
        String pattern1 = "AABA";
        System.out.println("=== Test 1: Single Pattern ===");
        System.out.println("Text: " + text1);
        System.out.println("Pattern: " + pattern1);
        System.out.println("Matches at: " + rk.search(text1, pattern1));
        System.out.println();
        
        // Test 2: Multiple patterns
        String text2 = "abcdefabcabc";
        List<String> patterns = Arrays.asList("abc", "def", "xyz");
        System.out.println("=== Test 2: Multiple Patterns ===");
        System.out.println("Text: " + text2);
        System.out.println("Patterns: " + patterns);
        Map<String, List<Integer>> results = rk.searchMultiple(text2, patterns);
        for (Map.Entry<String, List<Integer>> entry : results.entrySet()) {
            System.out.println("'" + entry.getKey() + "' found at: " + entry.getValue());
        }
        System.out.println();
        
        // Test 3: Longest duplicate substring
        String s3 = "banana";
        System.out.println("=== Test 3: Longest Duplicate ===");
        System.out.println("String: " + s3);
        System.out.println("Longest duplicate: '" + rk.longestDuplicateSubstring(s3) + "'");
        System.out.println("Expected: 'ana'");
        System.out.println();
        
        // Test 4: Count distinct substrings
        String s4 = "abab";
        System.out.println("=== Test 4: Distinct Substrings ===");
        System.out.println("String: " + s4);
        System.out.println("Count: " + rk.countDistinctSubstrings(s4));
        System.out.println("Substrings: a, b, ab, ba, aba, bab, abab");
        System.out.println();
        
        // Explain rolling hash
        System.out.println("=== ROLLING HASH EXPLAINED ===");
        System.out.println("For pattern 'abc' in text 'xabcy':");
        System.out.println("1. hash('xab') = x*256² + a*256 + b");
        System.out.println("2. Roll to 'abc':");
        System.out.println("   - Remove 'x': subtract x*256²");
        System.out.println("   - Shift left: multiply by 256");
        System.out.println("   - Add 'c': add c");
        System.out.println("3. Result: hash('abc') in O(1) time!");
        System.out.println();
        
        // CP Contest tips
        System.out.println("=== CP CONTEST TIPS ===");
        System.out.println("1. Use BASE = 31 or 256 depending on charset");
        System.out.println("2. MOD = 1e9+7 or 1e9+9 (large primes)");
        System.out.println("3. Double hashing (two MODs) to avoid collisions");
        System.out.println("4. Precompute powers of BASE for efficiency");
        System.out.println("5. Always verify match char-by-char (handle collisions)");
        System.out.println();
        
        System.out.println("=== WHEN TO USE ===");
        System.out.println("✓ Multiple pattern matching (better than KMP)");
        System.out.println("✓ Substring problems with binary search");
        System.out.println("✓ String equality checks in O(1)");
        System.out.println("✓ Finding duplicate substrings");
        System.out.println("✓ Plagiarism detection");
        System.out.println();
        
        System.out.println("=== ADVANTAGES OVER KMP/Z ===");
        System.out.println("✓ Easier to implement under contest pressure");
        System.out.println("✓ Multiple patterns handled naturally");
        System.out.println("✓ Works well with binary search");
        System.out.println("✓ Can compare substrings in O(1)");
    }
}

