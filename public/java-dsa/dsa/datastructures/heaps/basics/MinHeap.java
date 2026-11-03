package dsa.datastructures.heaps.basics;
/**
 * MIN HEAP IMPLEMENTATION
 * 
 * Time Complexities:
 * - Insert: O(log n)
 * - Extract Min: O(log n)
 * - Get Min: O(1)
 * - Heapify: O(n)
 * 
 * FAANG Interview Topics:
 * - Heap property maintenance
 * - Parent-child relationships
 * - Bubble up/down operations
 */

import java.util.ArrayList;
import java.util.List;

public class MinHeap {
    private List<Integer> heap;
    
    public MinHeap() {
        heap = new ArrayList<>();
    }
    
    // Build heap from array - O(n)
    public MinHeap(int[] arr) {
        heap = new ArrayList<>();
        for (int val : arr) {
            heap.add(val);
        }
        // Heapify from last non-leaf node
        for (int i = (heap.size() / 2) - 1; i >= 0; i--) {
            heapifyDown(i);
        }
    }
    
    // Get parent index
    private int parent(int i) {
        return (i - 1) / 2;
    }
    
    // Get left child index
    private int leftChild(int i) {
        return 2 * i + 1;
    }
    
    // Get right child index
    private int rightChild(int i) {
        return 2 * i + 2;
    }
    
    // Insert element - O(log n)
    public void insert(int value) {
        heap.add(value);
        heapifyUp(heap.size() - 1);
    }
    
    // Bubble up to maintain heap property
    private void heapifyUp(int i) {
        while (i > 0 && heap.get(i) < heap.get(parent(i))) {
            swap(i, parent(i));
            i = parent(i);
        }
    }
    
    // Extract minimum - O(log n)
    public int extractMin() {
        if (heap.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }
        
        int min = heap.get(0);
        int last = heap.remove(heap.size() - 1);
        
        if (!heap.isEmpty()) {
            heap.set(0, last);
            heapifyDown(0);
        }
        
        return min;
    }
    
    // Bubble down to maintain heap property
    private void heapifyDown(int i) {
        int minIndex = i;
        int left = leftChild(i);
        int right = rightChild(i);
        
        if (left < heap.size() && heap.get(left) < heap.get(minIndex)) {
            minIndex = left;
        }
        
        if (right < heap.size() && heap.get(right) < heap.get(minIndex)) {
            minIndex = right;
        }
        
        if (minIndex != i) {
            swap(i, minIndex);
            heapifyDown(minIndex);
        }
    }
    
    // Get minimum without removing - O(1)
    public int peek() {
        if (heap.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }
        return heap.get(0);
    }
    
    // Delete element at index i - O(log n)
    public void delete(int i) {
        if (i >= heap.size()) return;
        
        heap.set(i, Integer.MIN_VALUE);
        heapifyUp(i);
        extractMin();
    }
    
    // Decrease key at index i - O(log n)
    public void decreaseKey(int i, int newValue) {
        if (newValue > heap.get(i)) {
            throw new IllegalArgumentException("New value is greater than current value");
        }
        heap.set(i, newValue);
        heapifyUp(i);
    }
    
    private void swap(int i, int j) {
        int temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
    }
    
    public int size() {
        return heap.size();
    }
    
    public boolean isEmpty() {
        return heap.isEmpty();
    }
    
    // For testing
    public void printHeap() {
        System.out.println(heap);
    }
    
    public static void main(String[] args) {
        // Test 1: Basic operations
        MinHeap minHeap = new MinHeap();
        minHeap.insert(3);
        minHeap.insert(2);
        minHeap.insert(15);
        minHeap.insert(5);
        minHeap.insert(4);
        minHeap.insert(45);
        
        System.out.println("Min Heap: ");
        minHeap.printHeap();
        System.out.println("Min element: " + minHeap.peek());
        System.out.println("Extracted min: " + minHeap.extractMin());
        System.out.println("After extraction: ");
        minHeap.printHeap();
        
        // Test 2: Build heap from array
        System.out.println("\nBuilding heap from array:");
        int[] arr = {10, 5, 14, 25, 12, 45, 30};
        MinHeap heap2 = new MinHeap(arr);
        heap2.printHeap();
    }
}

