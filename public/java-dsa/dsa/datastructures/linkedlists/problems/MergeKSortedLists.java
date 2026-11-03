package dsa.datastructures.linkedlists.problems;

/**
 * MERGE K SORTED LISTS - Min-heap approach
 * LeetCode #23
 */

import java.util.*;

public class MergeKSortedLists {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public ListNode mergeKLists(ListNode[] lists) {
        PriorityQueue<ListNode> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a.val));
        for (ListNode node : lists) if (node != null) pq.offer(node);
        ListNode dummy = new ListNode(0), tail = dummy;
        while (!pq.isEmpty()) {
            ListNode cur = pq.poll();
            tail.next = cur; tail = cur;
            if (cur.next != null) pq.offer(cur.next);
        }
        return dummy.next;
    }

    public static void main(String[] args) {
        MergeKSortedLists s = new MergeKSortedLists();
        ListNode a = new ListNode(1); a.next=new ListNode(4); a.next.next=new ListNode(5);
        ListNode b = new ListNode(1); b.next=new ListNode(3); b.next.next=new ListNode(4);
        ListNode c = new ListNode(2); c.next=new ListNode(6);
        ListNode r = s.mergeKLists(new ListNode[]{a,b,c});
        while (r!=null){System.out.print(r.val+" "); r=r.next;} System.out.println();
    }
}

