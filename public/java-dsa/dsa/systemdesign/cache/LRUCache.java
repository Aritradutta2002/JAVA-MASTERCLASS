package dsa.systemdesign.cache;
/**
 * LRU CACHE
 * 
 * LeetCode #146 - Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Apple, Bloomberg
 * 
 * Design a Least Recently Used (LRU) cache with O(1) operations:
 * - get(key): Get value if key exists
 * - put(key, value): Set or insert value
 * 
 * When capacity is reached, evict the least recently used item.
 * 
 * Solution: HashMap + Doubly Linked List
 * - HashMap: O(1) access to nodes
 * - Doubly Linked List: O(1) add/remove operations
 * 
 * Time: O(1) for both get and put
 * Space: O(capacity)
 */

import java.util.HashMap;
import java.util.Map;

public class LRUCache {
    
    // Doubly Linked List Node
    private class Node {
        int key;
        int value;
        Node prev;
        Node next;
        
        public Node(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }
    
    private Map<Integer, Node> cache;
    private int capacity;
    private Node head; // Dummy head (most recent)
    private Node tail; // Dummy tail (least recent)
    
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        
        // Initialize dummy head and tail
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }
    
    /**
     * Get value by key
     * If key exists, move to head (most recently used)
     */
    public int get(int key) {
        if (!cache.containsKey(key)) {
            return -1;
        }
        
        Node node = cache.get(key);
        moveToHead(node);
        return node.value;
    }
    
    /**
     * Put key-value pair
     * If key exists, update value and move to head
     * If capacity reached, evict LRU item
     */
    public void put(int key, int value) {
        if (cache.containsKey(key)) {
            // Update existing key
            Node node = cache.get(key);
            node.value = value;
            moveToHead(node);
        } else {
            // Add new key
            Node newNode = new Node(key, value);
            cache.put(key, newNode);
            addToHead(newNode);
            
            // Check capacity
            if (cache.size() > capacity) {
                // Remove LRU item
                Node lru = removeTail();
                cache.remove(lru.key);
            }
        }
    }
    
    // Add node right after head
    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    // Remove node from list
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    // Move node to head (mark as most recently used)
    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }
    
    // Remove and return tail node (LRU)
    private Node removeTail() {
        Node lru = tail.prev;
        removeNode(lru);
        return lru;
    }
    
    // For testing/debugging
    public void printCache() {
        System.out.print("Cache (MRU -> LRU): ");
        Node curr = head.next;
        while (curr != tail) {
            System.out.print("(" + curr.key + ":" + curr.value + ") ");
            curr = curr.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        System.out.println("=== LRU Cache Example ===\n");
        
        LRUCache cache = new LRUCache(2);
        
        cache.put(1, 1);
        System.out.println("put(1, 1)");
        cache.printCache();
        
        cache.put(2, 2);
        System.out.println("put(2, 2)");
        cache.printCache();
        
        System.out.println("get(1) = " + cache.get(1)); // returns 1
        cache.printCache();
        
        cache.put(3, 3); // evicts key 2
        System.out.println("put(3, 3) - evicts key 2");
        cache.printCache();
        
        System.out.println("get(2) = " + cache.get(2)); // returns -1 (not found)
        
        cache.put(4, 4); // evicts key 1
        System.out.println("put(4, 4) - evicts key 1");
        cache.printCache();
        
        System.out.println("get(1) = " + cache.get(1)); // returns -1
        System.out.println("get(3) = " + cache.get(3)); // returns 3
        System.out.println("get(4) = " + cache.get(4)); // returns 4
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. MUST use HashMap + Doubly Linked List for O(1)");
        System.out.println("2. Dummy head/tail simplifies edge case handling");
        System.out.println("3. moveToHead = remove + addToHead");
        System.out.println("4. Always update cache map when adding/removing nodes");
        System.out.println("5. Follow-ups: LFU Cache, TTL cache, thread-safe version");
        System.out.println("6. Alternative: LinkedHashMap (Java built-in)");
    }
}

/**
 * BONUS: LRU Cache using LinkedHashMap
 * 
 * LinkedHashMap maintains insertion order and provides removeEldestEntry
 */
class LRUCacheSimple extends java.util.LinkedHashMap<Integer, Integer> {
    private int capacity;
    
    public LRUCacheSimple(int capacity) {
        super(capacity, 0.75f, true); // true = access-order
        this.capacity = capacity;
    }
    
    public int get(int key) {
        return super.getOrDefault(key, -1);
    }
    
    public void put(int key, int value) {
        super.put(key, value);
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > capacity;
    }
}

