# Binary Search - Interview Ready Problems

## Overview
This folder contains essential Binary Search problems frequently asked in FAANG/MAANG interviews.

## Problems List

### Medium
1. **Search in Rotated Sorted Array** (Leetcode 33)
   - Companies: Amazon, Facebook, Microsoft, Google, Apple, Bloomberg
   - Key Concept: Modified binary search on rotated array

2. **Find Minimum in Rotated Sorted Array** (Leetcode 153)
   - Companies: Amazon, Microsoft, Facebook, Google, Bloomberg
   - Key Concept: Finding rotation pivot

3. **Find First and Last Position** (Leetcode 34)
   - Companies: Amazon, Facebook, Microsoft, Google, Apple, LinkedIn
   - Key Concept: Lower and upper bound binary search

4. **Search a 2D Matrix** (Leetcode 74)
   - Companies: Amazon, Facebook, Microsoft, Google, Apple
   - Key Concept: Treating 2D array as 1D for binary search

5. **Find Peak Element** (Leetcode 162)
   - Companies: Amazon, Facebook, Microsoft, Google, Apple
   - Key Concept: Binary search on unsorted array

6. **Koko Eating Bananas** (Leetcode 875)
   - Companies: Amazon, Facebook, Google, Microsoft
   - Key Concept: Binary search on answer space

7. **Capacity to Ship Packages Within D Days** (Leetcode 1010)
   - Companies: Amazon, Microsoft, Google, Facebook
   - Key Concept: Binary search on capacity

8. **Find K Closest Elements** (Leetcode 658)
   - Companies: Amazon, Facebook, Google, Microsoft
   - Key Concept: Binary search on sliding window

9. **Search in Rotated Sorted Array II** (Leetcode 81)
   - Companies: Amazon, Facebook, Microsoft, Google
   - Key Concept: Rotated array with duplicates

10. **Single Element in Sorted Array** (Leetcode 540)
    - Companies: Amazon, Google, Microsoft, Facebook
    - Key Concept: Binary search on pairs

11. **Time Based Key-Value Store** (Leetcode 981)
    - Companies: Amazon, Google, Microsoft, Facebook, Apple
    - Key Concept: Binary search with timestamps

12. **Magnetic Force Between Two Balls** (Leetcode 1552)
    - Companies: Amazon, Google, Microsoft
    - Key Concept: Binary search on answer (similar to Aggressive Cows)

13. **Minimized Maximum Products to Store** (Leetcode 2064)
    - Companies: Amazon, Google
    - Key Concept: Minimize maximum with binary search

14. **Kth Smallest in Sorted Matrix** (Leetcode 378)
    - Companies: Amazon, Facebook, Google, Microsoft, Apple
    - Key Concept: Binary search on value range

15. **Aggressive Cows** (Classic Problem)
    - Companies: Amazon, Google, Microsoft (Very Popular)
    - Key Concept: Maximize minimum distance

### Hard
1. **Median of Two Sorted Arrays** (Leetcode 4)
   - Companies: Amazon, Microsoft, Google, Facebook, Apple, Bloomberg
   - Key Concept: Binary search with partitioning

2. **Split Array Largest Sum** (Leetcode 410)
   - Companies: Amazon, Google, Facebook, Microsoft
   - Key Concept: Binary search on answer with greedy validation

3. **Find Minimum in Rotated Sorted Array II** (Leetcode 154)
   - Companies: Amazon, Microsoft, Google
   - Key Concept: Rotated array with duplicates

4. **Smallest Rectangle Enclosing Black Pixels** (Leetcode 302)
   - Companies: Google, Facebook
   - Key Concept: Binary search in 2D grid

5. **Count of Smaller Numbers After Self** (Leetcode 315)
   - Companies: Amazon, Google, Microsoft, Facebook
   - Key Concept: Binary search with insertion

## Key Patterns to Master

### 1. Classical Binary Search
- Find exact target
- Find boundaries (first/last occurrence)

### 2. Rotated Array Search
- Finding pivot/minimum
- Search with rotation

### 3. Binary Search on Answer
- When answer lies in a range [min, max]
- Use binary search to find optimal answer
- Validate each mid value with a helper function

### 4. 2D Binary Search
- Row-wise and column-wise sorted
- Treating as 1D array

## Common Templates

### Template 1: Exact Match
```java
while (left <= right) {
    int mid = left + (right - left) / 2;
    if (arr[mid] == target) return mid;
    else if (arr[mid] < target) left = mid + 1;
    else right = mid - 1;
}
```

### Template 2: Find Boundary
```java
while (left < right) {
    int mid = left + (right - left) / 2;
    if (condition) right = mid;
    else left = mid + 1;
}
```

### Template 3: Binary Search on Answer
```java
while (left < right) {
    int mid = left + (right - left) / 2;
    if (isValid(mid)) right = mid;
    else left = mid + 1;
}
```

## Study Tips
1. Master the three templates above
2. Practice identifying when to use binary search
3. Be careful with boundary conditions (left <= right vs left < right)
4. Avoid integer overflow: use `left + (right - left) / 2`
5. For "binary search on answer", identify the search space and validation function

## Time Complexity
- Standard Binary Search: O(log n)
- With validation function: O(n log m) where m is search space range
