package dsa.systemdesign.datastructures;

import java.util.*;

/**
 * Max Stack (LeetCode 716) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Stack with O(log n) popMax operation
 */
public class MaxStack {
    
    class Node {
        int val;
        Node prev, next;
        
        Node(int val) {
            this.val = val;
        }
    }
    
    private TreeMap<Integer, List<Node>> map;
    private Node head, tail;
    
    public MaxStack() {
        map = new TreeMap<>();
        head = new Node(0);
        tail = new Node(0);
        head.next = tail;
        tail.prev = head;
    }
    
    public void push(int x) {
        Node node = new Node(x);
        node.next = tail;
        node.prev = tail.prev;
        tail.prev.next = node;
        tail.prev = node;
        
        map.putIfAbsent(x, new ArrayList<>());
        map.get(x).add(node);
    }
    
    public int pop() {
        Node node = tail.prev;
        remove(node);
        
        List<Node> list = map.get(node.val);
        list.remove(list.size() - 1);
        if (list.isEmpty()) {
            map.remove(node.val);
        }
        
        return node.val;
    }
    
    public int top() {
        return tail.prev.val;
    }
    
    public int peekMax() {
        return map.lastKey();
    }
    
    public int popMax() {
        int max = peekMax();
        List<Node> list = map.get(max);
        Node node = list.remove(list.size() - 1);
        
        if (list.isEmpty()) {
            map.remove(max);
        }
        
        remove(node);
        return max;
    }
    
    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    public static void main(String[] args) {
        MaxStack stack = new MaxStack();
        stack.push(5);
        stack.push(1);
        stack.push(5);
        System.out.println(stack.top()); // 5
        System.out.println(stack.popMax()); // 5
        System.out.println(stack.top()); // 1
        System.out.println(stack.peekMax()); // 5
        System.out.println(stack.pop()); // 1
        System.out.println(stack.top()); // 5
    }
}

