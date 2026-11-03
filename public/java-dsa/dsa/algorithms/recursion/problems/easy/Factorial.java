package dsa.algorithms.recursion.problems.easy;
import java.util.Scanner;
public class Factorial {


    static int factorial(int n) {
        if(n==1){
            return 1;
        }
        return n*factorial(n-1);
    }

    static int factorialMemoization(int n) {
        int res [] = new int [n + 1];
        res[0] = 0;
        res[1] = 1;
        for (int i = 2; i <= n; i++) {
            res[i] = res[i-1] * i;
        }
        return res[n];
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.println(factorialMemoization(n));
        System.out.println(factorial(n));

    }
}

