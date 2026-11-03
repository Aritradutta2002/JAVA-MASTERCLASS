package dsa.systemdesign.cache;

import java.util.*;

/**
 * Time Based Key-Value Store (LeetCode 981) - MEDIUM
 * FAANG Frequency: High (Amazon, Google)
 * 
 * Problem: Store multiple values for same key with timestamps
 * Time: O(log n) for get, O(1) for set
 */
public class TimeBasedKeyValueStore {
    
    class TimeMap {
        private Map<String, TreeMap<Integer, String>> map;
        
        public TimeMap() {
            map = new HashMap<>();
        }
        
        public void set(String key, String value, int timestamp) {
            map.putIfAbsent(key, new TreeMap<>());
            map.get(key).put(timestamp, value);
        }
        
        public String get(String key, int timestamp) {
            if (!map.containsKey(key)) {
                return "";
            }
            
            TreeMap<Integer, String> treeMap = map.get(key);
            Integer floorKey = treeMap.floorKey(timestamp);
            
            return floorKey == null ? "" : treeMap.get(floorKey);
        }
    }
    
    // Alternative: Using List + Binary Search
    class TimeMapBinarySearch {
        class Pair {
            int timestamp;
            String value;
            
            Pair(int timestamp, String value) {
                this.timestamp = timestamp;
                this.value = value;
            }
        }
        
        private Map<String, List<Pair>> map;
        
        public TimeMapBinarySearch() {
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
            int left = 0, right = list.size() - 1;
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
    
    public static void main(String[] args) {
        TimeBasedKeyValueStore.TimeMap timeMap = 
            new TimeBasedKeyValueStore().new TimeMap();
        
        timeMap.set("foo", "bar", 1);
        System.out.println(timeMap.get("foo", 1)); // "bar"
        System.out.println(timeMap.get("foo", 3)); // "bar"
        timeMap.set("foo", "bar2", 4);
        System.out.println(timeMap.get("foo", 4)); // "bar2"
        System.out.println(timeMap.get("foo", 5)); // "bar2"
    }
}

