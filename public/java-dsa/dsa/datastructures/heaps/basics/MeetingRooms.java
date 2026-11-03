package dsa.datastructures.heaps.basics;

import java.util.*;

/**
 * Meeting Rooms II (LeetCode 253) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Bloomberg)
 * 
 * Problem: Find minimum number of meeting rooms required
 * Time: O(n log n), Space: O(n)
 */
public class MeetingRooms {
    
    // Approach 1: Using Min Heap (Most intuitive)
    public int minMeetingRooms(int[][] intervals) {
        if (intervals == null || intervals.length == 0) {
            return 0;
        }
        
        // Sort by start time
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        
        // Min heap to track end times of meetings
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        minHeap.offer(intervals[0][1]);
        
        for (int i = 1; i < intervals.length; i++) {
            // If earliest ending meeting finishes before current starts
            if (intervals[i][0] >= minHeap.peek()) {
                minHeap.poll();
            }
            
            minHeap.offer(intervals[i][1]);
        }
        
        return minHeap.size();
    }
    
    // Approach 2: Using Two Arrays (More optimal)
    public int minMeetingRoomsTwoArrays(int[][] intervals) {
        int n = intervals.length;
        int[] starts = new int[n];
        int[] ends = new int[n];
        
        for (int i = 0; i < n; i++) {
            starts[i] = intervals[i][0];
            ends[i] = intervals[i][1];
        }
        
        Arrays.sort(starts);
        Arrays.sort(ends);
        
        int rooms = 0, endPtr = 0;
        
        for (int i = 0; i < n; i++) {
            if (starts[i] < ends[endPtr]) {
                rooms++;
            } else {
                endPtr++;
            }
        }
        
        return rooms;
    }
    
    // Meeting Rooms I (LeetCode 252) - Can attend all meetings?
    public boolean canAttendMeetings(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < intervals[i - 1][1]) {
                return false;
            }
        }
        
        return true;
    }
    
    // Meeting Rooms III (LeetCode 2402) - HARD
    public int mostBooked(int n, int[][] meetings) {
        Arrays.sort(meetings, (a, b) -> a[0] - b[0]);
        
        // Min heap for available rooms
        PriorityQueue<Integer> available = new PriorityQueue<>();
        for (int i = 0; i < n; i++) {
            available.offer(i);
        }
        
        // Min heap for busy rooms [endTime, roomNumber]
        PriorityQueue<long[]> busy = new PriorityQueue<>((a, b) -> 
            a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1])
        );
        
        int[] count = new int[n];
        
        for (int[] meeting : meetings) {
            long start = meeting[0];
            long end = meeting[1];
            
            // Free up rooms that have finished
            while (!busy.isEmpty() && busy.peek()[0] <= start) {
                available.offer((int) busy.poll()[1]);
            }
            
            if (!available.isEmpty()) {
                int room = available.poll();
                busy.offer(new long[]{end, room});
                count[room]++;
            } else {
                long[] earliest = busy.poll();
                long newEnd = earliest[0] + (end - start);
                int room = (int) earliest[1];
                busy.offer(new long[]{newEnd, room});
                count[room]++;
            }
        }
        
        int maxRoom = 0;
        for (int i = 1; i < n; i++) {
            if (count[i] > count[maxRoom]) {
                maxRoom = i;
            }
        }
        
        return maxRoom;
    }
    
    public static void main(String[] args) {
        MeetingRooms solution = new MeetingRooms();
        
        // Test Case 1
        int[][] intervals1 = {{0, 30}, {5, 10}, {15, 20}};
        System.out.println("Min Meeting Rooms: " + solution.minMeetingRooms(intervals1)); // 2
        
        // Test Case 2
        int[][] intervals2 = {{7, 10}, {2, 4}};
        System.out.println("Min Meeting Rooms: " + solution.minMeetingRooms(intervals2)); // 1
        
        // Test Case 3
        int[][] intervals3 = {{0, 30}, {5, 10}, {15, 20}};
        System.out.println("Can Attend All: " + solution.canAttendMeetings(intervals3)); // false
    }
}

