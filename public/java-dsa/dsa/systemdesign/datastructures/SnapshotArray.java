package dsa.systemdesign.datastructures;

import java.util.*;

/**
 * Snapshot Array (LeetCode 1146) - MEDIUM
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Array that supports snapshots
 * Time: O(1) for set, O(log n) for get
 */
public class SnapshotArray {
    
    private Map<Integer, TreeMap<Integer, Integer>> map;
    private int snapId;
    
    public SnapshotArray(int length) {
        map = new HashMap<>();
        snapId = 0;
        
        // Initialize with snap_id 0, value 0
        for (int i = 0; i < length; i++) {
            map.put(i, new TreeMap<>());
            map.get(i).put(0, 0);
        }
    }
    
    public void set(int index, int val) {
        map.get(index).put(snapId, val);
    }
    
    public int snap() {
        return snapId++;
    }
    
    public int get(int index, int snap_id) {
        return map.get(index).floorEntry(snap_id).getValue();
    }
    
    public static void main(String[] args) {
        SnapshotArray arr = new SnapshotArray(3);
        arr.set(0, 5);
        System.out.println(arr.snap()); // 0
        arr.set(0, 6);
        System.out.println(arr.get(0, 0)); // 5
    }
}

