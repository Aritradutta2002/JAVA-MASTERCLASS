package dsa.fundamentals.basics;

/**
 * Palindrome Check - Same Forward and Backward
 *
 * Problem: Determine whether a string or integer reads the same backward as forward.
 * Interview Angles:
 * - Explores string manipulation and pointer techniques
 * - Bridges string and numeric palindrome checks
 * - Encourages discussions on normalization (case/whitespace handling)
 */
public class PalindromeCheck {

    // Approach 1: Reverse string using StringBuilder
    // Time: O(n), Space: O(n)
    public static boolean isPalindromeUsingBuilder(String input) {
        String normalized = normalize(input);
        String reversed = new StringBuilder(normalized).reverse().toString();
        return normalized.equals(reversed);
    }

    // Approach 2: Two-pointer technique (in-place feeling)
    // Time: O(n), Space: O(1)
    public static boolean isPalindromeTwoPointer(String input) {
        String normalized = normalize(input);
        int left = 0;
        int right = normalized.length() - 1;
        while (left < right) {
            if (normalized.charAt(left) != normalized.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }

    // Approach 3: Recursive check
    // Time: O(n), Space: O(n) due to call stack
    public static boolean isPalindromeRecursive(String input) {
        String normalized = normalize(input);
        return isPalindromeRecursiveHelper(normalized, 0, normalized.length() - 1);
    }

    private static boolean isPalindromeRecursiveHelper(String input, int left, int right) {
        if (left >= right) {
            return true;
        }
        if (input.charAt(left) != input.charAt(right)) {
            return false;
        }
        return isPalindromeRecursiveHelper(input, left + 1, right - 1);
    }

    // Approach 4: Numeric palindrome using arithmetic reversal
    // Time: O(d), Space: O(1), where d is number of digits
    public static boolean isNumericPalindrome(int number) {
        if (number < 0) {
            return false; // negatives fail due to '-' sign
        }
        int original = number;
        int reversed = 0;
        while (number > 0) {
            int digit = number % 10;
            if (reversed > (Integer.MAX_VALUE - digit) / 10) {
                return false; // overflow guard
            }
            reversed = reversed * 10 + digit;
            number /= 10;
        }
        return original == reversed;
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim().toLowerCase();
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (char c : trimmed.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        String sample = "Able was I ere I saw Elba";
        String nonPalindrome = "Interview";
        int numericSample = 12321;
        int numericNonPalindrome = 12345;

        System.out.println("=== Palindrome Using StringBuilder ===");
        System.out.println(sample + " -> " + isPalindromeUsingBuilder(sample));

        System.out.println("\n=== Palindrome Using Two Pointers ===");
        System.out.println(nonPalindrome + " -> " + isPalindromeTwoPointer(nonPalindrome));

        System.out.println("\n=== Palindrome Using Recursion ===");
        System.out.println(sample + " -> " + isPalindromeRecursive(sample));

        System.out.println("\n=== Numeric Palindrome Check ===");
        System.out.println(numericSample + " -> " + isNumericPalindrome(numericSample));
        System.out.println(numericNonPalindrome + " -> " + isNumericPalindrome(numericNonPalindrome));
    }
}


