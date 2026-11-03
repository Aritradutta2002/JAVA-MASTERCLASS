package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #362 - Design Hit Counter (Medium - Premium)
 *   Problem: Design a hit counter that counts hits in the past 5 minutes
 *   Time Complexity: O(1) amortized for hit, O(n) for getHits (n = hits in 300s)
 */

public class DesignHitCounter {
    public static void main(String[] args) {
        HitCounter counter = new HitCounter();
        
        counter.hit(1);       // hit at timestamp 1
        counter.hit(2);       // hit at timestamp 2
        counter.hit(3);       // hit at timestamp 3
        System.out.println("Hits at 4: " + counter.getHits(4));    // 3 (all in last 300s)
        
        counter.hit(300);     // hit at timestamp 300
        System.out.println("Hits at 300: " + counter.getHits(300)); // 4
        System.out.println("Hits at 301: " + counter.getHits(301)); // 3 (hit at 1 is now 300s old)
    }
    
    static class HitCounter {
        private Queue<Integer> queue;
        
        public HitCounter() {
            queue = new LinkedList<>();
        }
        
        // Record a hit at timestamp
        public void hit(int timestamp) {
            queue.offer(timestamp);
        }
        
        // Get number of hits in the past 5 minutes (300 seconds)
        public int getHits(int timestamp) {
            // Remove all hits older than 300 seconds
            while (!queue.isEmpty() && queue.peek() <= timestamp - 300) {
                queue.poll();
            }
            return queue.size();
        }
    }
    
    // Optimized approach using circular buffer
    static class HitCounterOptimized {
        private int[] times;
        private int[] hits;
        
        public HitCounterOptimized() {
            times = new int[300];
            hits = new int[300];
        }
        
        public void hit(int timestamp) {
            int idx = timestamp % 300;
            if (times[idx] != timestamp) {
                times[idx] = timestamp;
                hits[idx] = 1;
            } else {
                hits[idx]++;
            }
        }
        
        public int getHits(int timestamp) {
            int total = 0;
            for (int i = 0; i < 300; i++) {
                if (timestamp - times[i] < 300) {
                    total += hits[i];
                }
            }
            return total;
        }
    }
}

