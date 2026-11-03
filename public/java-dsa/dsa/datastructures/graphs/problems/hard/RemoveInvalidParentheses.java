package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode #301 - Remove Invalid Parentheses
 * 
 * Given a string s that contains parentheses ( and ) and English letters.
 * Return all possible results by removing the minimum number of invalid parentheses to
 * make the input string valid. The order of the results does not matter.
 * 
 * Time Complexity: O(N * 2^N)
 * Space Complexity: O(N * 2^N)
 */
public class RemoveInvalidParentheses {
    public List<String> removeInvalidParentheses(String s) {
        List<String> results = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(s);
        visited.add(s);
        boolean found = false;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                String current = queue.poll();
                
                // Is this string valid?
                if (isValid(current)) {
                    results.add(current);
                    found = true;
                }
                
                // If we've found a valid string on this level,
                // we don't need to generate shorter strings (next level).
                // But we MUST finish this level.
                if (found) continue;
                
                // Generate all neighbors (remove 1 char)
                for (int j = 0; j < current.length(); j++) {
                    char c = current.charAt(j);
                    // Only remove parentheses
                    if (c != '(' && c != ')') continue;
                    
                    String neighbor = current.substring(0, j) + current.substring(j + 1);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }
            
            // If we found results on this level, stop
            if (found) break;
        }
        
        return results;
    }
    
    // Helper to check if a string is valid
    private boolean isValid(String s) {
        int balance = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') {
                balance++;
            } else if (c == ')') {
                balance--;
                if (balance < 0) return false;
            }
        }
        return balance == 0;
    }
}

