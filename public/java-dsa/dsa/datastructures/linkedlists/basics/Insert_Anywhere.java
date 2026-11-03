package dsa.datastructures.linkedlists.basics;

public class Insert_Anywhere {
    static class Node {
        int data;
        Node next;

        public Node(int data) {
            this.data = data;
            this.next = null;
        }
    }

    static class LinkedList {
        Node head;
        Node tail;

        public int size() {
            int len = 0;
            Node temp = head;
            while (temp != null) {
                len += 1;
                temp = temp.next; // Move to the next node
            }
            return len;
        }

        public void insert(int data, int position) {
            Node newNode = new Node(data);

            if (position == 0) {
                // Handle empty list (insert at head)
                insertFirst(data);
                return;
            }

            if (position == size()) {
                insertLast(data);
                return;
            }

            // Traverse to the node before insertion position
            Node temp = head;
            for (int i = 1; i < position  && temp != null; i++) {
                temp = temp.next;
            }

            // Check if position is out of bounds
            if (temp == null) {
                System.out.println("Invalid Position!");
                return;
            }

            // Insert between temp and temp.next
            newNode.next = temp.next;
            temp.next = newNode;
        }

        public void insertFirst(int data) {
            Node node = new Node(data);
            node.next = head;
            head = node;
            if (tail == null) {
                tail = node;
            }
        }

        public void insertLast(int data) {
            Node node = new Node(data);
            if (head == null) {
                head = node;
            } else {
                tail.next = node;
            }
            tail = node;
        }

        public void display() {
            Node temp = head;
            while (temp != null) {
                System.out.print(temp.data + " -> ");
                temp = temp.next;
            }
            System.out.println("Null");
        }
    }

    public static void main(String[] args) {
        LinkedList list = new LinkedList();

        list.insertFirst(40);
        list.insertFirst(30);
        list.insertFirst(20);
        list.insertFirst(10);
        list.display();
        list.insert(40, 2);
        list.display();
    }
}

