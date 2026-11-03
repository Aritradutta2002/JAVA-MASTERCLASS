package dsa.datastructures.strings.manipulation;

/**
 * Reverse Words in a String (LeetCode 151, 186) - MEDIUM
 * FAANG Frequency: High (Amazon, Microsoft, Facebook)
 * 
 * Problem: Reverse the order of words in a string
 */
public class ReverseWords {
    
    // Reverse Words in a String I (LeetCode 151)
    public String reverseWords(String s) {
        // Trim and split by spaces
        String[] words = s.trim().split("\\s+");
        
        StringBuilder sb = new StringBuilder();
        for (int i = words.length - 1; i >= 0; i--) {
            sb.append(words[i]);
            if (i > 0) sb.append(" ");
        }
        
        return sb.toString();
    }
    
    // Reverse Words in a String II (LeetCode 186) - In-place with char array
    public void reverseWordsInPlace(char[] s) {
        // Reverse entire array
        reverse(s, 0, s.length - 1);
        
        // Reverse each word
        int start = 0;
        for (int i = 0; i <= s.length; i++) {
            if (i == s.length || s[i] == ' ') {
                reverse(s, start, i - 1);
                start = i + 1;
            }
        }
    }
    
    private void reverse(char[] s, int left, int right) {
        while (left < right) {
            char temp = s[left];
            s[left] = s[right];
            s[right] = temp;
            left++;
            right--;
        }
    }
    
    // Reverse Words in a String III (LeetCode 557) - Reverse each word individually
    public String reverseWordsIII(String s) {
        char[] chars = s.toCharArray();
        int start = 0;
        
        for (int i = 0; i <= chars.length; i++) {
            if (i == chars.length || chars[i] == ' ') {
                reverse(chars, start, i - 1);
                start = i + 1;
            }
        }
        
        return new String(chars);
    }
    
    // Without using split
    public String reverseWordsNoSplit(String s) {
        StringBuilder result = new StringBuilder();
        int i = s.length() - 1;
        
        while (i >= 0) {
            // Skip spaces
            while (i >= 0 && s.charAt(i) == ' ') i--;
            
            if (i < 0) break;
            
            // Find word boundary
            int end = i;
            while (i >= 0 && s.charAt(i) != ' ') i--;
            
            if (result.length() > 0) result.append(' ');
            result.append(s.substring(i + 1, end + 1));
        }
        
        return result.toString();
    }
    
    public static void main(String[] args) {
        ReverseWords solution = new ReverseWords();
        
        // Test Case 1
        System.out.println(solution.reverseWords("the sky is blue")); // "blue is sky the"
        
        // Test Case 2
        System.out.println(solution.reverseWords("  hello world  ")); // "world hello"
        
        // Test Case 3
        char[] s = "the sky is blue".toCharArray();
        solution.reverseWordsInPlace(s);
        System.out.println(new String(s)); // "blue is sky the"
    }
}

