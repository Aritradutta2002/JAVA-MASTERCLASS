package dsa.techniques.twopointers.problems.easy;

public class ReverseVowels_Leetcode345 {
    public static String reverseVowels(String s) {
        char[] c = s.toCharArray();
        int l = 0, r = c.length - 1;
        while (l < r) {
            while (l < r && !isVowel(c[l])) l++;
            while (l < r && !isVowel(c[r])) r--;
            char tmp = c[l]; c[l] = c[r]; c[r] = tmp; l++; r--;
        }
        return new String(c);
    }
    private static boolean isVowel(char ch) {
        ch = Character.toLowerCase(ch);
        return ch=='a'||ch=='e'||ch=='i'||ch=='o'||ch=='u';
    }
}

