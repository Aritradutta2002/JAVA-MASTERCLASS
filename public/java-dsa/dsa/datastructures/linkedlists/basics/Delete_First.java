package dsa.datastructures.linkedlists.basics;

public class Delete_First {
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
    static class DeleteFirst{
        Node head;
        Node tail;

        public void deleteFirst(){
            if(head != null){
                head = head.next;
            }
        }
        public void insertFirst(int data){
            Node newNode = new Node(data);
            newNode.next = head;
            head = newNode;
        }
        public int size(){
            int count = 0;
            Node temp = head;
            while(temp != null){
                count += 1;
            }
            return count;
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
        DeleteFirst list = new DeleteFirst();
        System.out.println("Before deleting first element");
        list.insertFirst(40);
        list.insertFirst(50);
        list.insertFirst(60);
        list.insertFirst(80);
        list.display();
        System.out.println("After deleting first element");
        list.deleteFirst();
        list.display();
    }
}

