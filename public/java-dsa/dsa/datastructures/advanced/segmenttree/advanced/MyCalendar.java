package dsa.datastructures.advanced.segmenttree.advanced;

import java.util.*;

/**
 * My Calendar I, II, III (LeetCode 729, 731, 732)
 * FAANG Frequency: High (Google, Amazon, Facebook)
 * 
 * Problem: Book events and handle overlaps
 */
public class MyCalendar {
    
    // My Calendar I - No overlaps allowed
    static class MyCalendarOne {
        TreeMap<Integer, Integer> calendar;
        
        public MyCalendarOne() {
            calendar = new TreeMap<>();
        }
        
        public boolean book(int start, int end) {
            Integer prev = calendar.floorKey(start);
            Integer next = calendar.ceilingKey(start);
            
            if ((prev == null || calendar.get(prev) <= start) &&
                (next == null || end <= next)) {
                calendar.put(start, end);
                return true;
            }
            
            return false;
        }
    }
    
    // My Calendar II - At most double booking
    static class MyCalendarTwo {
        List<int[]> calendar;
        List<int[]> overlaps;
        
        public MyCalendarTwo() {
            calendar = new ArrayList<>();
            overlaps = new ArrayList<>();
        }
        
        public boolean book(int start, int end) {
            // Check if it causes triple booking
            for (int[] overlap : overlaps) {
                if (start < overlap[1] && end > overlap[0]) {
                    return false;
                }
            }
            
            // Add new overlaps
            for (int[] event : calendar) {
                if (start < event[1] && end > event[0]) {
                    overlaps.add(new int[]{
                        Math.max(start, event[0]),
                        Math.min(end, event[1])
                    });
                }
            }
            
            calendar.add(new int[]{start, end});
            return true;
        }
    }
    
    // My Calendar III - Count max k-booking
    static class MyCalendarThree {
        TreeMap<Integer, Integer> timeline;
        
        public MyCalendarThree() {
            timeline = new TreeMap<>();
        }
        
        public int book(int start, int end) {
            timeline.put(start, timeline.getOrDefault(start, 0) + 1);
            timeline.put(end, timeline.getOrDefault(end, 0) - 1);
            
            int ongoing = 0, maxBooking = 0;
            for (int count : timeline.values()) {
                ongoing += count;
                maxBooking = Math.max(maxBooking, ongoing);
            }
            
            return maxBooking;
        }
    }
    
    // Using Segment Tree for Calendar III
    static class MyCalendarThreeSegmentTree {
        class Node {
            int start, end;
            int count, lazy;
            Node left, right;
            
            Node(int start, int end) {
                this.start = start;
                this.end = end;
            }
        }
        
        private Node root;
        private static final int MAX = 1000000000;
        
        public MyCalendarThreeSegmentTree() {
            root = new Node(0, MAX);
        }
        
        public int book(int start, int end) {
            update(root, start, end - 1);
            return query(root);
        }
        
        private void update(Node node, int start, int end) {
            if (start > node.end || end < node.start) {
                return;
            }
            
            if (start <= node.start && node.end <= end) {
                node.count++;
                node.lazy++;
                return;
            }
            
            pushDown(node);
            update(node.left, start, end);
            update(node.right, start, end);
            node.count = Math.max(node.left.count, node.right.count);
        }
        
        private int query(Node node) {
            return node.count;
        }
        
        private void pushDown(Node node) {
            int mid = node.start + (node.end - node.start) / 2;
            
            if (node.left == null) {
                node.left = new Node(node.start, mid);
            }
            if (node.right == null) {
                node.right = new Node(mid + 1, node.end);
            }
            
            if (node.lazy > 0) {
                node.left.count += node.lazy;
                node.left.lazy += node.lazy;
                node.right.count += node.lazy;
                node.right.lazy += node.lazy;
                node.lazy = 0;
            }
        }
    }
    
    public static void main(String[] args) {
        // Test Calendar I
        System.out.println("=== My Calendar I ===");
        MyCalendarOne cal1 = new MyCalendarOne();
        System.out.println(cal1.book(10, 20)); // true
        System.out.println(cal1.book(15, 25)); // false
        System.out.println(cal1.book(20, 30)); // true
        
        // Test Calendar II
        System.out.println("\n=== My Calendar II ===");
        MyCalendarTwo cal2 = new MyCalendarTwo();
        System.out.println(cal2.book(10, 20)); // true
        System.out.println(cal2.book(50, 60)); // true
        System.out.println(cal2.book(10, 40)); // true
        System.out.println(cal2.book(5, 15)); // false
        
        // Test Calendar III
        System.out.println("\n=== My Calendar III ===");
        MyCalendarThree cal3 = new MyCalendarThree();
        System.out.println(cal3.book(10, 20)); // 1
        System.out.println(cal3.book(50, 60)); // 1
        System.out.println(cal3.book(10, 40)); // 2
        System.out.println(cal3.book(5, 15)); // 3
    }
}

