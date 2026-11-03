package dsa.datastructures.linkedlists.basics;
import java.util.*;
public class DeleteAnywhere {
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
    static class DeleteAt{
        Node head;
        Node tail;

        public void deleteAnywhere(int position){
            if(position <= 1) {
                deleteFirst();
                return;
            }
            if(position == size()) {
                deleteLast();
                return;
            }
            Node temp = head;
            for (int i = 1; i < (position -1) && temp != null ; i++) {
                temp = temp.next;
            }
            temp.next = temp.next.next;
            tail = temp.next;
        }
        public void deleteLast() {
            if(size() <= 1){
                deleteFirst();
                return;
            }
            Node temp = head;
            for (int i = 1; i < size()-1 && temp != null; i++) {
                temp = temp.next;
            }
            tail = temp;
            tail.next = null;
        }

        public void deleteFirst(){
            if(head != null){
                head = head.next;
            }
        }
        public int size(){
            int size = 0;
            Node current = head;
            while(current != null){
                current = current.next;
                size += 1;
            }
            return size;
        }


        public void insert(int data){
            Node newNode = new Node(data);
            Node node = new Node(data);
            node.next =head;
            head = node;
        }
        public void display(){
            Node temp = head;
            while(temp != null){
                System.out.print(temp.data+" -> ");
                temp = temp.next;
            }
            System.out.println("Null");
        }

    }

    public static void main(String[] args) {
        DeleteAt list = new DeleteAt();
        System.out.println("Linked List Before Deletion");
       Scanner sc = new Scanner(System.in);

        list.insert(95);
        list.insert(90);
        list.insert(85);
        list.insert(80);
        list.insert(70);
        list.insert(62);
        list.insert(42);
        list.insert(40);
        list.insert(29);
        list.insert(25);
        list.insert(10);
        list.display();
        System.out.print("Delete at the position of : ");
        int position =sc.nextInt();
        //System.out.println("Linked List After Deletion");
        list.deleteAnywhere(position);
        System.out.println("Linked List After Deletion");
        list.display();
    }

}

