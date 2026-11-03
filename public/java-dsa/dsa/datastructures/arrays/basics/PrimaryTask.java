package dsa.datastructures.arrays.basics;
import java.util.Scanner;

public class PrimaryTask {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int num = sc.nextInt();
        long[] arr = new long[num];

        // Input the array values
        for (int i = 0; i < num; i++) {
            arr[i] = sc.nextLong();
        }

        // Check each number if it's an important integer
        for (long n : arr) {
            if (isImportantInteger(n)) {
                System.out.println("Yes");
            } else {
                System.out.println("No");
            }
        }
    }

    static boolean isImportantInteger(long n) {
        String str = Long.toString(n);

        // Check if the number starts with "10"
        if (str.startsWith("10")) {
            String remainder = str.substring(2); // Get the part after "10"

            // Check if the remainder has leading zeros or is empty
            if (remainder.isEmpty() || remainder.startsWith("0")) {
                return false;
            }

            // Parse the remainder part as an integer and check if it's greater than 2
            int remainderValue = Integer.parseInt(remainder);
            return remainderValue >= 2;
        }

        return false;
    }
}

