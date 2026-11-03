package dsa.datastructures.stacksqueues.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 *   Author : Aritra
 *   LeetCode #981 - Time Based Key-Value Store (Medium)
 *   Problem: Design time-based key-value data structure
 *   Time Complexity: set O(1), get O(log n) using binary search
 */

public class TimeBasedKeyValueStore {
    public static void main(String[] args) {
        TimeMap timeMap = new TimeMap();
        
        timeMap.set("foo", "bar", 1);
        System.out.println("get(foo, 1): " + timeMap.get("foo", 1));  // "bar"
        System.out.println("get(foo, 3): " + timeMap.get("foo", 3));  // "bar"
        
        timeMap.set("foo", "bar2", 4);
        System.out.println("get(foo, 4): " + timeMap.get("foo", 4));  // "bar2"
        System.out.println("get(foo, 5): " + timeMap.get("foo", 5));  // "bar2"
        System.out.println("get(foo, 3): " + timeMap.get("foo", 3));  // "bar"
    }
    
    static class TimeMap {
        private Map<String, List<Pair>> map;
        
        static class Pair {
            int timestamp;
            String value;
            
            public Pair(int timestamp, String value) {
                this.timestamp = timestamp;
                this.value = value;
            }
        }
        
        public TimeMap() {
            map = new HashMap<>();
        }
        
        public void set(String key, String value, int timestamp) {
            map.putIfAbsent(key, new ArrayList<>());
            map.get(key).add(new Pair(timestamp, value));
        }
        
        public String get(String key, int timestamp) {
            if (!map.containsKey(key)) {
                return "";
            }
            
            List<Pair> list = map.get(key);
            return binarySearch(list, timestamp);
        }
        
        private String binarySearch(List<Pair> list, int timestamp) {
            int left = 0;
            int right = list.size() - 1;
            String result = "";
            
            while (left <= right) {
                int mid = left + (right - left) / 2;
                
                if (list.get(mid).timestamp <= timestamp) {
                    result = list.get(mid).value;
                    left = mid + 1;
                } else {
                    right = mid - 1;
                }
            }
            
            return result;
        }
    }
}

