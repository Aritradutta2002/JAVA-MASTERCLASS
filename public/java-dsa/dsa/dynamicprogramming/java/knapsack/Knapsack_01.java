package JAVA_DSA.DYNAMIC_PROGRAMMING.java.knapsack;
import java.util.*;

public class Knapsack_01 {
     public static int Knapsack(int[] value, int[] weight, int C, int size) {
        int[][] dp = new int [size + 1] [C + 1];
        for(int i = 1; i < dp.length; i++){
            for(int j = 1; j < dp[0].length; j++){
                dp[i] [j] =  dp[i-1][j];

                if(j >= weight[i-1]){
                    int remainingCapacity = j - weight[i-1];
                    if(dp[i - 1] [remainingCapacity] + value [i-1] > dp[i-1] [j]){
                        dp[i][j] = dp[i - 1][remainingCapacity] + value [i-1];
                    }
                    else{
                        dp[i][j] = dp[i-1][j];
                    }
                }
                else{
                    dp[i][j] = dp[i-1][j];
                }
            }
        }
        return dp[size][C];
    }

    public static void main(String[] args) throws Exception{
       Scanner sc = new Scanner(System.in);
       System.out.println("Enter Capacity of the bag: ");
       int Capacity = sc.nextInt();
       System.out.println("Enter weight_value size of the bag: ");
       int size = sc.nextInt();

       int [] weight = new int [size];
       int [] value = new int [size];

        System.out.println("Enter the weight values");
        for (int i = 0; i < size; i++) {
            weight[i] = sc.nextInt();
        }
        System.out.println("Enter the values");
        for (int i = 0; i < size; i++) {
            value[i] = sc.nextInt();
        }

        int max_profit = Knapsack(value, weight, Capacity, size);
        System.out.println(max_profit);
    }
}
