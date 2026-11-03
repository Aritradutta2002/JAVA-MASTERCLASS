package dsa.techniques.bitmanipulation.hard;
/**
 * Traveling Salesman Problem using Bitmask Dynamic Programming
 * Difficulty: Hard
 * 
 * Given a list of cities and the distances between each pair of cities,
 * find the shortest possible route that visits each city exactly once and returns home.
 * 
 * This is a classic application of Bitmask DP where:
 * - Each bit in the mask represents whether a city has been visited
 * - dp[mask][i] = minimum cost to visit cities in mask, ending at city i
 * 
 * Time Complexity: O(n^2 * 2^n)
 * Space Complexity: O(n * 2^n)
 * 
 * Example:
 * Cities: 4 cities (0, 1, 2, 3)
 * Distance matrix represents cost between each pair
 */
public class BitmaskDP_TSP {
    
    private static final int INF = 1_000_000_000;
    
    /**
     * Solve TSP using Bitmask DP
     * @param dist Distance matrix where dist[i][j] is distance from city i to city j
     * @return Minimum cost to visit all cities and return to start
     */
    public int solveTSP(int[][] dist) {
        int n = dist.length;
        int FULL_MASK = (1 << n) - 1;
        
        // dp[mask][i] = minimum cost to visit cities in mask, ending at city i
        int[][] dp = new int[1 << n][n];
        
        // Initialize with infinity
        for (int[] row : dp) {
            java.util.Arrays.fill(row, INF);
        }
        
        // Base case: start from city 0
        dp[1][0] = 0; // mask=1 means only city 0 is visited
        
        // Fill DP table
        for (int mask = 1; mask <= FULL_MASK; mask++) {
            for (int last = 0; last < n; last++) {
                // Skip if city 'last' is not in current mask
                if ((mask & (1 << last)) == 0) continue;
                // Skip if this state is unreachable
                if (dp[mask][last] == INF) continue;
                
                // Try to extend to next unvisited city
                for (int next = 0; next < n; next++) {
                    // Skip if city 'next' is already visited
                    if ((mask & (1 << next)) != 0) continue;
                    
                    int newMask = mask | (1 << next);
                    dp[newMask][next] = Math.min(
                        dp[newMask][next],
                        dp[mask][last] + dist[last][next]
                    );
                }
            }
        }
        
        // Find minimum cost ending at any city, then return to start
        int result = INF;
        for (int i = 0; i < n; i++) {
            if (dp[FULL_MASK][i] != INF) {
                result = Math.min(result, dp[FULL_MASK][i] + dist[i][0]);
            }
        }
        
        return result == INF ? -1 : result;
    }
    
    /**
     * Get the actual path of TSP solution
     */
    public java.util.List<Integer> getTSPPath(int[][] dist) {
        int n = dist.length;
        int FULL_MASK = (1 << n) - 1;
        
        int[][] dp = new int[1 << n][n];
        int[][] parent = new int[1 << n][n]; // To reconstruct path
        
        for (int[] row : dp) {
            java.util.Arrays.fill(row, INF);
        }
        for (int[] row : parent) {
            java.util.Arrays.fill(row, -1);
        }
        
        dp[1][0] = 0;
        
        // Fill DP table with path tracking
        for (int mask = 1; mask <= FULL_MASK; mask++) {
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0 || dp[mask][last] == INF) continue;
                
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    
                    int newMask = mask | (1 << next);
                    int newCost = dp[mask][last] + dist[last][next];
                    
                    if (newCost < dp[newMask][next]) {
                        dp[newMask][next] = newCost;
                        parent[newMask][next] = last;
                    }
                }
            }
        }
        
        // Find best ending city
        int bestLast = -1;
        int minCost = INF;
        for (int i = 0; i < n; i++) {
            if (dp[FULL_MASK][i] + dist[i][0] < minCost) {
                minCost = dp[FULL_MASK][i] + dist[i][0];
                bestLast = i;
            }
        }
        
        // Reconstruct path
        java.util.List<Integer> path = new java.util.ArrayList<>();
        int currentMask = FULL_MASK;
        int current = bestLast;
        
        while (current != -1) {
            path.add(current);
            int prev = parent[currentMask][current];
            currentMask ^= (1 << current); // Remove current city from mask
            current = prev;
        }
        
        java.util.Collections.reverse(path);
        path.add(0); // Return to start
        
        return path;
    }
    
    /**
     * Optimized version using bottom-up approach
     */
    public int solveTSPOptimized(int[][] dist) {
        int n = dist.length;
        if (n == 0) return 0;
        if (n == 1) return 0;
        
        // Use array instead of 2D array for better cache performance
        int[] dp = new int[1 << n];
        int[] newDp = new int[1 << n];
        java.util.Arrays.fill(dp, INF);
        
        dp[1] = 0; // Start at city 0
        
        for (int len = 2; len <= n; len++) {
            java.util.Arrays.fill(newDp, INF);
            
            // Iterate through all masks with exactly 'len' bits set
            for (int mask = 1; mask < (1 << n); mask++) {
                if (Integer.bitCount(mask) != len) continue;
                if ((mask & 1) == 0) continue; // Must include city 0
                
                for (int last = 1; last < n; last++) {
                    if ((mask & (1 << last)) == 0) continue;
                    
                    int prevMask = mask ^ (1 << last);
                    if (dp[prevMask] == INF) continue;
                    
                    for (int prev = 0; prev < n; prev++) {
                        if ((prevMask & (1 << prev)) == 0) continue;
                        
                        newDp[mask] = Math.min(newDp[mask], 
                                             dp[prevMask] + dist[prev][last]);
                    }
                }
            }
            
            dp = newDp.clone();
        }
        
        int result = INF;
        int fullMask = (1 << n) - 1;
        for (int i = 1; i < n; i++) {
            if ((fullMask & (1 << i)) != 0) {
                result = Math.min(result, dp[fullMask] + dist[i][0]);
            }
        }
        
        return result == INF ? -1 : result;
    }
    
    /**
     * Memory-optimized version for large inputs
     */
    public int solveTSPMemoryOptimized(int[][] dist) {
        int n = dist.length;
        
        // Use map to store only reachable states
        java.util.Map<Integer, java.util.Map<Integer, Integer>> dp = new java.util.HashMap<>();
        
        // Initialize
        dp.put(1, new java.util.HashMap<>());
        dp.get(1).put(0, 0);
        
        for (int mask = 1; mask < (1 << n); mask++) {
            if (!dp.containsKey(mask)) continue;
            
            for (java.util.Map.Entry<Integer, Integer> entry : dp.get(mask).entrySet()) {
                int last = entry.getKey();
                int cost = entry.getValue();
                
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    
                    int newMask = mask | (1 << next);
                    int newCost = cost + dist[last][next];
                    
                    dp.computeIfAbsent(newMask, k -> new java.util.HashMap<>());
                    dp.get(newMask).put(next, 
                        Math.min(dp.get(newMask).getOrDefault(next, INF), newCost));
                }
            }
        }
        
        int fullMask = (1 << n) - 1;
        int result = INF;
        
        if (dp.containsKey(fullMask)) {
            for (java.util.Map.Entry<Integer, Integer> entry : dp.get(fullMask).entrySet()) {
                int last = entry.getKey();
                int cost = entry.getValue();
                result = Math.min(result, cost + dist[last][0]);
            }
        }
        
        return result == INF ? -1 : result;
    }
    
    /**
     * Utility: Generate random distance matrix
     */
    public static int[][] generateRandomDistanceMatrix(int n, int maxDistance) {
        int[][] dist = new int[n][n];
        java.util.Random rand = new java.util.Random(42); // Fixed seed for reproducibility
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dist[i][j] = 0;
                } else {
                    dist[i][j] = 1 + rand.nextInt(maxDistance);
                    dist[j][i] = dist[i][j]; // Make symmetric
                }
            }
        }
        
        return dist;
    }
    
    /**
     * Utility: Print distance matrix
     */
    public static void printDistanceMatrix(int[][] dist) {
        int n = dist.length;
        System.out.println("Distance Matrix:");
        System.out.print("   ");
        for (int i = 0; i < n; i++) {
            System.out.printf("%3d", i);
        }
        System.out.println();
        
        for (int i = 0; i < n; i++) {
            System.out.printf("%3d", i);
            for (int j = 0; j < n; j++) {
                System.out.printf("%3d", dist[i][j]);
            }
            System.out.println();
        }
    }
    
    /**
     * Test and demonstration method
     */
    public static void main(String[] args) {
        BitmaskDP_TSP solver = new BitmaskDP_TSP();
        
        // Test case 1: Small example
        int[][] dist1 = {
            {0, 10, 15, 20},
            {10, 0, 35, 25},
            {15, 35, 0, 30},
            {20, 25, 30, 0}
        };
        
        System.out.println("Test Case 1:");
        printDistanceMatrix(dist1);
        
        int result1 = solver.solveTSP(dist1);
        java.util.List<Integer> path1 = solver.getTSPPath(dist1);
        
        System.out.println("Minimum cost: " + result1);
        System.out.println("Path: " + path1);
        System.out.println();
        
        // Test case 2: Larger random example
        System.out.println("Test Case 2: Random 6-city problem");
        int[][] dist2 = generateRandomDistanceMatrix(6, 50);
        printDistanceMatrix(dist2);
        
        long startTime = System.nanoTime();
        int result2 = solver.solveTSP(dist2);
        long endTime = System.nanoTime();
        
        java.util.List<Integer> path2 = solver.getTSPPath(dist2);
        
        System.out.println("Minimum cost: " + result2);
        System.out.println("Path: " + path2);
        System.out.printf("Time taken: %.2f ms%n", (endTime - startTime) / 1_000_000.0);
        System.out.println();
        
        // Performance comparison
        System.out.println("Performance Comparison:");
        for (int n = 4; n <= 12; n++) {
            int[][] distMatrix = generateRandomDistanceMatrix(n, 100);
            
            startTime = System.nanoTime();
            int normalResult = solver.solveTSP(distMatrix);
            long normalTime = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            int optimizedResult = solver.solveTSPMemoryOptimized(distMatrix);
            long optimizedTime = System.nanoTime() - startTime;
            
            System.out.printf("n=%2d: Normal=%.2fms (cost=%d), Optimized=%.2fms (cost=%d)%n",
                n, normalTime / 1_000_000.0, normalResult,
                optimizedTime / 1_000_000.0, optimizedResult);
        }
        
        // Demonstrate bitmask operations used in TSP
        System.out.println("\nBitmask Operations Demo:");
        int n = 4;
        System.out.println("For " + n + " cities:");
        System.out.println("Full mask (all cities): " + ((1 << n) - 1) + 
                          " (binary: " + Integer.toBinaryString((1 << n) - 1) + ")");
        
        for (int mask = 1; mask < (1 << n); mask++) {
            System.out.printf("Mask %2d (binary: %4s): Cities visited: ", 
                            mask, Integer.toBinaryString(mask));
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    System.out.print(i + " ");
                }
            }
            System.out.println();
        }
    }
}

