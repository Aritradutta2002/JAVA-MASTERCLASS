package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Smallest Rectangle Enclosing Black Pixels (Leetcode 302)
 *   Difficulty: Hard
 *   Companies: Google, Facebook
 */
public class SmallestRectangleBlackPixels_Leetcode302 {
    public static void main(String[] args) {
        char[][] image = {
            {'0', '0', '1', '0'},
            {'0', '1', '1', '0'},
            {'0', '1', '0', '0'}
        };
        int x = 0, y = 2;
        System.out.println("Minimum area: " + minArea(image, x, y));
    }
    
    public static int minArea(char[][] image, int x, int y) {
        int m = image.length, n = image[0].length;
        int left = searchColumns(image, 0, y, 0, m, true);
        int right = searchColumns(image, y + 1, n, 0, m, false);
        int top = searchRows(image, 0, x, left, right, true);
        int bottom = searchRows(image, x + 1, m, left, right, false);
        
        return (right - left) * (bottom - top);
    }
    
    private static int searchColumns(char[][] image, int i, int j, int top, int bottom, boolean opt) {
        while (i != j) {
            int k = top, mid = (i + j) / 2;
            while (k < bottom && image[k][mid] == '0') {
                k++;
            }
            if (k < bottom == opt) {
                j = mid;
            } else {
                i = mid + 1;
            }
        }
        return i;
    }
    
    private static int searchRows(char[][] image, int i, int j, int left, int right, boolean opt) {
        while (i != j) {
            int k = left, mid = (i + j) / 2;
            while (k < right && image[mid][k] == '0') {
                k++;
            }
            if (k < right == opt) {
                j = mid;
            } else {
                i = mid + 1;
            }
        }
        return i;
    }
}

