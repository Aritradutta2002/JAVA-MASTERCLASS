package JAVA_DSA.MixedCodes;
import java.util.*;

public class BadDays {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int size = sc.nextInt();
        int arr []  = new int [size];
        for (int i = 0; i < size; i++) {
            arr[i] = sc.nextInt();
        }
        int max1 = -1;
        int max2 = -1;
        int badDaysCount = 0;

        // Iterate over each day's visit count
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] < max2) {
                badDaysCount++; // The current day is a "bad day"
            }

            // Update the max1 and max2 values
            if (arr[i] > max1) {
                max2 = max1;
                max1 = arr[i];
            } else if (arr[i] > max2) {
                max2 = arr[i];
            }
        }

        // Print the count of bad days
        System.out.println(badDaysCount);

    }
}
