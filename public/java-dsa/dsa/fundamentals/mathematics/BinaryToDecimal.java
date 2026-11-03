package dsa.fundamentals.mathematics;
import java.util.Scanner;

public class BinaryToDecimal {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Prompt user to enter a binary number
        System.out.print("Enter a binary number: ");
        String binaryString = sc.nextLine(); // Read binary string
        int decimal = 0; // Initialize decimal result

        // Iterate over each character in the binary string
        for (int i = 0; i < binaryString.length(); i++){
            char c = binaryString.charAt(i);
            int power = binaryString.length() - i - 1;
            decimal += (c - '0') * Math.pow(2, power);
        }
        System.out.println("Decimal value: " + decimal);
    }
}

