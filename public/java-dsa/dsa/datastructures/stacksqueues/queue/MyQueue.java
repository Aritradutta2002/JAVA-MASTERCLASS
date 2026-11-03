package dsa.datastructures.stacksqueues.queue;

import java.util.NoSuchElementException;
import java.util.Stack;

/*
 *   Author : Aritra
 *   Created On: Friday,16.05.2025 12:07 am
 */

public class MyQueue {
    private final Stack<Integer> stack1;
    private final Stack<Integer> stack2;

    public MyQueue() {
        stack1 = new Stack<>();
        stack2 = new Stack<>();
    }

    public void enqueue(int x) {
        stack1.push(x);
    }

    public int dequeue() {
        if (empty()) {
            throw new NoSuchElementException("Queue is empty");
        }
        if (stack2.isEmpty()) {
            while (!stack1.isEmpty()) {
                stack2.push(stack1.pop());
            }
        }
        return stack2.pop();
    }

    public int peek() {
        if (empty()) {
            throw new NoSuchElementException("Queue is empty");
        }
        if (stack2.isEmpty()) {
            while (!stack1.isEmpty()) {
                stack2.push(stack1.pop());
            }
        }
        return stack2.peek();
    }

    public boolean empty() {
        return stack1.isEmpty() && stack2.isEmpty();
    }
}



