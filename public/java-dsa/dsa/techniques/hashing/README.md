# HASHING - MAANG/FAANG Interview Problems

## 📚 Overview

Hashing is one of the most commonly tested topics in technical interviews. This folder contains **15+ essential hashing problems** covering all major patterns used at Google, Amazon, Facebook, Microsoft, and other top companies.

---

## 🎯 Problem List

### Easy Problems (7)

| #   | Problem                                                        | LeetCode | Companies                           | Pattern                 |
| --- | -------------------------------------------------------------- | -------- | ----------------------------------- | ----------------------- |
| 1   | [Two Sum](TwoSum_LC1.java)                                     | #1       | Google, Amazon, Facebook, Microsoft | HashMap Lookup          |
| 2   | [Contains Duplicate](ContainsDuplicate_LC217.java)             | #217     | Amazon, Google, Microsoft           | HashSet                 |
| 3   | [Valid Anagram](ValidAnagram_LC242.java)                       | #242     | Amazon, Facebook, Google            | Frequency Count         |
| 4   | [First Unique Character](FirstUniqueCharacter_LC387.java)      | #387     | Amazon, Google, Microsoft           | HashMap                 |
| 5   | [Intersection of Two Arrays](IntersectionTwoArrays_LC349.java) | #349     | Amazon, Google                      | HashSet                 |
| 6   | [Isomorphic Strings](IsomorphicStrings_LC205.java)             | #205     | Amazon, Google                      | Bidirectional Map       |
| 7   | [Happy Number](HappyNumber_LC202.java)                         | #202     | Amazon, Google                      | HashSet Cycle Detection |

### Medium Problems (6)

| #   | Problem                                                               | LeetCode | Companies                      | Pattern                     |
| --- | --------------------------------------------------------------------- | -------- | ------------------------------ | --------------------------- |
| 8   | [Group Anagrams](GroupAnagrams_LC49.java)                             | #49      | Amazon, Facebook, Google, Uber | HashMap with Key Generation |
| 9   | [Top K Frequent Elements](TopKFrequentElements_LC347.java)            | #347     | Amazon, Facebook, Google       | HashMap + Heap/Bucket Sort  |
| 10  | [Longest Consecutive Sequence](LongestConsecutiveSequence_LC128.java) | #128     | Google, Amazon, Facebook       | HashSet O(n)                |
| 11  | [Valid Sudoku](ValidSudoku_LC36.java)                                 | #36      | Amazon, Google, Apple          | Multiple HashSets           |
| 12  | [4Sum II](FourSumII_LC454.java)                                       | #454     | Amazon, Facebook               | HashMap Optimization        |
| 13  | [Subarray Sum Equals K](SubarraySumEqualsK_LC560.java)                | #560     | Facebook, Amazon, Google       | Prefix Sum + HashMap        |
| 14  | Highest Occurring Element                                             | Classic  | -                              | Frequency Count             |

---

## 🔑 Key Patterns

### 1. **HashMap for O(1) Lookup**

**Use when:** Need to find pairs, complements, or check existence

```java
// Two Sum pattern
Map<Integer, Integer> map = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int complement = target - nums[i];
    if (map.containsKey(complement)) {
        return new int[] {map.get(complement), i};
    }
    map.put(nums[i], i);
}
```

**Problems:** Two Sum, 4Sum II

### 2. **HashSet for Uniqueness**

**Use when:** Need to track unique elements or detect duplicates

```java
Set<Integer> seen = new HashSet<>();
for (int num : nums) {
    if (!seen.add(num)) {
        return true; // Found duplicate
    }
}
```

**Problems:** Contains Duplicate, Longest Consecutive Sequence

### 3. **Frequency Counting**

**Use when:** Need to count occurrences

```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.put(c, freq.getOrDefault(c, 0) + 1);
}
```

**Problems:** Valid Anagram, Group Anagrams, Top K Frequent

### 4. **Prefix Sum + HashMap**

**Use when:** Solving subarray sum problems

```java
Map<Integer, Integer> prefixSumCount = new HashMap<>();
prefixSumCount.put(0, 1);
int count = 0, prefixSum = 0;
for (int num : nums) {
    prefixSum += num;
    count += prefixSumCount.getOrDefault(prefixSum - k, 0);
    prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
}
```

**Problems:** Subarray Sum Equals K, Subarray Sum Divisible by K

### 5. **Bidirectional Mapping**

**Use when:** Need one-to-one character mapping

```java
Map<Character, Character> mapST = new HashMap<>();
Map<Character, Character> mapTS = new HashMap<>();
// Check both directions for bijection
```

**Problems:** Isomorphic Strings, Word Pattern

### 6. **Cycle Detection with HashSet**

**Use when:** Detecting loops or cycles

```java
Set<Integer> seen = new HashSet<>();
while (n != target && !seen.contains(n)) {
    seen.add(n);
    n = getNext(n);
}
```

**Problems:** Happy Number, Linked List Cycle

---

## 💡 Common Tricks & Tips

### HashMap vs HashSet

- Use **HashMap** when you need to store key-value pairs (frequency, indices)
- Use **HashSet** when you only need to check existence

### Time Complexity

- HashMap/HashSet operations: **O(1) average**, O(n) worst case
- Always better than sorting: O(n log n)

### Space Optimization

- Frequency array (26 letters): `int[] freq = new int[26]` is O(1) space
- HashMap/HashSet: O(n) space

### When to Use Hashing

✅ Finding pairs with given sum
✅ Checking for duplicates
✅ Counting frequencies
✅ Grouping anagrams
✅ Subarray sum problems
✅ Detecting cycles

---

## 🏢 Company-Specific Focus

### **Google** ⭐⭐⭐

- Longest Consecutive Sequence
- Group Anagrams
- Top K Frequent Elements

### **Amazon** ⭐⭐⭐

- Two Sum (most asked!)
- Group Anagrams
- Valid Sudoku

### **Facebook/Meta** ⭐⭐⭐

- Subarray Sum Equals K
- Top K Frequent Elements
- Group Anagrams

### **Microsoft** ⭐⭐

- Contains Duplicate
- Valid Anagram
- First Unique Character

---

## 📖 Learning Path

### Beginner (Days 1-3)

1. Two Sum
2. Contains Duplicate
3. Valid Anagram
4. Intersection of Two Arrays

### Intermediate (Days 4-6)

5. Group Anagrams
6. First Unique Character
7. Isomorphic Strings
8. Happy Number

### Advanced (Days 7-10)

9. Longest Consecutive Sequence
10. Top K Frequent Elements
11. Valid Sudoku
12. 4Sum II
13. Subarray Sum Equals K

---

## 🎓 Interview Tips

1. **Always ask about constraints**

   - Input size (affects space complexity decision)
   - Character set (lowercase only? ASCII? Unicode?)

2. **Optimize space when possible**

   - Use frequency array for lowercase letters instead of HashMap
   - Use int[] instead of HashMap for limited range

3. **Handle edge cases**

   - Empty arrays/strings
   - Single element
   - All duplicates
   - Negative numbers (for sum problems)

4. **Communication is key**
   - Explain your approach before coding
   - Discuss time/space complexity trade-offs
   - Mention alternative solutions

---

## 🔄 Related Topics

- **Arrays** - Many hashing problems involve arrays
- **Strings** - Anagram and pattern matching problems
- **Two Pointers** - Alternative approach for some problems
- **Sliding Window** - Can be combined with hashing

---

## ⏱️ Time Complexity Cheat Sheet

| Problem             | Brute Force        | Optimized (Hashing)  |
| ------------------- | ------------------ | -------------------- |
| Two Sum             | O(n²)              | O(n)                 |
| Contains Duplicate  | O(n log n) sorting | O(n)                 |
| Group Anagrams      | O(n²k log k)       | O(nk) or O(nk log k) |
| Longest Consecutive | O(n log n) sorting | O(n)                 |
| Subarray Sum = K    | O(n²)              | O(n)                 |

---

## 📌 Must-Know for FAANG

**Top 5 Most Asked:**

1. ⭐⭐⭐ Two Sum (LC #1)
2. ⭐⭐⭐ Group Anagrams (LC #49)
3. ⭐⭐⭐ Longest Consecutive Sequence (LC #128)
4. ⭐⭐⭐ Top K Frequent Elements (LC #347)
5. ⭐⭐⭐ Subarray Sum Equals K (LC #560)

**Practice Order:**
Easy (1-2 days) → Medium (3-5 days) → Review & Mock Interviews

---

_Last Updated: November 2024_
_Problems: 14 | Easy: 7 | Medium: 7 | Hard: 0_
