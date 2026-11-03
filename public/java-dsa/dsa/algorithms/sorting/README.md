# SORTING - FAANG Interview Ready

## 📂 Structure
```
SORTING/
├── theory/          # Basic sorting algorithms
└── problems/        # Interview problems
```

## 🎯 Critical FAANG Problems

### ⭐ **Must-Do (Top Priority)**

| Problem | LeetCode | Difficulty | Companies | Pattern |
|---------|----------|------------|-----------|---------|
| **Merge Intervals** | #56 | Medium | FB, AMZN, GOOG, MSFT | Sort + Merge |
| **Sort Colors** | #75 | Medium | FB, MSFT, AMZN | 3-Pointers |
| **Meeting Rooms II** | #253 | Medium | AMZN, FB, GOOG | Sort + Sweep |
| **Insert Interval** | #57 | Medium | FB, GOOG, LNKD | Sort + Merge |
| **K Closest Points** | #973 | Medium | AMZN, FB, GOOG | Quick Select |

### 🔥 **High Frequency**

| Problem | LeetCode | Difficulty | Pattern |
|---------|----------|------------|---------|
| Largest Number | #179 | Medium | Custom Comparator |
| Top K Frequent Elements | #347 | Medium | Counting + Sort |
| Sort List | #148 | Medium | Merge Sort on LinkedList |
| Kth Largest Element | #215 | Medium | Quick Select |
| Non-overlapping Intervals | #435 | Medium | Sort + Greedy |

### 💪 **CP Level (Advanced)**

| Problem | LeetCode | Difficulty | Technique |
|---------|----------|------------|-----------|
| Count of Smaller After Self | #315 | Hard | Merge Sort + BIT |
| Maximum Gap | #164 | Hard | Bucket Sort |
| Wiggle Sort II | #324 | Medium | 3-way partition |
| Pancake Sorting | #969 | Medium | Simulation |
| Minimize Deviation | #1675 | Hard | Priority Queue |

---

## 📚 Patterns & Techniques

### 1. **INTERVALS Pattern**
**When to use:** Problems with start/end times, ranges

**Template:**
```java
// Sort intervals
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

int[] current = intervals[0];
for (int i = 1; i < intervals.length; i++) {
    if (intervals[i][0] <= current[1]) {
        // Overlapping - merge
        current[1] = Math.max(current[1], intervals[i][1]);
    } else {
        // Non-overlapping - add current
        result.add(current);
        current = intervals[i];
    }
}
```

**Problems:**
- Merge Intervals (#56) ⭐⭐⭐
- Insert Interval (#57) ⭐⭐⭐
- Meeting Rooms II (#253) ⭐⭐⭐
- Non-overlapping Intervals (#435)

---

### 2. **CUSTOM COMPARATOR Pattern**
**When to use:** Non-standard sorting logic

**Template:**
```java
Arrays.sort(arr, (a, b) -> {
    // Custom logic
    return compareLogic(a, b);
});
```

**Problems:**
- Largest Number (#179) - String comparison
- K Closest Points (#973) - Distance comparison
- Sort Characters by Frequency (#451)
- Custom Sort String (#791)

---

### 3. **QUICK SELECT Pattern**  
**When to use:** Find Kth element without full sort

**Template:**
```java
public int quickSelect(int[] nums, int k) {
    return quickSelect(nums, 0, nums.length - 1, k);
}

private int quickSelect(int[] nums, int left, int right, int k) {
    int pivot = partition(nums, left, right);
    
    if (pivot == k) return nums[pivot];
    else if (pivot < k) return quickSelect(nums, pivot + 1, right, k);
    else return quickSelect(nums, left, pivot - 1, k);
}
```

**Time:** O(n) average, O(n²) worst  
**Problems:**
- Kth Largest Element (#215) ⭐⭐⭐
- Top K Frequent Elements (#347) ⭐⭐⭐
- Kth Smallest in Sorted Matrix (#378)

---

### 4. **COUNTING SORT Pattern**
**When to use:** Small range of integers

**Time:** O(n + k) where k = range  
**Space:** O(k)

**Problems:**
- Sort Colors (#75) ⭐⭐⭐
- Sort Array By Parity (#905)
- Relative Sort Array (#1122)

---

### 5. **BUCKET SORT Pattern**
**When to use:** Uniform distribution

**Problems:**
- Top K Frequent Elements (#347)
- Maximum Gap (#164)
- Contains Duplicate III (#220)

---

## 🎓 Study Plan

### Week 1: Master Intervals
- [ ] Merge Intervals (#56)
- [ ] Insert Interval (#57)
- [ ] Meeting Rooms I & II (#252, #253)
- [ ] Non-overlapping Intervals (#435)

### Week 2: Partitioning & Selection
- [ ] Sort Colors (#75)
- [ ] Kth Largest Element (#215)
- [ ] Top K Frequent (#347)
- [ ] Wiggle Sort (#324)

### Week 3: Advanced
- [ ] Sort List (#148)
- [ ] Largest Number (#179)
- [ ] Count of Smaller After Self (#315)
- [ ] Maximum Gap (#164)

---

## 💡 Interview Tips

### 1. **Sort vs Don't Sort**
✅ Sort when:
- Need to find duplicates/overlaps
- Greedy approach works
- O(n log n) is acceptable

❌ Don't sort when:
- Need relative order
- O(n) solution exists
- Streaming data

### 2. **Choosing Sort Algorithm**

| Algorithm | Time | Space | Stable | When to Use |
|-----------|------|-------|--------|-------------|
| **Arrays.sort()** | O(n log n) | O(log n) | No | Default choice |
| **Quick Select** | O(n) avg | O(1) | No | Kth element |
| **Counting Sort** | O(n + k) | O(k) | Yes | Small range |
| **Bucket Sort** | O(n) | O(n) | Yes | Uniform dist |
| **Merge Sort** | O(n log n) | O(n) | Yes | LinkedList |

### 3. **Common Mistakes**
❌ Forgetting to sort first  
❌ Not handling edge cases (empty, single element)  
❌ Wrong comparator logic  
❌ Integer overflow in comparisons  
❌ Modifying input when shouldn't  

### 4. **Optimization Tricks**
- Use quick select for Kth problems (O(n) vs O(n log n))
- Use counting sort for small range
- Custom comparator for special cases
- Avoid sorting if possible (use heap, map, etc.)

---

## 🔥 Company-Specific Focus

### **Amazon** (Loves Intervals)
1. Merge Intervals (#56)
2. Meeting Rooms II (#253)
3. Minimum Arrows (#452)

### **Facebook** (Loves Comparators)
1. Sort Colors (#75)
2. Largest Number (#179)
3. K Closest Points (#973)

### **Google** (Loves Advanced)
1. Count of Smaller After Self (#315)
2. Maximum Gap (#164)
3. Wiggle Sort II (#324)

### **Microsoft** (Loves Practical)
1. Meeting Rooms II (#253)
2. Sort List (#148)
3. Top K Frequent (#347)

---

## 📝 Quick Reference

### Interval Problems Checklist
```java
// 1. Sort by start time
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

// 2. Track current interval
int[] current = intervals[0];

// 3. Merge logic
if (overlap) merge();
else add_to_result();
```

### Kth Element Checklist
```java
// Option 1: Min/Max Heap - O(n log k)
PriorityQueue<Integer> heap = new PriorityQueue<>();

// Option 2: Quick Select - O(n) average
return quickSelect(nums, k);

// Option 3: Sort - O(n log n)
Arrays.sort(nums);
return nums[k];
```

---

## 🚀 Next Steps

1. **Start with:** Merge Intervals, Sort Colors
2. **Master:** Quick Select pattern
3. **Practice:** 2-3 problems per pattern
4. **Review:** Common mistakes section
5. **Time yourself:** Aim for 25-30 mins per problem

**Total Must-Do Problems:** 15  
**Recommended Time:** 2-3 weeks

---

Good luck! 🎯
