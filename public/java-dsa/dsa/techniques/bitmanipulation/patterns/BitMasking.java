package dsa.techniques.bitmanipulation.patterns;
/**
 * Bit Masking Patterns - Advanced Interview Techniques
 * 
 * Bit masking is used to represent states, subsets, and optimize algorithms.
 * Essential for dynamic programming, backtracking, and combinatorial problems.
 */
public class BitMasking {
    
    /**
     * Pattern 1: Generate All Subsets using Bit Masking
     * LeetCode 78: Subsets
     */
    public java.util.List<java.util.List<Integer>> subsets(int[] nums) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        int n = nums.length;
        
        // Generate all possible bitmasks from 0 to 2^n - 1
        for (int mask = 0; mask < (1 << n); mask++) {
            java.util.List<Integer> subset = new java.util.ArrayList<>();
            
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(nums[i]);
                }
            }
            
            result.add(subset);
        }
        
        return result;
    }
    
    /**
     * Pattern 2: Traveling Salesman Problem (TSP) using Bit Masking
     * DP with bitmask to represent visited cities
     */
    public int tsp(int[][] dist) {
        int n = dist.length;
        int[][] dp = new int[1 << n][n];
        
        // Initialize with infinity
        for (int i = 0; i < (1 << n); i++) {
            java.util.Arrays.fill(dp[i], Integer.MAX_VALUE / 2);
        }
        
        dp[1][0] = 0; // Start from city 0 with only city 0 visited
        
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int u = 0; u < n; u++) {
                if ((mask & (1 << u)) == 0) continue; // u not visited
                
                for (int v = 0; v < n; v++) {
                    if ((mask & (1 << v)) != 0) continue; // v already visited
                    
                    int newMask = mask | (1 << v);
                    dp[newMask][v] = Math.min(dp[newMask][v], dp[mask][u] + dist[u][v]);
                }
            }
        }
        
        // Find minimum cost to return to start
        int minCost = Integer.MAX_VALUE;
        int finalMask = (1 << n) - 1;
        
        for (int i = 1; i < n; i++) {
            if (dp[finalMask][i] != Integer.MAX_VALUE / 2) {
                minCost = Math.min(minCost, dp[finalMask][i] + dist[i][0]);
            }
        }
        
        return minCost;
    }
    
    /**
     * Pattern 3: Shortest Hamiltonian Path
     * Find shortest path visiting all nodes exactly once
     */
    public int shortestHamiltonianPath(int[][] graph) {
        int n = graph.length;
        int[][] dp = new int[1 << n][n];
        
        // Initialize with infinity
        for (int i = 0; i < (1 << n); i++) {
            java.util.Arrays.fill(dp[i], Integer.MAX_VALUE / 2);
        }
        
        // Can start from any node
        for (int i = 0; i < n; i++) {
            dp[1 << i][i] = 0;
        }
        
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int u = 0; u < n; u++) {
                if ((mask & (1 << u)) == 0 || dp[mask][u] == Integer.MAX_VALUE / 2) {
                    continue;
                }
                
                for (int v = 0; v < n; v++) {
                    if ((mask & (1 << v)) != 0) continue;
                    
                    int newMask = mask | (1 << v);
                    dp[newMask][v] = Math.min(dp[newMask][v], dp[mask][u] + graph[u][v]);
                }
            }
        }
        
        // Find minimum among all ending positions
        int result = Integer.MAX_VALUE;
        int finalMask = (1 << n) - 1;
        
        for (int i = 0; i < n; i++) {
            result = Math.min(result, dp[finalMask][i]);
        }
        
        return result;
    }
    
    /**
     * Pattern 4: Maximum Students Taking Exam
     * LeetCode 1349: Maximum Students Taking Exam
     */
    public int maxStudents(char[][] seats) {
        int m = seats.length;
        int n = seats[0].length;
        
        // Precompute valid masks for each row
        int[] validMasks = new int[m];
        for (int i = 0; i < m; i++) {
            validMasks[i] = 0;
            for (int mask = 0; mask < (1 << n); mask++) {
                if (isValidMask(seats[i], mask)) {
                    validMasks[i] |= (1 << mask);
                }
            }
        }
        
        // DP with bitmask
        int[][] dp = new int[m][1 << n];
        for (int i = 0; i < m; i++) {
            java.util.Arrays.fill(dp[i], -1);
        }
        
        return solve(0, 0, seats, validMasks, dp);
    }
    
    private boolean isValidMask(char[] row, int mask) {
        for (int i = 0; i < row.length; i++) {
            if ((mask & (1 << i)) != 0) {
                // Check if seat is broken
                if (row[i] == '#') return false;
                
                // Check adjacent students in same row
                if (i > 0 && (mask & (1 << (i - 1))) != 0) return false;
                if (i < row.length - 1 && (mask & (1 << (i + 1))) != 0) return false;
            }
        }
        return true;
    }
    
    private int solve(int row, int prevMask, char[][] seats, int[] validMasks, int[][] dp) {
        if (row == seats.length) return 0;
        if (dp[row][prevMask] != -1) return dp[row][prevMask];
        
        int maxStudents = 0;
        
        for (int mask = 0; mask < (1 << seats[0].length); mask++) {
            if ((validMasks[row] & (1 << mask)) == 0) continue;
            
            // Check conflicts with previous row
            boolean valid = true;
            for (int i = 0; i < seats[0].length; i++) {
                if ((mask & (1 << i)) != 0) {
                    // Check diagonal conflicts
                    if (i > 0 && (prevMask & (1 << (i - 1))) != 0) valid = false;
                    if (i < seats[0].length - 1 && (prevMask & (1 << (i + 1))) != 0) valid = false;
                }
            }
            
            if (valid) {
                int students = Integer.bitCount(mask) + solve(row + 1, mask, seats, validMasks, dp);
                maxStudents = Math.max(maxStudents, students);
            }
        }
        
        return dp[row][prevMask] = maxStudents;
    }
    
    /**
     * Pattern 5: Partition into K Equal Sum Subsets
     * LeetCode 698: Partition to K Equal Sum Subsets
     */
    public boolean canPartitionKSubsets(int[] nums, int k) {
        int sum = java.util.Arrays.stream(nums).sum();
        if (sum % k != 0) return false;
        
        int target = sum / k;
        java.util.Arrays.sort(nums);
        
        if (nums[nums.length - 1] > target) return false;
        
        return canPartition(nums, 0, k, 0, target, new boolean[nums.length]);
    }
    
    private boolean canPartition(int[] nums, int start, int k, int currentSum, 
                                int target, boolean[] used) {
        if (k == 1) return true; // Last subset automatically valid
        
        if (currentSum == target) {
            // Found one subset, try to find remaining k-1 subsets
            return canPartition(nums, 0, k - 1, 0, target, used);
        }
        
        for (int i = start; i < nums.length; i++) {
            if (!used[i] && currentSum + nums[i] <= target) {
                used[i] = true;
                if (canPartition(nums, i + 1, k, currentSum + nums[i], target, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        
        return false;
    }
    
    /**
     * Pattern 6: Count Ways to Assign Tasks
     * Use bitmask to represent which tasks are assigned
     */
    public int countTaskAssignments(int[] tasks, int[] workers) {
        int n = tasks.length;
        int m = workers.length;
        
        // dp[mask] = number of ways to assign tasks represented by mask
        int[] dp = new int[1 << n];
        dp[0] = 1;
        
        for (int mask = 0; mask < (1 << n); mask++) {
            if (dp[mask] == 0) continue;
            
            int assignedTasks = Integer.bitCount(mask);
            if (assignedTasks >= m) continue;
            
            int worker = assignedTasks; // Next worker to assign
            
            for (int task = 0; task < n; task++) {
                if ((mask & (1 << task)) == 0 && workers[worker] >= tasks[task]) {
                    dp[mask | (1 << task)] += dp[mask];
                }
            }
        }
        
        return dp[(1 << n) - 1];
    }
    
    /**
     * Pattern 7: Bitmask DP for Game Theory
     * Nim Game with multiple piles
     */
    public boolean canWinNim(int[] piles) {
        // Calculate Nim sum (XOR of all pile sizes)
        int nimSum = 0;
        for (int pile : piles) {
            nimSum ^= pile;
        }
        
        return nimSum != 0; // First player wins if nim sum is non-zero
    }
    
    /**
     * Utility Methods for Bit Masking
     */
    
    /**
     * Check if mask represents a valid subset based on constraints
     */
    public boolean isValidSubset(int mask, int[] constraints) {
        for (int constraint : constraints) {
            // Example: constraint could be "elements at positions i and j cannot both be selected"
            // This is problem-specific
        }
        return true;
    }
    
    /**
     * Get next subset lexicographically
     */
    public int nextSubset(int mask) {
        // Gosper's hack for next subset with same number of bits
        int c = mask;
        int r = mask & -mask;
        int next = c + r;
        return next | (((c ^ next) / r) >> 2);
    }
    
    /**
     * Iterate through all subsets of a given mask
     */
    public java.util.List<Integer> getAllSubsets(int mask) {
        java.util.List<Integer> subsets = new java.util.ArrayList<>();
        
        for (int subset = mask; ; subset = (subset - 1) & mask) {
            subsets.add(subset);
            if (subset == 0) break;
        }
        
        return subsets;
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        BitMasking bm = new BitMasking();
        
        // Test Pattern 1: Generate Subsets
        System.out.println("=== Pattern 1: Generate Subsets ===");
        int[] nums = {1, 2, 3};
        System.out.println("Subsets of [1,2,3]: " + bm.subsets(nums));
        
        // Test TSP
        System.out.println("\n=== Pattern 2: TSP ===");
        int[][] dist = {
            {0, 10, 15, 20},
            {10, 0, 35, 25},
            {15, 35, 0, 30},
            {20, 25, 30, 0}
        };
        System.out.println("TSP minimum cost: " + bm.tsp(dist));
        
        // Test subset utilities
        System.out.println("\n=== Utility Methods ===");
        int mask = 0b1011; // Binary: 1011
        System.out.println("All subsets of mask " + Integer.toBinaryString(mask) + ":");
        for (int subset : bm.getAllSubsets(mask)) {
            System.out.println("  " + Integer.toBinaryString(subset));
        }
        
        // Test Nim game
        System.out.println("\n=== Pattern 7: Nim Game ===");
        int[] piles = {3, 5, 7};
        System.out.println("Can win Nim with piles [3,5,7]: " + bm.canWinNim(piles));
    }
}

