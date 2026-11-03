package dsa.datastructures.strings.advanced;
/**
 * SUFFIX ARRAY + LCP (Kasai)
 * 
 * Build suffix array in O(n log n) and LCP array in O(n)
 * CP Essential for substring queries, distinct substrings, lexicographic problems.
 */

import java.util.*;

public class SuffixArrayAndLCP {
    // Build SA using O(n log^2 n) doubling (simple and robust)
    public int[] buildSuffixArray(String s) {
        int n = s.length();
        Integer[] sa = new Integer[n];
        int[] ranks = new int[n];
        int[] tmp = new int[n];
        
        for (int i = 0; i < n; i++) {
            sa[i] = i;
            ranks[i] = s.charAt(i);
        }
        
        for (int k = 1; k < n; k <<= 1) {
            final int kk = k;
            Arrays.sort(sa, (a, b) -> {
                if (ranks[a] != ranks[b]) return Integer.compare(ranks[a], ranks[b]);
                int ra = a + kk < n ? ranks[a + kk] : -1;
                int rb = b + kk < n ? ranks[b + kk] : -1;
                return Integer.compare(ra, rb);
            });
            tmp[sa[0]] = 0;
            for (int i = 1; i < n; i++) {
                tmp[sa[i]] = tmp[sa[i-1]] + (compareRank(ranks, sa[i-1], sa[i], k, n) ? 1 : 0);
            }
            System.arraycopy(tmp, 0, ranks, 0, n);
            if (ranks[sa[n-1]] == n-1) break; // all ranks unique
        }
        
        // Convert Integer[] to int[]
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = sa[i];
        }
        return result;
    }
    
    private boolean compareRank(int[] r, int a, int b, int k, int n) {
        if (r[a] != r[b]) return true;
        int ra = a + k < n ? r[a + k] : -1;
        int rb = b + k < n ? r[b + k] : -1;
        return ra != rb;
    }
    
    // Kasai algorithm for LCP array
    // lcp[i] = LCP(sa[i], sa[i-1]) with previous suffix in SA
    public int[] buildLCP(String s, int[] sa) {
        int n = s.length();
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) rank[sa[i]] = i;
        int[] lcp = new int[n];
        int h = 0;
        for (int i = 0; i < n; i++) {
            int r = rank[i];
            if (r > 0) {
                int j = sa[r - 1];
                while (i + h < n && j + h < n && s.charAt(i + h) == s.charAt(j + h)) h++;
                lcp[r] = h;
                if (h > 0) h--;
            }
        }
        return lcp;
    }

    // Distinct substrings count = n*(n+1)/2 - sum(LCP)
    public long countDistinctSubstrings(String s) {
        int n = s.length();
        int[] sa = buildSuffixArray(s);
        int[] lcp = buildLCP(s, sa);
        long total = (long) n * (n + 1) / 2;
        long dup = 0;
        for (int i = 0; i < n; i++) dup += lcp[i];
        return total - dup;
    }

    // Longest repeated substring via max LCP
    public String longestRepeatedSubstring(String s) {
        int[] sa = buildSuffixArray(s);
        int[] lcp = buildLCP(s, sa);
        int max = 0, idx = 0;
        for (int i = 1; i < s.length(); i++) {
            if (lcp[i] > max) { max = lcp[i]; idx = sa[i]; }
        }
        return max == 0 ? "" : s.substring(idx, idx + max);
    }

    public static void main(String[] args) {
        SuffixArrayAndLCP sol = new SuffixArrayAndLCP();
        String s = "banana";
        System.out.println("=== Suffix Array + LCP ===");
        System.out.println("String: " + s);
        int[] sa = sol.buildSuffixArray(s);
        System.out.println("SA: " + Arrays.toString(sa));
        System.out.println("LCP: " + Arrays.toString(sol.buildLCP(s, sa)));
        System.out.println("Distinct substrings: " + sol.countDistinctSubstrings(s) + " (expected: 15)");
        System.out.println("Longest repeated: '" + sol.longestRepeatedSubstring(s) + "' (expected: 'ana')");
    }
}

