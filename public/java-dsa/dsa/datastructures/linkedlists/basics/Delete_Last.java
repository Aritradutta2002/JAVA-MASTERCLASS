package dsa.datastructures.linkedlists.basics;
@SuppressWarnings("unused")

public class Delete_Last {
    static class Node {
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
    static class DeleteLast{
         Node head;
         Node tail;
        public void deleteLast() {
            if (size() <= 1) {
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
        public void insertLast(int data){
          Node newNode;
            newNode = new Node(data);
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
         public int size(){
             int size = 0;
             Node temp = head;
             while(temp != null){
                 temp = temp.next;
                 size += 1;
             }
             return size;
         }
        public void display(){
            Node temp = head;
            while(temp != null){
                System.out.print(temp.data + " -> ");
                temp = temp.next;
            }
            System.out.println("Null");
        }

    }

    public static void main(String[] args) {
        DeleteLast list = new DeleteLast();
        list.insertLast(50);
        list.display();
        list.deleteLast();
        list.display();
    }

}

