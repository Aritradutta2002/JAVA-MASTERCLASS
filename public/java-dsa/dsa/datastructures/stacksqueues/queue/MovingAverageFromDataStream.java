package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #346 - Moving Average from Data Stream (Easy - Premium)
 *   Problem: Calculate moving average of last n elements
 *   Time Complexity: O(1), Space Complexity: O(n)
 */

public class MovingAverageFromDataStream {
    public static void main(String[] args) {
        MovingAverage movingAverage = new MovingAverage(3);
        
        System.out.println("next(1): " + movingAverage.next(1));   // 1.0
        System.out.println("next(10): " + movingAverage.next(10)); // 5.5
        System.out.println("next(3): " + movingAverage.next(3));   // 4.66667
        System.out.println("next(5): " + movingAverage.next(5));   // 6.0 (avg of 10, 3, 5)
    }
    
    static class MovingAverage {
        private Queue<Integer> queue;
        private int size;
        private double sum;
        
        public MovingAverage(int size) {
            this.size = size;
            this.queue = new LinkedList<>();
            this.sum = 0;
        }
        
        public double next(int val) {
            queue.offer(val);
            sum += val;
            
            // If queue exceeds size, remove oldest element
            if (queue.size() > size) {
                sum -= queue.poll();
            }
            
            return sum / queue.size();
        }
    }
    
    // Alternative implementation using circular array
    static class MovingAverageOptimized {
        private int[] window;
        private int count;
        private int insert;
        private long sum;
        
        public MovingAverageOptimized(int size) {
            window = new int[size];
            count = 0;
            insert = 0;
            sum = 0;
        }
        
        public double next(int val) {
            if (count < window.length) {
                count++;
            } else {
                sum -= window[insert];
            }
            
            window[insert] = val;
            sum += val;
            insert = (insert + 1) % window.length;
            
            return (double) sum / count;
        }
    }
}

