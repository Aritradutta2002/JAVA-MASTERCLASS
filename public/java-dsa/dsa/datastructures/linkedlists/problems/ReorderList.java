package dsa.datastructures.linkedlists.problems;
/**
 * REORDER LIST - Merge halves and interleave
 * LeetCode #143
 */

public class ReorderList {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public void reorderList(ListNode head) {
        if (head == null || head.next == null) return;
        // 1) find middle
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }
        // 2) reverse second half
        ListNode prev = null, cur = slow.next; slow.next = null;
        while (cur != null) { ListNode nxt=cur.next; cur.next=prev; prev=cur; cur=nxt; }
        // 3) merge
        ListNode p1 = head, p2 = prev;
        while (p2 != null) {
            ListNode n1 = p1.next, n2 = p2.next;
            p1.next = p2; p2.next = n1;
            p1 = n1; p2 = n2;
        }
    }

    public static void main(String[] args) {
        ReorderList s = new ReorderList();
        ListNode a = new ListNode(1); a.next=new ListNode(2); a.next.next=new ListNode(3); a.next.next.next=new ListNode(4);
        s.reorderList(a);
        while (a!=null){System.out.print(a.val+" "); a=a.next;} System.out.println();
    }
}

