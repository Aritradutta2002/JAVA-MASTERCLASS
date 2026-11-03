package dsa.algorithms.searching.binary.problems;
/**
 * PEAK INDEX IN MOUNTAIN ARRAY - Binary search peak
 * LeetCode #852
 */

public class PeakIndexInMountainArray {
    public int peakIndexInMountainArray(int[] arr) {
        int l = 0, r = arr.length - 1;
        while (l < r) {
            int m = l + (r - l) / 2;
            if (arr[m] < arr[m + 1]) l = m + 1; // climb up
            else r = m; // peak is here or left
        }
        return l;
    }

    public static void main(String[] args) {
        PeakIndexInMountainArray s = new PeakIndexInMountainArray();
        System.out.println(s.peakIndexInMountainArray(new int[]{0,2,3,4,3,2,1}) + " (expected 3)");
    }
}

