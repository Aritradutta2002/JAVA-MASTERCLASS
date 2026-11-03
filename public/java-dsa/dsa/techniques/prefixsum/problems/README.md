# Prefix Sum Problems - Organized by Difficulty

## 📊 Problem Distribution

### Easy Problems (Foundation Building)
- **RangeSumQuery_Leetcode303**: Basic range sum queries
- **RunningSum_Leetcode1480**: Simple prefix sum construction
- **PivotIndex_Leetcode724**: Finding equilibrium point
- **SubarraySum_Leetcode560**: Fundamental HashMap pattern

### Medium Problems (Core Patterns)
- **RangeSumQuery2D_Leetcode304**: 2D prefix sum implementation
- **ContinuousSubarraySum_Leetcode523**: Modular arithmetic with prefix sum
- **ProductExceptSelf_Leetcode238**: Prefix/suffix product pattern
- **SubarraysDivByK_Leetcode974**: Advanced modular arithmetic

### Hard Problems (Advanced Techniques)
- **SubarrayWithKDistinct_Leetcode992**: Sliding window + counting
- **MaxSumRectangle_Classic**: 2D Kadane's algorithm extension
- **ShortestSubarray_Leetcode862**: Monotonic deque optimization
- **SubarrayXOR_Advanced**: Prefix XOR with counting

## 🎯 Learning Progression

### Phase 1: Fundamentals (Easy)
1. Start with `RunningSum_Leetcode1480` to understand basic concept
2. Practice `RangeSumQuery_Leetcode303` for query optimization
3. Solve `PivotIndex_Leetcode724` for left/right sum balance
4. Master `SubarraySum_Leetcode560` for HashMap pattern

### Phase 2: Pattern Recognition (Medium)
1. Learn 2D extension with `RangeSumQuery2D_Leetcode304`
2. Apply modular arithmetic in `ContinuousSubarraySum_Leetcode523`
3. Understand prefix/suffix in `ProductExceptSelf_Leetcode238`
4. Practice advanced counting in `SubarraysDivByK_Leetcode974`

### Phase 3: Advanced Optimization (Hard)
1. Combine techniques in `SubarrayWithKDistinct_Leetcode992`
2. Extend to 2D problems with `MaxSumRectangle_Classic`
3. Learn monotonic optimization in `ShortestSubarray_Leetcode862`
4. Master XOR variations in `SubarrayXOR_Advanced`

## 🔍 Key Patterns by Problem Type

### Range Query Problems
- **Template**: Build prefix sum, answer in O(1)
- **Examples**: Leetcode 303, 304
- **Variations**: 1D/2D, mutable/immutable

### Subarray Counting Problems
- **Template**: HashMap with prefix sum differences
- **Examples**: Leetcode 560, 523, 974
- **Key Insight**: `prefixSum[j] - prefixSum[i] = target`

### Optimization Problems
- **Template**: Sliding window + prefix sum
- **Examples**: Leetcode 992, 862
- **Techniques**: Monotonic deque, two-pointer

### 2D Extensions
- **Template**: Matrix prefix sum
- **Examples**: Leetcode 304, MaxSumRectangle
- **Formula**: Include-exclude principle

## 📈 Complexity Analysis

| Problem Category | Time Complexity | Space Complexity | Difficulty |
|-----------------|----------------|------------------|------------|
| Basic Range Query | O(n) + O(1) per query | O(n) | Easy |
| Subarray Counting | O(n) | O(n) | Medium |
| 2D Range Query | O(mn) + O(1) per query | O(mn) | Medium |
| Advanced Optimization | O(n) to O(n²) | O(n) to O(mn) | Hard |

## 🚀 Interview Tips

### Most Frequently Asked
1. **Leetcode 560** - Subarray Sum Equals K
2. **Leetcode 303** - Range Sum Query
3. **Leetcode 724** - Find Pivot Index
4. **Leetcode 304** - Range Sum Query 2D

### Common Follow-ups
- What if array is mutable? → Use Segment Tree or Binary Indexed Tree
- What about negative numbers? → Handle modular arithmetic carefully
- Memory optimization? → Use difference array for updates
- 2D extensions? → Apply inclusion-exclusion principle

### Edge Cases to Consider
- Empty arrays or single elements
- All negative/positive numbers
- Integer overflow (use `long`)
- Zero values and their impact
- Negative remainders in modular arithmetic

## 🎪 Practice Schedule

### Week 1: Foundation
- Day 1-2: Easy problems
- Day 3-4: Basic HashMap pattern
- Day 5-7: Review and practice

### Week 2: Patterns
- Day 1-3: Medium problems
- Day 4-5: 2D extensions
- Day 6-7: Pattern recognition

### Week 3: Mastery
- Day 1-4: Hard problems
- Day 5-6: Optimization techniques
- Day 7: Mock interviews

---
*Focus on understanding the underlying patterns rather than memorizing solutions!*