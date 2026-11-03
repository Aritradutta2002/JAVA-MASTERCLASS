package JAVA_DSA.Contests.CSESProblemSet;
/*
 *   Author  : Aritra Dutta
 *   Created : Tuesday, 11.02.2025 01:05 am
 */
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.abs;
import static java.lang.System.out;
import java.io.*;
import java.util.*;
import java.math.*;

public class FibonacciNumbers {
    public static void main(String[] args) {
        FastScanner fs = new FastScanner();
        PrintWriter out = new PrintWriter(System.out);

        long n = fs.nextLong();
        out.println(fib(n));
        out.close();
    }

    public static Map<Long, Long> f = new HashMap<>();
    static final int MOD = 1_000_000_007;

    public static long inverse(long i) {
        if (i == 1) return 1;
        return (MOD - ((MOD / i) * inverse(MOD % i)) % MOD + MOD) % MOD;
    }

    public static long POW(long a, long b) {
        if (b == 0) return 1;
        if (b == 1) return a % MOD;
        long temp = POW(a, b / 2);
        if (b % 2 == 0) return (temp * temp) % MOD;
        else return (((temp * temp) % MOD) * a) % MOD;
    }

    public static long fib(long n) {
        if (f.containsKey(n)) return f.get(n);
        if (n == 0) return 0;
        if (n == 1 || n == 2) return 1;
        if (n % 2 == 0) {
            long k = n / 2;
            long ret1 = fib(k - 1), ret2 = fib(k);
            f.put(n, ((((2L * ret1) % MOD + ret2) % MOD) * ret2) % MOD);
        } else {
            long k = (n + 1) / 2;
            long ret1 = fib(k - 1), ret2 = fib(k);
            f.put(n, ((ret1 * ret1) % MOD + (ret2 * ret2) % MOD) % MOD);
        }
        return f.get(n);
    }

/*
===================================================================================================================================================================================================================
===================================================================================================================================================================================================================
 */

    public static boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2 || n == 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        long sqrtN = (long) Math.sqrt(n) + 1;
        for (long i = 6L; i <= sqrtN; i += 6) {
            if (n % (i - 1) == 0 || n % (i + 1) == 0) return false;
        }
        return true;
    }

    public static void ruffleSort(int[] arr) {
        Random random = new Random();
        for (int i = 0; i < arr.length; i++) {
            int oi = random.nextInt(arr.length), temp = arr[oi];
            arr[oi] = arr[i];
            arr[i] = temp;
        }
        Arrays.sort(arr);
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
        // for debugging only
        for (int x : arr)
            out.print(x + " ");
        out.println();
    }

    public static long add(long a, long b) {
        return (a + b) % MOD;
    }

    public static long sub(long a, long b) {
        return ((a - b) % MOD + MOD) % MOD;
    }

    public static long mul(long a, long b) {
        return (a * b) % MOD;
    }

    public static long calPow(long base, long exponent) {
        if (exponent == 0) return 1;
        if (exponent == 1) return base % MOD;
        long temp = calPow(base, exponent / 2);
        if (exponent % 2 == 0) return (temp * temp) % MOD;
        else return (((temp * temp) % MOD) * base) % MOD;
    }

    public static long exp(long base, long exp) {
        if (exp == 0) return 1;
        long half = exp(base, exp / 2);
        if (exp % 2 == 0) return mul(half, half);
        return mul(half, mul(half, base));
    }

    static long[] factorials = new long[2_000_001];
    static long[] invFactorials = new long[2_000_001];

    public static void precompFacts() {
        factorials[0] = invFactorials[0] = 1;
        for (int i = 1; i < factorials.length; i++)
            factorials[i] = mul(factorials[i - 1], i);
        invFactorials[factorials.length - 1] = exp(factorials[factorials.length - 1], MOD - 2);
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
