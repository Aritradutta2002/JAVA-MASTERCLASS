package dsa.datastructures.stacksqueues.queue;

/*
 *   Author : Aritra
 *   LeetCode #622 - Design Circular Queue (Medium)
 *   Problem: Design a circular queue with fixed size
 *   Time Complexity: O(1) for all operations
 */

public class DesignCircularQueue {
    public static void main(String[] args) {
        MyCircularQueue queue = new MyCircularQueue(3);
        
        System.out.println("Enqueue 1: " + queue.enQueue(1));  // true
        System.out.println("Enqueue 2: " + queue.enQueue(2));  // true
        System.out.println("Enqueue 3: " + queue.enQueue(3));  // true
        System.out.println("Enqueue 4: " + queue.enQueue(4));  // false (queue full)
        System.out.println("Rear: " + queue.Rear());           // 3
        System.out.println("IsFull: " + queue.isFull());       // true
        System.out.println("Dequeue: " + queue.deQueue());     // true
        System.out.println("Enqueue 4: " + queue.enQueue(4));  // true
        System.out.println("Rear: " + queue.Rear());           // 4
        System.out.println("Front: " + queue.Front());         // 2
    }
    
    static class MyCircularQueue {
        private int[] data;
        private int front;
        private int rear;
        private int size;
        private int capacity;
        
        public MyCircularQueue(int k) {
            this.capacity = k;
            this.data = new int[k];
            this.front = 0;
            this.rear = -1;
            this.size = 0;
        }
        
        public boolean enQueue(int value) {
            if (isFull()) {
                return false;
            }
            rear = (rear + 1) % capacity;
            data[rear] = value;
            size++;
            return true;
        }
        
        public boolean deQueue() {
            if (isEmpty()) {
                return false;
            }
            front = (front + 1) % capacity;
            size--;
            return true;
        }
        
        public int Front() {
            if (isEmpty()) {
                return -1;
            }
            return data[front];
        }
        
        public int Rear() {
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

