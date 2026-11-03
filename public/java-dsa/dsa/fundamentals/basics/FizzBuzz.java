package dsa.fundamentals.basics;
/**
 * FizzBuzz - Classic Interview Problem
 * 
 * Problem: Print numbers from 1 to n, but:
 * - For multiples of 3, print "Fizz"
 * - For multiples of 5, print "Buzz"
 * - For multiples of both 3 and 5, print "FizzBuzz"
 * - Otherwise, print the number
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(1)
 * 
 * Interview Tips:
 * - This is often used as a warm-up question
 * - Shows basic understanding of conditionals and loops
 * - Can be extended with more conditions
 */

public class FizzBuzz {
    
    // Approach 1: Simple if-else chain
    public static void fizzBuzz(int n) {
        for (int i = 1; i <= n; i++) {
            if (i % 15 == 0) {
                System.out.println("FizzBuzz");
            } else if (i % 3 == 0) {
                System.out.println("Fizz");
            } else if (i % 5 == 0) {
                System.out.println("Buzz");
            } else {
                System.out.println(i);
            }
        }
    }
    
    // Approach 2: String concatenation (more scalable)
    public static void fizzBuzzOptimized(int n) {
        for (int i = 1; i <= n; i++) {
            String result = "";
            
            if (i % 3 == 0) result += "Fizz";
            if (i % 5 == 0) result += "Buzz";
            
            System.out.println(result.isEmpty() ? i : result);
        }
    }
    
    // Approach 3: Using List for return values (LeetCode style)
    public static java.util.List<String> fizzBuzzList(int n) {
        java.util.List<String> result = new java.util.ArrayList<>();
        
        for (int i = 1; i <= n; i++) {
            String output = "";
            
            if (i % 3 == 0) output += "Fizz";
            if (i % 5 == 0) output += "Buzz";
            
            result.add(output.isEmpty() ? String.valueOf(i) : output);
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        System.out.println("=== FizzBuzz (1 to 15) ===");
        fizzBuzz(15);
        
        System.out.println("\n=== FizzBuzz Optimized (1 to 15) ===");
        fizzBuzzOptimized(15);
        
        System.out.println("\n=== FizzBuzz List (1 to 15) ===");
        System.out.println(fizzBuzzList(15));
    }
}

