# Greedy Algorithms - FAANG Interview Problems

## Folder Structure
```
GREEDY/
├── intervals/          # Interval scheduling problems
├── arrays/            # Array-based greedy problems
├── strings/           # String greedy problems
└── advanced/          # Advanced greedy algorithms
```

## Pattern Overview
Greedy algorithms make locally optimal choices at each step, hoping to find global optimum.

## Problem Categories

### 1. Interval Problems (Very Important)
- **MergeIntervals.java** - LeetCode 56 (MEDIUM) ⭐⭐⭐
- **NonOverlappingIntervals.java** - LeetCode 435 (MEDIUM) ⭐⭐⭐
- **MeetingRooms.java** - LeetCode 252, 253 (MEDIUM) ⭐⭐⭐
- **InsertInterval.java** - LeetCode 57 (MEDIUM) ⭐⭐

### 2. Array Problems
- **JumpGame.java** - LeetCode 55, 45 (MEDIUM/HARD) ⭐⭐⭐
- **GasStation.java** - LeetCode 134 (MEDIUM) ⭐⭐⭐
- **PartitionLabels.java** - LeetCode 763 (MEDIUM) ⭐⭐
- **MaximumSubarray.java** - LeetCode 53 (MEDIUM) ⭐⭐⭐

### 3. String Problems
- **RemoveKDigits.java** - LeetCode 402 (MEDIUM) ⭐⭐
- **MinimumDeletions.java** - LeetCode 1653 (MEDIUM) ⭐⭐

### 4. Advanced Greedy
- **TaskScheduler.java** - LeetCode 621 (MEDIUM) ⭐⭐⭐
- **ReorganizeString.java** - LeetCode 767 (MEDIUM) ⭐⭐

## FAANG Interview Frequency
- **Amazon**: Very High (Intervals, Jump Game, Gas Station)
- **Google**: Very High (All categories)
- **Facebook/Meta**: High (Intervals, Array problems)
- **Microsoft**: High (Intervals, Task Scheduler)
- **Apple**: Medium-High

## Key Patterns

### Interval Sorting
```java
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
```

### Greedy Choice
```java
// Make locally optimal choice
int best = findBest(options);
// Update state
state = update(state, best);
```

## Common Tricks
1. **Sort first**: Most interval problems need sorting
2. **Track boundaries**: Keep track of min/max values
3. **Greedy proof**: Ensure greedy choice leads to optimal solution
4. **Counter-examples**: Test edge cases

## Practice Strategy
1. Master interval problems (sort + merge pattern)
2. Solve Jump Game variations
3. Practice array greedy problems
4. Learn proof techniques for greedy correctness
