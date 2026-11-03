package dsa.datastructures.linkedlists.problems;
/**
 * REVERSE LINKED LIST - Iterative and Recursive
 * LeetCode #206
 */

public class ReverseLinkedList {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public ListNode reverseList(ListNode head) {
        ListNode prev = null, cur = head;
        while (cur != null) {
            ListNode nxt = cur.next;
            cur.next = prev;
            prev = cur;
            cur = nxt;
        }
        return prev;
    }

    public ListNode reverseListRecursive(ListNode head) {
        if (head == null || head.next == null) return head;
        ListNode p = reverseListRecursive(head.next);
        head.next.next = head;
        head.next = null;
        return p;
    }

    public static void main(String[] args) {
        ReverseLinkedList s = new ReverseLinkedList();
        ListNode a = new ListNode(1); a.next = new ListNode(2); a.next.next = new ListNode(3);
        ListNode r = s.reverseList(a);
        while (r!=null){System.out.print(r.val+" "); r=r.next;}
        System.out.println();
    }
}

