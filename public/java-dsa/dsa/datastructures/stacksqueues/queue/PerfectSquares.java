package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #279 - Perfect Squares (Medium)
 *   Problem: Find least number of perfect squares that sum to n
 *   Time Complexity: O(n * sqrt(n)), Space Complexity: O(n)
 */

public class PerfectSquares {
    public static void main(String[] args) {
        PerfectSquares solution = new PerfectSquares();
        
        int n1 = 12;
        System.out.println("n = " + n1);
        System.out.println("Least perfect squares: " + solution.numSquares(n1));
        System.out.println("DP Solution: " + solution.numSquaresDP(n1));
        // Expected: 3 (4 + 4 + 4)
        
        int n2 = 13;
        System.out.println("\nn = " + n2);
        System.out.println("Least perfect squares: " + solution.numSquares(n2));
        System.out.println("DP Solution: " + solution.numSquaresDP(n2));
        // Expected: 2 (4 + 9)
    }
    
    // BFS Solution
    public int numSquares(int n) {
        Queue<Integer> queue = new LinkedList<>();
        boolean[] visited = new boolean[n + 1];
        
        queue.offer(0);
        visited[0] = true;
        int level = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            level++;
            
            for (int i = 0; i < size; i++) {
                int curr = queue.poll();
                
                // Try all perfect squares
                for (int j = 1; j * j <= n; j++) {
                    int next = curr + j * j;
                    
                    if (next == n) {
                        return level;
                    }
                    
                    if (next < n && !visited[next]) {
                        queue.offer(next);
                        visited[next] = true;
                    }
                }
            }
        }
        
        return level;
    }
    
    // DP Solution
    public int numSquaresDP(int n) {
        int[] dp = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            dp[i] = i; // worst case: all 1s
            
            for (int j = 1; j * j <= i; j++) {
                dp[i] = Math.min(dp[i], dp[i - j * j] + 1);
            }
        }
        
        return dp[n];
    }
}

