package dsa.techniques.bitmanipulation.advanced;
/**
 * Advanced Bitmask Iteration Patterns
 * Difficulty: Hard
 * 
 * This class demonstrates advanced bit manipulation patterns commonly used in
 * competitive programming and complex algorithmic problems. These patterns are
 * essential for solving subset enumeration, dynamic programming on subsets,
 * and optimization problems.
 * 
 * Key Patterns:
 * 1. Iterate all subsets of a mask
 * 2. Iterate all supersets of a mask
 * 3. Gosper's hack (next combination with same popcount)
 * 4. SOS (Sum Over Subsets) DP
 * 5. Meet in the middle technique
 * 6. Subset sum enumeration
 */
public class BitmaskIterationPatterns {
    
    /**
     * Pattern 1: Iterate all subsets of a given mask
     * Usage: When you need to enumerate all possible subsets of a given set
     * Time: O(3^n) for all masks, O(2^popcount(mask)) for single mask
     */
    public void iterateSubsets(int mask) {
        System.out.println("Subsets of mask " + mask + " (binary: " + Integer.toBinaryString(mask) + "):");
        
        // Method 1: Standard iteration
        for (int subset = mask; ; subset = (subset - 1) & mask) {
            System.out.printf("Subset: %2d (binary: %8s)%n", 
                subset, String.format("%8s", Integer.toBinaryString(subset)).replace(' ', '0'));
            if (subset == 0) break;
        }
        System.out.println();
    }
    
    /**
     * Pattern 2: Iterate all subsets with callback
     */
    public void iterateSubsetsWithCallback(int mask, java.util.function.Consumer<Integer> callback) {
        for (int subset = mask; ; subset = (subset - 1) & mask) {
            callback.accept(subset);
            if (subset == 0) break;
        }
    }
    
    /**
     * Pattern 3: Count all subsets of a mask
     */
    public int countSubsets(int mask) {
        return 1 << Integer.bitCount(mask); // 2^(number of set bits)
    }
    
    /**
     * Pattern 4: Gosper's Hack - Generate next combination with same popcount
     * Generates the next number with the same number of set bits
     */
    public int nextCombination(int x) {
        int u = x & -x;              // Rightmost set bit
        int v = x + u;               // Add to get carry
        return v | (((v ^ x) / u) >>> 2);
    }
    
    /**
     * Pattern 5: Generate all k-bit combinations using Gosper's hack
     */
    public java.util.List<Integer> generateKBitCombinations(int n, int k) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        
        if (k > n || k < 0) return result;
        
        int combination = (1 << k) - 1; // Start with k rightmost bits set
        int limit = 1 << n;
        
        while (combination < limit) {
            result.add(combination);
            combination = nextCombination(combination);
        }
        
        return result;
    }
    
    /**
     * Pattern 6: SOS (Sum Over Subsets) Dynamic Programming
     * Calculate sum of values for all subsets of each mask
     */
    public int[] sosDP(int[] values) {
        int n = Integer.numberOfLeadingZeros(Integer.highestOneBit(values.length - 1)) - 1;
        if (values.length != (1 << n)) {
            throw new IllegalArgumentException("Array length must be power of 2");
        }
        
        int[] dp = values.clone();
        
        // For each bit position
        for (int i = 0; i < n; i++) {
            // For each mask
            for (int mask = 0; mask < (1 << n); mask++) {
                // If ith bit is set, add contribution from mask without ith bit
                if ((mask & (1 << i)) != 0) {
                    dp[mask] += dp[mask ^ (1 << i)];
                }
            }
        }
        
        return dp;
    }
    
    /**
     * Pattern 7: Inverse SOS DP (Möbius transform)
     * Reverse the SOS transformation
     */
    public int[] inverseSosDP(int[] sosValues) {
        int n = Integer.numberOfLeadingZeros(Integer.highestOneBit(sosValues.length - 1)) - 1;
        int[] dp = sosValues.clone();
        
        // Reverse the SOS transformation
        for (int i = 0; i < n; i++) {
            for (int mask = 0; mask < (1 << n); mask++) {
                if ((mask & (1 << i)) != 0) {
                    dp[mask] -= dp[mask ^ (1 << i)];
                }
            }
        }
        
        return dp;
    }
    
    /**
     * Pattern 8: Meet in the Middle for subset sum
     * Split array in half and use bitmask to generate all possible sums
     */
    public boolean subsetSumMeetInMiddle(int[] arr, int target) {
        int n = arr.length;
        int half = n / 2;
        
        // Generate all possible sums for first half
        java.util.Set<Integer> firstHalfSums = new java.util.HashSet<>();
        for (int mask = 0; mask < (1 << half); mask++) {
            int sum = 0;
            for (int i = 0; i < half; i++) {
                if ((mask & (1 << i)) != 0) {
                    sum += arr[i];
                }
            }
            firstHalfSums.add(sum);
        }
        
        // Generate all possible sums for second half and check
        for (int mask = 0; mask < (1 << (n - half)); mask++) {
            int sum = 0;
            for (int i = 0; i < (n - half); i++) {
                if ((mask & (1 << i)) != 0) {
                    sum += arr[half + i];
                }
            }
            
            if (firstHalfSums.contains(target - sum)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Pattern 9: Enumerate all subset sums efficiently
     */
    public java.util.Map<Integer, Integer> enumerateSubsetSums(int[] arr) {
        java.util.Map<Integer, Integer> sumCount = new java.util.HashMap<>();
        int n = arr.length;
        
        for (int mask = 0; mask < (1 << n); mask++) {
            int sum = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    sum += arr[i];
                }
            }
            sumCount.put(sum, sumCount.getOrDefault(sum, 0) + 1);
        }
        
        return sumCount;
    }
    
    /**
     * Pattern 10: Subset convolution using SOS DP
     * Calculate convolution of two functions defined on subsets
     */
    public int[] subsetConvolution(int[] a, int[] b) {
        int n = Integer.numberOfLeadingZeros(Integer.highestOneBit(a.length - 1)) - 1;
        
        // Ranked SOS transform
        int[][][] fa = new int[n + 1][1 << n][1];
        int[][][] fb = new int[n + 1][1 << n][1];
        
        // Initialize
        for (int mask = 0; mask < (1 << n); mask++) {
            fa[Integer.bitCount(mask)][mask][0] = a[mask];
            fb[Integer.bitCount(mask)][mask][0] = b[mask];
        }
        
        // Apply SOS transform for each rank
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j < n; j++) {
                for (int mask = 0; mask < (1 << n); mask++) {
                    if ((mask & (1 << j)) != 0) {
                        fa[i][mask][0] += fa[i][mask ^ (1 << j)][0];
                        fb[i][mask][0] += fb[i][mask ^ (1 << j)][0];
                    }
                }
            }
        }
        
        // Pointwise multiplication
        int[][][] fh = new int[n + 1][1 << n][1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= i; j++) {
                for (int mask = 0; mask < (1 << n); mask++) {
                    fh[i][mask][0] += fa[j][mask][0] * fb[i - j][mask][0];
                }
            }
        }
        
        // Inverse SOS transform
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j < n; j++) {
                for (int mask = 0; mask < (1 << n); mask++) {
                    if ((mask & (1 << j)) != 0) {
                        fh[i][mask][0] -= fh[i][mask ^ (1 << j)][0];
                    }
                }
            }
        }
        
        int[] result = new int[1 << n];
        for (int mask = 0; mask < (1 << n); mask++) {
            result[mask] = fh[Integer.bitCount(mask)][mask][0];
        }
        
        return result;
    }
    
    /**
     * Pattern 11: Generate all masks with exactly k bits set
     */
    public java.util.List<Integer> generateMasksWithKBits(int n, int k) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        
        if (k > n || k < 0) return result;
        
        // Use recursive approach to generate combinations
        generateCombinations(0, 0, n, k, result);
        
        return result;
    }
    
    private void generateCombinations(int pos, int mask, int n, int k, java.util.List<Integer> result) {
        if (k == 0) {
            result.add(mask);
            return;
        }
        
        if (pos == n || k > n - pos) return;
        
        // Include current position
        generateCombinations(pos + 1, mask | (1 << pos), n, k - 1, result);
        
        // Exclude current position
        generateCombinations(pos + 1, mask, n, k, result);
    }
    
    /**
     * Pattern 12: Iterate through all supermasks of a given mask
     * (masks that contain all bits of the given mask)
     */
    public void iterateSupermasks(int mask, int universalSet) {
        System.out.println("Supermasks of " + Integer.toBinaryString(mask) + 
                          " within " + Integer.toBinaryString(universalSet) + ":");
        
        for (int supermask = mask; ; supermask = (supermask + 1) | mask) {
            if ((supermask & universalSet) == supermask) {
                System.out.printf("Supermask: %s%n", Integer.toBinaryString(supermask));
            }
            if (supermask == universalSet) break;
        }
    }
    
    /**
     * Utility: Demonstrate all patterns
     */
    public void demonstrateAllPatterns() {
        System.out.println("=== ADVANCED BITMASK ITERATION PATTERNS DEMO ===\n");
        
        // Pattern 1: Subset iteration
        System.out.println("1. SUBSET ITERATION:");
        iterateSubsets(0b1011); // 11 in decimal
        
        // Pattern 4: Gosper's hack
        System.out.println("2. GOSPER'S HACK (Next combination with same popcount):");
        int x = 0b1011; // 11 in decimal
        System.out.println("Starting with: " + Integer.toBinaryString(x));
        for (int i = 0; i < 5; i++) {
            x = nextCombination(x);
            System.out.println("Next: " + Integer.toBinaryString(x));
        }
        System.out.println();
        
        // Pattern 5: K-bit combinations
        System.out.println("3. ALL 3-BIT COMBINATIONS IN 5 BITS:");
        java.util.List<Integer> combinations = generateKBitCombinations(5, 3);
        for (int comb : combinations) {
            System.out.printf("%s (%d)%n", 
                String.format("%5s", Integer.toBinaryString(comb)).replace(' ', '0'), comb);
        }
        System.out.println();
        
        // Pattern 6: SOS DP
        System.out.println("4. SOS DYNAMIC PROGRAMMING:");
        int[] values = {1, 2, 3, 4, 5, 6, 7, 8}; // 8 = 2^3
        int[] sosResult = sosDP(values);
        System.out.println("Original values: " + java.util.Arrays.toString(values));
        System.out.println("SOS DP result:   " + java.util.Arrays.toString(sosResult));
        
        // Verify with inverse
        int[] inverse = inverseSosDP(sosResult);
        System.out.println("Inverse SOS:     " + java.util.Arrays.toString(inverse));
        System.out.println("Matches original: " + java.util.Arrays.equals(values, inverse));
        System.out.println();
        
        // Pattern 8: Meet in the middle
        System.out.println("5. MEET IN THE MIDDLE SUBSET SUM:");
        int[] arr = {1, 3, 5, 7, 9, 11};
        int target = 13;
        boolean found = subsetSumMeetInMiddle(arr, target);
        System.out.println("Array: " + java.util.Arrays.toString(arr));
        System.out.println("Target: " + target);
        System.out.println("Subset sum exists: " + found);
        System.out.println();
        
        // Pattern 9: Subset sum enumeration
        System.out.println("6. SUBSET SUM ENUMERATION:");
        int[] smallArr = {1, 2, 3};
        java.util.Map<Integer, Integer> sumCounts = enumerateSubsetSums(smallArr);
        System.out.println("Array: " + java.util.Arrays.toString(smallArr));
        System.out.println("Sum frequencies:");
        sumCounts.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> System.out.printf("Sum %d: %d times%n", entry.getKey(), entry.getValue()));
        System.out.println();
        
        // Pattern 11: K-bit masks
        System.out.println("7. ALL MASKS WITH EXACTLY 2 BITS SET (n=4):");
        java.util.List<Integer> kBitMasks = generateMasksWithKBits(4, 2);
        for (int mask : kBitMasks) {
            System.out.printf("%s (%d)%n", 
                String.format("%4s", Integer.toBinaryString(mask)).replace(' ', '0'), mask);
        }
        System.out.println();
        
        // Pattern 12: Supermask iteration
        System.out.println("8. SUPERMASK ITERATION:");
        iterateSupermasks(0b101, 0b111);
    }
    
    /**
     * Performance benchmarking
     */
    public void performanceBenchmark() {
        System.out.println("=== PERFORMANCE BENCHMARK ===");
        
        // Benchmark subset iteration
        int mask = (1 << 15) - 1; // 15 bits set
        long start = System.nanoTime();
        
        int count = 0;
        iterateSubsetsWithCallback(mask, subset -> {});
        for (int subset = mask; ; subset = (subset - 1) & mask) {
            count++;
            if (subset == 0) break;
        }
        
        long end = System.nanoTime();
        System.out.printf("Iterated %d subsets in %.2f ms%n", count, (end - start) / 1_000_000.0);
        
        // Benchmark SOS DP
        int[] largeArray = new int[1 << 16]; // 2^16 elements
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = i % 1000;
        }
        
        start = System.nanoTime();
        int[] sosResult = sosDP(largeArray);
        end = System.nanoTime();
        
        System.out.printf("SOS DP on 2^16 elements: %.2f ms%n", (end - start) / 1_000_000.0);
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        BitmaskIterationPatterns patterns = new BitmaskIterationPatterns();
        
        patterns.demonstrateAllPatterns();
        patterns.performanceBenchmark();
    }
}

