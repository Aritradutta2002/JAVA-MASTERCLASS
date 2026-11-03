package dsa.fundamentals.mathematics;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Friday, 07.03.2025  09:39 am
*/
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;
import static java.lang.System.out;
import java.io.*;
import java.util.*;
import java.math.*;
public class Closest_Prime_Numbers_In_Range_Leetcode2523 {

    public static final Random random = new Random();
    public static final int mod = 1_000_000_007;

    public static void main(String[] args) throws Exception{
        FastScanner fs = new FastScanner();
        PrintWriter out = new PrintWriter(System.out);
        int left =  fs.nextInt();
        int right = fs.nextInt();

        System.out.println(Arrays.toString(closestPrimes(left, right)));

        out.close();
    }

    public static int[] closestPrimes(int left, int right) {
        int[] res = {-1, -1};
        int minDiff = Integer.MAX_VALUE;
        int prevPrime = -1;

        boolean[] isPrime = sieveOfEratosthenes(right);

        for (int i = left; i <= right; i++) {
            if (isPrime[i]) {
                if (prevPrime != -1) {
                    int currDiff = i - prevPrime;
                    if (currDiff < minDiff) {
                        minDiff = currDiff;
                        res[0] = prevPrime;
                        res[1] = i;
                    }
                }
                prevPrime = i;
            }
        }
        return res;
    }

    private static boolean[] sieveOfEratosthenes(int n) {
        boolean[] isPrime = new boolean[n + 1];
        Arrays.fill(isPrime, true);
        isPrime[0] = isPrime[1] = false;

        for (int i = 2; i * i <= n; i++) {
            if (isPrime[i]) {
                for (int j = i * i; j <= n; j += i) {
                    isPrime[j] = false;
                }
            }
        }
        return isPrime;
    }



/*=================================================================================================================================================
================================================================================================================================================= */


    /* ----------- BFS (Recursive) -------------- */

    static void bfsRecursive(int start, List<List<Integer>> adj) {
        Queue<Integer> q = new ArrayDeque<>();
        boolean[] visited = new boolean[adj.size()];

        q.add(start);
        visited[start] = true;

        while (!q.isEmpty()) {
            int u = q.poll();
            out.print(u + " "); // Process node u

            for (int v : adj.get(u)) {
                if (!visited[v]) {
                    visited[v] = true;
                    q.add(v);
                }
            }
        }
    }

    /* ------ Optimized BFS ------ */

    static void bfsOptimized(int start, List<List<Integer>> adj) {
        ArrayDeque<Integer> q = new ArrayDeque<>();
        BitSet visited = new BitSet(adj.size());
        q.add(start);
        visited.set(start);

        while (!q.isEmpty()) {
            int u = q.poll();
            // Process node u here (if needed)

            for (int v : adj.get(u)) {
                if (!visited.get(v)) {
                    visited.set(v);
                    q.add(v);
                }
            }
        }
    }

    /* ----- DFS (Recursive) ------- */

    static void dfsRecursive(int u, List<List<Integer>> adj, boolean[] visited) {
        visited[u] = true;
        out.print(u + " "); // Process node u

        for (int v : adj.get(u)) {
            if (!visited[v]) {
                dfsRecursive(v, adj, visited);
            }
        }
    }

    /* --------- Optimized DFS (Iterative) no recursion overhead ------- */
    static void dfsOptimized(int start, List<List<Integer>> adj) {
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        BitSet visited = new BitSet(adj.size());
        stack.push(start);
        visited.set(start);

        while (!stack.isEmpty()) {
            int u = stack.pop();
            // Process node u here (if needed)

            for (int v : adj.get(u)) {
                if (!visited.get(v)) {
                    visited.set(v);
                    stack.push(v);
                }
            }
        }
    }

    /* -------- Graph Input Helper ----------- */

    static List<Integer>[] createGraph(int nodes, int edges, FastScanner fs) {
        List<Integer>[] adj = new ArrayList[nodes];
        for (int i = 0; i < nodes; i++) adj[i] = new ArrayList<>();
        for (int i = 0; i < edges; i++) {
            int u = fs.nextInt() - 1, v = fs.nextInt() - 1;
            adj[u].add(v);
            adj[v].add(u); // Remove if directed graph
        }
        return adj;
    }

    /*------ Dijkstra's Algorithm -----*/

    static int[] dijkstra(int src, List<int[]>[] adj) {
        int n = adj.length;
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[src] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.add(new int[]{src, 0});

        while (!pq.isEmpty()) {
            int[] node = pq.poll();
            int u = node[0], d = node[1];
            if (d > dist[u]) continue;

            for (int[] edge : adj[u]) {
                int v = edge[0], weight = edge[1];
                if (dist[u] + weight < dist[v]) {
                    dist[v] = dist[u] + weight;
                    pq.add(new int[]{v, dist[v]});
                }
            }
        }
        return dist;
    }

    /* ============================== Math Utilities ======================================= */

    public static boolean isPrime(long n) {
        if(n < 2) return false;
        if(n == 2 || n == 3) return true;
        if(n%2 == 0 || n%3 == 0) return false;
        long sqrtN = (long)Math.sqrt(n)+1;
        for(long i = 6L; i <= sqrtN; i += 6) {
            if(n%(i-1) == 0 || n%(i+1) == 0) return false;
        }
        return true;
    }

    /* ------- Sieve Of Eratosthenes ---------------- */

    public static List<Integer> sieve(int n) {
        List<Integer> primes = new ArrayList<>();
        boolean[] isPrime = new boolean[n + 1];
        Arrays.fill(isPrime, true);
        isPrime[0] = isPrime[1] = false;

        for (int i = 2; i <= n; i++) {
            if (isPrime[i]) {
                primes.add(i);
                for (int j = 2 * i; j <= n; j += i) {
                    isPrime[j] = false;
                }
            }
        }
        return primes;
    }

    /* ------ Modular Exponentiation (x^y % mod) ------- */

    static long modPow(long x, long y, long mod) {
        long res = 1;
        while (y > 0) {
            if ((y & 1) == 1) res = (res * x) % mod;
            x = (x * x) % mod;
            y >>= 1;
        }
        return res;
    }


    static void ruffleSort(int[] a) {
        int n = a.length;// shuffle, then sort
        for (int i = 0; i < n; i++) {
            int oi = random.nextInt(n), temp = a[oi];
            a[oi] = a[i];
            a[i] = temp;
        }
        Arrays.sort(a);
    }

    public static long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public static void print(int[] arr) {
        //for debugging only
        for (int x : arr)
            out.print(x + " ");
        out.println();
    }

    public static long add(long a, long b) {
        return (a + b) % mod;
    }

    public static long sub(long a, long b) {
        return ((a - b) % mod + mod) % mod;
    }

    static long mul(long a, long b) {
        return (a * b) % mod;
    }
    static long calPow(long base, long exponent) {
        if (exponent == 0) {
            return 1;
        }
        if (exponent == 1) {
            return base % mod;
        }
        long temp = calPow(base, exponent / 2);

        if (exponent % 2 == 0) {
            return (temp * temp) % mod;
        } else {
            return (((temp * temp) % mod) * base) % mod;
        }
    }


    public static long exp(long base, long exp) {
        if (exp == 0)
            return 1;
        long half = exp(base, exp / 2);
        if (exp % 2 == 0)
            return mul(half, half);
        return mul(half, mul(half, base));
    }

    static long[] factorials = new long[2_000_001];
    static long[] invFactorials = new long[2_000_001];

    public static void precompFacts() {
        factorials[0] = invFactorials[0] = 1;
        for (int i = 1; i < factorials.length; i++)
            factorials[i] = mul(factorials[i - 1], i);
        invFactorials[factorials.length - 1] = exp(factorials[factorials.length - 1], mod - 2);
        for (int i = invFactorials.length - 2; i >= 0; i--)
            invFactorials[i] = mul(invFactorials[i + 1], i + 1);
    }

    public static long nCk(int n, int k) {
        return mul(factorials[n], mul(invFactorials[k], invFactorials[n - k]));
    }

    public static void sort(int[] a) {
        ArrayList<Integer> l = new ArrayList<>();
        for (int i : a)
            l.add(i);
        Collections.sort(l);
        for (int i = 0; i < a.length; i++)
            a[i] = l.get(i);
    }

    public static class FastScanner {
        private int BS = 1 << 16;
        private char NC = (char) 0;
        private byte[] buf = new byte[BS];
        private int bId = 0, size = 0;
        private char c = NC;
        private double cnt = 1;
        private BufferedInputStream in;

        public FastScanner() {
            in = new BufferedInputStream(System.in, BS);
        }

        public FastScanner(String s) {
            try {
                in = new BufferedInputStream(new FileInputStream(new File(s)), BS);
            } catch (Exception e) {
                in = new BufferedInputStream(System.in, BS);
            }
        }

        private char getChar() {
            while (bId == size) {
                try {
                    size = in.read(buf);
                } catch (Exception e) {
                    return NC;
                }
                if (size == -1) return NC;
                bId = 0;
            }
            return (char) buf[bId++];
        }

        public int nextInt() {
            return (int) nextLong();
        }

        public int[] nextInts(int N) {
            int[] res = new int[N];
            for (int i = 0; i < N; i++) {
                res[i] = (int) nextLong();
            }
            return res;
        }

        public long[] nextLongs(int N) {
            long[] res = new long[N];
            for (int i = 0; i < N; i++) {
                res[i] = nextLong();
            }
            return res;
        }

        public long nextLong() {
            cnt = 1;
            boolean neg = false;
            if (c == NC) c = getChar();
            for (; (c < '0' || c > '9'); c = getChar()) {
                if (c == '-') neg = true;
            }
            long res = 0;
            for (; c >= '0' && c <= '9'; c = getChar()) {
                res = (res << 3) + (res << 1) + c - '0';
                cnt *= 10;
            }
            return neg ? -res : res;
        }

        public double nextDouble() {
            double cur = nextLong();
            return c != '.' ? cur : cur + nextLong() / cnt;
        }

        public double[] nextDoubles(int N) {
            double[] res = new double[N];
            for (int i = 0; i < N; i++) {
                res[i] = nextDouble();
            }
            return res;
        }

        public String next() {
            StringBuilder res = new StringBuilder();
            while (c <= 32) c = getChar();
            while (c > 32) {
                res.append(c);
                c = getChar();
            }
            return res.toString();
        }

        public String nextLine() {
            StringBuilder res = new StringBuilder();
            while (c <= 32) c = getChar();
            while (c != '\n') {
                res.append(c);
                c = getChar();
            }
            return res.toString();
        }

        public boolean hasNext() {
            if (c > 32) return true;
            while (true) {
                c = getChar();
                if (c == NC) return false;
                else if (c > 32) return true;
            }
        }
    }
}

