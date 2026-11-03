package dsa.datastructures.arrays.basics;
import java.util.*;
import java.io.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Saturday, 01.02.2025  12:33 pm
 */
public class RotateMatrix {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[][] matrix = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix[i][j] = sc.nextInt();
            }
        }
        transposeMatrix(matrix);
        reverseMatrix(matrix);
        printMatrix(matrix);
    }

    public static void transposeMatrix(int[][] matrix) {
        int n = matrix.length;

        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                swap(matrix, i, j, j, i);
            }
        }
    }

    public static void reverseMatrix(int[][] matrix) {
        int n = matrix.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < (n/2); j++) {
                swap(matrix, i, j, i, n - 1 - j);
            }
        }
    }

    public static void swap(int[][] arr, int i, int j, int x, int y) {
        int temp = arr[i][j];
        arr[i][j] = arr[x][y];
        arr[x][y] = temp;
    }

    public static void printMatrix(int[][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }

}

