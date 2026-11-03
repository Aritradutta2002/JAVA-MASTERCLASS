package dsa.techniques.twopointers.theory;

public class TwoPointersFundamentals {
    // Opposite-ends example: check palindrome (alnum, case-insensitive)
    public static boolean isAlnumPalindrome(String s) {
        int l = 0, r = s.length() - 1;
        while (l < r) {
            while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
            while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
            if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r))) return false;
            l++; r--;
        }
        return true;
    }

    // Partition/compaction example: remove duplicates from sorted array
    public static int removeDuplicates(int[] nums) {
        if (nums.length == 0) return 0;
        int k = 1;
        for (int i = 1; i < nums.length; i++) if (nums[i] != nums[i-1]) nums[k++] = nums[i];
        return k;
    }
}

