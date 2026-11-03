# 🔍 SEARCHING — The Ultimate Guide (Basics → Advanced CP)

> **"Binary Search isn't just for sorted arrays — it's for ANY monotonic decision space."**

---

## 📂 Table of Contents
1. [Searching Algorithm Zoo](#1-searching-algorithm-zoo)
2. [Linear Search](#2-linear-search)
3. [Binary Search — All Templates](#3-binary-search--all-templates)
4. [Binary Search Patterns](#4-binary-search-patterns)
5. [Binary Search on Answer Space](#5-binary-search-on-answer-space)
6. [Advanced & CP-Level Patterns](#6-advanced--cp-level-patterns)
7. [Ternary Search](#7-ternary-search)
8. [Exponential & Interpolation Search](#8-exponential--interpolation-search)
9. [Bitwise / Bit-parallel Search](#9-bitwise--bit-parallel-search)
10. [Problem Bank by Pattern](#10-problem-bank-by-pattern)
11. [Company-Wise Must-Dos](#11-company-wise-must-dos)
12. [Study Plan](#12-study-plan)
13. [Cheat Sheet & Common Mistakes](#13-cheat-sheet--common-mistakes)

---

## 1. Searching Algorithm Zoo

| Algorithm | Time (avg) | Time (worst) | Space | Prerequisite |
|---|---|---|---|---|
| **Linear Search** | O(n) | O(n) | O(1) | None |
| **Binary Search** | O(log n) | O(log n) | O(1) | Sorted / Monotonic |
| **Ternary Search** | O(log₃ n) | O(log₃ n) | O(1) | Unimodal function |
| **Exponential Search** | O(log n) | O(log n) | O(1) | Sorted, unbounded |
| **Interpolation Search** | O(log log n) | O(n) | O(1) | Sorted, uniform dist |
| **Jump Search** | O(√n) | O(√n) | O(1) | Sorted |
| **Fibonacci Search** | O(log n) | O(log n) | O(1) | Sorted |
| **Meta Binary Search** | O(log n) | O(log n) | O(1) | Sorted, power-of-2 |

---

## 2. Linear Search

### Basic
```java
int linearSearch(int[] arr, int target) {
    for (int i = 0; i < arr.length; i++)
        if (arr[i] == target) return i;
    return -1;
}
```

### Sentinel Linear Search (faster in practice)
```java
int sentinelSearch(int[] arr, int n, int target) {
    int last = arr[n - 1];
    arr[n - 1] = target;          // place sentinel
    int i = 0;
    while (arr[i] != target) i++;
    arr[n - 1] = last;            // restore
    return (i < n - 1 || last == target) ? i : -1;
}
```

**Problems:** #1295, #1346, #217 (HashSet beats linear for duplicates)

---

## 3. Binary Search — All Templates

### ⚠️ The Golden Rule
```
mid = left + (right - left) / 2;   // NEVER (left + right) / 2  → overflow!
```

---

### Template A — Exact Match (`left <= right`)
```java
int binarySearch(int[] arr, int target) {
    int lo = 0, hi = arr.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] == target) return mid;
        else if (arr[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```
> Loop exits when `lo > hi`. Use when you need an exact match.

---

### Template B — Lower Bound (first index ≥ target)
```java
int lowerBound(int[] arr, int target) {
    int lo = 0, hi = arr.length; // hi = n (open right)
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] < target) lo = mid + 1;
        else hi = mid;
    }
    return lo; // first index where arr[lo] >= target
}
```

### Template C — Upper Bound (first index > target)
```java
int upperBound(int[] arr, int target) {
    int lo = 0, hi = arr.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] <= target) lo = mid + 1;
        else hi = mid;
    }
    return lo; // first index where arr[lo] > target
}
// Count of target = upperBound(arr,t) - lowerBound(arr,t)
```

---

### Template D — Find First/Last Position (LC #34)
```java
int[] searchRange(int[] nums, int target) {
    return new int[]{ lowerBound(nums, target) < nums.length &&
                      nums[lowerBound(nums, target)] == target
                      ? lowerBound(nums, target) : -1,
                      upperBound(nums, target) - 1 >= 0 &&
                      nums[upperBound(nums, target)-1] == target
                      ? upperBound(nums, target)-1 : -1 };
}
```

---

### Template E — Find First True (`left < right` boundary style)
```java
// Monotonic predicate: FFFFTTTTT
// Returns index of first T
int firstTrue(int lo, int hi) { // [lo, hi] inclusive
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (predicate(mid)) hi = mid;      // T → go left
        else lo = mid + 1;                 // F → go right
    }
    return lo; // lo == hi at the end
}
```

### Template F — Find Last True (`left < right` boundary style)
```java
// Monotonic predicate: TTTTTFFFF
// Returns index of last T
int lastTrue(int lo, int hi) {
    while (lo < hi) {
        int mid = lo + (hi - lo + 1) / 2; // ceil mid to avoid infinite loop
        if (predicate(mid)) lo = mid;      // T → go right
        else hi = mid - 1;                 // F → go left
    }
    return lo;
}
```

> **Key insight:** Use ceil-mid (`(hi-lo+1)/2`) when doing `lo = mid` to prevent infinite loop!

---

## 4. Binary Search Patterns

### Pattern 1 — Rotated Sorted Array
```java
// LC #33 — Search in Rotated Sorted Array
int searchRotated(int[] nums, int target) {
    int lo = 0, hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) return mid;
        // Left half sorted?
        if (nums[lo] <= nums[mid]) {
            if (target >= nums[lo] && target < nums[mid]) hi = mid - 1;
            else lo = mid + 1;
        } else { // Right half sorted
            if (target > nums[mid] && target <= nums[hi]) lo = mid + 1;
            else hi = mid - 1;
        }
    }
    return -1;
}

// LC #153 — Find Minimum in Rotated Sorted Array
int findMin(int[] nums) {
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] > nums[hi]) lo = mid + 1; // min is in right half
        else hi = mid;                            // min is in left half (incl mid)
    }
    return nums[lo];
}

// LC #81 — Rotated with Duplicates
int searchRotatedII(int[] nums, int target) {
    int lo = 0, hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) return mid; // or true for #81
        // Handle duplicates: shrink window
        if (nums[lo] == nums[mid] && nums[mid] == nums[hi]) { lo++; hi--; continue; }
        if (nums[lo] <= nums[mid]) {
            if (target >= nums[lo] && target < nums[mid]) hi = mid - 1;
            else lo = mid + 1;
        } else {
            if (target > nums[mid] && target <= nums[hi]) lo = mid + 1;
            else hi = mid - 1;
        }
    }
    return -1; // or false
}
```

---

### Pattern 2 — Peak Finding
```java
// LC #162 — Find Peak Element (any peak, O(log n))
int findPeakElement(int[] nums) {
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] > nums[mid + 1]) hi = mid;   // peak ≤ mid
        else lo = mid + 1;                           // peak > mid
    }
    return lo;
}

// LC #852 — Peak Index in Mountain Array
int peakIndexInMountainArray(int[] arr) {
    int lo = 0, hi = arr.length - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] < arr[mid + 1]) lo = mid + 1;
        else hi = mid;
    }
    return lo;
}

// LC #1095 — Find in Mountain Array (3-phase BS)
int findInMountainArray(int target, MountainArray mArr) {
    int n = mArr.length();
    // Phase 1: find peak
    int lo = 0, hi = n - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (mArr.get(mid) < mArr.get(mid + 1)) lo = mid + 1;
        else hi = mid;
    }
    int peak = lo;
    // Phase 2: search ascending half [0, peak]
    lo = 0; hi = peak;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        int val = mArr.get(mid);
        if (val == target) return mid;
        else if (val < target) lo = mid + 1;
        else hi = mid - 1;
    }
    // Phase 3: search descending half [peak+1, n-1]
    lo = peak + 1; hi = n - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        int val = mArr.get(mid);
        if (val == target) return mid;
        else if (val > target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```

---

### Pattern 3 — 2D Matrix Search
```java
// LC #74 — Search a 2D Matrix (treat as 1D sorted array)
boolean searchMatrix(int[][] matrix, int target) {
    int m = matrix.length, n = matrix[0].length;
    int lo = 0, hi = m * n - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        int val = matrix[mid / n][mid % n];  // 1D → 2D mapping
        if (val == target) return true;
        else if (val < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return false;
}

// LC #240 — Search a 2D Matrix II (staircase search O(m+n))
boolean searchMatrixII(int[][] matrix, int target) {
    int row = 0, col = matrix[0].length - 1;
    while (row < matrix.length && col >= 0) {
        if (matrix[row][col] == target) return true;
        else if (matrix[row][col] < target) row++;
        else col--;
    }
    return false;
}
```

---

### Pattern 4 — Median of Two Sorted Arrays (LC #4 ⭐⭐⭐)
```java
// O(log(min(m,n))) partition-based binary search
double findMedianSortedArrays(int[] A, int[] B) {
    if (A.length > B.length) return findMedianSortedArrays(B, A); // ensure A is smaller
    int m = A.length, n = B.length;
    int lo = 0, hi = m;
    while (lo <= hi) {
        int partA = lo + (hi - lo) / 2;
        int partB = (m + n + 1) / 2 - partA;
        int maxLeftA  = partA == 0 ? Integer.MIN_VALUE : A[partA - 1];
        int minRightA = partA == m ? Integer.MAX_VALUE : A[partA];
        int maxLeftB  = partB == 0 ? Integer.MIN_VALUE : B[partB - 1];
        int minRightB = partB == n ? Integer.MAX_VALUE : B[partB];
        if (maxLeftA <= minRightB && maxLeftB <= minRightA) {
            if ((m + n) % 2 == 0)
                return (Math.max(maxLeftA, maxLeftB) + Math.min(minRightA, minRightB)) / 2.0;
            else
                return Math.max(maxLeftA, maxLeftB);
        } else if (maxLeftA > minRightB) hi = partA - 1;
        else lo = partA + 1;
    }
    return 0;
}
```

---

### Pattern 5 — Kth Smallest / Closest
```java
// LC #378 — Kth Smallest in Sorted Matrix
int kthSmallest(int[][] matrix, int k) {
    int n = matrix.length;
    int lo = matrix[0][0], hi = matrix[n-1][n-1];
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        int count = countLessEqual(matrix, mid, n);
        if (count < k) lo = mid + 1;
        else hi = mid;
    }
    return lo;
}
int countLessEqual(int[][] m, int val, int n) {
    int count = 0, row = n - 1, col = 0;
    while (row >= 0 && col < n) {
        if (m[row][col] <= val) { count += row + 1; col++; }
        else row--;
    }
    return count;
}

// LC #658 — Find K Closest Elements
int[] findClosestElements(int[] arr, int k, int x) {
    int lo = 0, hi = arr.length - k;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        // Compare: is x closer to arr[mid] or arr[mid+k]?
        if (x - arr[mid] > arr[mid + k] - x) lo = mid + 1;
        else hi = mid;
    }
    return Arrays.copyOfRange(arr, lo, lo + k);
}
```

---

## 5. Binary Search on Answer Space

### 🧠 When to Apply
- Problem asks to **minimize the maximum** or **maximize the minimum**
- Answer lies in a numeric range `[lo, hi]`
- You can verify: *"Is answer = mid feasible?"* in O(n) or O(n log n)
- The feasibility function is **monotone** (if mid works, mid+1 also works, or vice versa)

### Universal Template
```java
int bsOnAnswer(int[] arr) {
    int lo = MIN_POSSIBLE, hi = MAX_POSSIBLE;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (feasible(arr, mid)) hi = mid;  // ← minimize
        // if (feasible(arr, mid)) lo = mid; // ← maximize (use ceil-mid!)
        else lo = mid + 1;
    }
    return lo;
}
boolean feasible(int[] arr, int mid) { /* check if mid is achievable */ return true; }
```

### Classic Problems

**Koko Eating Bananas (LC #875)**
```java
int minEatingSpeed(int[] piles, int h) {
    int lo = 1, hi = Arrays.stream(piles).max().getAsInt();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        long hours = 0;
        for (int p : piles) hours += (p + mid - 1) / mid; // ceil(p/mid)
        if (hours <= h) hi = mid;
        else lo = mid + 1;
    }
    return lo;
}
```

**Capacity to Ship Packages (LC #1011)**
```java
int shipWithinDays(int[] weights, int days) {
    int lo = Arrays.stream(weights).max().getAsInt();
    int hi = Arrays.stream(weights).sum();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        int d = 1, cur = 0;
        for (int w : weights) { if (cur + w > mid) { d++; cur = 0; } cur += w; }
        if (d <= days) hi = mid; else lo = mid + 1;
    }
    return lo;
}
```

**Split Array Largest Sum (LC #410)**
```java
int splitArray(int[] nums, int k) {
    int lo = Arrays.stream(nums).max().getAsInt(), hi = Arrays.stream(nums).sum();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        int parts = 1, cur = 0;
        for (int n : nums) { if (cur + n > mid) { parts++; cur = 0; } cur += n; }
        if (parts <= k) hi = mid; else lo = mid + 1;
    }
    return lo;
}
```

**Magnetic Force Between Balls (LC #1552)**
```java
// Maximize minimum distance between k balls placed in positions[]
int maxDistance(int[] position, int m) {
    Arrays.sort(position);
    int lo = 1, hi = position[position.length-1] - position[0];
    while (lo < hi) {
        int mid = lo + (hi - lo + 1) / 2; // ceil mid (maximize)
        if (canPlace(position, m, mid)) lo = mid;
        else hi = mid - 1;
    }
    return lo;
}
boolean canPlace(int[] pos, int m, int minDist) {
    int count = 1, last = pos[0];
    for (int p : pos) { if (p - last >= minDist) { count++; last = p; } }
    return count >= m;
}
```

**Painter's Partition / Book Allocation (Classic CP)**
```java
// Same structure as Split Array Largest Sum — minimize max pages
int minPages(int[] pages, int students) { return splitArray(pages, students); }
```

**Minimum Time to Finish Jobs (LC #1723)**
```java
int minimumTimeRequired(int[] jobs, int k) {
    int lo = Arrays.stream(jobs).max().getAsInt();
    int hi = Arrays.stream(jobs).sum();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (canAssign(jobs, k, mid, 0, new int[k])) hi = mid;
        else lo = mid + 1;
    }
    return lo;
}
boolean canAssign(int[] jobs, int k, int limit, int idx, int[] workers) {
    if (idx == jobs.length) return true;
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < k; i++) {
        if (seen.contains(workers[i])) continue;
        if (workers[i] + jobs[idx] <= limit) {
            workers[i] += jobs[idx];
            if (canAssign(jobs, k, limit, idx + 1, workers)) return true;
            workers[i] -= jobs[idx];
        }
        seen.add(workers[i]);
    }
    return false;
}
```

---

## 6. Advanced & CP-Level Patterns

### Pattern 6 — Binary Search + Greedy (AGC/Codeforces problems)

**Minimum number of days to make m bouquets (LC #1482)**
```java
int minDays(int[] bloomDay, int m, int k) {
    if ((long) m * k > bloomDay.length) return -1;
    int lo = 1, hi = Arrays.stream(bloomDay).max().getAsInt();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        int bouquets = 0, flowers = 0;
        for (int d : bloomDay) {
            if (d <= mid) { flowers++; if (flowers == k) { bouquets++; flowers = 0; } }
            else flowers = 0;
        }
        if (bouquets >= m) hi = mid; else lo = mid + 1;
    }
    return lo;
}
```

---

### Pattern 7 — Parallel Binary Search (Advanced CP)

**Use case:** k independent BS problems that all call the same offline queries.  
Instead of k × O(n log n), do O(n log n log k).

```java
// Template: maintain [lo,hi] for each query, iterate log(MAX) rounds
void parallelBinarySearch(int[][] queries) {
    int Q = queries.length;
    int[] lo = new int[Q], hi = new int[Q], ans = new int[Q];
    Arrays.fill(hi, MAX);
    for (int iter = 0; iter < 30; iter++) { // log2(MAX) iterations
        // Group queries by their current mid
        List<List<Integer>> atMid = new ArrayList<>();
        // ... process all events up to mid, answer each query's group
        for (int i = 0; i < Q; i++) {
            int mid = lo[i] + (hi[i] - lo[i]) / 2;
            // if condition met for query i at mid: hi[i] = mid
            // else: lo[i] = mid + 1
        }
    }
}
```

---

### Pattern 8 — Fractional Binary Search / Real-valued BS

```java
// Minimize/maximize a real-valued answer (e.g., maximum average subarray)
double realBS(int[] arr, int k) {
    double lo = 0, hi = 1e9, eps = 1e-7;
    while (hi - lo > eps) {
        double mid = (lo + hi) / 2;
        if (feasibleReal(arr, mid, k)) hi = mid;
        else lo = mid;
    }
    return lo;
}
```

**LC #644 — Maximum Average Subarray II**
```java
double findMaxAverage(int[] nums, int k) {
    double lo = -10000, hi = 10000;
    while (hi - lo > 1e-5) {
        double mid = (lo + hi) / 2;
        if (canAchieve(nums, mid, k)) lo = mid;
        else hi = mid;
    }
    return lo;
}
boolean canAchieve(int[] nums, double avg, int k) {
    // subtract avg from each element, check if any subarray of len >= k has sum >= 0
    double sum = 0, prev = 0, minPrev = 0;
    for (int i = 0; i < nums.length; i++) {
        sum += nums[i] - avg;
        if (i >= k) { prev += nums[i - k] - avg; minPrev = Math.min(minPrev, prev); }
        if (i >= k - 1 && sum - minPrev >= 0) return true;
    }
    return false;
}
```

---

### Pattern 9 — Binary Search on Segment Tree / BIT (Order Statistics)

```java
// Find k-th element in BIT (1-indexed prefix sums)
int kthElement(int[] bit, int k, int n) {
    int pos = 0;
    for (int pw = Integer.highestOneBit(n); pw > 0; pw >>= 1) {
        if (pos + pw <= n && bit[pos + pw] < k) {
            pos += pw;
            k -= bit[pos];
        }
    }
    return pos + 1; // 1-indexed answer
}
```

**LC #315 — Count of Smaller Numbers After Self** (BS + BIT)
```java
// Build BIT over coordinate-compressed values, query rank from right
```

---

### Pattern 10 — Binary Search + DP (Classic)

**LC #300 — Longest Increasing Subsequence (O(n log n) patience sort)**
```java
int lengthOfLIS(int[] nums) {
    List<Integer> tails = new ArrayList<>();
    for (int num : nums) {
        int lo = 0, hi = tails.size();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (tails.get(mid) < num) lo = mid + 1;
            else hi = mid;
        }
        if (lo == tails.size()) tails.add(num);
        else tails.set(lo, num);
    }
    return tails.size();
}
```

**LC #354 — Russian Doll Envelopes** (sort + LIS with BS)
```java
int maxEnvelopes(int[][] envelopes) {
    // Sort by width ASC, then height DESC (to prevent same-width stacking)
    Arrays.sort(envelopes, (a, b) -> a[0] == b[0] ? b[1] - a[1] : a[0] - b[0]);
    int[] heights = new int[envelopes.length];
    for (int i = 0; i < envelopes.length; i++) heights[i] = envelopes[i][1];
    return lengthOfLIS(heights);
}
```

**LC #1235 — Maximum Profit in Job Scheduling** (sort + BS + DP)
```java
int jobScheduling(int[] startTime, int[] endTime, int[] profit) {
    int n = startTime.length;
    int[][] jobs = new int[n][3];
    for (int i = 0; i < n; i++) jobs[i] = new int[]{endTime[i], startTime[i], profit[i]};
    Arrays.sort(jobs, (a, b) -> a[0] - b[0]);
    TreeMap<Integer, Integer> dp = new TreeMap<>();
    dp.put(0, 0);
    for (int[] job : jobs) {
        int cur = dp.floorEntry(job[1]).getValue() + job[2];
        if (cur > dp.lastEntry().getValue())
            dp.put(job[0], cur);
    }
    return dp.lastEntry().getValue();
}
```

---

### Pattern 11 — Binary Lifting / Sparse Table (BS on trees)

**Binary Lifting for LCA:**
```java
// Precompute: up[v][j] = 2^j-th ancestor of v
// LCA query in O(log n): lift in binary
int lca(int u, int v, int[] depth, int[][] up) {
    if (depth[u] < depth[v]) { int t = u; u = v; v = t; }
    int diff = depth[u] - depth[v];
    for (int j = 0; j < 20; j++) if ((diff >> j & 1) == 1) u = up[u][j];
    if (u == v) return u;
    for (int j = 19; j >= 0; j--) if (up[u][j] != up[v][j]) { u = up[u][j]; v = up[v][j]; }
    return up[u][0];
}
```

**Kth ancestor in O(log n):**
```java
int kthAncestor(int node, int k, int[][] up) {
    for (int j = 0; j < 20; j++) if ((k >> j & 1) == 1) node = up[node][j];
    return node;
}
```

---

### Pattern 12 — Weighted Binary Search (Randomized / Expected)

**Quickselect (expected O(n)) — Kth Smallest:**
```java
int quickselect(int[] nums, int k) {
    // k is 1-indexed
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        int pivot = partition(nums, lo, hi);
        if (pivot == k - 1) return nums[pivot];
        else if (pivot < k - 1) lo = pivot + 1;
        else hi = pivot - 1;
    }
    return nums[lo];
}
int partition(int[] nums, int lo, int hi) {
    int pivot = nums[hi], i = lo;
    for (int j = lo; j < hi; j++) if (nums[j] <= pivot) { int t=nums[i];nums[i]=nums[j];nums[j]=t; i++; }
    int t=nums[i];nums[i]=nums[hi];nums[hi]=t;
    return i;
}
```

---

## 7. Ternary Search

**Use:** Find minimum/maximum of a **unimodal function** (one peak/valley).

```java
// On integers: minimize f(x) on [lo, hi]
int ternarySearchMin(int lo, int hi) {
    while (hi - lo > 2) {
        int m1 = lo + (hi - lo) / 3;
        int m2 = hi - (hi - lo) / 3;
        if (f(m1) < f(m2)) hi = m2 - 1;
        else lo = m1 + 1;
    }
    int ans = lo;
    for (int x = lo; x <= hi; x++) if (f(x) < f(ans)) ans = x;
    return ans;
}

// On reals: minimize f(x) on [lo, hi]
double ternarySearchReal(double lo, double hi) {
    for (int iter = 0; iter < 200; iter++) {  // 200 iters → precision ~1e-60
        double m1 = lo + (hi - lo) / 3;
        double m2 = hi - (hi - lo) / 3;
        if (f(m1) < f(m2)) hi = m2;
        else lo = m1;
    }
    return (lo + hi) / 2;
}
```

**When ternary > binary:** Convex/concave functions, geometry (shortest path from point to segment), etc.

---

## 8. Exponential & Interpolation Search

### Exponential Search (unbounded / infinite arrays)
```java
int exponentialSearch(int[] arr, int target) {
    if (arr[0] == target) return 0;
    int bound = 1;
    while (bound < arr.length && arr[bound] <= target) bound *= 2;
    // Now binary search in [bound/2, min(bound, n-1)]
    int lo = bound / 2, hi = Math.min(bound, arr.length - 1);
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] == target) return mid;
        else if (arr[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```

### Jump Search
```java
int jumpSearch(int[] arr, int target) {
    int n = arr.length, step = (int) Math.sqrt(n), prev = 0;
    while (arr[Math.min(step, n) - 1] < target) {
        prev = step; step += (int) Math.sqrt(n);
        if (prev >= n) return -1;
    }
    while (arr[prev] < target) { prev++; if (prev == Math.min(step, n)) return -1; }
    return arr[prev] == target ? prev : -1;
}
```

### Interpolation Search (uniform distribution → O(log log n))
```java
int interpolationSearch(int[] arr, int target) {
    int lo = 0, hi = arr.length - 1;
    while (lo <= hi && target >= arr[lo] && target <= arr[hi]) {
        if (lo == hi) return arr[lo] == target ? lo : -1;
        // Probe position
        int pos = lo + (int)((long)(hi - lo) * (target - arr[lo]) / (arr[hi] - arr[lo]));
        if (arr[pos] == target) return pos;
        else if (arr[pos] < target) lo = pos + 1;
        else hi = pos - 1;
    }
    return -1;
}
```

### Fibonacci Search
```java
int fibonacciSearch(int[] arr, int target) {
    int n = arr.length;
    int fib2 = 0, fib1 = 1, fib = 1;
    while (fib < n) { fib2 = fib1; fib1 = fib; fib = fib1 + fib2; }
    int offset = -1;
    while (fib > 1) {
        int i = Math.min(offset + fib2, n - 1);
        if (arr[i] < target) { fib = fib1; fib1 = fib2; fib2 = fib - fib1; offset = i; }
        else if (arr[i] > target) { fib = fib2; fib1 = fib1 - fib2; fib2 = fib - fib1; }
        else return i;
    }
    return (fib1 == 1 && arr[offset + 1] == target) ? offset + 1 : -1;
}
```

---

## 9. Bitwise / Bit-parallel Search

### Bitmasking Search
```java
// Find subset with target XOR (meet-in-the-middle)
// Split array into halves; enumerate all 2^(n/2) subsets each half
// Sort one half, binary search for complement
int countSubsetsWithXOR(int[] arr, int target) {
    int n = arr.length, half = n / 2, count = 0;
    int size = 1 << half;
    int[] left = new int[size];
    for (int mask = 0; mask < size; mask++) {
        int xor = 0;
        for (int i = 0; i < half; i++) if ((mask >> i & 1) == 1) xor ^= arr[i];
        left[mask] = xor;
    }
    Arrays.sort(left);
    for (int mask = 0; mask < (1 << (n - half)); mask++) {
        int xor = 0;
        for (int i = 0; i < n - half; i++) if ((mask >> i & 1) == 1) xor ^= arr[half + i];
        int need = xor ^ target;
        count += upperBound(left, need) - lowerBound(left, need);
    }
    return count;
}
```

### Bit-by-bit Greedy (maximize XOR — Trie / BS hybrid)
```java
// LC #421 — Maximum XOR of Two Numbers in an Array
int findMaximumXOR(int[] nums) {
    int max = 0, mask = 0;
    for (int i = 31; i >= 0; i--) {
        mask |= (1 << i);
        Set<Integer> prefixes = new HashSet<>();
        for (int num : nums) prefixes.add(num & mask);
        int temp = max | (1 << i);
        for (int prefix : prefixes) {
            if (prefixes.contains(temp ^ prefix)) { max = temp; break; }
        }
    }
    return max;
}
```

---

## 10. Problem Bank by Pattern

### 🟢 Easy / Warm-up
| # | Problem | Pattern |
|---|---------|---------|
| 704 | Binary Search | Template A |
| 278 | First Bad Version | Template B |
| 35 | Search Insert Position | Lower bound |
| 69 | Sqrt(x) | BS on answer |
| 374 | Guess Number | Template A |
| 744 | Find Smallest > target | Upper bound |
| 1351 | Count Negatives in Matrix | Row-wise BS |

### 🟡 Medium — Core Patterns
| # | Problem | Pattern |
|---|---------|---------|
| 34 | First and Last Position ⭐ | Lower/Upper Bound |
| 33 | Search in Rotated Array ⭐⭐⭐ | Rotated BS |
| 153 | Find Minimum in Rotated ⭐⭐ | Rotated BS |
| 162 | Find Peak Element ⭐⭐ | Peak BS |
| 74 | Search a 2D Matrix ⭐⭐ | 2D→1D BS |
| 875 | Koko Eating Bananas ⭐⭐⭐ | BS on Answer |
| 1011 | Capacity to Ship Packages ⭐⭐⭐ | BS on Answer |
| 2187 | Min Time to Complete Trips | BS on Answer |
| 1482 | Min Days to Make Bouquets | BS on Answer |
| 1552 | Magnetic Force Between Balls | BS on Answer (maximize min) |
| 300 | Longest Increasing Subsequence | BS + DP (O(n log n)) |
| 658 | Find K Closest Elements | BS + Two Pointers |
| 378 | Kth Smallest in Sorted Matrix | BS + Count |
| 852 | Peak Index in Mountain Array | Peak BS |
| 540 | Single Element in Sorted Array | Parity BS |
| 1760 | Min Limit of Balls in a Bag | BS on Answer |

### 🔴 Hard — Advanced
| # | Problem | Pattern |
|---|---------|---------|
| 4 | Median of Two Sorted Arrays ⭐⭐⭐ | Partition BS |
| 410 | Split Array Largest Sum ⭐⭐⭐ | BS on Answer |
| 315 | Count of Smaller Numbers After Self | BS + BIT |
| 354 | Russian Doll Envelopes | Sort + LIS via BS |
| 1235 | Max Profit in Job Scheduling | Sort + BS + DP |
| 1095 | Find in Mountain Array | 3-Phase BS |
| 644 | Maximum Average Subarray II | Real-valued BS |
| 1723 | Min Time to Finish Jobs | BS + Backtracking |
| 719 | Find K-th Smallest Pair Distance | BS + Counting |
| 786 | K-th Smallest Prime Fraction | BS on fractions |
| 2141 | Max Running Time of N Computers | BS on Answer |
| 1970 | Last Day Where You Can Still Cross | BS + BFS/Union-Find |

### ⚡ CP-Level (Codeforces / ICPC)
| Problem | Source | Pattern |
|---------|--------|---------|
| Aggressive Cows | SPOJ / CF | Maximize min distance |
| Book Allocation | GFG / CF | Minimize max pages |
| Painter's Partition | GFG | BS on Answer |
| K-th Smallest in Merge of Sorted Lists | CF | BS + Binary Indexed |
| Parallel BS | CF Educational | Parallel BS framework |
| DP + BS on convex hull trick | CF | CHT / Li Chao Tree |

---

## 11. Company-Wise Must-Dos

### 🟦 Meta / Facebook
| Problem | LC # | Why |
|---------|------|-----|
| Search in Rotated Sorted Array | 33 | Classic |
| Find Peak Element | 162 | Non-obvious BS |
| First and Last Position | 34 | Boundary BS |
| Median of Two Sorted Arrays | 4 | Hard, frequently asked |

### 🟧 Amazon
| Problem | LC # |
|---------|------|
| Koko Eating Bananas | 875 |
| Capacity to Ship Packages | 1011 |
| Search a 2D Matrix | 74 |
| Kth Smallest in Matrix | 378 |

### 🟩 Google
| Problem | LC # |
|---------|------|
| Median of Two Sorted Arrays | 4 |
| Split Array Largest Sum | 410 |
| Find K Closest Elements | 658 |
| Maximum Average Subarray II | 644 |
| Russian Doll Envelopes | 354 |

### 🟪 Microsoft
| Problem | LC # |
|---------|------|
| Search in Rotated Sorted Array | 33 |
| Find Minimum in Rotated Array | 153 |
| Kth Smallest in Sorted Matrix | 378 |
| Longest Increasing Subsequence | 300 |

---

## 12. Study Plan

### Week 1 — Foundation
- [ ] LC 704 — Binary Search (Template A)
- [ ] LC 278 — First Bad Version (Template B)
- [ ] LC 35 — Search Insert Position
- [ ] LC 69 — Sqrt(x)
- [ ] LC 34 — First and Last Position ⭐

### Week 2 — Core Patterns
- [ ] LC 33 — Search in Rotated Array ⭐⭐⭐
- [ ] LC 153 — Find Minimum in Rotated ⭐⭐
- [ ] LC 81 — Rotated with Duplicates
- [ ] LC 162 — Find Peak Element ⭐⭐
- [ ] LC 74 — Search 2D Matrix

### Week 3 — BS on Answer (GAME CHANGER)
- [ ] LC 875 — Koko Eating Bananas ⭐⭐⭐
- [ ] LC 1011 — Capacity to Ship ⭐⭐⭐
- [ ] LC 410 — Split Array Largest Sum ⭐⭐⭐
- [ ] LC 1552 — Magnetic Force ⭐⭐
- [ ] LC 1482 — Min Days for Bouquets

### Week 4 — Hard & Advanced
- [ ] LC 4 — Median of Two Sorted Arrays ⭐⭐⭐
- [ ] LC 300 — LIS (O(n log n)) ⭐⭐⭐
- [ ] LC 354 — Russian Doll Envelopes
- [ ] LC 315 — Count Smaller After Self
- [ ] LC 1235 — Max Profit Job Scheduling

### Week 5 — CP Level
- [ ] Ternary Search on continuous functions
- [ ] Parallel Binary Search template
- [ ] Real-valued binary search
- [ ] Binary lifting / Sparse Table
- [ ] Quickselect + Order Statistics Tree

---

## 13. Cheat Sheet & Common Mistakes

### ✅ 3 Universal Templates

```java
// T1: Exact match
while (lo <= hi) { if (arr[mid] == t) return mid; ... }

// T2: First True (lower bound)
while (lo < hi) { if (pred(mid)) hi = mid; else lo = mid+1; } return lo;

// T3: Last True (upper bound)
while (lo < hi) { int mid=lo+(hi-lo+1)/2; if (pred(mid)) lo=mid; else hi=mid-1; } return lo;
```

### ❌ Common Mistakes & Fixes

| Mistake | Fix |
|---------|-----|
| `mid = (lo+hi)/2` | ✅ `mid = lo + (hi-lo)/2` (overflow!) |
| `lo = mid` with `while(lo<hi)` and floor mid | ✅ Use ceil mid: `(hi-lo+1)/2` |
| Wrong boundary for BS on answer | ✅ Think: what is the absolute min/max possible? |
| Forgetting `arr[lo]==target` check after loop | ✅ Always verify the final `lo` |
| Off-by-one in upper bound | ✅ Test with array of all same elements |
| Infinite loop in rotated array with duplicates | ✅ Use `lo++; hi--` when `nums[lo]==nums[mid]==nums[hi]` |

### 🔑 Decision Tree

```
Need O(log n)?
├── Array sorted? ──YES──► Classic BS (Template A/B/C)
│   ├── Rotated? ──────────► Pattern 1 (Rotated BS)
│   ├── Has Peak? ─────────► Pattern 2 (Peak BS)
│   └── 2D? ───────────────► Pattern 3 (2D BS)
│
├── Monotone predicate? ──► First/Last True (Template B/C)
│
├── Min/Max optimization? ► BS on Answer Space (Pattern 5)
│   └── ask: is feasible(mid) monotone? If YES → apply!
│
├── Unimodal function? ───► Ternary Search
│
└── DP + monotone cost? ──► BS + CHT / Li Chao Tree (Advanced)
```

### 💡 Key Insight Summary

| Insight | Note |
|---------|------|
| BS works on **any monotone space** | Not just sorted arrays |
| **Lower bound** = first index ≥ target | `upperBound - lowerBound` = count |
| BS on answer: identify `[lo, hi]` then write `feasible()` | The hardest part is the check function |
| Ternary search needs unimodal, not just sorted | Cannot use for general BS |
| Real-valued BS: iterate fixed times (100–200) | Precision converges exponentially |
| Parallel BS: amortize k queries over log rounds | Major optimization in offline settings |

---

> **The Deeper Truth:** Binary Search is not an algorithm — it's a **paradigm**. Any time you have a monotone function over a discrete or continuous domain, you can binary search over it. Master the predicate and the bounds, and every problem becomes the same problem. 🎯
