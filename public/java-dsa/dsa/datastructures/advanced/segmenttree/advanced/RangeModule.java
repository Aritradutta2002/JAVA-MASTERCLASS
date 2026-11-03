package dsa.datastructures.advanced.segmenttree.advanced;

import java.util.*;

/**
 * Range Module (LeetCode 715) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Track ranges and query if range is tracked
 * Time: O(log n) per operation
 */
public class RangeModule {
    
    // Approach 1: Using TreeMap (Most Practical)
    static class RangeModuleTreeMap {
        TreeMap<Integer, Integer> map;
        
        public RangeModuleTreeMap() {
            map = new TreeMap<>();
        }
        
        public void addRange(int left, int right) {
            Integer start = map.floorKey(left);
            Integer end = map.floorKey(right);
            
            if (start != null && map.get(start) >= left) {
                left = start;
            }
            if (end != null && map.get(end) > right) {
                right = map.get(end);
            }
            
            map.put(left, right);
            
            // Remove covered ranges
            map.subMap(left, false, right, false).clear();
        }
        
        public boolean queryRange(int left, int right) {
            Integer start = map.floorKey(left);
            return start != null && map.get(start) >= right;
        }
        
        public void removeRange(int left, int right) {
            Integer start = map.floorKey(left);
            Integer end = map.floorKey(right);
            
            if (end != null && map.get(end) > right) {
                map.put(right, map.get(end));
            }
            if (start != null && map.get(start) > left) {
                map.put(start, left);
            }
            
            map.subMap(left, true, right, false).clear();
        }
    }
    
    // Approach 2: Using Segment Tree with Lazy Propagation
    static class RangeModuleSegmentTree {
        class Node {
            int start, end;
            boolean tracked;
            boolean lazy;
            Node left, right;
            
            Node(int start, int end) {
                this.start = start;
                this.end = end;
                this.tracked = false;
                this.lazy = false;
            }
        }
        
        private Node root;
        private static final int MAX = 1000000000;
        
        public RangeModuleSegmentTree() {
            root = new Node(0, MAX);
        }
        
        public void addRange(int left, int right) {
            update(root, left, right - 1, true);
        }
        
        public boolean queryRange(int left, int right) {
            return query(root, left, right - 1);
        }
        
        public void removeRange(int left, int right) {
            update(root, left, right - 1, false);
        }
        
        private void update(Node node, int left, int right, boolean tracked) {
            if (node == null || left > node.end || right < node.start) {
                return;
            }
            
            if (left <= node.start && node.end <= right) {
                node.tracked = tracked;
                node.lazy = true;
                node.left = null;
                node.right = null;
                return;
            }
            
            pushDown(node);
            
            int mid = node.start + (node.end - node.start) / 2;
            update(node.left, left, right, tracked);
            update(node.right, left, right, tracked);
            
            node.tracked = node.left.tracked && node.right.tracked;
        }
        
        private boolean query(Node node, int left, int right) {
            if (node == null || left > node.end || right < node.start) {
                return true;
            }
            
            if (node.lazy) {
                return node.tracked;
            }
            
            if (left <= node.start && node.end <= right) {
                return node.tracked;
            }
            
            pushDown(node);
            
            return query(node.left, left, right) && query(node.right, left, right);
        }
        
        private void pushDown(Node node) {
            if (node.lazy) {
                int mid = node.start + (node.end - node.start) / 2;
                
                if (node.left == null) {
                    node.left = new Node(node.start, mid);
                }
                if (node.right == null) {
                    node.right = new Node(mid + 1, node.end);
                }
                
                node.left.tracked = node.tracked;
                node.left.lazy = true;
                node.right.tracked = node.tracked;
                node.right.lazy = true;
                
                node.lazy = false;
            } else {
                int mid = node.start + (node.end - node.start) / 2;
                if (node.left == null) {
                    node.left = new Node(node.start, mid);
                }
                if (node.right == null) {
                    node.right = new Node(mid + 1, node.end);
                }
            }
        }
    }
    
    public static void main(String[] args) {
        RangeModuleTreeMap rm = new RangeModuleTreeMap();
        
        rm.addRange(10, 20);
        rm.removeRange(14, 16);
        System.out.println(rm.queryRange(10, 14)); // true
        System.out.println(rm.queryRange(13, 15)); // false
        System.out.println(rm.queryRange(16, 17)); // true
    }
}

