package dsa.fundamentals.basics;

import java.util.Scanner;
public class BinaryToDecimal{
    public static void main(String[] args) {
        System.out.println("Enter Binary Number");
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        String str = String.valueOf(n);
        int length = str.length();
        int[] arr = new int[length];

        // Initialize the array with powers of 2 in reverse order
        for (int i = 0; i < length; i++) {
            arr[length - 1 - i] = (int) Math.pow(2, i);
        }

        int decimal = 0;
        for (int i = 0; i < length; i++) {
            decimal += Character.getNumericValue(str.charAt(i)) * arr[i];
        }
        System.out.println(decimal);
    }
}

