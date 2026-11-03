package dsa.algorithms.sorting.theory;
/**
 * HEAP SORT
 * 
 * Time: O(n log n) - GUARANTEED (unlike QuickSort)
 * Space: O(1) - In-place
 * Stability: NO (unstable)
 * 
 * KEY CONCEPTS:
 * 1. Max Heap property: parent >= children
 * 2. Heapify: Make subtree satisfy heap property - O(log n)
 * 3. Build heap: Bottom-up heapify - O(n) surprisingly!
 * 4. Extract max repeatedly: O(n log n)
 * 
 * CP Usage: When O(n log n) WORST case is required with O(1) space
 */

import java.util.*;

public class HeapSort {
    
    /**
     * Main heap sort algorithm
     * Time: O(n log n), Space: O(1)
     */
    public void heapSort(int[] arr) {
        int n = arr.length;
        
        // Step 1: Build max heap - O(n)
        buildMaxHeap(arr, n);
        
        // Step 2: Extract elements one by one - O(n log n)
        for (int i = n - 1; i > 0; i--) {
            // Move current root (max) to end
            swap(arr, 0, i);
            
            // Heapify the reduced heap
            heapify(arr, i, 0);
        }
    }
    
    /**
     * Build max heap from unsorted array
     * Time: O(n) - NOT O(n log n)!
     * 
     * Why O(n)? Most nodes are near bottom (do less work)
     */
    private void buildMaxHeap(int[] arr, int n) {
        // Start from last non-leaf node and heapify backwards
        // Last non-leaf node is at index (n/2 - 1)
        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }
    }
    
    /**
     * Heapify subtree rooted at index i
     * Time: O(log n), Space: O(log n) for recursion
     * 
     * Assumes subtrees are already heaps
     */
    private void heapify(int[] arr, int n, int i) {
        int largest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;
        
        // Check if left child exists and is larger
        if (left < n && arr[left] > arr[largest]) {
            largest = left;
        }
        
        // Check if right child exists and is larger
        if (right < n && arr[right] > arr[largest]) {
            largest = right;
        }
        
        // If largest is not root, swap and continue heapifying
        if (largest != i) {
            swap(arr, i, largest);
            heapify(arr, n, largest);
        }
    }
    
    /**
     * Iterative heapify (better for CP - no recursion)
     */
    private void heapifyIterative(int[] arr, int n, int i) {
        while (true) {
            int largest = i;
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            
            if (left < n && arr[left] > arr[largest]) {
                largest = left;
            }
            
            if (right < n && arr[right] > arr[largest]) {
                largest = right;
            }
            
            if (largest == i) break;
            
            swap(arr, i, largest);
            i = largest;
        }
    }
    
    /**
     * Min Heap Sort (ascending order using min heap)
     */
    public void heapSortMinHeap(int[] arr) {
        int n = arr.length;
        
        // Build min heap
        for (int i = n / 2 - 1; i >= 0; i--) {
            minHeapify(arr, n, i);
        }
        
        // Extract elements
        for (int i = n - 1; i > 0; i--) {
            swap(arr, 0, i);
            minHeapify(arr, i, 0);
        }
    }
    
    private void minHeapify(int[] arr, int n, int i) {
        int smallest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;
        
        if (left < n && arr[left] < arr[smallest]) {
            smallest = left;
        }
        
        if (right < n && arr[right] < arr[smallest]) {
            smallest = right;
        }
        
        if (smallest != i) {
            swap(arr, i, smallest);
            minHeapify(arr, n, smallest);
        }
    }
    
    /**
     * Find Kth largest using partial heap sort
     * More efficient than full sort for small k
     */
    public int findKthLargest(int[] arr, int k) {
        int n = arr.length;
        buildMaxHeap(arr, n);
        
        // Extract k-1 elements
        for (int i = 0; i < k - 1; i++) {
            swap(arr, 0, n - 1 - i);
            heapify(arr, n - 1 - i, 0);
        }
        
        return arr[0]; // Kth largest is now at root
    }
    
    private void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }
    
    /**
     * Visualize heap structure
     */
    private void printHeap(int[] arr, int n) {
        System.out.println("Heap structure:");
        int level = 0;
        int count = 0;
        int nodesInLevel = 1;
        
        for (int i = 0; i < n; i++) {
            System.out.print(arr[i] + " ");
            count++;
            
            if (count == nodesInLevel) {
                System.out.println();
                level++;
                count = 0;
                nodesInLevel = (int) Math.pow(2, level);
            }
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        HeapSort hs = new HeapSort();
        
        // Test 1: Basic sort
        int[] arr1 = {12, 11, 13, 5, 6, 7};
        System.out.println("=== Test 1: Basic Heap Sort ===");
        System.out.println("Original: " + Arrays.toString(arr1));
        
        // Show heap building process
        int[] temp = arr1.clone();
        hs.buildMaxHeap(temp, temp.length);
        System.out.println("After building max heap: " + Arrays.toString(temp));
        
        hs.heapSort(arr1);
        System.out.println("After sorting: " + Arrays.toString(arr1));
        System.out.println();
        
        // Test 2: Already sorted
        int[] arr2 = {1, 2, 3, 4, 5};
        System.out.println("=== Test 2: Already Sorted ===");
        System.out.println("Original: " + Arrays.toString(arr2));
        hs.heapSort(arr2);
        System.out.println("After sorting: " + Arrays.toString(arr2));
        System.out.println();
        
        // Test 3: Reverse sorted
        int[] arr3 = {5, 4, 3, 2, 1};
        System.out.println("=== Test 3: Reverse Sorted ===");
        System.out.println("Original: " + Arrays.toString(arr3));
        hs.heapSort(arr3);
        System.out.println("After sorting: " + Arrays.toString(arr3));
        System.out.println();
        
        // Test 4: Kth largest
        int[] arr4 = {3, 2, 1, 5, 6, 4};
        int k = 2;
        System.out.println("=== Test 4: Kth Largest ===");
        System.out.println("Array: " + Arrays.toString(arr4));
        System.out.println(k + "th largest: " + hs.findKthLargest(arr4.clone(), k));
        System.out.println();
        
        // Test 5: Visualize heap
        int[] arr5 = {4, 10, 3, 5, 1};
        System.out.println("=== Test 5: Heap Visualization ===");
        System.out.println("Array: " + Arrays.toString(arr5));
        hs.buildMaxHeap(arr5, arr5.length);
        System.out.println("Max Heap: " + Arrays.toString(arr5));
        hs.printHeap(arr5, arr5.length);
        
        // Heap theory explanation
        System.out.println("=== HEAP THEORY ===");
        System.out.println("Complete Binary Tree stored in array:");
        System.out.println("  Parent of i: (i-1)/2");
        System.out.println("  Left child: 2*i + 1");
        System.out.println("  Right child: 2*i + 2");
        System.out.println();
        System.out.println("Max Heap property:");
        System.out.println("  arr[parent] >= arr[left] AND arr[parent] >= arr[right]");
        System.out.println();
        
        System.out.println("=== TIME COMPLEXITY ANALYSIS ===");
        System.out.println("Build Heap: O(n) - Surprising!");
        System.out.println("  - Most nodes are near leaves (less work)");
        System.out.println("  - Height h has 2^h nodes, each does O(log n - h) work");
        System.out.println("  - Sum: n * Σ(h/2^h) = O(n)");
        System.out.println();
        System.out.println("Heapify: O(log n)");
        System.out.println("Sorting: n * heapify = O(n log n)");
        System.out.println();
        
        System.out.println("=== HEAP SORT VS OTHERS ===");
        System.out.println("Heap Sort:");
        System.out.println("  ✓ O(n log n) WORST case guaranteed");
        System.out.println("  ✓ O(1) space (in-place)");
        System.out.println("  ✗ Unstable");
        System.out.println("  ✗ Poor cache locality (random access)");
        System.out.println();
        System.out.println("Quick Sort:");
        System.out.println("  ✗ O(n²) worst case");
        System.out.println("  ✓ Better cache locality");
        System.out.println("  ✓ Faster in practice");
        System.out.println();
        System.out.println("Merge Sort:");
        System.out.println("  ✓ O(n log n) guaranteed");
        System.out.println("  ✓ Stable");
        System.out.println("  ✗ O(n) extra space");
        System.out.println();
        
        System.out.println("=== CP USAGE ===");
        System.out.println("Use Heap Sort when:");
        System.out.println("✓ Need guaranteed O(n log n) worst case");
        System.out.println("✓ Space is limited (O(1) in-place)");
        System.out.println("✓ Don't need stability");
        System.out.println("✓ Priority queue operations needed");
        System.out.println();
        System.out.println("In CP contests:");
        System.out.println("- Usually just use Arrays.sort() or Collections.sort()");
        System.out.println("- Implement heap sort for custom comparisons");
        System.out.println("- Use PriorityQueue for heap operations");
    }
}

