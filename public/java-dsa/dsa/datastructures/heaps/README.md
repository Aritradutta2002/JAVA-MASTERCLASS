# Heap & Priority Queue - FAANG Interview Problems

## Pattern Overview
Heap/Priority Queue is essential for problems involving:
- Top K elements
- Kth largest/smallest
- Median finding
- Merge operations
- Scheduling problems

## Problem Categories

### 1. Top K Problems
- **k_problems/KthLargestElement.java** - LeetCode 215 (MEDIUM) ⭐⭐
- **k_problems/TopKFrequentElements.java** - LeetCode 347 (MEDIUM) ⭐⭐
- **KClosestPoints.java** - LeetCode 973 (MEDIUM) ⭐⭐⭐

### 2. Merge Problems
- **applications/MergeKSortedLists.java** - LeetCode 23 (HARD) ⭐⭐⭐

### 3. Median Finding
- **applications/MedianFinder.java** - LeetCode 295 (HARD) ⭐⭐⭐
- **SlidingWindowMedian.java** - LeetCode 480 (HARD) ⭐⭐⭐

### 4. Scheduling & Greedy
- **TaskScheduler.java** - LeetCode 621 (MEDIUM) ⭐⭐⭐
- **MeetingRooms.java** - LeetCode 252, 253, 2402 (MEDIUM/HARD) ⭐⭐⭐
- **IPO.java** - LeetCode 502 (HARD) ⭐⭐

### 5. Basics
- **basics/MinHeap.java** - Implementation
- **basics/MaxHeap.java** - Implementation

## Key Patterns

### Max Heap for Top K Smallest
```java
PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
for (int num : nums) {
    maxHeap.offer(num);
    if (maxHeap.size() > k) {
        maxHeap.poll();
    }
}
```

### Two Heaps for Median
```java
PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a); // Lower half
PriorityQueue<Integer> minHeap = new PriorityQueue<>(); // Upper half

// Maintain: maxHeap.size() == minHeap.size() or maxHeap.size() == minHeap.size() + 1
```

### Custom Comparator
```java
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> {
    if (a[0] != b[0]) return a[0] - b[0];
    return a[1] - b[1];
});
```

## FAANG Interview Frequency
- **Amazon**: Very High (Meeting Rooms, Task Scheduler, K Closest)
- **Google**: Very High (Median Finder, IPO, All K problems)
- **Facebook/Meta**: Very High (K Closest, Merge K Lists)
- **Microsoft**: High (Meeting Rooms, Task Scheduler)
- **Apple**: Medium-High (Top K problems)

## Time Complexity
- Insert: O(log n)
- Delete: O(log n)
- Peek: O(1)
- Build heap: O(n)

## Common Tricks
1. **Max heap for K smallest**: Keep size = k, top is kth smallest
2. **Min heap for K largest**: Keep size = k, top is kth largest
3. **Two heaps**: For median or range queries
4. **Lazy deletion**: Mark for deletion, clean up later
5. **QuickSelect alternative**: O(n) average for kth element

## Practice Strategy
1. Master basic heap operations
2. Solve all Top K problems
3. Learn two-heap pattern (Median Finder)
4. Practice merge problems
5. Tackle scheduling problems (Meeting Rooms, Task Scheduler)

## Related Topics
- Sorting
- QuickSelect
- Binary Search
- Greedy algorithms
