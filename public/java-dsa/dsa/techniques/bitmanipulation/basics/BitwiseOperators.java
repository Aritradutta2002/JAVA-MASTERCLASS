package dsa.techniques.bitmanipulation.basics;
/**
 * Basic Bitwise Operators in Java
 * 
 * This class demonstrates all fundamental bitwise operations
 * Essential foundation for bit manipulation problems
 */
public class BitwiseOperators {
    
    public static void main(String[] args) {
        int a = 12; // 1100 in binary
        int b = 5;  // 0101 in binary
        
        System.out.println("a = " + a + " (binary: " + Integer.toBinaryString(a) + ")");
        System.out.println("b = " + b + " (binary: " + Integer.toBinaryString(b) + ")");
        System.out.println();
        
        // AND operator (&)
        System.out.println("AND Operation:");
        System.out.println("a & b = " + (a & b) + " (binary: " + Integer.toBinaryString(a & b) + ")");
        
        // OR operator (|)
        System.out.println("OR Operation:");
        System.out.println("a | b = " + (a | b) + " (binary: " + Integer.toBinaryString(a | b) + ")");
        
        // XOR operator (^)
        System.out.println("XOR Operation:");
        System.out.println("a ^ b = " + (a ^ b) + " (binary: " + Integer.toBinaryString(a ^ b) + ")");
        
        // NOT operator (~)
        System.out.println("NOT Operation:");
        System.out.println("~a = " + (~a) + " (binary: " + Integer.toBinaryString(~a) + ")");
        
        // Left shift (<<)
        System.out.println("Left Shift:");
        System.out.println("a << 2 = " + (a << 2) + " (binary: " + Integer.toBinaryString(a << 2) + ")");
        
        // Right shift (>>)
        System.out.println("Right Shift:");
        System.out.println("a >> 2 = " + (a >> 2) + " (binary: " + Integer.toBinaryString(a >> 2) + ")");
        
        // Unsigned right shift (>>>)
        System.out.println("Unsigned Right Shift:");
        System.out.println("a >>> 2 = " + (a >>> 2) + " (binary: " + Integer.toBinaryString(a >>> 2) + ")");
    }
}

