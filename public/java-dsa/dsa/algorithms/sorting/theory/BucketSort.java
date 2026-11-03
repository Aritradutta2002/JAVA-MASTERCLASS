package dsa.algorithms.sorting.theory;

/**
 * BUCKET SORT
 * Average-case linear-time sort for uniformly distributed data
 * Often used for floats in [0,1) or when bucketing by range works
 */

import java.util.*;

public class BucketSort {
    // Bucket sort for floats in [0,1)
    @SuppressWarnings("unchecked")
    public void bucketSort(float[] arr) {
        int n = arr.length;
        List<Float>[] buckets = new List[n];
        for (int i = 0; i < n; i++) buckets[i] = new ArrayList<>();
        for (float v : arr) {
            int idx = Math.min((int)(v * n), n - 1);
            buckets[idx].add(v);
        }
        for (int i = 0; i < n; i++) Collections.sort(buckets[i]);
        int k = 0;
        for (int i = 0; i < n; i++) for (float v : buckets[i]) arr[k++] = v;
    }

    public static void main(String[] args) {
        BucketSort s = new BucketSort();
        float[] a = {0.78f, 0.17f, 0.39f, 0.26f, 0.72f, 0.94f, 0.21f, 0.12f, 0.23f, 0.68f};
        s.bucketSort(a);
        System.out.println(Arrays.toString(a));
    }
}

