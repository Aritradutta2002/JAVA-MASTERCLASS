package dsa.algorithms.recursion.problems.medium;

public class GCDRecursive {
    static int gcd(int a, int b) {
      if(b == 0) return a;
      return gcd(b, a % b);
    }

    static int Recursive_euclidean_algorithm(int num1, int num2) {
        if (num1 < 0 || num2 < 0) {
            return -1; // return an error value for negative inputs
        }
        if (num2 == 0) {
            return num1;
        } else {
            return euclidean_algorithm(num2, num1 % num2);
        }
    }

    static int euclidean_algorithm(int num1, int num2){

        while(num1 > 0 && num2 > 0){

            if(num1 < num2) num2 = num2 % num1;

            else num1 = num1 % num2;
        }

        if(num1 == 0) return num2;

        return num1;
    }
    public static void main(String[] args) {

        int num1 = 25;
        int num2 = 35;
        System.out.println(gcd(num1, num2));
        System.out.println(euclidean_algorithm(num1, num2));
        System.out.println(Recursive_euclidean_algorithm(num1, num2));
    }
}

