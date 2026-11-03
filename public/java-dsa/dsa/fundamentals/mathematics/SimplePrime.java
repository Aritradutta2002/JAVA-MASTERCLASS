package dsa.fundamentals.mathematics;
import java.lang.*;
import java.util.*;
public class SimplePrime {
    static boolean isPrime(int num){
        if(num == 1)
            return false;
        for(int i=2; i*i<num; i++)
            if(num % i == 0)
                return false;

        return true;
    }
    public static void main(String[] args) {
        int flag=0;
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter a number to check prime or not: ");
        int num = sc.nextInt();
        if(num==1){
            System.out.println(num+" is neither prime nor Composite number");
        }
            if (isPrime(num)) {
                System.out.println(num + " is a prime number.");
            } else {
                System.out.println(num + " is not a prime number");
            }
        }
    }


