# Monotonic Stack & Queue - FAANG Interview Problems

## Pattern Overview
Monotonic Stack/Queue maintains elements in monotonic (increasing/decreasing) order. Essential for problems involving:
- Next/Previous Greater/Smaller elements
- Range queries with min/max
- Histogram-based problems

## Problem Categories

### 1. Next/Previous Greater/Smaller Elements
- **NextGreaterElement.java** - LeetCode 496, 503 (EASY/MEDIUM)
- **StockSpanProblem.java** - LeetCode 901 (MEDIUM)

### 2. Histogram Problems (Very Important for FAANG)
- **LargestRectangleHistogram.java** - LeetCode 84 (HARD) ⭐⭐⭐
- **MaximalRectangle.java** - LeetCode 85 (HARD) ⭐⭐⭐
- **TrappingRainWater.java** - LeetCode 42 (HARD) ⭐⭐⭐

### 3. Sliding Window with Monotonic Queue
- **SlidingWindowMaximum.java** - LeetCode 239 (HARD) ⭐⭐⭐

## Key Patterns

### Monotonic Increasing Stack
```java
Stack<Integer> stack = new Stack<>();
for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && arr[stack.peek()] > arr[i]) {
        stack.pop();
    }
    stack.push(i);
}
```

### Monotonic Decreasing Deque (Sliding Window Max)
```java
Deque<Integer> deque = new ArrayDeque<>();
for (int i = 0; i < n; i++) {
    while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
        deque.pollFirst();
    }
    while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i]) {
        deque.pollLast();
    }
    deque.offerLast(i);
}
```

## FAANG Interview Frequency
- **Amazon**: Very High (Histogram, Trapping Water, Stock Span)
- **Google**: Very High (All problems)
- **Facebook/Meta**: High (Sliding Window Max, Histogram)
- **Microsoft**: High (Next Greater, Histogram)
- **Apple**: Medium-High

## Time Complexity
Most problems: **O(n)** with amortized analysis
- Each element pushed once, popped once

## Practice Strategy
1. Master basic Next Greater/Smaller pattern
2. Solve Largest Rectangle in Histogram (foundation for many problems)
3. Practice Trapping Rain Water (multiple approaches)
4. Tackle Sliding Window Maximum (monotonic deque)
5. Combine patterns: Maximal Rectangle

## Related Topics
- Stack & Queue basics
- Two Pointers
- Dynamic Programming (alternative approaches)
- Sliding Window
