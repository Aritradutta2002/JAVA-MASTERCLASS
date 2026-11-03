package dsa.fundamentals.mathematics;
import java.util.Scanner;

public class SumOfTheDivisors {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int sum = 0;

        for (int i = 1; i * i <= n; i++) {
            if (n % i == 0) {
                sum += i; // Add i if it's a divisor
                if (i != n / i) {
                    sum += n / i; // Add the corresponding pair divisor n/i
                }
            }
        }
        System.out.println(sum);
    }
}

