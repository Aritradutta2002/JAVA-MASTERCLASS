package dsa.systemdesign.cache;
/**
 * MIN STACK
 * 
 * LeetCode #155 - Medium
 * Companies: Amazon, Bloomberg, Microsoft, Google, Adobe
 * 
 * Design a stack that supports:
 * - push(x): Push element x onto stack
 * - pop(): Remove top element
 * - top(): Get top element
 * - getMin(): Retrieve minimum element in O(1)
 * 
 * All operations must be O(1) time complexity
 */

import java.util.Stack;

public class MinStack {
    
    // APPROACH 1: Two Stacks (Most intuitive)
    private Stack<Integer> stack;
    private Stack<Integer> minStack;
    
    public MinStack() {
        stack = new Stack<>();
        minStack = new Stack<>();
    }
    
    public void push(int val) {
        stack.push(val);
        
        // Push to minStack if it's new minimum
        if (minStack.isEmpty() || val <= minStack.peek()) {
            minStack.push(val);
        }
    }
    
    public void pop() {
        int val = stack.pop();
        
        // If popped value is current min, pop from minStack too
        if (val == minStack.peek()) {
            minStack.pop();
        }
    }
    
    public int top() {
        return stack.peek();
    }
    
    public int getMin() {
        return minStack.peek();
    }
}

/**
 * APPROACH 2: Single Stack with Pairs
 * Store (value, currentMin) pairs
 */
class MinStack2 {
    private Stack<int[]> stack; // [value, minSoFar]
    
    public MinStack2() {
        stack = new Stack<>();
    }
    
    public void push(int val) {
        if (stack.isEmpty()) {
            stack.push(new int[]{val, val});
        } else {
            int currentMin = Math.min(val, stack.peek()[1]);
            stack.push(new int[]{val, currentMin});
        }
    }
    
    public void pop() {
        stack.pop();
    }
    
    public int top() {
        return stack.peek()[0];
    }
    
    public int getMin() {
        return stack.peek()[1];
    }
}

/**
 * APPROACH 3: Single Stack with Node class
 * Each node stores value and min at that point
 */
class MinStack3 {
    private class Node {
        int value;
        int min;
        Node next;
        
        public Node(int value, int min, Node next) {
            this.value = value;
            this.min = min;
            this.next = next;
        }
    }
    
    private Node head;
    
    public MinStack3() {
        head = null;
    }
    
    public void push(int val) {
        if (head == null) {
            head = new Node(val, val, null);
        } else {
            int currentMin = Math.min(val, head.min);
            head = new Node(val, currentMin, head);
        }
    }
    
    public void pop() {
        head = head.next;
    }
    
    public int top() {
        return head.value;
    }
    
    public int getMin() {
        return head.min;
    }
}

/**
 * FOLLOW-UP: Max Stack
 * Same as MinStack but for maximum
 */
class MaxStack {
    private Stack<Integer> stack;
    private Stack<Integer> maxStack;
    
    public MaxStack() {
        stack = new Stack<>();
        maxStack = new Stack<>();
    }
    
    public void push(int val) {
        stack.push(val);
        if (maxStack.isEmpty() || val >= maxStack.peek()) {
            maxStack.push(val);
        }
    }
    
    public void pop() {
        int val = stack.pop();
        if (val == maxStack.peek()) {
            maxStack.pop();
        }
    }
    
    public int top() {
        return stack.peek();
    }
    
    public int getMax() {
        return maxStack.peek();
    }
}

/**
 * FOLLOW-UP: Min/Max Stack Combined
 */
class MinMaxStack {
    private Stack<Integer> stack;
    private Stack<Integer> minStack;
    private Stack<Integer> maxStack;
    
    public MinMaxStack() {
        stack = new Stack<>();
        minStack = new Stack<>();
        maxStack = new Stack<>();
    }
    
    public void push(int val) {
        stack.push(val);
        
        if (minStack.isEmpty() || val <= minStack.peek()) {
            minStack.push(val);
        }
        
        if (maxStack.isEmpty() || val >= maxStack.peek()) {
            maxStack.push(val);
        }
    }
    
    public void pop() {
        int val = stack.pop();
        
        if (val == minStack.peek()) {
            minStack.pop();
        }
        
        if (val == maxStack.peek()) {
            maxStack.pop();
        }
    }
    
    public int top() {
        return stack.peek();
    }
    
    public int getMin() {
        return minStack.peek();
    }
    
    public int getMax() {
        return maxStack.peek();
    }
}

/**
 * Test and demonstration
 */
class MinStackTest {
    public static void main(String[] args) {
        System.out.println("=== Min Stack Example ===\n");
        
        MinStack minStack = new MinStack();
        
        minStack.push(-2);
        minStack.push(0);
        minStack.push(-3);
        
        System.out.println("After pushing -2, 0, -3:");
        System.out.println("getMin() = " + minStack.getMin()); // -3
        
        minStack.pop();
        System.out.println("\nAfter pop():");
        System.out.println("top() = " + minStack.top());       // 0
        System.out.println("getMin() = " + minStack.getMin()); // -2
        
        // Test MinStack2 (with pairs)
        System.out.println("\n=== Min Stack 2 (Pairs) ===\n");
        MinStack2 minStack2 = new MinStack2();
        minStack2.push(5);
        minStack2.push(3);
        minStack2.push(7);
        minStack2.push(3);
        minStack2.push(2);
        
        System.out.println("After pushing 5, 3, 7, 3, 2:");
        System.out.println("getMin() = " + minStack2.getMin()); // 2
        minStack2.pop();
        System.out.println("After pop(), getMin() = " + minStack2.getMin()); // 3
        
        // Test MaxStack
        System.out.println("\n=== Max Stack ===\n");
        MaxStack maxStack = new MaxStack();
        maxStack.push(5);
        maxStack.push(1);
        maxStack.push(5);
        
        System.out.println("After pushing 5, 1, 5:");
        System.out.println("getMax() = " + maxStack.getMax()); // 5
        maxStack.pop();
        System.out.println("After pop(), getMax() = " + maxStack.getMax()); // 5
        
        // Test MinMaxStack
        System.out.println("\n=== Min/Max Stack ===\n");
        MinMaxStack mmStack = new MinMaxStack();
        mmStack.push(3);
        mmStack.push(1);
        mmStack.push(5);
        mmStack.push(2);
        
        System.out.println("After pushing 3, 1, 5, 2:");
        System.out.println("getMin() = " + mmStack.getMin()); // 1
        System.out.println("getMax() = " + mmStack.getMax()); // 5
        System.out.println("top() = " + mmStack.top());       // 2
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Two stacks approach is most intuitive");
        System.out.println("2. Single stack with pairs saves space for sparse mins");
        System.out.println("3. CRITICAL: Check for duplicates when popping minStack");
        System.out.println("4. Use <= not < when pushing to minStack");
        System.out.println("5. Follow-ups: MaxStack, MinMaxStack, popMax()");
        System.out.println("6. Space: O(n) worst case, O(1) best case");
    }
}

