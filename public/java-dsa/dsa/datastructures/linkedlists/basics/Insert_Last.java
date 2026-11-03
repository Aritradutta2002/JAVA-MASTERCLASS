package dsa.datastructures.linkedlists.basics;

public class Insert_Last {
    static class Node{
        int data;
        Node next;

        Node(int data) {
            this.data = data;
            next = null;
        }
    }
    static class Insert_AtLast {
        Node head;
        public void insertLast(int data){
            Node newNode = new Node(data);
             if(head == null){
                 head = newNode;
                 return;
             }
            Node last = head;
            while (last.next != null) {
                last = last.next;
            }

            // Set the new node as the next node of the last node
            last.next = newNode;
        }

        public void printLL() {
            Node temp = head;
            while (temp != null) {
                System.out.print(temp.data + " -> ");
                temp = temp.next;
            }
            System.out.println("null");
        }
    }

    public static void main(String[] args){

        Insert_AtLast list = new Insert_AtLast();
        System.out.println("\n*** Current Linked List ***");
        list.insertLast(30);
        list.insertLast(50);
        list.insertLast(60);
        list.printLL();
        System.out.println("*** Insert After last in Linked List ***");
        list.insertLast(70);
        list.insertLast(80);
        list.printLL();

    }
}

