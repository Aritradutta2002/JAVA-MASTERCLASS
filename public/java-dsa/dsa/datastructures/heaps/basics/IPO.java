package dsa.datastructures.heaps.basics;

import java.util.*;

/**
 * IPO (LeetCode 502) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Maximize capital by selecting at most k projects
 * Time: O(n log n), Space: O(n)
 * 
 * Key: Two heaps - min heap for capital, max heap for profit
 */
public class IPO {
    
    public int findMaximizedCapital(int k, int w, int[] profits, int[] capital) {
        int n = profits.length;
        
        // Min heap for projects sorted by capital requirement
        PriorityQueue<int[]> minCapital = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        
        // Max heap for available projects sorted by profit
        PriorityQueue<Integer> maxProfit = new PriorityQueue<>((a, b) -> b - a);
        
        // Add all projects to min heap
        for (int i = 0; i < n; i++) {
            minCapital.offer(new int[]{capital[i], profits[i]});
        }
        
        // Select k projects
        for (int i = 0; i < k; i++) {
            // Move all affordable projects to max profit heap
            while (!minCapital.isEmpty() && minCapital.peek()[0] <= w) {
                maxProfit.offer(minCapital.poll()[1]);
            }
            
            // If no project available, break
            if (maxProfit.isEmpty()) {
                break;
            }
            
            // Pick project with maximum profit
            w += maxProfit.poll();
        }
        
        return w;
    }
    
    public static void main(String[] args) {
        IPO solution = new IPO();
        
        // Test Case 1
        int[] profits1 = {1, 2, 3};
        int[] capital1 = {0, 1, 1};
        System.out.println(solution.findMaximizedCapital(2, 0, profits1, capital1)); // 4
        
        // Test Case 2
        int[] profits2 = {1, 2, 3};
        int[] capital2 = {0, 1, 2};
        System.out.println(solution.findMaximizedCapital(3, 0, profits2, capital2)); // 6
    }
}

