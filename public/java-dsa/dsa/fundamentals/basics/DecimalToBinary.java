package dsa.fundamentals.basics;
import java.util.*;
import java.lang.*;
public class DecimalToBinary {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the decimal number: ");
        int decimal = sc.nextInt();

        int power = 1;
        int answer = 0; // return the binary value of the decimal

        while(decimal > 0) {
            int parity = decimal % 2;
            answer += (power * parity);
            power *= 10;
            decimal = decimal / 2;
        }
        System.out.println(answer);
    }
}

