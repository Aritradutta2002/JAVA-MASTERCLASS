# Strings - FAANG Interview Problems

## Folder Structure
```
STRINGS/
├── palindrome/          # Palindrome-related problems
├── substring/           # Substring search and matching
├── manipulation/        # String manipulation and transformation
├── anagram/            # Anagram and character frequency
├── parentheses/        # Parentheses and bracket problems
└── advanced/           # Advanced string algorithms
```

## Problem Categories

### 1. Palindrome Problems
- **LongestPalindromicSubstring.java** - LeetCode 5 (MEDIUM) ⭐⭐⭐
- **PalindromicSubstrings.java** - LeetCode 647 (MEDIUM) ⭐⭐
- **ValidPalindrome.java** - LeetCode 125, 680 (EASY/MEDIUM) ⭐⭐

### 2. Substring Problems
- **LongestSubstringWithoutRepeating.java** - LeetCode 3 (MEDIUM) ⭐⭐⭐
- **MinimumWindowSubstring.java** - LeetCode 76 (HARD) ⭐⭐⭐
- **SubstringWithConcatenation.java** - LeetCode 30 (HARD) ⭐⭐

### 3. String Manipulation
- **ReverseWords.java** - LeetCode 151, 186 (MEDIUM) ⭐⭐
- **StringToInteger.java** - LeetCode 8 (MEDIUM) ⭐⭐⭐
- **IntegerToRoman.java** - LeetCode 12, 13 (MEDIUM) ⭐⭐

### 4. Anagram & Frequency
- **GroupAnagrams.java** - LeetCode 49 (MEDIUM) ⭐⭐⭐
- **FindAllAnagrams.java** - LeetCode 438 (MEDIUM) ⭐⭐

### 5. Parentheses Problems
- **ValidParentheses.java** - LeetCode 20 (EASY) ⭐⭐⭐
- **GenerateParentheses.java** - LeetCode 22 (MEDIUM) ⭐⭐⭐
- **LongestValidParentheses.java** - LeetCode 32 (HARD) ⭐⭐⭐

### 6. Advanced Algorithms
- **KMP.java** - Pattern matching O(n+m)
- **RabinKarp.java** - Rolling hash pattern matching
- **ZAlgorithm.java** - Linear time pattern matching

## FAANG Interview Frequency
- **Amazon**: Very High (All categories)
- **Google**: Very High (Palindrome, Substring, Advanced)
- **Facebook/Meta**: Very High (Manipulation, Anagram)
- **Microsoft**: High (Parentheses, Manipulation)
- **Apple**: Medium-High

## Key Patterns

### Two Pointers
```java
int left = 0, right = s.length() - 1;
while (left < right) {
    // Process
    left++; right--;
}
```

### Sliding Window
```java
Map<Character, Integer> map = new HashMap<>();
int left = 0;
for (int right = 0; right < s.length(); right++) {
    // Expand window
    while (invalid()) {
        // Shrink window
        left++;
    }
}
```

### Character Frequency
```java
int[] freq = new int[26]; // or 128 for ASCII
for (char c : s.toCharArray()) {
    freq[c - 'a']++;
}
```

## Common Tricks
1. **Character array manipulation**: Convert to char[] for in-place operations
2. **StringBuilder**: Use for string concatenation in loops
3. **ASCII tricks**: Use array[128] instead of HashMap for ASCII
4. **Two pointers**: For palindrome and reversal problems
5. **Sliding window**: For substring problems

## Time Complexity
- Most problems: O(n) or O(n²)
- Pattern matching: O(n+m) with KMP/Z-algorithm
- Palindrome expansion: O(n²)

## Practice Strategy
1. Master two pointers technique
2. Learn sliding window for substring problems
3. Practice palindrome problems (expand around center)
4. Solve parentheses problems (stack-based)
5. Study advanced algorithms (KMP, Rabin-Karp)
