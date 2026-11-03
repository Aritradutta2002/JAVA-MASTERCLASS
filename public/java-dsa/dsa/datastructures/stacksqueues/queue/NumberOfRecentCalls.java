package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #933 - Number of Recent Calls (Easy)
 *   Problem: Count requests in the past 3000ms using a queue
 *   Time Complexity: O(1) amortized, Space Complexity: O(n)
 */

public class NumberOfRecentCalls {
    public static void main(String[] args) {
        RecentCounter counter = new RecentCounter();
        
        System.out.println("ping(1): " + counter.ping(1));       // 1
        System.out.println("ping(100): " + counter.ping(100));   // 2
        System.out.println("ping(3001): " + counter.ping(3001)); // 3
        System.out.println("ping(3002): " + counter.ping(3002)); // 3
    }
    
    static class RecentCounter {
        private Queue<Integer> queue;
        
        public RecentCounter() {
            queue = new LinkedList<>();
        }
        
        public int ping(int t) {
            queue.offer(t);
            
            // Remove all pings older than 3000ms
            while (!queue.isEmpty() && queue.peek() < t - 3000) {
                queue.poll();
            }
            
            return queue.size();
        }
    }
}

