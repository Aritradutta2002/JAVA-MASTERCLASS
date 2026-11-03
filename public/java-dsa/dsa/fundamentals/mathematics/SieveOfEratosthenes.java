package dsa.fundamentals.mathematics;
import java.lang.*;
import java.util.Arrays;
import java.util.Scanner;
/*
    step1: First make a function of Sieve with parameter num
    step2: make a Boolean Array of size [num + 1]
    step3: Find prime factors and mark them as False suppose, 2*2=4 , 2*3=6..... and 3*2=6 , 3*3=9 ... and 5*2=10, 5*3=15, .... so on.
    step4: print all the true after marking all composites numbers as false.
    step5: End
 */
public class SieveOfEratosthenes {

    public static void SieveArray(int num)
    {
        boolean [] isPrime = new boolean[num + 1];
        Arrays.fill(isPrime,true);
        for(int i=2; i*i<=num; i++){
           if(isPrime[i]){
               // making multiples not prime
               for(int j= i*i; j< isPrime.length; j += i){
                   isPrime[j] = false; // elements which are not prime
               }
           }
        }
        for(int i=2; i< isPrime.length; i++){
            if(isPrime[i])
                System.out.print(i+ " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int num;
        /*
            System.out.println("Enter the starting number: ");
            int low = sc.nextInt();
            System.out.println("Enter the ending number: ");
            int high = sc.nextInt();
         */
        System.out.println("Enter number 2 to X : ");
        num = sc.nextInt();
        if(num<=2)
            System.out.println(num);
        else
            SieveArray(num);

    }
}

