package dsa.datastructures.heaps.basics;
/**
 * MAX HEAP IMPLEMENTATION
 * 
 * Used in: K smallest elements, Heap Sort (descending), Job scheduling
 */

import java.util.ArrayList;
import java.util.List;

public class MaxHeap {
    private List<Integer> heap;
    
    public MaxHeap() {
        heap = new ArrayList<>();
    }
    
    public MaxHeap(int[] arr) {
        heap = new ArrayList<>();
        for (int val : arr) {
            heap.add(val);
        }
        for (int i = (heap.size() / 2) - 1; i >= 0; i--) {
            heapifyDown(i);
        }
    }
    
    private int parent(int i) { return (i - 1) / 2; }
    private int leftChild(int i) { return 2 * i + 1; }
    private int rightChild(int i) { return 2 * i + 2; }
    
    public void insert(int value) {
        heap.add(value);
        heapifyUp(heap.size() - 1);
    }
    
    private void heapifyUp(int i) {
        while (i > 0 && heap.get(i) > heap.get(parent(i))) {
            swap(i, parent(i));
            i = parent(i);
        }
    }
    
    public int extractMax() {
        if (heap.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }
        
        int max = heap.get(0);
        int last = heap.remove(heap.size() - 1);
        
        if (!heap.isEmpty()) {
            heap.set(0, last);
            heapifyDown(0);
        }
        
        return max;
    }
    
    private void heapifyDown(int i) {
        int maxIndex = i;
        int left = leftChild(i);
        int right = rightChild(i);
        
        if (left < heap.size() && heap.get(left) > heap.get(maxIndex)) {
            maxIndex = left;
        }
        
        if (right < heap.size() && heap.get(right) > heap.get(maxIndex)) {
            maxIndex = right;
        }
        
        if (maxIndex != i) {
            swap(i, maxIndex);
            heapifyDown(maxIndex);
        }
    }
    
    public int peek() {
        if (heap.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }
        return heap.get(0);
    }
    
    private void swap(int i, int j) {
        int temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
    }
    
    public int size() { return heap.size(); }
    public boolean isEmpty() { return heap.isEmpty(); }
    public void printHeap() { System.out.println(heap); }
    
    public static void main(String[] args) {
        MaxHeap maxHeap = new MaxHeap();
        maxHeap.insert(3);
        maxHeap.insert(2);
        maxHeap.insert(15);
        maxHeap.insert(5);
        maxHeap.insert(4);
        maxHeap.insert(45);
        
        System.out.println("Max Heap: ");
        maxHeap.printHeap();
        System.out.println("Max element: " + maxHeap.peek());
        System.out.println("Extracted max: " + maxHeap.extractMax());
        System.out.println("After extraction: ");
        maxHeap.printHeap();
    }
}

