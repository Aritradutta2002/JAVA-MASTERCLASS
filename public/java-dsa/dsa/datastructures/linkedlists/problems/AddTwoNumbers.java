package dsa.datastructures.linkedlists.problems;
/**
 * ADD TWO NUMBERS - Linked lists addition
 * LeetCode #2
 */

public class AddTwoNumbers {
    static class ListNode { int val; ListNode next; ListNode(int v){val=v;} }

    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0), cur = dummy;
        int carry = 0;
        while (l1 != null || l2 != null || carry != 0) {
            int sum = carry + (l1 != null ? l1.val : 0) + (l2 != null ? l2.val : 0);
            carry = sum / 10;
            cur.next = new ListNode(sum % 10);
            cur = cur.next;
            if (l1 != null) l1 = l1.next;
            if (l2 != null) l2 = l2.next;
        }
        return dummy.next;
    }

    public static void main(String[] args) {
        AddTwoNumbers s = new AddTwoNumbers();
        ListNode a = new ListNode(2); a.next=new ListNode(4); a.next.next=new ListNode(3);
        ListNode b = new ListNode(5); b.next=new ListNode(6); b.next.next=new ListNode(4);
        ListNode r = s.addTwoNumbers(a,b);
        while (r!=null){System.out.print(r.val+" "); r=r.next;} System.out.println();
    }
}

