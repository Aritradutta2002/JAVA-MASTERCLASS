package dsa.datastructures.linkedlists.problems;
/**
 * LINKED LIST CYCLE II - Detect cycle and return start node (Floyd)
 * LeetCode #142
 */

public class LinkedListCycleII {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public ListNode detectCycle(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next; fast = fast.next.next;
            if (slow == fast) {
                ListNode ptr = head;
                while (ptr != slow) { ptr = ptr.next; slow = slow.next; }
                return ptr;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        LinkedListCycleII s = new LinkedListCycleII();
        ListNode a = new ListNode(3); a.next=new ListNode(2); a.next.next=new ListNode(0); a.next.next.next=new ListNode(-4);
        a.next.next.next.next = a.next; // cycle at node 2
        System.out.println(s.detectCycle(a).val + " (expected 2)");
    }
}

