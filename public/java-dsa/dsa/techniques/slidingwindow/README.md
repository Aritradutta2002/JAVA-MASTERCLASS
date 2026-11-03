# Sliding Window - FAANG Interview Problems

## Pattern Overview
Sliding Window is used for problems involving contiguous subarrays/substrings. Two main types:
1. **Fixed-size window**: Window size is constant
2. **Variable-size window**: Window expands/shrinks based on conditions

## Problem Categories

### 1. Variable Window - Shrinkable (Most Common)
- **MinimumWindowSubstring.java** - LeetCode 76 (HARD) ⭐⭐⭐
- **LongestSubstringKDistinct.java** - LeetCode 340, 159 (MEDIUM) ⭐⭐
- **LongestRepeatingCharacterReplacement.java** - LeetCode 424 (MEDIUM) ⭐⭐⭐
- **MaxConsecutiveOnes.java** - LeetCode 1004, 487, 485 (MEDIUM)
- **MinimumSizeSubarraySum.java** - LeetCode 209 (MEDIUM) ⭐⭐

### 2. Fixed Window
- **PermutationInString.java** - LeetCode 567, 438 (MEDIUM) ⭐⭐

### 3. Advanced Patterns
- **SubarraysWithKDifferent.java** - LeetCode 992 (HARD) ⭐⭐⭐

## Key Patterns

### Shrinkable Window Template
```java
int left = 0, result = 0;
for (int right = 0; right < n; right++) {
    // Expand window
    add(arr[right]);
    
    // Shrink window while invalid
    while (invalid()) {
        remove(arr[left]);
        left++;
    }
    
    // Update result
    result = Math.max(result, right - left + 1);
}
```

### Fixed Window Template
```java
for (int right = 0; right < n; right++) {
    add(arr[right]);
    
    if (right >= k - 1) {
        // Process window
        result = process();
        
        // Slide window
        remove(arr[right - k + 1]);
    }
}
```

## FAANG Interview Frequency
- **Amazon**: Very High (All problems, especially Minimum Window)
- **Google**: Very High (K Distinct, Character Replacement)
- **Facebook/Meta**: High (Minimum Window, Permutation)
- **Microsoft**: High (Permutation, Consecutive Ones)
- **Apple**: Medium-High

## Common Tricks
1. **AtMost K trick**: exactlyK = atMostK - atMost(K-1)
2. **Character frequency**: Use array[26] for lowercase letters
3. **Two pointers**: left and right for window boundaries
4. **HashMap vs Array**: Array faster for limited character set

## Time Complexity
Most problems: **O(n)** where n is array/string length
- Each element visited at most twice (once by right, once by left)

## Practice Strategy
1. Start with fixed window (easier)
2. Master shrinkable window template
3. Practice with HashMap/frequency array
4. Learn the "atMost K" trick
5. Solve Minimum Window Substring (hardest but most important)

## Related Topics
- Two Pointers
- HashMap/HashSet
- String manipulation
- Array techniques
