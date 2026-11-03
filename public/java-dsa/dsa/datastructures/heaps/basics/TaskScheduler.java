package dsa.datastructures.heaps.basics;

import java.util.*;

/**
 * Task Scheduler (LeetCode 621) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft)
 * 
 * Problem: Find minimum time to execute all tasks with cooling period
 * Time: O(n), Space: O(1)
 */
public class TaskScheduler {
    
    // Approach 1: Using Max Heap and Queue
    public int leastInterval(char[] tasks, int n) {
        // Count frequency of each task
        int[] freq = new int[26];
        for (char task : tasks) {
            freq[task - 'A']++;
        }
        
        // Max heap for task frequencies
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        for (int f : freq) {
            if (f > 0) maxHeap.offer(f);
        }
        
        // Queue to store tasks in cooling period [frequency, availableTime]
        Queue<int[]> queue = new LinkedList<>();
        
        int time = 0;
        
        while (!maxHeap.isEmpty() || !queue.isEmpty()) {
            time++;
            
            if (!maxHeap.isEmpty()) {
                int count = maxHeap.poll() - 1;
                if (count > 0) {
                    queue.offer(new int[]{count, time + n});
                }
            }
            
            // Check if any task is ready from cooling
            if (!queue.isEmpty() && queue.peek()[1] == time) {
                maxHeap.offer(queue.poll()[0]);
            }
        }
        
        return time;
    }
    
    // Approach 2: Mathematical (Most Optimal)
    public int leastIntervalMath(char[] tasks, int n) {
        int[] freq = new int[26];
        int maxFreq = 0;
        
        for (char task : tasks) {
            freq[task - 'A']++;
            maxFreq = Math.max(maxFreq, freq[task - 'A']);
        }
        
        // Count tasks with max frequency
        int maxCount = 0;
        for (int f : freq) {
            if (f == maxFreq) maxCount++;
        }
        
        // Calculate minimum time
        int partCount = maxFreq - 1;
        int partLength = n - (maxCount - 1);
        int emptySlots = partCount * partLength;
        int availableTasks = tasks.length - maxFreq * maxCount;
        int idles = Math.max(0, emptySlots - availableTasks);
        
        return tasks.length + idles;
    }
    
    // Simplified mathematical approach
    public int leastIntervalSimple(char[] tasks, int n) {
        int[] freq = new int[26];
        for (char task : tasks) {
            freq[task - 'A']++;
        }
        
        Arrays.sort(freq);
        int maxFreq = freq[25];
        int idleTime = (maxFreq - 1) * n;
        
        for (int i = 24; i >= 0 && freq[i] > 0; i--) {
            idleTime -= Math.min(freq[i], maxFreq - 1);
        }
        
        return idleTime > 0 ? idleTime + tasks.length : tasks.length;
    }
    
    public static void main(String[] args) {
        TaskScheduler solution = new TaskScheduler();
        
        // Test Case 1
        char[] tasks1 = {'A', 'A', 'A', 'B', 'B', 'B'};
        System.out.println(solution.leastInterval(tasks1, 2)); // 8
        
        // Test Case 2
        char[] tasks2 = {'A', 'A', 'A', 'B', 'B', 'B'};
        System.out.println(solution.leastIntervalMath(tasks2, 0)); // 6
        
        // Test Case 3
        char[] tasks3 = {'A', 'A', 'A', 'A', 'A', 'A', 'B', 'C', 'D', 'E', 'F', 'G'};
        System.out.println(solution.leastInterval(tasks3, 2)); // 16
    }
}

