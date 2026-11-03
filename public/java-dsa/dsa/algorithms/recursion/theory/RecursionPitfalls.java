package dsa.algorithms.recursion.theory;

/**
 * RecursionPitfalls - Common mistakes and how to avoid them.
 *
 * Use this checklist while drafting recursive solutions in interviews.
 */
public final class RecursionPitfalls {

    private RecursionPitfalls() {
        // Utility class
    }

    /**
     * Highlights typical mistakes with base cases, parameter updates, and stack depth.
     */
    public static void printPitfalls() {
        System.out.println("=== Recursion Pitfalls Checklist ===");
        System.out.println("1. Missing or incorrect base case -> leads to infinite recursion");
        System.out.println("2. Mutating shared state without backtracking -> corrupts branches");
        System.out.println("3. Ignoring stack depth limits -> plan for tail recursion or iteration");
        System.out.println("4. Recomputing overlapping subproblems -> consider memoization");
        System.out.println("5. Off-by-one errors -> double-check inclusive/exclusive indices");
        System.out.println("6. Forgetting to copy mutable structures when storing results");
        System.out.println("7. Not analyzing time/space complexity -> be ready to discuss growth");
    }

    /**
     * Demonstrates converting a recursive algorithm to iteration using an explicit stack.
     * Useful when interviewers ask for stack-safe alternatives.
     */
    public static int factorialIterative(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    public static int factorialRecursive(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        if (n <= 1) {
            return 1;
        }
        return n * factorialRecursive(n - 1);
    }

    public static void main(String[] args) {
        printPitfalls();

        System.out.println("\n=== Recursive vs Iterative Factorial ===");
        int sample = 5;
        System.out.println("Recursive(" + sample + ") = " + factorialRecursive(sample));
        System.out.println("Iterative(" + sample + ") = " + factorialIterative(sample));
    }
}


