package dsa.datastructures.heaps.basics;

import java.util.ArrayList;

/**
 * ============================================
 * BASIC HEAP IMPLEMENTATION (Min Heap)
 * ============================================
 *
 * A Heap is a complete binary tree stored in an array/list.
 *
 * For a node at index i:
 * - Parent = (i - 1) / 2
 * - Left Child = 2 * i + 1
 * - Right Child = 2 * i + 2
 *
 * Min Heap Property: parent <= children
 * Max Heap Property: parent >= children
 *
 * Time Complexities:
 * - Insert : O(log n)
 * - Delete/Poll: O(log n)
 * - Peek : O(1)
 * - Heapify : O(n)
 */
public class basicHeap01 {

    // ---- Min Heap using ArrayList ----
    static ArrayList<Integer> heap = new ArrayList<>();

    // --- Helper: Swap two elements ---
    static void swap(int i, int j) {
        int temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
    }

    // --- Helper: Get parent index ---
    static int parent(int i) {
        return (i - 1) / 2;
    }

    // --- Helper: Get left child index ---
    static int leftChild(int i) {
        return 2 * i + 1;
    }

    // --- Helper: Get right child index ---
    static int rightChild(int i) {
        return 2 * i + 2;
    }

    // =========================================
    // INSERT into Min Heap
    // =========================================
    // 1. Add element at the end
    // 2. "Bubble Up" — compare with parent and swap if smaller
    static void insert(int val) {
        heap.add(val); // add at end
        int i = heap.size() - 1;

        // bubble up
        while (i > 0 && heap.get(i) < heap.get(parent(i))) {
            swap(i, parent(i));
            i = parent(i);
        }
    }

    // =========================================
    // PEEK — return the minimum (root)
    // =========================================
    static int peek() {
        if (heap.isEmpty()) {
            System.out.println("Heap is empty!");
            return -1;
        }
        return heap.get(0);
    }

    // =========================================
    // DELETE / POLL — remove the minimum (root)
    // =========================================
    // 1. Replace root with last element
    // 2. Remove last element
    // 3. "Heapify Down" — compare with children and swap with the smaller child
    static int poll() {
        if (heap.isEmpty()) {
            System.out.println("Heap is empty!");
            return -1;
        }

        int min = heap.get(0);

        // move last element to root
        heap.set(0, heap.get(heap.size() - 1));
        heap.remove(heap.size() - 1);

        // heapify down from root
        heapifyDown(0);

        return min;
    }

    // =========================================
    // HEAPIFY DOWN (used after deletion)
    // =========================================
    static void heapifyDown(int i) {
        int smallest = i;
        int left = leftChild(i);
        int right = rightChild(i);

        if (left < heap.size() && heap.get(left) < heap.get(smallest)) {
            smallest = left;
        }
        if (right < heap.size() && heap.get(right) < heap.get(smallest)) {
            smallest = right;
        }

        if (smallest != i) {
            swap(i, smallest);
            heapifyDown(smallest); // recurse
        }
    }

    // =========================================
    // PRINT HEAP
    // =========================================
    static void printHeap() {
        System.out.println("Heap: " + heap);
    }

    // =========================================
    // MAIN — Test the Min Heap
    // =========================================
    public static void main(String[] args) {

        System.out.println("=== Min Heap Operations ===\n");

        // Insert elements
        insert(10);
        insert(20);
        insert(5);
        insert(30);
        insert(1);
        insert(15);

        printHeap(); // [1, 5, 10, 30, 20, 15]

        // Peek
        System.out.println("Peek (min): " + peek()); // 1

        // Poll (remove min)
        System.out.println("Polled: " + poll()); // 1
        printHeap(); // [5, 20, 10, 30, 15]

        System.out.println("Polled: " + poll()); // 5
        printHeap(); // [10, 20, 15, 30]

        // Insert more
        insert(2);
        printHeap(); // [2, 20, 10, 30, 15] — 2 bubbles up to root

        System.out.println("\nPeek (min): " + peek()); // 2
    }
}
