package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Tuesday, 11.02.2025  12:51 am
*/

// TIME COMPLEXITY =  O(N)
// SPACE COMPLEXITY = O(1)

public class FibonacciSequence {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the number of elements in the sequence: ");
        int n = sc.nextInt();
        System.out.println("The fibonacci sequence is: " + fibonacciSequence(n));
    }

    public static List<Long> fibonacciSequence(int n) {
        List<Long> fibo = new ArrayList<>();
        long a = 0;
        long b = 1;

        for (int i = 0; i < n; i++) {
            fibo.add(a);
            long sum = a + b;
            a = b;
            b = sum;
        }
        return fibo;
    }
}
