package dsa.techniques.twopointers.patterns;

import java.util.*;

public class CommonPatterns {

    // Opposite-ends 2-sum on sorted array
    public static int[] twoSumSorted(int[] a, int target) {
        int l = 0, r = a.length - 1;
        while (l < r) {
            int s = a[l] + a[r];
            if (s == target) return new int[]{l, r};
            if (s < target) l++; else r--;
        }
        return new int[]{-1, -1};
    }

    // Stable compaction (remove element)
    public static int removeElement(int[] nums, int val) {
        int k = 0;
        for (int x : nums) if (x != val) nums[k++] = x;
        return k;
    }

    // Fast–slow: detect cycle
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }
    public static boolean hasCycle(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next; fast = fast.next.next;
            if (slow == fast) return true;
        }
        return false;
    }
}

