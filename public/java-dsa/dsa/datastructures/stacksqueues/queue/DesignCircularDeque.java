package dsa.datastructures.stacksqueues.queue;

/*
 *   Author : Aritra
 *   LeetCode #641 - Design Circular Deque (Medium)
 *   Problem: Design circular double-ended queue
 *   Time Complexity: O(1) for all operations
 */

public class DesignCircularDeque {
    public static void main(String[] args) {
        MyCircularDeque deque = new MyCircularDeque(3);
        
        System.out.println("insertLast(1): " + deque.insertLast(1));    // true
        System.out.println("insertLast(2): " + deque.insertLast(2));    // true
        System.out.println("insertFront(3): " + deque.insertFront(3));  // true
        System.out.println("insertFront(4): " + deque.insertFront(4));  // false (full)
        System.out.println("getRear(): " + deque.getRear());            // 2
        System.out.println("isFull(): " + deque.isFull());              // true
        System.out.println("deleteLast(): " + deque.deleteLast());      // true
        System.out.println("insertFront(4): " + deque.insertFront(4));  // true
        System.out.println("getFront(): " + deque.getFront());          // 4
    }
    
    static class MyCircularDeque {
        private int[] data;
        private int front;
        private int rear;
        private int size;
        private int capacity;
        
        public MyCircularDeque(int k) {
            this.capacity = k;
            this.data = new int[k];
            this.front = 0;
            this.rear = 0;
            this.size = 0;
        }
        
        public boolean insertFront(int value) {
            if (isFull()) {
                return false;
            }
            
            if (size == 0) {
                data[front] = value;
            } else {
                front = (front - 1 + capacity) % capacity;
                data[front] = value;
            }
            size++;
            return true;
        }
        
        public boolean insertLast(int value) {
            if (isFull()) {
                return false;
            }
            
            if (size == 0) {
                data[rear] = value;
            } else {
                rear = (rear + 1) % capacity;
                data[rear] = value;
            }
            size++;
            return true;
        }
        
        public boolean deleteFront() {
            if (isEmpty()) {
                return false;
            }
            
            if (size == 1) {
                front = 0;
                rear = 0;
            } else {
                front = (front + 1) % capacity;
            }
            size--;
            return true;
        }
        
        public boolean deleteLast() {
            if (isEmpty()) {
                return false;
            }
            
            if (size == 1) {
                front = 0;
                rear = 0;
            } else {
                rear = (rear - 1 + capacity) % capacity;
            }
            size--;
            return true;
        }
        
        public int getFront() {
            if (isEmpty()) {
                return -1;
            }
            return data[front];
        }
        
        public int getRear() {
            if (isEmpty()) {
                return -1;
            }
            return data[rear];
        }
        
        public boolean isEmpty() {
            return size == 0;
        }
        
        public boolean isFull() {
            return size == capacity;
        }
    }
}

