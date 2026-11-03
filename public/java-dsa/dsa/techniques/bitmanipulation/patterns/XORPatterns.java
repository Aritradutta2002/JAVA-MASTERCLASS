package dsa.techniques.bitmanipulation.patterns;
/**
 * XOR Patterns - Essential Interview Techniques
 * 
 * XOR is one of the most powerful bit manipulation operations for interviews.
 * This class covers all essential XOR patterns and their applications.
 * 
 * Key XOR Properties:
 * 1. a ^ a = 0
 * 2. a ^ 0 = a
 * 3. XOR is commutative: a ^ b = b ^ a
 * 4. XOR is associative: (a ^ b) ^ c = a ^ (b ^ c)
 * 5. Self-inverse: a ^ b ^ b = a
 */
public class XORPatterns {
    
    /**
     * Pattern 1: Find Single Element (Numbers appear twice except one)
     * LeetCode 136: Single Number
     */
    public int singleNumber(int[] nums) {
        int result = 0;
        for (int num : nums) {
            result ^= num;
        }
        return result;
    }
    
    /**
     * Pattern 2: Find Two Single Elements (Others appear twice)
     * LeetCode 260: Single Number III
     */
    public int[] singleNumberIII(int[] nums) {
        int xor = 0;
        for (int num : nums) {
            xor ^= num;
        }
        
        // Find rightmost set bit (differentiating bit)
        int rightmostBit = xor & (-xor);
        
        int num1 = 0, num2 = 0;
        for (int num : nums) {
            if ((num & rightmostBit) != 0) {
                num1 ^= num;
            } else {
                num2 ^= num;
            }
        }
        
        return new int[]{num1, num2};
    }
    
    /**
     * Pattern 3: Missing Number in Array
     * LeetCode 268: Missing Number
     */
    public int missingNumber(int[] nums) {
        int n = nums.length;
        int expectedXor = 0;
        int actualXor = 0;
        
        // XOR of expected numbers [0, n]
        for (int i = 0; i <= n; i++) {
            expectedXor ^= i;
        }
        
        // XOR of actual numbers
        for (int num : nums) {
            actualXor ^= num;
        }
        
        return expectedXor ^ actualXor;
    }
    
    /**
     * Pattern 4: Swap Two Numbers Without Extra Space
     */
    public void swapWithoutTemp(int[] arr, int i, int j) {
        if (i != j) {  // Important check to avoid zeroing same element
            arr[i] ^= arr[j];
            arr[j] ^= arr[i];
            arr[i] ^= arr[j];
        }
    }
    
    /**
     * Pattern 5: Check if Two Numbers are Equal
     */
    public boolean areEqual(int a, int b) {
        return (a ^ b) == 0;
    }
    
    /**
     * Pattern 6: Toggle Case of Alphabetic Character
     */
    public char toggleCase(char c) {
        // XOR with space (32) toggles case for alphabetic characters
        return (char) (c ^ 32);
    }
    
    /**
     * Pattern 7: Find XOR of Range [L, R]
     */
    public int xorRange(int L, int R) {
        return xorUpTo(R) ^ xorUpTo(L - 1);
    }
    
    private int xorUpTo(int n) {
        if (n < 0) return 0;
        
        // Pattern repeats every 4 numbers: n, 1, n+1, 0
        switch (n % 4) {
            case 0: return n;
            case 1: return 1;
            case 2: return n + 1;
            case 3: return 0;
            default: return 0;
        }
    }
    
    /**
     * Pattern 8: Minimum XOR Pair in Array
     */
    public int minimumXORPair(int[] nums) {
        java.util.Arrays.sort(nums);
        int minXor = Integer.MAX_VALUE;
        
        for (int i = 0; i < nums.length - 1; i++) {
            minXor = Math.min(minXor, nums[i] ^ nums[i + 1]);
        }
        
        return minXor;
    }
    
    /**
     * Pattern 9: XOR Queries on Array
     * Handle multiple range XOR queries efficiently
     */
    public int[] xorQueries(int[] arr, int[][] queries) {
        int n = arr.length;
        int[] prefixXor = new int[n + 1];
        
        // Build prefix XOR array
        for (int i = 0; i < n; i++) {
            prefixXor[i + 1] = prefixXor[i] ^ arr[i];
        }
        
        int[] result = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            int left = queries[i][0];
            int right = queries[i][1];
            result[i] = prefixXor[right + 1] ^ prefixXor[left];
        }
        
        return result;
    }
    
    /**
     * Pattern 10: Encode and Decode using XOR
     */
    public static class XORCipher {
        private int key;
        
        public XORCipher(int key) {
            this.key = key;
        }
        
        public int encode(int data) {
            return data ^ key;
        }
        
        public int decode(int encodedData) {
            return encodedData ^ key;  // XOR is self-inverse
        }
        
        public String encodeString(String text) {
            StringBuilder encoded = new StringBuilder();
            for (char c : text.toCharArray()) {
                encoded.append((char) (c ^ key));
            }
            return encoded.toString();
        }
        
        public String decodeString(String encodedText) {
            return encodeString(encodedText);  // Same operation due to XOR property
        }
    }
    
    /**
     * Pattern 11: Gray Code Generation
     * LeetCode 89: Gray Code
     */
    public java.util.List<Integer> grayCode(int n) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        
        for (int i = 0; i < (1 << n); i++) {
            result.add(i ^ (i >> 1));  // Gray code formula
        }
        
        return result;
    }
    
    /**
     * Pattern 12: Find Duplicate in Array (Cyclic Sort approach)
     */
    public int findDuplicate(int[] nums) {
        // Using XOR to find duplicate when numbers are in range [1, n]
        int n = nums.length - 1;
        int xor1 = 0, xor2 = 0;
        
        // XOR all array elements
        for (int num : nums) {
            xor1 ^= num;
        }
        
        // XOR all numbers from 1 to n
        for (int i = 1; i <= n; i++) {
            xor2 ^= i;
        }
        
        return xor1 ^ xor2;
    }
    
    /**
     * Pattern 13: Maximum XOR Subset
     * Find subset with maximum XOR (Gaussian elimination approach)
     */
    public int maxXORSubset(int[] nums) {
        int maxXor = 0;
        int index = 0;
        
        // Process each bit position from MSB to LSB
        for (int bit = 30; bit >= 0; bit--) {
            int maxElement = Integer.MIN_VALUE;
            int maxIndex = -1;
            
            // Find element with maximum value at current bit position
            for (int i = index; i < nums.length; i++) {
                if ((nums[i] & (1 << bit)) != 0 && nums[i] > maxElement) {
                    maxElement = nums[i];
                    maxIndex = i;
                }
            }
            
            if (maxIndex == -1) continue;
            
            // Swap maximum element to current index
            int temp = nums[index];
            nums[index] = nums[maxIndex];
            nums[maxIndex] = temp;
            
            maxXor = Math.max(maxXor, maxXor ^ nums[index]);
            
            // Eliminate this bit from all other elements
            for (int i = 0; i < nums.length; i++) {
                if (i != index && (nums[i] & (1 << bit)) != 0) {
                    nums[i] ^= nums[index];
                }
            }
            
            index++;
        }
        
        return maxXor;
    }
    
    /**
     * Pattern 14: Beautiful Arrangement (State compression)
     */
    public int countArrangements(int n) {
        return backtrack(n, 1, 0);
    }
    
    private int backtrack(int n, int pos, int usedMask) {
        if (pos > n) return 1;
        
        int count = 0;
        for (int i = 1; i <= n; i++) {
            if ((usedMask & (1 << i)) == 0 && (pos % i == 0 || i % pos == 0)) {
                count += backtrack(n, pos + 1, usedMask | (1 << i));
            }
        }
        
        return count;
    }
    
    /**
     * Test method demonstrating all patterns
     */
    public static void main(String[] args) {
        XORPatterns patterns = new XORPatterns();
        
        // Test Pattern 1: Single Number
        System.out.println("=== Pattern 1: Single Number ===");
        int[] single = {4, 1, 2, 1, 2};
        System.out.println("Single number: " + patterns.singleNumber(single));
        
        // Test Pattern 2: Two Single Numbers
        System.out.println("\n=== Pattern 2: Two Single Numbers ===");
        int[] twoSingle = {1, 2, 1, 3, 2, 5};
        int[] result = patterns.singleNumberIII(twoSingle);
        System.out.println("Two single numbers: [" + result[0] + ", " + result[1] + "]");
        
        // Test Pattern 3: Missing Number
        System.out.println("\n=== Pattern 3: Missing Number ===");
        int[] missing = {3, 0, 1};
        System.out.println("Missing number: " + patterns.missingNumber(missing));
        
        // Test Pattern 4: Swap Without Temp
        System.out.println("\n=== Pattern 4: Swap Without Temp ===");
        int[] swapArray = {5, 10};
        System.out.println("Before swap: [" + swapArray[0] + ", " + swapArray[1] + "]");
        patterns.swapWithoutTemp(swapArray, 0, 1);
        System.out.println("After swap: [" + swapArray[0] + ", " + swapArray[1] + "]");
        
        // Test Pattern 6: Toggle Case
        System.out.println("\n=== Pattern 6: Toggle Case ===");
        System.out.println("Toggle 'A': " + patterns.toggleCase('A'));
        System.out.println("Toggle 'a': " + patterns.toggleCase('a'));
        
        // Test Pattern 7: XOR Range
        System.out.println("\n=== Pattern 7: XOR Range ===");
        System.out.println("XOR range [3, 7]: " + patterns.xorRange(3, 7));
        
        // Test Pattern 11: Gray Code
        System.out.println("\n=== Pattern 11: Gray Code ===");
        System.out.println("Gray code for n=3: " + patterns.grayCode(3));
        
        // Test XOR Cipher
        System.out.println("\n=== Pattern 10: XOR Cipher ===");
        XORCipher cipher = new XORCipher(42);
        String original = "Hello World";
        String encoded = cipher.encodeString(original);
        String decoded = cipher.decodeString(encoded);
        System.out.println("Original: " + original);
        System.out.println("Encoded: " + encoded);
        System.out.println("Decoded: " + decoded);
        System.out.println("Correct: " + original.equals(decoded));
    }
}

