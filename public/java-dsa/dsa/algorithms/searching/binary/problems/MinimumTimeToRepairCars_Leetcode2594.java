package dsa.algorithms.searching.binary.problems;
import java.util.Scanner;
/*
*   Author  : Aritra Dutta
*   Created : Sunday, 16.03.2025  05:10 pm
*/
public class MinimumTimeToRepairCars_Leetcode2594 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int[] arr = new int[5];
        for(int i = 0; i < 5; i++){
            arr[i] = sc.nextInt();
        }
        int cars = sc.nextInt();
        System.out.println(repairCars(arr, cars));
    }

    public static long repairCars(int[] ranks, int cars) {
        long start = Long.MAX_VALUE;
        long end = Long.MIN_VALUE;
        for (int rank : ranks){
            end = Math.max(end, rank);
            start = Math.min(start,rank);
        }
        end = end * cars * cars;
        long ans=0;
        while (start <= end) {
            long mid = start + (end - start) / 2;
            if (isCarsRepaired(mid, ranks,cars)){
                ans = mid;
                end=mid-1;
            }
            else {
                start=mid+1;
            }
        }

        return ans;
    }

    public static boolean isCarsRepaired(long time, int ranks[], int carsToBeRepaired){
        long carsRepaired = 0;
        for (int rank : ranks){
            carsRepaired += (long) (Math.sqrt((1.0 * time) / rank));
            if(carsRepaired>=carsToBeRepaired) return true;
        }
        return false;
    }
}

