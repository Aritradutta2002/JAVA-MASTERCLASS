package dsa.datastructures.stacksqueues.queue;

import java.util.*;

/*
 *   Author : Aritra
 *   LeetCode #621 - Task Scheduler (Medium)
 *   Problem: Find minimum time to complete all tasks with cooldown period
 *   Time Complexity: O(n), Space Complexity: O(1) - fixed 26 letters
 */

public class TaskScheduler {
    public static void main(String[] args) {
        TaskScheduler solution = new TaskScheduler();
        
        char[] tasks1 = {'A', 'A', 'A', 'B', 'B', 'B'};
        int n1 = 2;
        System.out.println("Tasks: " + Arrays.toString(tasks1) + ", n = " + n1);
        System.out.println("Minimum intervals: " + solution.leastInterval(tasks1, n1));
        // Expected: 8 (A -> B -> idle -> A -> B -> idle -> A -> B)
        
        char[] tasks2 = {'A', 'A', 'A', 'B', 'B', 'B'};
        int n2 = 0;
        System.out.println("\nTasks: " + Arrays.toString(tasks2) + ", n = " + n2);
        System.out.println("Minimum intervals: " + solution.leastInterval(tasks2, n2));
        // Expected: 6
    }
    
    public int leastInterval(char[] tasks, int n) {
        // Count frequency of each task
        int[] freq = new int[26];
        for (char task : tasks) {
            freq[task - 'A']++;
        }
        
        // Sort frequencies in descending order
        Arrays.sort(freq);
        
        // Get max frequency
        int maxFreq = freq[25];
        
        // Calculate number of idle slots needed
        int idleSlots = (maxFreq - 1) * n;
        
        // Fill idle slots with other tasks
        for (int i = 24; i >= 0 && freq[i] > 0; i--) {
            idleSlots -= Math.min(freq[i], maxFreq - 1);
        }
        
        // If there are still idle slots, add them to total time
        idleSlots = Math.max(0, idleSlots);
        
        return tasks.length + idleSlots;
    }
}

