package dsa.datastructures.stacksqueues.queue;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #1306 - Jump Game III (Medium)
 *   Problem: Determine if you can reach index with value 0 (BFS approach)
 *   Time Complexity: O(n), Space Complexity: O(n)
 */

public class JumpGameIII {
    public static void main(String[] args) {
        JumpGameIII solution = new JumpGameIII();
        
        int[] arr1 = {4, 2, 3, 0, 3, 1, 2};
        int start1 = 5;
        System.out.println("Array: " + Arrays.toString(arr1) + ", start = " + start1);
        System.out.println("Can reach zero: " + solution.canReach(arr1, start1));
        // Expected: true (5 -> 4 -> 1 -> 3)
        
        int[] arr2 = {4, 2, 3, 0, 3, 1, 2};
        int start2 = 0;
        System.out.println("\nArray: " + Arrays.toString(arr2) + ", start = " + start2);
        System.out.println("Can reach zero: " + solution.canReach(arr2, start2));
        // Expected: true
        
        int[] arr3 = {3, 0, 2, 1, 2};
        int start3 = 2;
        System.out.println("\nArray: " + Arrays.toString(arr3) + ", start = " + start3);
        System.out.println("Can reach zero: " + solution.canReach(arr3, start3));
        // Expected: false
    }
    
    // BFS Solution
    public boolean canReach(int[] arr, int start) {
        int n = arr.length;
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.offer(start);
        visited[start] = true;
        
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            
            // Check if current value is 0
            if (arr[curr] == 0) {
                return true;
            }
            
            // Jump forward
            int forward = curr + arr[curr];
            if (forward < n && !visited[forward]) {
                queue.offer(forward);
                visited[forward] = true;
            }
            
            // Jump backward
            int backward = curr - arr[curr];
            if (backward >= 0 && !visited[backward]) {
                queue.offer(backward);
                visited[backward] = true;
            }
        }
        
        return false;
    }
    
    // DFS Alternative Solution
    public boolean canReachDFS(int[] arr, int start) {
        if (start < 0 || start >= arr.length || arr[start] < 0) {
            return false;
        }
        
        if (arr[start] == 0) {
            return true;
        }
        
        // Mark as visited by making it negative
        int val = arr[start];
        arr[start] = -val;
        
        return canReachDFS(arr, start + val) || canReachDFS(arr, start - val);
    }
}

