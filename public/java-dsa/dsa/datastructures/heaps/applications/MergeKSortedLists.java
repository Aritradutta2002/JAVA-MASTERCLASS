package dsa.datastructures.heaps.applications;
/**
 * MERGE K SORTED LISTS
 * 
 * LeetCode #23 - Hard
 * Companies: Facebook, Amazon, Microsoft, Google, LinkedIn, Apple
 * 
 * Problem: Merge k sorted linked lists into one sorted list.
 * 
 * Solutions:
 * 1. Min Heap - O(N log k) where N = total nodes, k = number of lists
 * 2. Divide and Conquer - O(N log k)
 * 3. Sequential Merge - O(kN) - Brute force
 */

import java.util.*;

class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

public class MergeKSortedLists {
    
    // APPROACH 1: Min Heap (Most Optimal and Interview-Friendly)
    public ListNode mergeKLists_Heap(ListNode[] lists) {
        if (lists == null || lists.length == 0) return null;
        
        // Min heap based on node value
        PriorityQueue<ListNode> minHeap = new PriorityQueue<>(
            (a, b) -> a.val - b.val
        );
        
        // Add first node of each list to heap
        for (ListNode head : lists) {
            if (head != null) {
                minHeap.offer(head);
            }
        }
        
        ListNode dummy = new ListNode(0);
        ListNode current = dummy;
        
        while (!minHeap.isEmpty()) {
            ListNode smallest = minHeap.poll();
            current.next = smallest;
            current = current.next;
            
            // Add next node from the same list
            if (smallest.next != null) {
                minHeap.offer(smallest.next);
            }
        }
        
        return dummy.next;
    }
    
    // APPROACH 2: Divide and Conquer
    public ListNode mergeKLists_DivideConquer(ListNode[] lists) {
        if (lists == null || lists.length == 0) return null;
        return divideAndConquer(lists, 0, lists.length - 1);
    }
    
    private ListNode divideAndConquer(ListNode[] lists, int left, int right) {
        if (left == right) return lists[left];
        
        int mid = left + (right - left) / 2;
        ListNode l1 = divideAndConquer(lists, left, mid);
        ListNode l2 = divideAndConquer(lists, mid + 1, right);
        
        return mergeTwoLists(l1, l2);
    }
    
    private ListNode mergeTwoLists(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0);
        ListNode current = dummy;
        
        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) {
                current.next = l1;
                l1 = l1.next;
            } else {
                current.next = l2;
                l2 = l2.next;
            }
            current = current.next;
        }
        
        current.next = (l1 != null) ? l1 : l2;
        return dummy.next;
    }
    
    // RELATED PROBLEM: Merge K Sorted Arrays
    public int[] mergeKSortedArrays(int[][] arrays) {
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        
        int totalSize = 0;
        // Add first element of each array: [value, arrayIndex, elementIndex]
        for (int i = 0; i < arrays.length; i++) {
            if (arrays[i].length > 0) {
                minHeap.offer(new int[]{arrays[i][0], i, 0});
                totalSize += arrays[i].length;
            }
        }
        
        int[] result = new int[totalSize];
        int idx = 0;
        
        while (!minHeap.isEmpty()) {
            int[] current = minHeap.poll();
            result[idx++] = current[0];
            
            int arrayIdx = current[1];
            int elementIdx = current[2];
            
            // Add next element from the same array
            if (elementIdx + 1 < arrays[arrayIdx].length) {
                minHeap.offer(new int[]{
                    arrays[arrayIdx][elementIdx + 1],
                    arrayIdx,
                    elementIdx + 1
                });
            }
        }
        
        return result;
    }
    
    // HELPER: Create linked list from array
    private ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    // HELPER: Print linked list
    private void printList(ListNode head) {
        List<Integer> values = new ArrayList<>();
        while (head != null) {
            values.add(head.val);
            head = head.next;
        }
        System.out.println(values);
    }
    
    public static void main(String[] args) {
        MergeKSortedLists solution = new MergeKSortedLists();
        
        // Test Case 1: Merge K Sorted Lists
        System.out.println("=== Merge K Sorted Lists ===");
        ListNode[] lists = new ListNode[3];
        lists[0] = solution.createList(new int[]{1, 4, 5});
        lists[1] = solution.createList(new int[]{1, 3, 4});
        lists[2] = solution.createList(new int[]{2, 6});
        
        System.out.print("List 1: "); solution.printList(lists[0]);
        System.out.print("List 2: "); solution.printList(lists[1]);
        System.out.print("List 3: "); solution.printList(lists[2]);
        
        ListNode mergedHeap = solution.mergeKLists_Heap(lists);
        System.out.print("Merged (Heap): "); solution.printList(mergedHeap);
        
        // Recreate lists for second test
        lists[0] = solution.createList(new int[]{1, 4, 5});
        lists[1] = solution.createList(new int[]{1, 3, 4});
        lists[2] = solution.createList(new int[]{2, 6});
        
        ListNode mergedDC = solution.mergeKLists_DivideConquer(lists);
        System.out.print("Merged (D&C): "); solution.printList(mergedDC);
        
        // Test Case 2: Merge K Sorted Arrays
        System.out.println("\n=== Merge K Sorted Arrays ===");
        int[][] arrays = {
            {1, 3, 5, 7},
            {2, 4, 6, 8},
            {0, 9, 10, 11}
        };
        
        int[] mergedArray = solution.mergeKSortedArrays(arrays);
        System.out.println("Merged Array: " + Arrays.toString(mergedArray));
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Heap approach: O(N log k) - Best space-time trade-off");
        System.out.println("2. D&C approach: O(N log k) - More stack space");
        System.out.println("3. Always handle edge cases: null lists, empty lists");
        System.out.println("4. This pattern works for: lists, arrays, streams");
        System.out.println("5. Related: Smallest Range Covering K Lists");
    }
}

