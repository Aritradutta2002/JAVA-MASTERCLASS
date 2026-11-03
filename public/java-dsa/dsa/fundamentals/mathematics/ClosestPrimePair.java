package dsa.fundamentals.mathematics;
import java.util.*;
import java.lang.*;

public class ClosestPrimePair {
    public static Vector <Integer> closestPrime(int left, int right)
    {
        Vector<Integer> temp = new Vector<>();
        Vector<Integer> prime = new Vector<>();
        boolean [] isPrime = new boolean[right + 1];
        Arrays.fill(isPrime,true);
        for(int i=2; i*i<= isPrime.length; i++){
            if(isPrime[i]){
                // making multiples not prime
                for(int j= i*i; j< isPrime.length; j = j+i){
                    isPrime[j] = false; // elements which are not prime
                }
            }
        }
        for(int i=2; i< isPrime.length; i++) {
            if (isPrime[i])
                temp.add(i);
        }
        for (int i = 0; i< temp.size();i++){
            if(temp.get(i)>=left)
                prime.add(temp.get(i));
        }


        return prime;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int right, left;
        System.out.print("Enter the left: ");
        left = sc.nextInt();
        System.out.print("Enter the right: ");
        right = sc.nextInt();
        Vector<Integer> list = closestPrime(left,right);

        //System.out.println(list);

        int min = Integer.MAX_VALUE;
        int num1=-1, num2=-1;
        for(int i= 1; i<list.size();i++){
            int diff = list.get(i) - list.get(i-1);
            if(min > diff) {
                min = diff;
                num2 = list.get(i);
                num1 = list.get(i - 1);
            }
        }
        int [] arr = {num1,num2};
        System.out.println("The Closest pair is: "+Arrays.toString(arr));
    }
}

