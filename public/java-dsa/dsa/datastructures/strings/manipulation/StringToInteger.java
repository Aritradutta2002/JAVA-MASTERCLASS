package dsa.datastructures.strings.manipulation;

/**
 * String to Integer (atoi) (LeetCode 8) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Microsoft, Facebook, Apple)
 * 
 * Problem: Convert string to integer with edge cases
 * Time: O(n), Space: O(1)
 */
public class StringToInteger {
    
    public int myAtoi(String s) {
        if (s == null || s.length() == 0) return 0;
        
        int i = 0, n = s.length();
        
        // Step 1: Skip leading whitespace
        while (i < n && s.charAt(i) == ' ') {
            i++;
        }
        
        if (i == n) return 0;
        
        // Step 2: Check sign
        int sign = 1;
        if (s.charAt(i) == '+' || s.charAt(i) == '-') {
            sign = s.charAt(i) == '-' ? -1 : 1;
            i++;
        }
        
        // Step 3: Convert digits
        long result = 0;
        while (i < n && Character.isDigit(s.charAt(i))) {
            result = result * 10 + (s.charAt(i) - '0');
            
            // Check overflow
            if (sign == 1 && result > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (sign == -1 && -result < Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            
            i++;
        }
        
        return (int) (sign * result);
    }
    
    // Alternative: Without using long
    public int myAtoiNoLong(String s) {
        if (s == null || s.length() == 0) return 0;
        
        int i = 0, n = s.length();
        
        // Skip whitespace
        while (i < n && s.charAt(i) == ' ') i++;
        if (i == n) return 0;
        
        // Check sign
        int sign = 1;
        if (s.charAt(i) == '+' || s.charAt(i) == '-') {
            sign = s.charAt(i) == '-' ? -1 : 1;
            i++;
        }
        
        int result = 0;
        while (i < n && Character.isDigit(s.charAt(i))) {
            int digit = s.charAt(i) - '0';
            
            // Check overflow before multiplication
            if (result > Integer.MAX_VALUE / 10 || 
                (result == Integer.MAX_VALUE / 10 && digit > Integer.MAX_VALUE % 10)) {
                return sign == 1 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            }
            
            result = result * 10 + digit;
            i++;
        }
        
        return sign * result;
    }
    
    public static void main(String[] args) {
        StringToInteger solution = new StringToInteger();
        
        System.out.println(solution.myAtoi("42")); // 42
        System.out.println(solution.myAtoi("   -42")); // -42
        System.out.println(solution.myAtoi("4193 with words")); // 4193
        System.out.println(solution.myAtoi("words and 987")); // 0
        System.out.println(solution.myAtoi("-91283472332")); // -2147483648
    }
}

