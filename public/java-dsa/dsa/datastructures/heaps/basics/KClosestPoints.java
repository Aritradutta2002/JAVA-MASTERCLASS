package dsa.datastructures.heaps.basics;

import java.util.*;

/**
 * K Closest Points to Origin (LeetCode 973) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Facebook, Google)
 * 
 * Problem: Find k closest points to origin
 * Multiple approaches with different trade-offs
 */
public class KClosestPoints {
    
    // Approach 1: Max Heap - O(n log k)
    public int[][] kClosest(int[][] points, int k) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
            (a, b) -> (b[0] * b[0] + b[1] * b[1]) - (a[0] * a[0] + a[1] * a[1])
        );
        
        for (int[] point : points) {
            maxHeap.offer(point);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }
        
        int[][] result = new int[k][2];
        for (int i = 0; i < k; i++) {
            result[i] = maxHeap.poll();
        }
        
        return result;
    }
    
    // Approach 2: QuickSelect - O(n) average, O(n^2) worst
    public int[][] kClosestQuickSelect(int[][] points, int k) {
        quickSelect(points, 0, points.length - 1, k);
        return Arrays.copyOfRange(points, 0, k);
    }
    
    private void quickSelect(int[][] points, int left, int right, int k) {
        if (left >= right) return;
        
        int pivotIndex = partition(points, left, right);
        
        if (pivotIndex == k) {
            return;
        } else if (pivotIndex < k) {
            quickSelect(points, pivotIndex + 1, right, k);
        } else {
            quickSelect(points, left, pivotIndex - 1, k);
        }
    }
    
    private int partition(int[][] points, int left, int right) {
        int[] pivot = points[right];
        int pivotDist = distance(pivot);
        int i = left;
        
        for (int j = left; j < right; j++) {
            if (distance(points[j]) < pivotDist) {
                swap(points, i, j);
                i++;
            }
        }
        
        swap(points, i, right);
        return i;
    }
    
    private int distance(int[] point) {
        return point[0] * point[0] + point[1] * point[1];
    }
    
    private void swap(int[][] points, int i, int j) {
        int[] temp = points[i];
        points[i] = points[j];
        points[j] = temp;
    }
    
    // Approach 3: Sorting - O(n log n)
    public int[][] kClosestSort(int[][] points, int k) {
        Arrays.sort(points, (a, b) -> 
            (a[0] * a[0] + a[1] * a[1]) - (b[0] * b[0] + b[1] * b[1])
        );
        
        return Arrays.copyOfRange(points, 0, k);
    }
    
    public static void main(String[] args) {
        KClosestPoints solution = new KClosestPoints();
        
        // Test Case 1
        int[][] points1 = {{1, 3}, {-2, 2}};
        int[][] result1 = solution.kClosest(points1, 1);
        System.out.println("K Closest: " + Arrays.deepToString(result1));
        
        // Test Case 2
        int[][] points2 = {{3, 3}, {5, -1}, {-2, 4}};
        int[][] result2 = solution.kClosestQuickSelect(points2, 2);
        System.out.println("K Closest: " + Arrays.deepToString(result2));
    }
}

