package dsa.datastructures.linkedlists.problems;
/**
 * SORT LIST - Merge sort for linked list
 * LeetCode #148
 */

public class SortList {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public ListNode sortList(ListNode head) {
        if (head == null || head.next == null) return head;
        // split
        ListNode slow = head, fast = head, prev = null;
        while (fast != null && fast.next != null) { prev = slow; slow = slow.next; fast = fast.next.next; }
        prev.next = null;
        ListNode l1 = sortList(head);
        ListNode l2 = sortList(slow);
        return merge(l1, l2);
    }

    private ListNode merge(ListNode a, ListNode b) {
        ListNode dummy = new ListNode(0), t = dummy;
        while (a != null && b != null) {
            if (a.val <= b.val) { t.next = a; a = a.next; }
            else { t.next = b; b = b.next; }
            t = t.next;
        }
        t.next = (a != null) ? a : b;
        return dummy.next;
    }

    public static void main(String[] args) {
        SortList s = new SortList();
        ListNode a = new ListNode(4); a.next=new ListNode(2); a.next.next=new ListNode(1); a.next.next.next=new ListNode(3);
        ListNode r = s.sortList(a);
        while (r!=null){System.out.print(r.val+" "); r=r.next;} System.out.println();
    }
}

