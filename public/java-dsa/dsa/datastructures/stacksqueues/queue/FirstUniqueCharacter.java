package dsa.datastructures.stacksqueues.queue;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #387 - First Unique Character in a String (Easy)
 *   Problem: Find first non-repeating character in a string
 *   Time Complexity: O(n), Space Complexity: O(n)
 */

public class FirstUniqueCharacter {
    public static void main(String[] args) {
        FirstUniqueCharacter solution = new FirstUniqueCharacter();
        
        String s1 = "leetcode";
        System.out.println("Input: " + s1);
        System.out.println("First unique character index: " + solution.firstUniqChar(s1));
        System.out.println("First unique character: " + solution.firstUniqCharActual(s1));
        // Expected: 0 (l)
        
        String s2 = "loveleetcode";
        System.out.println("\nInput: " + s2);
        System.out.println("First unique character index: " + solution.firstUniqChar(s2));
        System.out.println("First unique character: " + solution.firstUniqCharActual(s2));
        // Expected: 2 (v)
        
        String s3 = "aabb";
        System.out.println("\nInput: " + s3);
        System.out.println("First unique character index: " + solution.firstUniqChar(s3));
        // Expected: -1
    }
    
    // Method 1: Using HashMap
    public int firstUniqChar(String s) {
        Map<Character, Integer> freq = new HashMap<>();
        
        // Count frequency
        for (char c : s.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }
        
        // Find first unique
        for (int i = 0; i < s.length(); i++) {
            if (freq.get(s.charAt(i)) == 1) {
                return i;
            }
        }
        
        return -1;
    }
    
    // Method 2: Using Queue (Stream processing approach)
    public char firstUniqCharActual(String s) {
        Map<Character, Integer> freq = new HashMap<>();
        Queue<Character> queue = new LinkedList<>();
        
        for (char c : s.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
            queue.offer(c);
            
            // Remove characters from front that are no longer unique
            while (!queue.isEmpty() && freq.get(queue.peek()) > 1) {
                queue.poll();
            }
        }
        
        return queue.isEmpty() ? '\0' : queue.peek();
    }
}

