package dsa.datastructures.linkedlists.problems;
/**
 * REVERSE NODES IN K-GROUP - In-place reversal by blocks
 * LeetCode #25
 */

public class ReverseNodesInKGroup {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public ListNode reverseKGroup(ListNode head, int k) {
        ListNode dummy = new ListNode(0); dummy.next = head;
        ListNode groupPrev = dummy;
        while (true) {
            ListNode kth = getKth(groupPrev, k);
            if (kth == null) break;
            ListNode groupNext = kth.next;
            // reverse group
            ListNode prev = groupNext, cur = groupPrev.next;
            while (cur != groupNext) {
                ListNode nxt = cur.next;
                cur.next = prev;
                prev = cur;
                cur = nxt;
            }
            ListNode tmp = groupPrev.next; // after reverse becomes tail
            groupPrev.next = kth;
            groupPrev = tmp;
        }
        return dummy.next;
    }

    private ListNode getKth(ListNode start, int k) {
        while (start != null && k > 0) { start = start.next; k--; }
        return start;
    }

    public static void main(String[] args) {
        ReverseNodesInKGroup s = new ReverseNodesInKGroup();
        ListNode a = new ListNode(1); a.next=new ListNode(2); a.next.next=new ListNode(3); a.next.next.next=new ListNode(4); a.next.next.next.next=new ListNode(5);
        ListNode r = s.reverseKGroup(a, 2);
        while (r!=null){System.out.print(r.val+" "); r=r.next;}
        System.out.println();
    }
}

