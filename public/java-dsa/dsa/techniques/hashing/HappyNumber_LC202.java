package dsa.techniques.hashing;

/*
 * Problem: Happy Number
 * LeetCode: #202
 * Difficulty: Easy
 * Companies: Amazon, Google, Uber
 * 
 * Problem Statement:
 * Write an algorithm to determine if a number n is happy.
 * A happy number is a number defined by the following process:
 * - Starting with any positive integer, replace the number by the sum of the 
 *   squares of its digits.
 * - Repeat the process until the number equals 1 (where it will stay), or it 
 *   loops endlessly in a cycle which does not include 1.
 * - Those numbers for which this process ends in 1 are happy.
 * Return true if n is a happy number, and false if not.
 *
 * Example 1:
 * Input: n = 19
 * Output: true
 * Explanation:
 * 1^2 + 9^2 = 82
 * 8^2 + 2^2 = 68
 * 6^2 + 8^2 = 100
 * 1^2 + 0^2 + 0^2 = 1
 *
 * Example 2:
 * Input: n = 2
 * Output: false
 *
 * Constraints:
 * - 1 <= n <= 2^31 - 1
 *
 * Time Complexity: O(log n) - number of digits
 * Space Complexity: O(log n) for HashSet
 *
 * Approach:
 * Use HashSet to detect cycles. If we see a number we've seen before, we're 
 * in a cycle and it's not a happy number.
 */

import java.util.*;

public class HappyNumber_LC202 {

    // Solution using HashSet to detect cycle
    public boolean isHappy(int n) {
        Set<Integer> seen = new HashSet<>();

        while (n != 1 && !seen.contains(n)) {
            seen.add(n);
            n = getNext(n);
        }

        return n == 1;
    }

    private int getNext(int n) {
        int sum = 0;

        while (n > 0) {
            int digit = n % 10;
            sum += digit * digit;
            n /= 10;
        }

        return sum;
    }

    // Alternative: Floyd's Cycle Detection (Slow and Fast pointers)
    // Space: O(1)
    public boolean isHappyFloyd(int n) {
        int slow = n;
        int fast = n;

        do {
            slow = getNext(slow);
            fast = getNext(getNext(fast));
        } while (slow != fast);

        return slow == 1;
    }

    // Alternative: Hardcoded cycle detection
    // There are only a few cycles possible
    public boolean isHappyHardcoded(int n) {
        Set<Integer> cycles = new HashSet<>(Arrays.asList(
                4, 16, 37, 58, 89, 145, 42, 20));

        while (n != 1 && !cycles.contains(n)) {
            n = getNext(n);
        }

        return n == 1;
    }

    public static void main(String[] args) {
        HappyNumber_LC202 solution = new HappyNumber_LC202();

        // Test Case 1
        System.out.println("Test 1: " + solution.isHappy(19)); // true

        // Test Case 2
        System.out.println("Test 2: " + solution.isHappy(2)); // false

        // Test Case 3
        System.out.println("Test 3: " + solution.isHappy(1)); // true

        // Test Case 4
        System.out.println("Test 4: " + solution.isHappy(7)); // true

        // Test Floyd's solution
        System.out.println("\nFloyd's Solution:");
        System.out.println("Test 1: " + solution.isHappyFloyd(19)); // true
        System.out.println("Test 2: " + solution.isHappyFloyd(2)); // false
    }
}

