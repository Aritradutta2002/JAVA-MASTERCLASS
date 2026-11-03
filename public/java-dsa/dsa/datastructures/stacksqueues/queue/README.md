# Queue Problems - LeetCode Interview Ready

This folder contains high-quality queue-based problems commonly asked in technical interviews.

## 📚 Problem Categories

### 1. **Design Problems**
- **MyQueue.java** - Implement Queue using Stacks (LeetCode #232) - Easy
- **Implement_Stack_using_Queues.java** - Implement Stack using Queues (LeetCode #225) - Easy
- **DesignCircularQueue.java** - Design Circular Queue (LeetCode #622) - Medium
- **DesignCircularDeque.java** - Design Circular Deque (LeetCode #641) - Medium

### 2. **Sliding Window with Deque**
- **SlidingWindowMaximum.java** - Sliding Window Maximum (LeetCode #239) - Hard ⭐
  - Uses monotonic deque technique
  - O(n) time complexity

### 3. **BFS Graph Problems**
- **RottenOranges.java** - Rotting Oranges (LeetCode #994) - Medium ⭐
  - Multi-source BFS
  - Grid traversal pattern
  
- **ShortestPathInBinaryMatrix.java** - Shortest Path in Binary Matrix (LeetCode #1091) - Medium
  - 8-directional movement
  - Classic BFS shortest path

- **WallsAndGates.java** - Walls and Gates (LeetCode #286) - Medium
  - Multi-source BFS from gates
  - Premium problem

### 4. **BFS Puzzle Problems**
- **OpenTheLock.java** - Open the Lock (LeetCode #752) - Medium ⭐
  - State space search
  - Avoiding deadends

- **SnakesAndLadders.java** - Snakes and Ladders (LeetCode #909) - Medium
  - Board game simulation
  - BFS with transformations

- **JumpGameIII.java** - Jump Game III (LeetCode #1306) - Medium
  - Array traversal with BFS/DFS

- **PerfectSquares.java** - Perfect Squares (LeetCode #279) - Medium
  - BFS + DP approaches
  - Number theory problem

### 5. **Stream Processing**
- **FirstUniqueCharacter.java** - First Unique Character (LeetCode #387) - Easy
  - Queue-based stream processing
  
- **NumberOfRecentCalls.java** - Number of Recent Calls (LeetCode #933) - Easy
  - Time-based queue operations
  
- **MovingAverageFromDataStream.java** - Moving Average (LeetCode #346) - Easy
  - Sliding window with queue

- **DesignHitCounter.java** - Design Hit Counter (LeetCode #362) - Medium
  - Time-series data management
  - Premium problem

### 6. **Advanced Design**
- **TimeBasedKeyValueStore.java** - Time Based Key-Value Store (LeetCode #981) - Medium
  - Binary search + HashMap
  
- **TaskScheduler.java** - Task Scheduler (LeetCode #621) - Medium ⭐
  - Greedy + Queue approach
  - CPU scheduling simulation

## 🎯 Key Concepts Covered

1. **Queue Operations**: FIFO, enqueue, dequeue, peek
2. **Circular Queue**: Efficient space usage with circular buffer
3. **Deque**: Double-ended queue operations
4. **BFS Traversal**: Level-order traversal, shortest path
5. **Multi-source BFS**: Starting BFS from multiple sources
6. **Monotonic Deque**: Maintaining order for sliding window problems
7. **Stream Processing**: Real-time data processing
8. **Design Patterns**: Implementing data structures from scratch

## 💡 Interview Tips

1. **BFS vs DFS**: Use BFS when finding shortest path or level-order traversal
2. **Queue vs Stack**: Queue for FIFO, Stack for LIFO
3. **Circular Buffer**: Save space by reusing array positions
4. **Multi-source BFS**: Add all sources to queue initially
5. **Monotonic Deque**: Keep elements in increasing/decreasing order
6. **Time Complexity**: Most queue operations are O(1)

## 🔥 Must-Know Problems

⭐ **Top Priority for Interviews:**
- Sliding Window Maximum
- Rotting Oranges
- Open the Lock
- Task Scheduler

## 📈 Difficulty Distribution

- **Easy**: 5 problems
- **Medium**: 13 problems
- **Hard**: 1 problem

## 🚀 Practice Strategy

1. Start with basic design problems (MyQueue, DesignCircularQueue)
2. Master BFS patterns (RottenOranges, ShortestPathInBinaryMatrix)
3. Learn advanced techniques (SlidingWindowMaximum, TaskScheduler)
4. Practice stream processing (MovingAverage, HitCounter)
5. Solve puzzle problems (OpenTheLock, SnakesAndLadders)

---
**Author**: Aritra  
**Last Updated**: October 2025
