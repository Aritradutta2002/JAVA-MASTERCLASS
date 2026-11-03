package dsa.algorithms.recursion.problems.easy;

import java.util.Scanner;

public class ReverseNumber {

    static int base(int n) {
        int count = 0;
        while(n > 0) {
            n = n /10;
            count++;
        }
        return count;
    }

    static int reverseNumber(int n) {
       int base = base(n);
        int rem = 0;
        int result= 0;

        while(base > 0) {
            rem = n % 10;
            n = n / 10;
            result +=  rem * Math.pow(10, base - 1);
            base --;
        }
        return result;
    }

    static int result = 0;

    static void reverseNumberRecursive(int n) {
        if(n == 0)
            return;

        int rem  = n % 10;
        System.out.print(rem);
        reverseNumberRecursive(n/10);
    }


    public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    int n = sc.nextInt();
    System.out.println(reverseNumber(n));
    reverseNumberRecursive(n);
    }
}

