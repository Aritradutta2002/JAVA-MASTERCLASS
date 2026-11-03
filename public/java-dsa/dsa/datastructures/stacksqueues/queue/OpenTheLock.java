package dsa.datastructures.stacksqueues.queue;

import java.util.*;

/*
 *   Author : Aritra
 *   LeetCode #752 - Open the Lock (Medium)
 *   Problem: Find minimum moves to unlock from "0000" avoiding deadends
 *   Time Complexity: O(10^4), Space Complexity: O(10^4)
 */

public class OpenTheLock {
    public static void main(String[] args) {
        OpenTheLock solution = new OpenTheLock();
        
        String[] deadends1 = {"0201", "0101", "0102", "1212", "2002"};
        String target1 = "0202";
        System.out.println("Deadends: " + Arrays.toString(deadends1));
        System.out.println("Target: " + target1);
        System.out.println("Minimum moves: " + solution.openLock(deadends1, target1));
        // Expected: 6
        
        String[] deadends2 = {"8888"};
        String target2 = "0009";
        System.out.println("\nDeadends: " + Arrays.toString(deadends2));
        System.out.println("Target: " + target2);
        System.out.println("Minimum moves: " + solution.openLock(deadends2, target2));
        // Expected: 1
        
        String[] deadends3 = {"8887", "8889", "8878", "8898", "8788", "8988", "7888", "9888"};
        String target3 = "8888";
        System.out.println("\nDeadends: " + Arrays.toString(deadends3));
        System.out.println("Target: " + target3);
        System.out.println("Minimum moves: " + solution.openLock(deadends3, target3));
        // Expected: -1
    }
    
    public int openLock(String[] deadends, String target) {
        Set<String> dead = new HashSet<>(Arrays.asList(deadends));
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        String start = "0000";
        
        if (dead.contains(start)) {
            return -1;
        }
        
        queue.offer(start);
        visited.add(start);
        int moves = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                String curr = queue.poll();
                
                if (curr.equals(target)) {
                    return moves;
                }
                
                // Generate all possible next combinations
                for (int j = 0; j < 4; j++) {
                    // Turn wheel up
                    String next1 = turnUp(curr, j);
                    if (!visited.contains(next1) && !dead.contains(next1)) {
                        queue.offer(next1);
                        visited.add(next1);
                    }
                    
                    // Turn wheel down
                    String next2 = turnDown(curr, j);
                    if (!visited.contains(next2) && !dead.contains(next2)) {
                        queue.offer(next2);
                        visited.add(next2);
                    }
                }
            }
            moves++;
        }
        
        return -1;
    }
    
    private String turnUp(String s, int pos) {
        char[] chars = s.toCharArray();
        chars[pos] = chars[pos] == '9' ? '0' : (char) (chars[pos] + 1);
        return new String(chars);
    }
    
    private String turnDown(String s, int pos) {
        char[] chars = s.toCharArray();
        chars[pos] = chars[pos] == '0' ? '9' : (char) (chars[pos] - 1);
        return new String(chars);
    }
}

