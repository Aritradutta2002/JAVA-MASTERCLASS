package dsa.algorithms.recursion.problems.easy;
import java.util.Scanner;

public class SumOfDigits {

    static int sumOfDigits(int n) {
        if( n <= 9){
            return n;
        }
        return (n%10) + sumOfDigits(n/10);
    }
    static int product(int n) {
        if( n <= 9){
            return n;
        }
        return (n%10) * product(n/10);
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.println(sumOfDigits(n));
        System.out.println(product(n));
    }
}

