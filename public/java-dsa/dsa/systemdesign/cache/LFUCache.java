package dsa.systemdesign.cache;

import java.util.*;

/**
 * LFU Cache (LeetCode 460) - HARD
 * FAANG Frequency: Very High (Amazon, Google, Facebook)
 * 
 * Problem: Implement Least Frequently Used cache
 * Time: O(1) for get and put
 */
public class LFUCache {
    
    class Node {
        int key, value, freq;
        Node prev, next;
        
        Node(int key, int value) {
            this.key = key;
            this.value = value;
            this.freq = 1;
        }
    }
    
    class DLList {
        Node head, tail;
        int size;
        
        DLList() {
            head = new Node(0, 0);
            tail = new Node(0, 0);
            head.next = tail;
            tail.prev = head;
        }
        
        void add(Node node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
            size++;
        }
        
        void remove(Node node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            size--;
        }
        
        Node removeLast() {
            if (size == 0) return null;
            Node node = tail.prev;
            remove(node);
            return node;
        }
    }
    
    private Map<Integer, Node> cache;
    private Map<Integer, DLList> freqMap;
    private int capacity;
    private int minFreq;
    
    public LFUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.freqMap = new HashMap<>();
        this.minFreq = 0;
    }
    
    public int get(int key) {
        if (!cache.containsKey(key)) {
            return -1;
        }
        
        Node node = cache.get(key);
        updateFreq(node);
        return node.value;
    }
    
    public void put(int key, int value) {
        if (capacity == 0) return;
        
        if (cache.containsKey(key)) {
            Node node = cache.get(key);
            node.value = value;
            updateFreq(node);
        } else {
            if (cache.size() >= capacity) {
                DLList minFreqList = freqMap.get(minFreq);
                Node toRemove = minFreqList.removeLast();
                cache.remove(toRemove.key);
            }
            
            Node newNode = new Node(key, value);
            cache.put(key, newNode);
            freqMap.putIfAbsent(1, new DLList());
            freqMap.get(1).add(newNode);
            minFreq = 1;
        }
    }
    
    private void updateFreq(Node node) {
        int freq = node.freq;
        DLList list = freqMap.get(freq);
        list.remove(node);
        
        if (freq == minFreq && list.size == 0) {
            minFreq++;
        }
        
        node.freq++;
        freqMap.putIfAbsent(node.freq, new DLList());
        freqMap.get(node.freq).add(node);
    }
    
    public static void main(String[] args) {
        LFUCache cache = new LFUCache(2);
        
        cache.put(1, 1);
        cache.put(2, 2);
        System.out.println(cache.get(1)); // 1
        cache.put(3, 3); // Evicts key 2
        System.out.println(cache.get(2)); // -1
        System.out.println(cache.get(3)); // 3
        cache.put(4, 4); // Evicts key 1
        System.out.println(cache.get(1)); // -1
        System.out.println(cache.get(3)); // 3
        System.out.println(cache.get(4)); // 4
    }
}

