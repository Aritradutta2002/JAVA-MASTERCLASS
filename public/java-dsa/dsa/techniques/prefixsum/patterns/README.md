# Prefix Sum Interview Patterns - Master Guide

## 🎯 Essential Patterns for Technical Interviews

This section contains the most important patterns and templates that frequently appear in coding interviews at top tech companies like Google, Amazon, Microsoft, Meta, and Apple.

## 📋 Pattern Categories

### 1. Basic Range Query Pattern
**When to use**: Multiple range sum queries on static array
**Template**: Build prefix sum array, answer queries in O(1)
**Key Problems**: LeetCode 303, 304

```java
// Template
class RangeSum {
    int[] prefixSum;
    
    public RangeSum(int[] nums) {
        prefixSum = new int[nums.length + 1];
        for (int i = 0; i < nums.length; i++) {
            prefixSum[i + 1] = prefixSum[i] + nums[i];
        }
    }
    
    public int query(int left, int right) {
        return prefixSum[right + 1] - prefixSum[left];
    }
}
```

### 2. HashMap + Prefix Sum Pattern
**When to use**: Count subarrays with specific sum/property
**Key Insight**: If `prefixSum[j] - prefixSum[i] = target`, then subarray from i+1 to j has sum = target
**Key Problems**: LeetCode 560, 930, 974

```java
// Template
public int countSubarrays(int[] nums, int target) {
    Map<Integer, Integer> prefixSumCount = new HashMap<>();
    prefixSumCount.put(0, 1); // Empty prefix
    
    int prefixSum = 0, count = 0;
    for (int num : nums) {
        prefixSum += num;
        count += prefixSumCount.getOrDefault(prefixSum - target, 0);
        prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
    }
    return count;
}
```

### 3. Modular Arithmetic Pattern
**When to use**: Subarrays divisible by k, or with specific remainder
**Key Insight**: If `prefixSum[i] % k == prefixSum[j] % k`, then subarray sum is divisible by k
**Key Problems**: LeetCode 523, 974

```java
// Template
public int countDivisibleSubarrays(int[] nums, int k) {
    Map<Integer, Integer> remainderCount = new HashMap<>();
    remainderCount.put(0, 1);
    
    int prefixSum = 0, count = 0;
    for (int num : nums) {
        prefixSum += num;
        int remainder = ((prefixSum % k) + k) % k; // Handle negatives
        count += remainderCount.getOrDefault(remainder, 0);
        remainderCount.put(remainder, remainderCount.getOrDefault(remainder, 0) + 1);
    }
    return count;
}
```

### 4. 2D Prefix Sum Pattern
**When to use**: Rectangle sum queries in matrix
**Key Formula**: Include-exclude principle
**Key Problems**: LeetCode 304, 1314

```java
// Template
class Matrix2DSum {
    int[][] prefixSum;
    
    public Matrix2DSum(int[][] matrix) {
        int rows = matrix.length, cols = matrix[0].length;
        prefixSum = new int[rows + 1][cols + 1];
        
        for (int i = 1; i <= rows; i++) {
            for (int j = 1; j <= cols; j++) {
                prefixSum[i][j] = matrix[i-1][j-1] + prefixSum[i-1][j] 
                                + prefixSum[i][j-1] - prefixSum[i-1][j-1];
            }
        }
    }
    
    public int sumRegion(int row1, int col1, int row2, int col2) {
        row1++; col1++; row2++; col2++; // Convert to 1-indexed
        return prefixSum[row2][col2] - prefixSum[row1-1][col2] 
             - prefixSum[row2][col1-1] + prefixSum[row1-1][col1-1];
    }
}
```

### 5. Sliding Window + Prefix Sum Pattern
**When to use**: Fixed/variable window with sum constraints
**Key Problems**: LeetCode 209, 862, 992

```java
// Template for "at most K distinct"
public int atMostK(int[] nums, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    int left = 0, result = 0;
    
    for (int right = 0; right < nums.length; right++) {
        count.put(nums[right], count.getOrDefault(nums[right], 0) + 1);
        
        while (count.size() > k) {
            count.put(nums[left], count.get(nums[left]) - 1);
            if (count.get(nums[left]) == 0) count.remove(nums[left]);
            left++;
        }
        
        result += right - left + 1;
    }
    return result;
}
```

### 6. Difference Array Pattern
**When to use**: Multiple range updates, then final query
**Key Problems**: LeetCode 370, 1109, 1854

```java
// Template
class DifferenceArray {
    int[] diff;
    
    public DifferenceArray(int[] nums) {
        diff = new int[nums.length + 1];
        diff[0] = nums[0];
        for (int i = 1; i < nums.length; i++) {
            diff[i] = nums[i] - nums[i-1];
        }
    }
    
    public void rangeUpdate(int left, int right, int val) {
        diff[left] += val;
        if (right + 1 < diff.length - 1) diff[right + 1] -= val;
    }
    
    public int[] getFinalArray() {
        int[] result = new int[diff.length - 1];
        result[0] = diff[0];
        for (int i = 1; i < result.length; i++) {
            result[i] = result[i-1] + diff[i];
        }
        return result;
    }
}
```

## 🔥 Interview Frequency Ranking

### Tier 1 (Most Frequent - Must Know)
1. **LeetCode 560** - Subarray Sum Equals K (HashMap pattern)
2. **LeetCode 303** - Range Sum Query (Basic pattern)
3. **LeetCode 238** - Product of Array Except Self (Prefix/Suffix)
4. **LeetCode 724** - Find Pivot Index (Balance point)

### Tier 2 (Common - Should Know)
1. **LeetCode 304** - Range Sum Query 2D (2D extension)
2. **LeetCode 523** - Continuous Subarray Sum (Modular arithmetic)
3. **LeetCode 974** - Subarray Sums Divisible by K (Advanced modular)
4. **LeetCode 325** - Maximum Size Subarray Sum Equals k (Length optimization)

### Tier 3 (Advanced - Nice to Know)
1. **LeetCode 862** - Shortest Subarray with Sum at Least K (Monotonic deque)
2. **LeetCode 992** - Subarrays with K Different Integers (Sliding window)
3. **LeetCode 1588** - Sum of All Odd Length Subarrays (Mathematical insight)
4. **Custom** - Maximum Sum Rectangle (2D Kadane's)

## 💡 Common Interview Follow-ups

### Performance Optimizations
- **Q**: What if we have frequent updates to the array?
- **A**: Use Segment Tree or Binary Indexed Tree for O(log n) updates

### Space Optimizations
- **Q**: Can we reduce space complexity?
- **A**: Use in-place modifications or difference arrays for updates

### Constraint Variations
- **Q**: What about negative numbers?
- **A**: Handle modular arithmetic carefully, use `((x % k) + k) % k`

### Extension Problems
- **Q**: What about 3D arrays?
- **A**: Extend inclusion-exclusion principle to higher dimensions

## 🎪 Interview Simulation

### Typical Interview Flow
1. **Clarification** (2-3 min): Understand constraints, edge cases
2. **Approach Discussion** (5-7 min): Explain brute force, then optimize
3. **Implementation** (15-20 min): Code the optimal solution
4. **Testing** (5-8 min): Walk through test cases
5. **Follow-up** (5-10 min): Discuss variations and optimizations

### Sample Questions to Ask
- "Are there any constraints on array size or element values?"
- "Should I handle integer overflow?"
- "Are there multiple queries or just one?"
- "Can the array be modified between queries?"

## 🚨 Common Pitfalls & Edge Cases

### Implementation Mistakes
- **Off-by-one errors** in prefix sum indexing
- **Integer overflow** with large sums (use `long`)
- **Negative modulus** results (add k before final mod)
- **Empty prefix** not handled in HashMap patterns

### Edge Cases to Test
```java
// Always test these cases
int[] empty = {};
int[] single = {5};
int[] allNegative = {-1, -2, -3};
int[] withZeros = {1, 0, -1, 0, 1};
int[] large = new int[100000]; // Performance test
```

### Debugging Checklist
- [ ] Check array bounds and indexing
- [ ] Verify HashMap initialization with base case
- [ ] Handle negative numbers in modular arithmetic
- [ ] Test with single element arrays
- [ ] Validate with provided examples

## 🏆 Master Template

Use this comprehensive template that handles most prefix sum interview questions:

```java
public class PrefixSumMaster {
    // Basic range sum
    public int rangeSum(int[] nums, int left, int right) {
        int[] prefix = buildPrefix(nums);
        return prefix[right + 1] - prefix[left];
    }
    
    // Subarray counting with target sum
    public int countSubarrays(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(0, 1);
        int prefix = 0, count = 0;
        
        for (int num : nums) {
            prefix += num;
            count += map.getOrDefault(prefix - target, 0);
            map.put(prefix, map.getOrDefault(prefix, 0) + 1);
        }
        return count;
    }
    
    // Helper method
    private int[] buildPrefix(int[] nums) {
        int[] prefix = new int[nums.length + 1];
        for (int i = 0; i < nums.length; i++) {
            prefix[i + 1] = prefix[i] + nums[i];
        }
        return prefix;
    }
}
```

---
**Remember**: Focus on pattern recognition over memorization. Understand the underlying mathematical principles, and you'll be able to adapt to any variation!

**Pro Tip**: Always start with the brute force approach, explain its limitations, then optimize using prefix sum patterns. This shows clear problem-solving progression to interviewers.