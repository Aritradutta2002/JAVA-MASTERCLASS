package dsa.datastructures.linkedlists.reversal;
/*
*   Author  : Aritra Dutta
*   Created : Wednesday, 19.03.2025  09:58 pm
*/
import com.dsa.datastructures.linkedlists.basics.ListNode;
import java.util.*;

public class Reverse_A_LinkedList {
    public static void main(String[] args){
        ListNode ll = new ListNode(20);
        ll = new ListNode(15, ll);
        ll = new ListNode(10, ll);
        ll = new ListNode(5, ll);

        System.out.println("original list: " + ListNode.toList(ll));
        System.out.println("Reversed List: " + ListNode.toList(reverseList(ll)));
    }
    public static ListNode reverseList(ListNode head) {
        if(head == null || head.next == null){
            return head;
        }
        ListNode prev = null;
        ListNode curr = head;
        while(curr != null){
            ListNode next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        return prev;
    }
}


