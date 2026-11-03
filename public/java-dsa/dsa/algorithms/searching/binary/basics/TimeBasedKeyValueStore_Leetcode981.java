package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Time Based Key-Value Store (Leetcode 981)
 *   Difficulty: Medium
 *   Companies: Amazon, Google, Microsoft, Facebook, Apple
 */
import java.util.*;

public class TimeBasedKeyValueStore_Leetcode981 {
    private Map<String, TreeMap<Integer, String>> map;
    
    public TimeBasedKeyValueStore_Leetcode981() {
        map = new HashMap<>();
    }
    
    public void set(String key, String value, int timestamp) {
        if (!map.containsKey(key)) {
            map.put(key, new TreeMap<>());
        }
        map.get(key).put(timestamp, value);
    }
    
    public String get(String key, int timestamp) {
        if (!map.containsKey(key)) {
            return "";
        }
        
        TreeMap<Integer, String> treeMap = map.get(key);
        Integer floorKey = treeMap.floorKey(timestamp);
        
        return floorKey != null ? treeMap.get(floorKey) : "";
    }
    
    public static void main(String[] args) {
        TimeBasedKeyValueStore_Leetcode981 store = new TimeBasedKeyValueStore_Leetcode981();
        store.set("foo", "bar", 1);
        System.out.println(store.get("foo", 1));  // "bar"
        System.out.println(store.get("foo", 3));  // "bar"
        store.set("foo", "bar2", 4);
        System.out.println(store.get("foo", 4));  // "bar2"
        System.out.println(store.get("foo", 5));  // "bar2"
    }
}

