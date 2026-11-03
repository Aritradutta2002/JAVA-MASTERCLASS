package dsa.algorithms.recursion.theory;

/**
 * RecursionBasics - Core patterns and terminology for recursion interviews.
 *
 * Covers:
 * 1. Linear recursion (counting up/down)
 * 2. Divide and conquer (binary search template)
 * 3. Tail recursion discussions
 *
 * Use this file as a quick refresher before interviews.
 */
public final class RecursionBasics {

    private RecursionBasics() {
        // Utility class
    }

    /**
     * Prints numbers from {@code current} to {@code target} using linear recursion.
     * Demonstrates: base case, pre-order work, recursive call.
     */
    public static void printIncreasing(int current, int target) {
        if (current > target) {
            return; // base case
        }
        System.out.println(current); // work before recursion
        printIncreasing(current + 1, target);
    }

    /**
     * Prints numbers from {@code target} down to {@code current} highlighting post-order work.
     */
    public static void printDecreasing(int current, int target) {
        if (current > target) {
            return;
        }
        printDecreasing(current + 1, target); // recurse to the end
        System.out.println(current); // work after recursion
    }

    /**
     * Binary search template showing divide-and-conquer recursion.
     * Time Complexity: O(log n), Space: O(log n) call stack.
     */
    public static int binarySearch(int[] arr, int target, int low, int high) {
        if (low > high) {
            return -1;
        }
        int mid = low + (high - low) / 2;
        if (arr[mid] == target) {
            return mid;
        }
        if (arr[mid] > target) {
            return binarySearch(arr, target, low, mid - 1);
        }
        return binarySearch(arr, target, mid + 1, high);
    }

    /**
     * Illustrates tail recursion: all work performed before the recursive call.
     * Many languages optimize tail calls; Java does not, but discussing it is valuable.
     */
    public static int sumTailRecursive(int n) {
        return sumTailHelper(n, 0);
    }

    private static int sumTailHelper(int n, int accumulator) {
        if (n <= 0) {
            return accumulator;
        }
        return sumTailHelper(n - 1, accumulator + n);
    }

    public static void main(String[] args) {
        System.out.println("=== Linear Recursion (Increasing) ===");
        printIncreasing(1, 5);

        System.out.println("\n=== Linear Recursion (Decreasing) ===");
        printDecreasing(1, 5);

        System.out.println("\n=== Binary Search (Divide & Conquer) ===");
        int[] sorted = {1, 3, 5, 7, 9, 11};
        System.out.println("Index of 7: " + binarySearch(sorted, 7, 0, sorted.length - 1));

        System.out.println("\n=== Tail Recursion Sum ===");
        System.out.println("Sum 1..5 = " + sumTailRecursive(5));
    }
}


