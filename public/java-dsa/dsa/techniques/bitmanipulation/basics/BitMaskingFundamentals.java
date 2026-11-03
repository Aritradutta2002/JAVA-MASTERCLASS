package dsa.techniques.bitmanipulation.basics;
/**
 * Bit Masking Fundamentals - Complete Guide
 * 
 * Masking is one of the most powerful bit manipulation techniques.
 * This file covers all essential masking patterns and applications.
 */
public class BitMaskingFundamentals {
    
    // ======================== BASIC MASKING OPERATIONS ========================
    
    /**
     * 1. CREATE MASKS
     */
    
    // Create mask with first n bits set
    public static int createMaskFirstNBits(int n) {
        return (1 << n) - 1;
    }
    
    // Create mask with only bit at position i set
    public static int createSingleBitMask(int i) {
        return 1 << i;
    }
    
    // Create mask with bits from position i to j set (inclusive)
    public static int createRangeMask(int i, int j) {
        int allOnes = ~0;
        int left = allOnes << (j + 1);
        int right = (1 << i) - 1;
        return ~(left | right);
    }
    
    /**
     * 2. EXTRACT BITS USING MASKS
     */
    
    // Extract bit at position i
    public static int extractBit(int num, int i) {
        return (num >> i) & 1;
    }
    
    // Extract bits from position i to j (inclusive)
    public static int extractBits(int num, int i, int j) {
        int mask = createRangeMask(i, j);
        return (num & mask) >> i;
    }
    
    // Extract even positioned bits (0, 2, 4, ...)
    public static int extractEvenBits(int num) {
        return num & 0x55555555; // 01010101...
    }
    
    // Extract odd positioned bits (1, 3, 5, ...)
    public static int extractOddBits(int num) {
        return num & 0xAAAAAAAA; // 10101010...
    }
    
    /**
     * 3. SET BITS USING MASKS
     */
    
    // Set bits from position i to j
    public static int setBitsRange(int num, int i, int j) {
        int mask = createRangeMask(i, j);
        return num | mask;
    }
    
    // Set multiple bits at once using mask
    public static int setBitsWithMask(int num, int mask) {
        return num | mask;
    }
    
    /**
     * 4. CLEAR BITS USING MASKS
     */
    
    // Clear bits from position i to j
    public static int clearBitsRange(int num, int i, int j) {
        int mask = createRangeMask(i, j);
        return num & ~mask;
    }
    
    // Clear all bits except those in mask
    public static int clearAllExceptMask(int num, int mask) {
        return num & mask;
    }
    
    /**
     * 5. TOGGLE BITS USING MASKS
     */
    
    // Toggle bits in range i to j
    public static int toggleBitsRange(int num, int i, int j) {
        int mask = createRangeMask(i, j);
        return num ^ mask;
    }
    
    // ======================== ADVANCED MASKING TECHNIQUES ========================
    
    /**
     * 6. SUBSET GENERATION USING MASKS
     */
    public static void generateAllSubsets(int[] arr) {
        int n = arr.length;
        
        // Generate all possible masks from 0 to 2^n - 1
        for (int mask = 0; mask < (1 << n); mask++) {
            System.out.print("Subset: {");
            
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    System.out.print(arr[i] + " ");
                }
            }
            
            System.out.println("}");
        }
    }
    
    /**
     * 7. ITERATE THROUGH ALL SUBSETS OF A MASK
     */
    public static void iterateSubsetsOfMask(int mask) {
        System.out.println("All subsets of mask " + Integer.toBinaryString(mask) + ":");
        
        for (int subset = mask; subset > 0; subset = (subset - 1) & mask) {
            System.out.println("  " + Integer.toBinaryString(subset));
        }
        System.out.println("  0"); // Empty subset
    }
    
    /**
     * 8. CHECK MASK PROPERTIES
     */
    
    // Check if mask has consecutive 1s
    public static boolean hasConsecutiveOnes(int mask) {
        // Add mask to itself shifted left by 1
        // If consecutive 1s exist, there will be no carry conflicts
        return ((mask + (mask << 1)) & mask) == 0;
    }
    
    // Count number of set bits in mask (population count)
    public static int popCount(int mask) {
        int count = 0;
        while (mask > 0) {
            count++;
            mask &= (mask - 1); // Clear rightmost set bit
        }
        return count;
    }
    
    // Find rightmost set bit position
    public static int rightmostSetBit(int mask) {
        return mask & (-mask);
    }
    
    // Find leftmost set bit position
    public static int leftmostSetBit(int mask) {
        mask |= mask >> 1;
        mask |= mask >> 2;
        mask |= mask >> 4;
        mask |= mask >> 8;
        mask |= mask >> 16;
        return mask - (mask >> 1);
    }
    
    /**
     * 9. PRACTICAL MASKING APPLICATIONS
     */
    
    // Permission system using bit masks
    public static class PermissionSystem {
        public static final int READ = 1 << 0;    // 001
        public static final int WRITE = 1 << 1;   // 010
        public static final int EXECUTE = 1 << 2; // 100
        
        public static boolean hasPermission(int userPermissions, int requiredPermission) {
            return (userPermissions & requiredPermission) != 0;
        }
        
        public static int grantPermission(int userPermissions, int newPermission) {
            return userPermissions | newPermission;
        }
        
        public static int revokePermission(int userPermissions, int permission) {
            return userPermissions & ~permission;
        }
    }
    
    // Color manipulation using bit masks
    public static class ColorMasking {
        // RGB color: RRRRRRRRGGGGGGGGBBBBBBBB (24 bits)
        
        public static int extractRed(int color) {
            return (color >> 16) & 0xFF;
        }
        
        public static int extractGreen(int color) {
            return (color >> 8) & 0xFF;
        }
        
        public static int extractBlue(int color) {
            return color & 0xFF;
        }
        
        public static int createColor(int red, int green, int blue) {
            return (red << 16) | (green << 8) | blue;
        }
        
        public static int setRed(int color, int newRed) {
            return (color & 0x00FFFF) | (newRed << 16);
        }
        
        public static int setGreen(int color, int newGreen) {
            return (color & 0xFF00FF) | (newGreen << 8);
        }
        
        public static int setBlue(int color, int newBlue) {
            return (color & 0xFFFF00) | newBlue;
        }
    }
    
    // Game state management using bit masks
    public static class GameState {
        // Game flags
        public static final int HAS_KEY = 1 << 0;
        public static final int DOOR_OPEN = 1 << 1;
        public static final int BOSS_DEFEATED = 1 << 2;
        public static final int TREASURE_FOUND = 1 << 3;
        public static final int LEVEL_COMPLETE = 1 << 4;
        
        private int state = 0;
        
        public void setState(int flag) {
            state |= flag;
        }
        
        public void clearState(int flag) {
            state &= ~flag;
        }
        
        public boolean hasState(int flag) {
            return (state & flag) != 0;
        }
        
        public boolean hasAllStates(int flags) {
            return (state & flags) == flags;
        }
        
        public boolean hasAnyState(int flags) {
            return (state & flags) != 0;
        }
        
        public void printState() {
            System.out.println("Game State: " + Integer.toBinaryString(state));
            System.out.println("  Has Key: " + hasState(HAS_KEY));
            System.out.println("  Door Open: " + hasState(DOOR_OPEN));
            System.out.println("  Boss Defeated: " + hasState(BOSS_DEFEATED));
            System.out.println("  Treasure Found: " + hasState(TREASURE_FOUND));
            System.out.println("  Level Complete: " + hasState(LEVEL_COMPLETE));
        }
    }
    
    // ======================== DEMONSTRATION METHODS ========================
    
    public static void demonstrateMaskCreation() {
        System.out.println("=== MASK CREATION ===");
        
        System.out.println("First 5 bits mask: " + Integer.toBinaryString(createMaskFirstNBits(5)));
        System.out.println("Single bit mask (position 3): " + Integer.toBinaryString(createSingleBitMask(3)));
        System.out.println("Range mask (bits 2-5): " + Integer.toBinaryString(createRangeMask(2, 5)));
    }
    
    public static void demonstrateBitExtraction() {
        System.out.println("\n=== BIT EXTRACTION ===");
        int num = 0b11010110; // 214
        
        System.out.println("Original number: " + Integer.toBinaryString(num));
        System.out.println("Bit at position 3: " + extractBit(num, 3));
        System.out.println("Bits 2-5: " + Integer.toBinaryString(extractBits(num, 2, 5)));
        System.out.println("Even bits: " + Integer.toBinaryString(extractEvenBits(num)));
        System.out.println("Odd bits: " + Integer.toBinaryString(extractOddBits(num)));
    }
    
    public static void demonstrateSubsetGeneration() {
        System.out.println("\n=== SUBSET GENERATION ===");
        int[] arr = {1, 2, 3};
        generateAllSubsets(arr);
    }
    
    public static void demonstratePermissionSystem() {
        System.out.println("\n=== PERMISSION SYSTEM ===");
        
        int userPerms = PermissionSystem.READ | PermissionSystem.WRITE;
        System.out.println("User permissions: " + Integer.toBinaryString(userPerms));
        System.out.println("Has READ: " + PermissionSystem.hasPermission(userPerms, PermissionSystem.READ));
        System.out.println("Has execute: " + PermissionSystem.hasPermission(userPerms, PermissionSystem.EXECUTE));
        
        userPerms = PermissionSystem.grantPermission(userPerms, PermissionSystem.EXECUTE);
        System.out.println("After granting execute: " + Integer.toBinaryString(userPerms));
    }
    
    public static void demonstrateColorMasking() {
        System.out.println("\n=== COLOR MASKING ===");
        
        int color = ColorMasking.createColor(255, 128, 64); // Red=255, Green=128, Blue=64
        System.out.println("Color RGB: " + Integer.toHexString(color));
        System.out.println("Red component: " + ColorMasking.extractRed(color));
        System.out.println("Green component: " + ColorMasking.extractGreen(color));
        System.out.println("Blue component: " + ColorMasking.extractBlue(color));
        
        int newColor = ColorMasking.setGreen(color, 200);
        System.out.println("After changing green to 200: " + Integer.toHexString(newColor));
    }
    
    public static void demonstrateGameState() {
        System.out.println("\n=== GAME STATE MANAGEMENT ===");
        
        GameState game = new GameState();
        game.setState(GameState.HAS_KEY);
        game.setState(GameState.DOOR_OPEN);
        
        game.printState();
        
        System.out.println("Can complete level: " + 
            game.hasAllStates(GameState.BOSS_DEFEATED | GameState.TREASURE_FOUND));
    }
    
    // ======================== MAIN METHOD ========================
    
    public static void main(String[] args) {
        System.out.println("BIT MASKING FUNDAMENTALS GUIDE");
        System.out.println("==============================");
        
        demonstrateMaskCreation();
        demonstrateBitExtraction();
        demonstrateSubsetGeneration();
        demonstratePermissionSystem();
        demonstrateColorMasking();
        demonstrateGameState();
        
        // Advanced demonstrations
        System.out.println("\n=== ADVANCED MASKING ===");
        
        // Iterate subsets of a mask
        int mask = 0b1011; // 11 in decimal
        iterateSubsetsOfMask(mask);
        
        // Mask properties
        System.out.println("\nMask properties:");
        System.out.println("Mask " + Integer.toBinaryString(mask) + " pop count: " + popCount(mask));
        System.out.println("Rightmost set bit: " + Integer.toBinaryString(rightmostSetBit(mask)));
        System.out.println("Leftmost set bit: " + Integer.toBinaryString(leftmostSetBit(mask)));
        
        System.out.println("\n=== MASKING OPERATIONS SUMMARY ===");
        System.out.println("✓ Create masks for any bit pattern");
        System.out.println("✓ Extract specific bits or bit ranges");
        System.out.println("✓ Set, clear, toggle bits using masks");
        System.out.println("✓ Generate and iterate through subsets");
        System.out.println("✓ Permission and flag management");
        System.out.println("✓ Color and data manipulation");
        System.out.println("✓ Game state and configuration management");
    }
}

