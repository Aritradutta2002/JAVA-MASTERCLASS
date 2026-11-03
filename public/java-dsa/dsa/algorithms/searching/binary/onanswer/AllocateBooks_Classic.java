package dsa.algorithms.searching.binary.onanswer;

/*
 * ALLOCATE BOOKS (Classic Problem)
 * Difficulty: Medium
 * Pattern: Binary Search on Answer (Resource Allocation)
 * Also known as: Book Allocation Problem, Painter's Partition (variant)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROBLEM STATEMENT:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Given an array 'pages[]' where pages[i] represents the number of pages in the 
 * ith book, and an integer 'students', allocate books to students such that:
 * 
 * 1. Each student gets at least one book
 * 2. Each book is allocated to exactly one student
 * 3. Books allocation must be in contiguous manner (can't skip books)
 * 4. Minimize the MAXIMUM pages assigned to any student
 * 
 * Return the minimum possible value of the maximum pages.
 * 
 * EXAMPLE 1:
 * Input: pages = [12, 34, 67, 90], students = 2
 * Output: 113
 * Explanation:
 * - Student 1: [12, 34, 67] = 113 pages
 * - Student 2: [90] = 90 pages
 * Maximum = 113
 * 
 * Other allocations:
 * - [12], [34, 67, 90] → max = 191
 * - [12, 34], [67, 90] → max = 157
 * - [12, 34, 67], [90] → max = 113 ✓ MINIMUM
 * 
 * EXAMPLE 2:
 * Input: pages = [15, 17, 20], students = 2
 * Output: 32
 * Explanation:
 * - Student 1: [15, 17] = 32 pages
 * - Student 2: [20] = 20 pages
 * 
 * EXAMPLE 3:
 * Input: pages = [10, 20, 30, 40], students = 4
 * Output: 40
 * Explanation: Each student gets one book, max = 40
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * UNDERSTANDING THE PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * REAL-WORLD ANALOGY:
 * -------------------
 * Imagine distributing work (books) among students:
 * - Want to finish as quickly as possible
 * - Each student works independently
 * - Time = maximum pages any student has to read
 * - Goal: Minimize this maximum time
 * 
 * CONSTRAINTS:
 * ------------
 * - Books must be allocated contiguously (can't give book 1 and 3 to same student)
 * - Each book goes to exactly one student
 * - Each student gets at least one book (so students <= books)
 * 
 * VISUAL EXAMPLE:
 * ---------------
 * Books: [12, 34, 67, 90], Students: 2
 * 
 * Allocation 1:
 * Student 1: [12] = 12
 * Student 2: [34, 67, 90] = 191
 * Max = 191 ✗ Too high
 * 
 * Allocation 2:
 * Student 1: [12, 34] = 46
 * Student 2: [67, 90] = 157
 * Max = 157 ✗ Still high
 * 
 * Allocation 3:
 * Student 1: [12, 34, 67] = 113
 * Student 2: [90] = 90
 * Max = 113 ✓ BEST!
 * 
 * WHY BINARY SEARCH?
 * ------------------
 * Question: "Minimize the maximum pages"
 * 
 * Key Observation (MONOTONICITY):
 * - If we can allocate books with max pages = M
 * - Then we can definitely allocate with max pages = M+1
 * - But maybe NOT with max pages = M-1
 * 
 * Visual:
 * Max Pages:  40  50  60  70  80  90  100  110  113  120
 * Can allocate: ✗   ✗   ✗   ✗   ✗   ✗   ✗    ✗    ✓    ✓
 *                                              ↑
 *                                      Minimum valid (answer!)
 * 
 * ANSWER SPACE:
 * -------------
 * Minimum: max(pages) - Each student gets at least the largest book
 * Maximum: sum(pages) - One student gets all books
 * 
 * We binary search on: [max(pages), sum(pages)]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ALGORITHM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * STEP 1: Define search space [max(pages), sum(pages)]
 * 
 * STEP 2: Binary search on maximum pages:
 *   - Try mid as maximum allowed pages per student
 *   - Can we allocate all books to 'students' with this limit?
 *   - If YES: Try smaller limit (minimize)
 *   - If NO: Need larger limit
 * 
 * STEP 3: Validation (Greedy Approach):
 * studentsNeeded = 1
 * currentPages = 0
 * 
 * For each book:
 *   If (currentPages + book > maxPages):
 *     Need a new student
 *     studentsNeeded++
 *     currentPages = book
 *   Else:
 *     Give to current student
 *     currentPages += book
 * 
 * Return studentsNeeded <= students
 * 
 * DETAILED EXAMPLE:
 * -----------------
 * pages = [12, 34, 67, 90], students = 2
 * 
 * Search space: [90, 203]
 * 
 * Iteration 1: mid = 146
 *   Can allocate with max = 146?
 *   - Student 1: 12, 34, 67 = 113 (next book 90 would exceed 146)
 *   - Student 2: 90
 *   Used 2 students ✓
 *   146 works, try smaller: right = 145
 * 
 * Iteration 2: mid = 117
 *   Can allocate with max = 117?
 *   - Student 1: 12, 34, 67 = 113 (next book 90 would exceed 117)
 *   - Student 2: 90
 *   Used 2 students ✓
 *   117 works, try smaller: right = 116
 * 
 * Iteration 3: mid = 113
 *   Can allocate with max = 113?
 *   - Student 1: 12, 34, 67 = 113
 *   - Student 2: 90
 *   Used 2 students ✓
 *   113 works, try smaller: right = 112
 * 
 * Iteration 4: mid = 101
 *   Can allocate with max = 101?
 *   - Student 1: 12, 34 = 46 (67 would exceed 101)
 *   - Student 2: 67 (90 would exceed 101)
 *   - Student 3: 90
 *   Used 3 students ✗ Too many!
 *   101 doesn't work: left = 102
 * 
 * Continue until left > right...
 * Answer: 113
 * 
 * TIME COMPLEXITY: O(n * log(sum - max))
 * SPACE COMPLEXITY: O(1)
 */

import java.util.*;

public class AllocateBooks_Classic {
    
    public int allocateBooks(int[] pages, int students) {
        int n = pages.length;
        
        // Edge case: More students than books
        if (students > n) {
            return -1;  // Can't give each student a book
        }
        
        // Step 1: Define search space
        int left = getMax(pages);      // Minimum: largest book
        int right = getSum(pages);     // Maximum: all books to one student
        int result = right;
        
        // Step 2: Binary search on maximum pages
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            // Check if we can allocate with this maximum
            if (canAllocate(pages, students, mid)) {
                result = mid;      // This maximum works, save it
                right = mid - 1;   // Try smaller maximum (minimize)
            } else {
                left = mid + 1;    // Maximum too small, need larger
            }
        }
        
        return result;
    }
    
    // Validation: Can we allocate books to 'students' with max pages = 'maxPages'?
    private boolean canAllocate(int[] pages, int students, int maxPages) {
        int studentsNeeded = 1;
        int currentPages = 0;
        
        for (int page : pages) {
            // If adding this book exceeds limit
            if (currentPages + page > maxPages) {
                // Assign to new student
                studentsNeeded++;
                currentPages = page;
                
                // If we need more students than available
                if (studentsNeeded > students) {
                    return false;
                }
            } else {
                // Give to current student
                currentPages += page;
            }
        }
        
        return true;
    }
    
    private int getMax(int[] arr) {
        int max = arr[0];
        for (int num : arr) {
            max = Math.max(max, num);
        }
        return max;
    }
    
    private int getSum(int[] arr) {
        int sum = 0;
        for (int num : arr) {
            sum += num;
        }
        return sum;
    }
    
    public static void main(String[] args) {
        AllocateBooks_Classic solution = new AllocateBooks_Classic();
        
        // Test Case 1
        int[] pages1 = {12, 34, 67, 90};
        int students1 = 2;
        System.out.println("Test Case 1:");
        System.out.println("Books: " + Arrays.toString(pages1));
        System.out.println("Students: " + students1);
        System.out.println("Minimum maximum pages: " + solution.allocateBooks(pages1, students1));
        System.out.println();
        
        // Test Case 2
        int[] pages2 = {15, 17, 20};
        int students2 = 2;
        System.out.println("Test Case 2:");
        System.out.println("Books: " + Arrays.toString(pages2));
        System.out.println("Students: " + students2);
        System.out.println("Minimum maximum pages: " + solution.allocateBooks(pages2, students2));
        System.out.println();
        
        // Test Case 3
        int[] pages3 = {10, 20, 30, 40};
        int students3 = 4;
        System.out.println("Test Case 3:");
        System.out.println("Books: " + Arrays.toString(pages3));
        System.out.println("Students: " + students3);
        System.out.println("Minimum maximum pages: " + solution.allocateBooks(pages3, students3));
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. PROBLEM TYPE: "Minimize Maximum"
 *    Template:
 *    if (canAllocate(mid)) {
 *        result = mid;
 *        right = mid - 1;  // Try to minimize
 *    }
 * 
 * 2. SEARCH SPACE BOUNDS:
 *    Lower: max(array) - Can't go below largest element
 *    Upper: sum(array) - Can't go above total
 *    DON'T use [0, sum] - waste of iterations!
 * 
 * 3. GREEDY VALIDATION:
 *    Fill current student until limit reached
 *    Then move to next student
 *    This minimizes students needed
 * 
 * 4. CONTIGUOUS CONSTRAINT:
 *    Books must be allocated in order
 *    Can't skip books or rearrange
 *    This makes greedy approach work
 * 
 * 5. SIMILAR PROBLEMS:
 *    - Split Array Largest Sum (Leetcode 410) - IDENTICAL logic
 *    - Painter's Partition Problem - Same pattern
 *    - Capacity To Ship Packages (Leetcode 1010) - Similar
 * 
 * 6. VARIATIONS:
 *    - Painter's Partition: boards[] instead of pages[]
 *    - Minimize maximum machine load
 *    - Distribute workload among workers
 * 
 * 7. COMMON MISTAKES:
 *    ❌ Using [0, sum] instead of [max, sum]
 *    ❌ Wrong search direction
 *    ❌ Forgetting to check students > books
 *    ❌ Not handling edge cases
 * 
 * 8. EDGE CASES:
 *    - students > books (impossible)
 *    - students = books (each gets one)
 *    - students = 1 (one gets all)
 *    - All books same size
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */


