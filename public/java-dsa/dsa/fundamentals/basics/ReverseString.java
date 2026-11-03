package dsa.fundamentals.basics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Reverse String - Flip Characters Order
 *
 * Problem: Reverse a given string.
 * Interview Angles:
 * - Highlights mutability vs immutability in Java strings
 * - Encourages discussing time/space trade-offs in different techniques
 * - Bridges recursion, iteration, and data structure-based approaches
 */
public class ReverseString {

    // Approach 1: Convert to char array and swap in place
    // Time: O(n), Space: O(n) due to char array copy
    public static String reverseUsingCharArray(String input) {
        if (input == null) {
            return null;
        }
        char[] chars = input.toCharArray();
        int left = 0;
        int right = chars.length - 1;
        while (left < right) {
            char temp = chars[left];
            chars[left] = chars[right];
            chars[right] = temp;
            left++;
            right--;
        }
        return new String(chars);
    }

    // Approach 2: StringBuilder reverse (leverages mutable builder)
    // Time: O(n), Space: O(n)
    public static String reverseUsingBuilder(String input) {
        if (input == null) {
            return null;
        }
        return new StringBuilder(input).reverse().toString();
    }

    // Approach 3: Recursion
    // Time: O(n^2) due to string concatenation, Space: O(n) call stack
    public static String reverseUsingRecursion(String input) {
        if (input == null || input.length() <= 1) {
            return input;
        }
        return reverseUsingRecursion(input.substring(1)) + input.charAt(0);
    }

    // Approach 4: Stack (Deque) to emphasize LIFO behavior
    // Time: O(n), Space: O(n)
    public static String reverseUsingStack(String input) {
        if (input == null) {
            return null;
        }
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : input.toCharArray()) {
            stack.push(c);
        }
        StringBuilder reversed = new StringBuilder(input.length());
        while (!stack.isEmpty()) {
            reversed.append(stack.pop());
        }
        return reversed.toString();
    }

    public static void main(String[] args) {
        String sample = "Interview";

        System.out.println("=== Reverse Using Char Array ===");
        System.out.println(reverseUsingCharArray(sample));

        System.out.println("\n=== Reverse Using StringBuilder ===");
        System.out.println(reverseUsingBuilder(sample));

        System.out.println("\n=== Reverse Using Recursion ===");
        System.out.println(reverseUsingRecursion(sample));

        System.out.println("\n=== Reverse Using Stack ===");
        System.out.println(reverseUsingStack(sample));

        // Discuss: recursion cost vs iterative efficiency vs built-in utilities.
    }
}


