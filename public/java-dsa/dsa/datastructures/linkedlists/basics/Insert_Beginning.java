package dsa.datastructures.linkedlists.basics;
import java.util.*;
import java.lang.*;

public class Insert_Beginning {
    static class Node{
        int data;
        Node next;

        public Node(int data) {
            this.data = data;
        }
        public Node(int data, Node next) {
            this.data = data;
            this.next = next;
        }
    }
    static class InsertFirst{
        Node head;
        public void insertFirst(int data){
            Node node = new Node(data);
            node.next =head;
            head = node;
        }
        public void display(){
            Node temp = head;
            while(temp != null){
                System.out.print(temp.data + "-->");
                temp = temp.next;
            }
            System.out.println("null");
        }
    }
    public static void main(String[] args) {
        InsertFirst ll = new InsertFirst();
        System.out.println("1)Insert First to the Linked List");
        Scanner sc = new Scanner(System.in);
        ll.insertFirst(70);
        ll.insertFirst(60);
        ll.insertFirst(50);
        ll.display();
        /*
        ll.insertFirst(40);
        ll.insertFirst(30);
        ll.insertFirst(20);
        ll.insertFirst(10);
        ll.display();
         */
        System.out.println("Enter you Insert At First: ");
        int val = sc.nextInt();
        ll.insertFirst(val);
        ll.display();
    }

}

