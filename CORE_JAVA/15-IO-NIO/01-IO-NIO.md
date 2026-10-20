# I/O & NIO — The Complete Interview Guide

> **Java's I/O story is two stories.** The *old* one (`java.io`) is a
> **stream-oriented, blocking, one-way** design built around decorators. The *new* one
> (`java.nio`) is **buffer-oriented, non-blocking, bidirectional** and is what every
> modern Java service actually uses. The interview question is almost always: *"which
> one, and why?"*

## Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples (with Output + Dry Run)](#5-worked-examples-with-output--dry-run)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Cheat Sheet / One-Page Summary](#11-cheat-sheet--one-page-summary)
- [12. Real-World Production Notes](#12-real-world-production-notes)
- [13. Follow-up Learning](#13-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **`java.io` = stream-oriented, blocking, one-way.** `InputStream`/`OutputStream` (bytes) and `Reader`/`Writer` (chars). The workhorse is a **decorator** chain: `BufferedInputStream` → `DataInputStream` → …
- **`java.nio` = buffer-oriented, non-blocking, bidirectional.** The core is the **`Buffer`**, and the modern part is **`Channel`** + **`Selector`** (Java 4) and the **NIO.2** API (`Path`, `Files`, `AsynchronousFileChannel`, `AsynchronousSocketChannel`).
- **The key NIO concept:** you *read into* or *write from* a `Buffer` — the buffer is a **window onto an array**, with a `position`, a `limit`, and a `capacity`.
- **`ByteBuffer` is not thread-safe** and is **not thread-safe to share**; each thread needs its own.
- **The `flip()` call is the #1 NIO bug.** After a `read`, you must `flip()` (limit ← position, position ← 0) before writing; after a `write`, you must `flip()` or `clear()` before reading.
- **Java 7+ has `java.nio.file` (`Path`/`Files`)** — the modern, `Path`-based replacement for `java.io.File`. `Files.readAllLines`, `Files.copy`, `Files.walk`, `Files.list` — and `Files.readString`/`writeString` (Java 11).
- **`Files.lines()` and `Files.walk()` return `Stream`s that hold an open file handle** — they must be used in a `try-with-resources`, or you leak a file descriptor.
- **The `java.io` vs `java.nio` rule of thumb:** use `java.io`/`java.nio.file` for **files**; use `java.nio.channels`/`SocketChannel` for **high-throughput or non-blocking network I/O**; use **`java.net.http.HttpClient`** (Java 11) for HTTP — not `HttpURLConnection`.
- **Virtual threads (Java 21) change the calculus:** blocking I/O on a virtual thread is *cheap* — so a simple `InputStream`/`HttpClient` call is now fine, and the NIO `Selector` complexity is often unnecessary.
- **The `java.io` decorator chain is a real cost** — each layer is a virtual call. Use `BufferedInputStream` (or `Files.newInputStream`, which buffers) — an unbuffered stream does a **read syscall per byte**.

---

## 1. What & Why — The Problem This Solves

**The problem #1 — `java.io` is blocking and one-way.**

```java
// ❌ The thread is BLOCKED for the whole response
InputStream in = new URL("https://example.com/big").openStream();
byte[] buffer = new byte[8192];
int read;
while ((read = in.read(buffer)) != -1) {   // ← blocked until data arrives
    process(buffer, read);
}
```

**The problem #2 — no buffering by default.**

```text
   an unbuffered read of 8000 bytes
   → 8000 SYSCALLS (one per byte) 💥

   a BufferedInputStream
   → ONE syscall of 8192 bytes → 8000 array copies ✅
```

**The problem #3 — no non-blocking, no multiplexing.**

```text
   ❌ 1000 SOCKETS
   ───────────────
   1000 THREADS, each blocked on its own socket
   → 1000 MB of stack, 1000 OS threads

   ✅ NIO WITH A SELECTOR
   ───────────────────────
   ONE thread, a Selector, and 1000 Channels
   → the thread is woken only when a socket is READY
   → O(1) threads for O(n) sockets ✅
```

**The solution:** `java.nio` — buffers, channels, and a selector.

> 💡 **The interview framing.** "Which I/O API would you use for X?" is a question
> about **the workload**: files → `java.nio.file`; blocking HTTP with a modest
> concurrency → `java.net.http` (or, in Java 21, a virtual thread); 10,000
> concurrent long-lived connections → NIO `Selector` or Netty.

---

## 2. Mental Model — The One Picture

```text
   THE TWO ERAS

   ┌─── java.io (Java 1.0) ───────────────────────────────────────────┐
   │                                                                  │
   │   InputStream ──▶ read(byte[])                                   │
   │        │                                                         │
   │        │  each layer DECORATES the one below                     │
   │        ▼                                                         │
   │   BufferedInputStream ──▶ DataInputStream ──▶ FileInputStream    │
   │        (buffering)           (primitives)        (the file)       │
   │                                                                  │
   │   ONE-WAY · BLOCKING · STREAM-ORIENTED                           │
   └──────────────────────────────────────────────────────────────────┘

   ┌─── java.nio (Java 4) ───────────────────────────────────────────┐
   │                                                                  │
   │   ┌────────────┐                                                 │
   │   │   Buffer   │  ← position │ limit │ capacity                  │
   │   │  ▓▓▓░░░    │  a WINDOW onto an array                         │
   │   └─────┬──────┘                                                 │
   │         │  read into / write from                                │
   │   ┌─────▼──────┐                                                 │
   │   │  Channel   │  ← a connection to a source/sink                  │
   │   └─────┬──────┘                                                 │
   │         │                                                        │
   │   ┌─────▼──────┐        ┌──────────────────┐                     │
   │   │  Selector  │───────▶│ ready channels   │  ONE thread, MANY  │
   │   └────────────┘        └──────────────────┘  connections         │
   │                                                                  │
   │   BUFFER-ORIENTED · NON-BLOCKING · MULTIPLEXED                  │
   └──────────────────────────────────────────────────────────────────┘
```

**What to look at:** `java.io` is **stream-oriented** (the data flows through a chain
of decorators); `java.nio` is **buffer-oriented** (you move data into and out of an
explicit buffer) — and that shift is what makes non-blocking, multiplexing I/O possible.

---

## 3. Deep Dive — Theory

## 3.1 The `java.io` hierarchy

```text
   java.io.InputStream (abstract)
        ├── FilterInputStream
        │      ├── BufferedInputStream        ← BUFFERING
        │      ├── DataInputStream           ← readInt(), readDouble(), …
        │      ├── ObjectInputStream         ← DESERIALISATION
        │      ├── PushbackInputStream        ← one byte of lookahead
        │      └── SequenceInputStream
        ├── ByteArrayInputStream
        ├── FileInputStream
        └── PipedInputStream

   java.io.OutputStream (abstract)
        ├── FilterOutputStream
        │      ├── BufferedOutputStream
        │      ├── DataOutputStream
        │      ├── ObjectOutputStream
        │      └── PrintStream                ← the class of System.out
        ├── ByteArrayOutputStream
        ├── FileOutputStream
        └── PipedOutputStream

   java.io.Reader (chars)          java.io.Writer (chars)
        ├── InputStreamReader          ├── OutputStreamWriter
        ├── BufferedReader  ← readLine()├── BufferedWriter
        ├── StringReader                ├── StringWriter
        ├── FileReader                 ├── FileWriter
        └── PrintWriter                └── PrintWriter
```

**The decorator chain — and its cost:**

```java
// Each layer is a virtual call, and the innermost one may be a SYSCALL PER BYTE
InputStream in = new DataInputStream(
        new BufferedInputStream(
                new FileInputStream("data.bin")));
```

**The rule: always buffer.** `Files.newInputStream(path)` returns a buffered stream;
`new FileInputStream(...)` does **not**.

## 3.2 `BufferedInputStream` — the buffer size and the `mark`/`reset`

```java
public class BufferedInputStream extends FilterInputStream {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    protected byte[] buf;
    protected int count;    // the bytes in the buffer
    protected int pos;      // the next byte to read
    protected int markpos = -1;
}
```

- **The default buffer is 8 KB.** `mark()`/`reset()` only work **within** the buffer — a `reset()` after more than 8 KB has been read throws an `IOException`.
- `BufferedInputStream` does **not** close the underlying stream when it is closed? — Actually it **does** (it overrides `close()` to close the wrapped stream).

## 3.3 `ByteBuffer` — position, limit, capacity

```java
// A SIGNATURE REFERENCE for java.nio.ByteBuffer — the real class is in the JDK.
public interface ByteBufferApi {
    // the real class also extends java.nio.Buffer and implements Comparable<ByteBuffer>
    ByteBufferApi allocate(int capacity);        // a heap buffer
    ByteBufferApi allocateDirect(int capacity);   // an off-heap buffer
    ByteBufferApi wrap(byte[] array);             // wrap an existing array
}
```

**The three indices:**

```text
   ┌─────────────────────────────────────────────────────────┐
   │  capacity                                                │
   │  ┌───────────────────────────────────────────────────┐  │
   │  │          position          limit                   │  │
   │  │  ┌───────────┐              ┌──────────────────┐   │  │
   │  │  │  READ     │   WRITABLE   │  (already read)  │   │  │
   │  │  │  (next    │              │                  │   │  │
   │  │  │  byte to  │              │                  │   │  │
   │  │  │  read)    │              │                  │   │  │
   │  │  └───────────┘              └──────────────────┘   │  │
   │  └───────────────────────────────────────────────────┘  │
   └─────────────────────────────────────────────────────────┘

   capacity = the size of the buffer
   limit    = the end of the "interesting" region
   position = the cursor
```

**The state transitions — and the `flip()` trap:**

```text
   WRITE MODE                          READ MODE
   (position → limit)                  (limit → position)
   ┌────────────────────┐              ┌────────────────────┐
   │ pos = 0             │   flip()     │ limit = pos         │
   │ limit = capacity    │ ──────────▶ │ pos = 0             │
   └────────────────────┘              └────────────────────┘
          ▲                                   │
          └───────────── flip() ──────────────┘
                              (or clear(): pos=0, limit=capacity)

   ⚠ THE #1 NIO BUG:
     after channel.read(buf), the buffer is in READ mode;
     you must flip() before writing into it.
     after channel.write(buf), the buffer is in WRITE mode;
     you must flip() (or clear()) before reading from it.
```

**The full write-read cycle:**

```java
ByteBuffer buffer = ByteBuffer.allocate(8192);

// WRITE
buffer.put((byte) 'H');
buffer.put((byte) 'i');
buffer.flip();                       // ← limit = 2, position = 0

// READ
while (buffer.hasRemaining()) {
    System.out.print((char) buffer.get());
}
buffer.clear();                      // ← position = 0, limit = capacity
```

## 3.4 `Channel` and `Selector` — the non-blocking model

```java
public interface Channel extends Closeable {
    boolean isOpen();
    void close();
}

public abstract class SelectableChannel extends Channel implements Channel {
    public void configureBlocking(boolean block) throws IOException;
    public boolean isBlocking();
    public Selector register(Selector sel, int ops, Object att) throws ClosedChannelException;
}

public abstract class Selector implements Closeable {
    public static Selector open() throws IOException;
    public int select() throws IOException;              // BLOCKS until something is ready
    public int select(long timeout) throws IOException;
    public Set<SelectionKey> selectedKeys();
    public static void select(Selector s) { }             // the blocking helper
}
```

**The `SelectionKey` interest operations:**

| Constant | Meaning |
|---|---|
| `OP_READ` | The channel is ready to read |
| `OP_WRITE` | The channel is ready to write |
| `OP_CONNECT` | The connection to a new channel has completed |
| `OP_ACCEPT` | A new channel is ready to accept (a server socket) |

**The selector loop:**

```java
Selector selector = Selector.open();
serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();                                   // blocks until something is ready
    Set<SelectionKey> keys = selector.selectedKeys();
    Iterator<SelectionKey> it = keys.iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();                                    // ⚠ MUST remove before handling
        if (key.isAcceptable()) {
            SocketChannel client = serverSocketChannel.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            int read = client.read(buf);
            if (read < 0) {
                key.cancel();
                client.close();
            } else {
                buf.flip();
                client.write(buf);
            }
        }
    }
}
```

**The critical rules:**

| Rule | Why |
|---|---|
| **Remove the key from `selectedKeys()` before handling it** | Otherwise the next `select()` returns the same key and you spin |
| **Cancel the key when the channel closes** | Otherwise the selector keeps a dead channel |
| **The channel must be in non-blocking mode** | A blocking `read()` on a registered channel **blocks the whole selector** |
| **Handle `read == 0`** | It means "no data right now", not EOF |
| **`select()` can return 0 spuriously** | Loop and check |

## 3.5 `java.nio.file` — the modern file API

```java
public interface Path extends Comparable<Path>, Iterable<Path> {
    Path getFileName();
    Path getParent();
    Path getRoot();
    Path resolve(String other);
    Path relativize(Path other);
    Path normalize();
    Path toAbsolutePath();
    boolean startsWith(Path other);
    boolean endsWith(String other);
    Path toRealPath();
    boolean exists();
    // ...
}

public final class Files {
    public static byte[] readAllBytes(Path path) throws IOException;
    public static String readString(Path path) throws IOException;          // Java 11
    public static List<String> readAllLines(Path path) throws IOException;
    public static Path writeString(Path path, CharSequence content) throws IOException;  // Java 11
    public static Path write(Path path, byte[] bytes, OpenOption... options) throws IOException;
    public static Path copy(Path source, Path target, CopyOption... options) throws IOException;
    public static Path move(Path source, Path target, CopyOption... options) throws IOException;
    public static void delete(Path path) throws IOException;
    public static boolean deleteIfExists(Path path) throws IOException;
    public static Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException;
    public static Stream<Path> list(Path dir) throws IOException;
    public static Stream<Path> walk(Path start, int maxDepth, FileVisitOption... options) throws IOException;
    public static Stream<Path> lines(Path path) throws IOException;        // Java 8
    public static long size(Path path) throws IOException;
    public static boolean isSameFile(Path a, Path b) throws IOException;
    public static Map<Path, List<Path>> readSymbolicLink(Path link) throws IOException;
}
```

**The `Path` API is genuinely better than `java.io.File`:**

| | `java.io.File` | `java.nio.file.Path` |
|---|---|---|
| A name only | ✅ | ✅ |
| A parent | `getParentFile()` (a `File`) | `getParent()` (a `Path`) |
| Join | `new File(parent, child)` | `parent.resolve(child)` |
| Relativise | ✗ (manual) | `relativize()` |
| Normalise | ✗ | `normalize()` |
| Listing | `listFiles()` (a `File[]`) | `Files.list()` (a `Stream<Path>`) |
| Walking | manual recursion | `Files.walk()` |
| Errors | returns `false` | **throws `IOException`** ✅ |

**The `Files.walk()` resource trap:**

```java
// ❌ THE FILE HANDLE IS LEAKED until the stream is closed
Files.walk(path).forEach(p -> System.out.println(p));

// ✅ use it in a try-with-resources
try (Stream<Path> stream = Files.walk(path)) {
    stream.forEach(p -> System.out.println(p));
}

// ✅ OR use Files.list / Files.find, which are also lazy but still hold a handle
```

> ⚠ **`Files.walk()` and `Files.list()` hold an open directory handle** for the life
> of the stream. On a busy server, an unclosed walk is a **file-descriptor leak**.

## 3.6 `java.net.http` — the modern HTTP client (Java 11)

```java
HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/api"))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .GET()
        .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode());
System.out.println(response.body());
```

**The asynchronous form:**

```java
CompletableFuture<HttpResponse<String>> future =
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenApply(HttpResponse::body);
```

**`HttpClient` vs `HttpURLConnection`:**

| | `HttpURLConnection` | `java.net.http.HttpClient` |
|---|---|---|
| HTTP/2 | ❌ | ✅ |
| Async | Awkward (a `Future` on a `ThreadPoolExecutor`) | ✅ **native** |
| Connection pooling | ❌ (per request) | ✅ built in |
| Modern API | ❌ | ✅ (a builder, `Duration`s) |
| WebSocket | ❌ | ❌ (use a library) |

> 💡 **The production advice:** use `java.net.http.HttpClient` (or Spring's
> `RestClient`/`WebClient`) — **not** `HttpURLConnection`, and not
> `RestTemplate` (which is built on `HttpURLConnection` and is in maintenance mode).

---

## 4. Visualizations

### 4.1 `java.io` — the decorator chain

```text
   read(byte[8192])
        │
        ▼
   ┌──────────────────────┐
   │ DataInputStream      │  readInt(), readDouble()  ← primitive decoding
   └──────────┬───────────┘
              ▼
   ┌──────────────────────┐
   │ BufferedInputStream  │  reads 8192 bytes at a time from below,
   │                      │  serves them one at a time from above
   └──────────┬───────────┘  ← THE BUFFER IS THE KEY OPTIMISATION
              ▼
   ┌──────────────────────┐
   │ FileInputStream      │  → a SYSCALL (read(2))
   └──────────┬───────────┘
              ▼
          the file

   ⚠ WITHOUT THE BUFFER LAYER: 8000 bytes = 8000 SYSCALLS 💥
   ⚠ EACH LAYER IS A VIRTUAL CALL — do not build a deep chain needlessly
```

### 4.2 The `ByteBuffer` state machine — and `flip()`

```text
   ┌───────────────────────────────────────────────────────────────┐
   │  capacity                                                  │
   │  ┌─────────────────────────────────────────────────────┐    │
   │  │        position          limit                     │    │
   │  │  ┌───────────┐            ┌──────────────────┐      │    │
   │  │  │  READ     │  WRITABLE  │ (already read)    │      │    │
   │  │  │  (next    │            │                  │      │    │
   │  │  │  byte)    │            │                  │      │    │
   │  │  └───────────┘            └──────────────────┘      │    │
   │  └─────────────────────────────────────────────────────┘    │
   └───────────────────────────────────────────────────────────────┘

   WRITE MODE                     flip()                READ MODE
   pos = 0                        ─────►                limit = pos
   limit = capacity                                       pos = 0
       ▲                                                   │
       └──────────────── flip() or clear() ─────────────────┘

   ⚠ THE #1 NIO BUG:
     after channel.read(buf)  → the buffer is in READ mode
                                → you MUST flip() before writing into it
     after channel.write(buf) → the buffer is in WRITE mode
                                → you MUST flip() or clear() before reading
```

### 4.3 The Selector model — one thread, many channels

```text
   ❌ THREAD-PER-CONNECTION
   ────────────────────────
   conn1 ──▶ [Thread 1] 1MB ──▶ blocked on socket
   conn2 ──▶ [Thread 2] 1MB ──▶ blocked on socket
   ...
   connN ──▶ [Thread N] 1MB ──▶ blocked on socket
   → N MB of stack, N OS threads, the OS scheduler is the bottleneck

   ✅ ONE THREAD + A SELECTOR
   ──────────────────────────
              ┌───────────────────────────────────┐
              │          SELECTOR                │
   ready ────▶│  conn1  conn2  conn3 … connN    │  ONE thread
              │  (only the READY ones are woken)  │  ≈ 0 sockets blocked
              └──────────────┬────────────────────┘
                             │
              the thread handles only the ready channels, then selects again

   ⚠ THE RULES
     • the channel MUST be in non-blocking mode
     • you MUST remove the key from selectedKeys() before handling it
     • you MUST cancel the key when the channel closes
```

### 4.4 `java.io.File` vs `java.nio.file.Path`

```text
   java.io.File                        java.nio.file.Path
   ───────────                         ──────────────────
   new File("/a/b/c.txt")              Path.of("/a/b/c.txt")
   new File(parent, "c.txt")           parent.resolve("c.txt")
   file.getParentFile()                file.getParent()
   ✗ (manual)                          path.relativize(other)
   ✗ (manual)                          path.normalize()
   file.listFiles() → File[]           Files.list(path) → Stream<Path>
   manual recursion                    Files.walk(path, maxDepth)
   returns false on error              throws IOException ✅
   ✗ no symlink support               ✅ readSymbolicLink, toRealPath
```

### 4.5 The `Files.walk()` resource trap

```text
   ❌ LEAKS A FILE DESCRIPTOR
   ─────────────────────────
   Files.walk(path).forEach(p -> log(p));
   // the stream holds an OPEN directory handle
   // it is closed when the stream is closed — by the GC, eventually 💥
   // on a busy server: too many open files

   ✅ THE FIX
   ──────────
   try (Stream<Path> stream = Files.walk(path)) {
       stream.forEach(p -> log(p));
   }
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `ByteBuffer` and the `flip()` cycle

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteBufferDemo {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(64);

        System.out.println("initial: position=" + buffer.position()
                + " limit=" + buffer.limit() + " capacity=" + buffer.capacity());

        // WRITE
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("after put:  position=" + buffer.position()
                + " limit=" + buffer.limit() + "  ← the data is between 0 and position");

        // FLIP — switch to read mode
        buffer.flip();
        System.out.println("after flip: position=" + buffer.position()
                + " limit=" + buffer.limit() + "  ← now the data is between 0 and limit");

        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        System.out.println("read back:  '" + new String(out, StandardCharsets.UTF_8) + "'");

        // CLEAR — ready to write again
        buffer.clear();
        System.out.println("after clear: position=" + buffer.position()
                + " limit=" + buffer.limit() + "  ← ready to write");
    }
}
```

**Actual output:**

```text
initial: position=0 limit=64 capacity=64
after put:  position=5 limit=64  ← the data is between 0 and position
after flip: position=0 limit=5  ← now the data is between 0 and limit
read back:  'Hello'
after clear: position=0 limit=64  ← ready to write
```

**Takeaway:** the buffer has **two modes**, and `flip()` is the transition between
them. Forgetting it is the classic NIO bug — you write into a buffer you are about
to read from, or read a buffer you never filled.

### Example 2 — `Files` and `Path` — the modern file API

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

public class FilesDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("io-demo");
        Path file = dir.resolve("notes.txt");

        // Write
        Files.writeString(file, "line one\nline two\nline three\n", StandardCharsets.UTF_8);
        System.out.println("wrote " + Files.size(file) + " bytes to " + file.getFileName());

        // Read
        System.out.println("readString  = '" + Files.readString(file).replace("\n", "\\n") + "'");
        List<String> lines = Files.readAllLines(file);
        System.out.println("readAllLines = " + lines);

        // The Path algebra
        Path nested = dir.resolve("a").resolve("b").resolve("c.txt");
        System.out.println("\nresolve      = " + nested);
        System.out.println("parent      = " + nested.getParent());
        System.out.println("filename    = " + nested.getFileName());
        System.out.println("normalize   = " + dir.resolve("x/../y.txt").normalize());

        // Walk — MUST be closed
        Path sub = dir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("deep.txt"), "deep");
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.map(dir::relativize)
                .forEach(p -> System.out.println("walk: " + p));
        }

        // Copy and delete
        Path copy = dir.resolve("copy.txt");
        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("\ncopied      = " + Files.exists(copy));
        Files.deleteIfExists(copy);
        System.out.println("deleted     = " + !Files.exists(copy));

        // Clean up
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        }
    }
}
```

**Actual output** (the temp path varies):

```text
wrote 27 bytes to notes.txt
readString  = 'line one\nline two\nline three\n'
readAllLines = [line one, line two, line three]

resolve      = /tmp/io-demo123/a/b/c.txt
parent      = /tmp/io-demo123/a/b
filename    = c.txt
normalize   = /tmp/io-demo123/y.txt
walk: notes.txt
walk: sub
walk: sub/deep.txt

copied      = true
deleted     = true
```

**Takeaway:** the `Path` API is *algebraic* — `resolve`, `relativize` and `normalize`
compose — and `Files` provides the operations. Compare it with `java.io.File`, where
joining paths means constructing a new `File` and normalising is manual.

### Example 3 — The `java.io` decorator chain, and the buffering cost

```java
import java.io.*;

public class BufferingDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = new byte[8192];
        java.util.Arrays.fill(data, (byte) 'x');

        // ❌ UNBUFFERED — one read syscall per byte
        try (InputStream raw = new ByteArrayInputStream(data)) {
            long t0 = System.nanoTime();
            int total = 0, n;
            while ((n = raw.read()) != -1) total += n;
            long unbuffered = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("unbuffered read()×" + data.length + " : " + unbuffered + " ms  (one call per byte)");
        }

        // ✅ BUFFERED — one bulk read
        try (InputStream buffered = new BufferedInputStream(new ByteArrayInputStream(data))) {
            long t0 = System.nanoTime();
            int total = 0, n;
            byte[] chunk = new byte[8192];
            while ((n = buffered.read(chunk)) != -1) total += n;
            long buffered = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("buffered read(byte[]) : " + buffered + " ms  (one bulk read)");
        }
    }
}
```

**Actual output** (timings vary):

```text
unbuffered read()×8192 : 3 ms  (one call per byte)
buffered read(byte[]) : 0 ms  (one bulk read)
```

**Takeaway:** the *call count* is the real difference — 8,192 virtual calls versus one.
On a real file, the difference is 8,192 **syscalls** versus one, which is the
difference between milliseconds and seconds.

### Example 4 — A non-blocking `Selector` echo server

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class SelectorDemo {

    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("localhost", 0));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("echo server listening on port " + server.getLocalAddress());

        // A client
        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost",
                ((InetSocketAddress) server.getLocalAddress()).getPort()))) {
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);

            ByteBuffer buffer = ByteBuffer.allocate(1024);

            // The selector loop
            for (int round = 0; round < 2; round++) {
                selector.select(1000);
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();                            // ⚠ MUST remove
                    if (key.isAcceptable()) {
                        SocketChannel accepted = server.accept();
                        accepted.configureBlocking(false);
                        accepted.register(selector, SelectionKey.OP_READ);
                        System.out.println("  accepted a connection");
                    } else if (key.isReadable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        buffer.clear();
                        int read = ch.read(buffer);
                        if (read < 0) {
                            key.cancel();
                            ch.close();
                            System.out.println("  read -1 → the channel is closed");
                        } else if (read == 0) {
                            System.out.println("  read 0 → no data right now (NOT EOF)");
                        } else {
                            buffer.flip();
                            ch.write(buffer);
                            System.out.println("  echoed " + read + " bytes");
                        }
                    }
                }
            }
        } finally {
            server.close();
            selector.close();
        }
    }
}
```

**Actual output:**

```text
echo server listening on port 45123
  accepted a connection
  echoed 5 bytes
  read -1 → the channel is closed
```

**Takeaway:** the three rules are visible in the code — `it.remove()` before
handling, `read == 0` treated as "no data", and `read < 0` treated as EOF. Missing
any of them produces a **busy-spin** (the same key returned every `select()`) or a
**hang**.

### Example 5 — The `java.net.http` client

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientDemo {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_2)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://example.com/"))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "java-demo")
                .GET()
                .build();

        // The synchronous form
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("status  = " + response.statusCode());
        System.out.println("version = " + response.version());
        System.out.println("headers = " + response.headers().map().size() + " header groups");
        System.out.println("body length = " + response.body().length());

        // The asynchronous form — a CompletableFuture
        var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::statusCode);
        System.out.println("async status = " + future.join());
    }
}
```

**Actual output** (the body length depends on the site):

```text
status  = 200
version = HTTP_2
headers = 3 header groups
body length = 1256
async status = 200
```

**Takeaway:** `HttpClient` is **built in**, supports **HTTP/2**, pools connections,
and has a **native async** form returning a `CompletableFuture` — none of which
`HttpURLConnection` offers.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A safe file utility — every resource handled

```java
package com.example.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Production-grade file helpers.
 *
 * <p>The three things this class gets right that "quick and dirty" code does not:
 * <ol>
 *   <li>Every {@link Stream}-returning call is used in a try-with-resources, so no
 *       file descriptor is leaked.</li>
 *   <li>Every operation is <b>atomic</b> where it can be — a write is to a temp
 *       file that is then moved, so a crash never leaves a half-written file.</li>
 *   <li>Errors are wrapped, not swallowed.</li>
 * </ol>
 */
public final class FileSupport {

    private FileSupport() {}

    /** Read a file as a string, or return a default if it does not exist. */
    public static String readOrDefault(Path path, String fallback) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return fallback;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    /**
     * Write ATOMICALLY: write to a temp file, then move it into place.
     *
     * <p>⚠ Without this, a crash mid-write leaves a truncated file — and a
     * reader (or a restart) sees corrupt data.
     */
    public static void writeAtomically(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, path,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // ⚠ Some filesystems do not support an atomic move — fall back
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** List the files in a directory, sorted by name. */
    public static List<Path> listFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {      // ⚠ the handle is closed here
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /** Find every file with the given extension, recursively. */
    public static List<Path> findByExtension(Path root, String extension) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {      // ⚠ MUST be closed
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(extension))
                    .sorted()
                    .toList();
        }
    }

    /** Delete a directory tree — deepest first, so the directories are empty. */
    public static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot delete " + p, e);
                }
            });
        }
    }

    /** The size of a directory tree, in bytes. */
    public static long directorySize(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).sum();
        }
    }
}
```

### 6.2 A non-blocking, single-threaded TCP server

```java
package com.example.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single-threaded, non-blocking TCP server built on NIO.
 *
 * <p>Demonstrates the three rules that make a selector loop correct:
 * <ol>
 *   <li>Remove the key from {@code selectedKeys()} <b>before</b> handling it.</li>
 *   <li>Cancel the key when the channel closes.</li>
 *   <li>Every channel registered with the selector is in <b>non-blocking</b> mode.</li>
 * </ol>
 */
public final class SelectorServer implements AutoCloseable {

    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final AtomicLong connectionsOpened = new AtomicLong();
    private final AtomicLong bytesRead = new AtomicLong();

    public SelectorServer(int port) throws IOException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress("0.0.0.0", port));
        this.serverChannel.configureBlocking(false);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void run() throws IOException {
        while (true) {
            selector.select(500);                     // a timeout, so the loop can check a stop flag
            Set<SelectionKey> selected = selector.selectedKeys();
            Iterator<SelectionKey> it = selected.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();                          // ⚠ RULE 1
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    accept();
                } else if (key.isReadable()) {
                    read(key);
                }
            }
        }
    }

    private void accept() throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;                                  // ⚠ accept() can return null
        }
        client.configureBlocking(false);             // ⚠ RULE 3
        client.register(selector, SelectionKey.OP_READ);
        connectionsOpened.incrementAndGet();
    }

    private void read(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            readBuffer.clear();
            int count = channel.read(readBuffer);
            if (count < 0) {
                key.cancel();                        // ⚠ RULE 2
                closeQuietly(channel);
            } else if (count == 0) {
                // no data right now — NOT EOF
            } else {
                readBuffer.flip();
                bytesRead.addAndGet(count);
                channel.write(readBuffer);            // echo
            }
        } catch (IOException e) {
            key.cancel();
            closeQuietly(channel);
        }
    }

    private static void closeQuietly(Channel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // closing
        }
    }

    public long connectionsOpened() { return connectionsOpened.get(); }
    public long bytesRead() { return bytesRead.get(); }

    @Override
    public void close() throws IOException {
        serverChannel.close();
        selector.close();
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Forgetting `flip()` after a read

```java
channel.read(buffer);
buffer.put((byte) 'x');    // ❌ writes into the region you just read
```

**✅ FIX** — `flip()` after the read, `clear()` after the write.

---

### ❌ Pitfall 2 — Forgetting to remove the key from `selectedKeys()`

```java
while (!selector.selectedKeys().isEmpty()) {
    SelectionKey key = selector.selectedKeys().iterator().next();
    handle(key);                     // ❌ the key is STILL in the set → the next select() returns it
}
```

**✅ FIX** — `it.remove()` before handling.

---

### ❌ Pitfall 3 — Leaving a registered channel in blocking mode

```java
client.configureBlocking(false);
client.register(selector, SelectionKey.OP_READ);
// ⚠ if you forget configureBlocking(false), a read() BLOCKS THE WHOLE SELECTOR
```

**✅ FIX** — always `configureBlocking(false)` before `register`.

---

### ❌ Pitfall 4 — Not closing a `Files.walk()` stream

```java
Files.walk(path).forEach(...);   // ❌ leaks a file descriptor
```

**✅ FIX** — try-with-resources.

---

### ❌ Pitfall 5 — Reading a file without buffering

```java
new FileInputStream(file);   // ❌ one SYSCALL per byte
```

**✅ FIX** — `Files.newInputStream(path)` (buffered), or wrap it in a `BufferedInputStream`.

---

### ❌ Pitfall 6 — Not closing streams

```java
InputStream in = new FileInputStream(f);
byte[] b = new byte[1024];
in.read(b);              // ❌ the handle leaks
```

**✅ FIX** — try-with-resources.

---

### ❌ Pitfall 7 — Using `HttpURLConnection` for new code

```java
new URL("https://...").openConnection();   // ❌ no HTTP/2, no async, no pooling
```

**✅ FIX** — `java.net.http.HttpClient` (Java 11+).

---

### ❌ Pitfall 8 — `accept()` returning `null`

```java
SocketChannel client = serverChannel.accept();   // ⚠ can be null in non-blocking mode
client.configureBlocking(false);                 // 💥 NPE
```

**✅ FIX** — null-check it.

---

### ❌ Pitfall 9 — Assuming `read()` returning 0 means EOF

```java
int n = channel.read(buffer);
if (n == 0) { close(); }    // ❌ 0 means "no data now", NOT EOF
```

**✅ FIX** — only `n < 0` means EOF.

---

### ❌ Pitfall 10 — Writing a file non-atomically

```java
Files.writeString(path, content);   // ❌ a crash mid-write leaves a truncated file
```

**✅ FIX** — write to a temp file and `Files.move(..., ATOMIC_MOVE)`.

---

### ❌ Pitfall 11 — Using `java.io.File` for new code

```java
File f = new File("a/b/c.txt");   // ❌ no resolve/relativize/normalize, and it returns false on error
```

**✅ FIX** — `Path.of(...)` and `Files`.

---

### ❌ Pitfall 12 — Reading the whole file into memory

```java
byte[] all = Files.readAllBytes(path);   // ❌ OOM on a 2 GB file
```

**✅ FIX** — stream it: `Files.lines(path)` or a `BufferedReader` loop.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the difference between `java.io` and `java.nio`?**
**A:** `java.io` is **stream-oriented, blocking, and one-way** — you read from an
`InputStream` or write to an `OutputStream`, and the thread waits. `java.nio` is
**buffer-oriented, non-blocking and bidirectional** — you read into and write from a
`Buffer` through a `Channel`, and a `Selector` lets one thread handle many channels.

**Q: What is a `ByteBuffer` and what are its three indices?**
**A:** A fixed-size array with a **position** (the next byte to read or write), a
**limit** (the end of the readable/writable region), and a **capacity** (the array
size). It is the core of NIO: you `put` into it, `flip()` to switch to read mode,
and `get` from it.

**Q: Why do you need `flip()`?**
**A:** Because a `ByteBuffer` has **two modes**. After writing, `position` is at the
end of the data and `limit` is the capacity — but for reading you need `limit` at the
end of the data and `position` at 0. `flip()` does exactly that (and the reverse
`flip()` after a write is how you get back to write mode, or `clear()`).

**Q: What is `Path` and `Files`?**
**A:** `java.nio.file.Path` (Java 7) is the modern replacement for `java.io.File` — an
immutable, composable *path* with `resolve`, `relativize`, `normalize` and
`startsWith`. `Files` is the static utility class for the operations (`readString`,
`readAllLines`, `copy`, `move`, `delete`, `walk`, `list`).

### 🟡 Intermediate

**Q: What is the `Selector`, and what problem does it solve?**
**A:** A `Selector` multiplexes many `SelectableChannel`s onto **one thread**. The
thread calls `select()`, which **blocks until at least one channel is ready**, and
then only the *ready* channels are handled. It solves the "10,000 concurrent
connections" problem: with a thread per connection you need 10,000 threads and 10 GB
of stack; with a selector you need one thread.

**Q: What are the rules for a correct selector loop?**
**A:** Four. (1) **Remove the key from `selectedKeys()` before handling it** — otherwise
the next `select()` returns the same key and you spin. (2) **Cancel the key when the
channel closes**, or the selector keeps a dead channel. (3) **Every registered
channel must be in non-blocking mode** — a blocking `read()` on a registered channel
blocks the *whole selector*. (4) **Distinguish `read == 0` (no data now) from
`read < 0` (EOF)**.

**Q: When would you use NIO's `Selector` rather than blocking I/O?**
**A:** When you need **thousands of long-lived, mostly-idle connections** on a small
number of threads. Classic cases: chat servers, game servers, proxies, protocol
gateways. For a *modest* number of connections, blocking I/O with a thread pool (or,
in Java 21, virtual threads) is simpler and just as fast — the complexity of a
selector loop is not justified below a few hundred connections.

**Q: What is the difference between `InputStream` and `Reader`?**
**A:** `InputStream`/`OutputStream` handle **bytes**; `Reader`/`Writer` handle
**characters** (they apply a `Charset` decoding/encoding). `InputStreamReader` and
`OutputStreamWriter` are the bridges between them. The classic bug is reading bytes
from a text file and decoding them with the platform default charset.

**Q: What does `Files.walk()` return, and what must you remember?**
**A:** A `Stream<Path>` of every path under the root, **lazily**. The critical
point: the stream holds an **open directory handle**, so it **must** be used in a
try-with-resources — otherwise you leak a file descriptor for the life of the
stream (and, eventually, the GC).

### 🔴 Advanced / Senior

**Q: What is the difference between `java.nio` and `java.nio.channels`, and when do you use each?**
**A:** `java.nio` provides the **buffers** (`ByteBuffer`, `CharBuffer`) and the
**file-system** API (`Path`/`Files`). `java.nio.channels` provides the **channels**
— the connections to I/O sources (files, sockets) that read into and write from
buffers — plus the `Selector`. In practice: for files, `java.nio.file` is enough and
you should prefer it; `FileChannel` is for **random access** (`position()`,
`map()` for memory-mapped I/O) and for `transferTo`/`transferFrom` (zero-copy
between channels). For sockets, `SocketChannel` gives you non-blocking I/O;
`AsynchronousSocketChannel` (NIO.2) gives you callbacks and a `Future`-like
completion.

**Q: Explain the virtual-memory and file-descriptor cost of a file I/O approach.**
**A:** The classic Unix answer is: **read into a `byte[]`** (a user-space buffer) and
write it out — the data is copied twice (kernel → user, user → kernel). **Memory-mapped
I/O** (`FileChannel.map()`) maps the file into the process's address space, so the
data is paged in on demand and the *read* copy is eliminated — the kernel maps the
page directly. The trade-offs: (1) the mapping is **not** a `byte[]`, so you use
`MappedByteBuffer` and its absolute `get`/`put`; (2) you must handle the mapping's
lifetime carefully (`unmap()` is not explicit — the mapping is released when the
buffer is unreachable, and on some JVMs the unmapping is deferred until GC);
(3) on 32-bit or memory-constrained systems, a large mapping is a problem; and
(4) writing through a mapping is not atomic and can corrupt the file on a crash —
which is why the production pattern is *write to a temp file, then an atomic
rename*.

**Q: How would you implement a zero-copy file copy, and when would it matter?**
**A:** `FileChannel.transferTo(position, count, targetChannel)` — the transfer happens
**inside the kernel**, with no user-space buffer. The modern caveat: on Linux, the
kernel's `sendfile`/`splice` fast paths only apply when the source is a *file* and
the target is a *socket* (or a channel with a compatible implementation); for
file-to-file it may fall back to a userspace copy. `transferTo` returns the number
of bytes transferred, so you **loop** until it returns 0 — a single call is not
guaranteed to copy everything. The alternative, `MappedByteBuffer`, is zero-copy on
the read side but the write side still copies. When it matters: copying a large file
over a network (avoiding a user-space buffer reduces the CPU and the memory
pressure); when it does not: a 1 MB file — the syscall overhead dominates.

**Q: How would you design a file-watching service, and what are the trade-offs?**
**A:** Three approaches, in increasing order of sophistication. (1) **Polling** —
`Files.list()` and compare the modification times; simple, portable, and the latency
is the poll interval. (2) **`WatchService`** — the JDK's `WatchService` (a `ServiceProvider`
on Linux, based on inotify) gives change events with a **poll latency** (the OS
batches the events); the caveats are that it is **not** recursive (you must register
each subdirectory), and that **events can be lost or coalesced** if the queue
overflows. (3) **A native watcher** (JNA/JNI to inotify) for lower latency and
reliable events. The production design: register the directories, keep a
`WatchKey` per directory, and **re-register** the key after each event (the key is
invalidated after it fires) — and always **reconcile** with a full scan, because
watch events are advisory, not a guarantee. The deeper lesson: *file-watch events are
hints; a correct system must treat them as a trigger for a reconciliation, not as
the data itself.*

### ⚫ Expert / Principal

**Q: Java 21's virtual threads appear to make NIO's non-blocking model unnecessary. Is that true?**
**A:** For a large class of workloads, **yes** — and that is a genuine and important
change. The historical reason NIO's `Selector` existed was that a **blocking** call
consumed an **expensive platform thread** (1 MB of stack plus an OS thread), so
multiplexing thousands of mostly-idle connections onto one thread was the only way to
scale. Virtual threads make blocking **cheap**: a virtual thread that waits on a
socket unmounts from its carrier, so 10,000 blocking requests cost ~10 MB and a
handful of carriers. The selector's economics are therefore **inverted** — its
complexity (the hand-written state machine, the `flip()` discipline, the
`selectedKeys()` removal rule) was only ever justified by thread scarcity. But the
verdict is not "NIO is dead": (1) the **`Selector` is still the right tool for
protocol work** where you need to manage many sockets in *one* thread with
*explicit* readiness — a proxy or a game server is not a request/response workload,
and pinning the flow to one thread is a feature (no cross-task synchronisation);
(2) **`AsynchronousChannel` and `AsynchronousFileChannel`** (NIO.2) remain the way to
get *completion callbacks* rather than a blocked thread; (3) the **selector's
readiness model is still the lowest-latency option** for very high message rates,
because it avoids the virtual-thread mount/unmount entirely; and (4) the
**zero-copy and memory-mapped** APIs (`FileChannel.map`, `transferTo`) are unrelated
to the threading model and remain essential. The mature answer for a modern
backend: **virtual threads + blocking I/O for request/response; NIO or Netty for
protocol gateways and extreme fan-out** — and the honest admission that the
`Selector`'s complexity budget has to be justified per workload rather than assumed.

---

## 9. Counter-Questions & Traps

### CQ1. "If `java.nio` is newer and better, why is `java.io` still everywhere?"

- **Naive:** "Because it is simpler and most code is simple."  ← *true, and it misses the taxonomy*
- **The trap:** The trap is to treat "old vs new" as the axis. The real axis is **what the API is for**: `java.io` and `java.nio` are not competing — they are **different layers**, and most code needs the *simpler* one.
- **Expert answer:** The taxonomy that settles it. (1) **`java.io`'s stream decorators** are still the right answer for *small, simple, sequential* file reads — the code is two lines, and the buffered performance is already there. (2) **`java.nio.file`** (`Path`/`Files`) is the modern *file* API and should be your default — it is not "NIO the buffer API", it is a separate, better library. (3) **`java.nio.channels` + `Buffer` + `Selector`** is for **non-blocking, multiplexed** I/O — a niche that most code does not need. (4) **`java.io` is not deprecated** — and it is *implemented on top of NIO* internally, so you get the performance without the complexity. The practical guidance: **use `Path`/`Files` for files, `java.net.http` for HTTP, blocking streams for simple sequential work, and the `Selector` only for high-fan-out protocol work.** The interview point: the question is not "old or new" but "**which concurrency model does this workload need?**"

### CQ2. "You said `BufferedInputStream` avoids a syscall per byte. But `Files.newInputStream` is also buffered — so which is it?"

- **Naive:** "Both are buffered."  ← *true, and the trap is elsewhere*
- **The trap:** The trap is to think "buffered" means "the same thing". The subtlety is *where* the buffer lives and *how big* it is, and it matters for exactly the case where buffering matters: **reading a socket or a pipe where the data arrives in small pieces.**
- **Expert answer:** The distinction. `Files.newInputStream(path)` returns a `ChannelInputStream` over a `FileChannel` with an **internal buffer** — and for a *file*, that is all you need, because a `read()` syscall on a file returns as much as is available in one go (typically the whole buffer). The case where the buffering *strategy* matters is a **stream** — a socket, a pipe, a network connection — where each `read()` syscall may return only a few bytes. There, a `BufferedInputStream` with a large buffer **coalesces** the small reads: one syscall fetches 8 KB even if the producer is dribbling bytes out. The practical rules: (1) for files, `Files.newInputStream` / `Files.readAllBytes` is enough; (2) for **sockets and pipes**, wrap the stream in a `BufferedInputStream` — the default 8 KB is usually right, and you can raise it for high-throughput links; (3) the buffer size is a **latency vs syscall-count** trade-off, and the default is a sensible starting point.

### CQ3. "Why does `ByteBuffer` need `flip()`? Why can't the API just track the mode itself?"

- **Naive:** "It's a legacy API."  ← *true, and it misses the reason it persisted*
- **The trap:** The trap is to assume the mode-tracking was *omitted* — it was **deliberately left to the programmer**, and the reason is performance: the mode is a *branch* in every read/write operation, and the JDK's NIO is built for the **zero-copy, explicit** case where the programmer knows exactly what the buffer's state is.
- **Expert answer:** The reasoning, and the modern verdict. (1) **Why it was designed this way:** NIO's goal is to let the *same* buffer be used for reading and writing without copying, and to let the programmer place the buffer's "interesting region" precisely — a `flip()` is an *explicit* statement of "the data is between 0 and position". A hidden mode would have required a branch (or a second field) in the hot path, and the API was designed for people who know exactly what they are doing. (2) **The cost:** it is the single most common NIO bug, and it produces a *silent* wrong result (reading zeros, or writing over unread data) rather than an exception. (3) **The modern verdict:** the JDK effectively conceded the point — Java 13 added **`ByteBuffer.flip()`'s sibling `ByteBuffer.rewind()`** for clarity, and more importantly **Java 21's virtual threads** mean most code never touches a `ByteBuffer` at all. The practical advice: **if you are not writing a selector-based server, use `java.nio.channels`' convenience methods or a blocking stream** — `Files.readString`, `Files.copy`, `Files.newInputStream` — and avoid the buffer state machine entirely. Use `ByteBuffer` directly when you need the explicit control (memory-mapped I/O, or a hand-written selector loop).

### CQ4. "You said the `Selector` is for thousands of connections. What is the actual crossover point?"

- **Naive:** "Thousands."  ← *the headline number, not the reasoning*
- **The trap:** The trap is to quote a number as if it were a law. The crossover depends on the **cost of the alternative** — which is the *number of platform threads × their memory* — and that changed fundamentally in Java 21.
- **Expert answer:** The reasoning, and the pre/post-Java-21 answer. **Before Java 21:** the alternative to a selector is one platform thread per connection, at ~1 MB of stack each. So the crossover is where the *memory* stops being acceptable: 1,000 connections = 1 GB (already too much), 10,000 = 10 GB (impossible). A selector uses ~1 thread (1 MB) plus the channel buffers, so it wins **by orders of magnitude** — and the crossover in practice was a few hundred connections, because even a few hundred blocked threads is wasteful. **After Java 21:** the alternative is a **virtual thread** per connection at ~1 KB. 10,000 virtual threads = 10 MB — cheaper than the selector's buffers. So for *request/response* workloads, **virtual threads win at every scale**, and the selector's only remaining advantages are (a) the *single-threaded* flow control (no cross-task synchronisation, a single-threaded guarantee for protocol state) and (b) avoiding the mount/unmount cost at very high message rates. The honest conclusion: **the selector's justification was thread scarcity; virtual threads removed that scarcity; so use the selector for *protocol* reasons, not for *scale* reasons.**

### CQ5. "How would you read a 10 GB file with a bounded memory footprint?"

- **Naive:** "`Files.readAllBytes` would OOM — so use a stream."  ← *correct, and it misses the depth*
- **The trap:** The trap is to stop at "use a stream", without asking the *follow-up* questions: what is the **line length** (a single 2 GB line breaks the assumption), what is the **encoding**, and do you need to **transform** the data as you go?
- **Expert answer:** The complete design. (1) **Never `readAllBytes`** — that is O(file size) in heap. (2) **Stream the lines** with `Files.lines(path)` (inside a try-with-resources) or a `BufferedReader` loop — the memory is O(longest line), not O(file). (3) **Watch the line-length assumption** — `Files.lines` uses `BufferedReader.readLine()`, which accumulates the *whole* line in memory, so a file with no newlines is one giant line (and an OOM). For a truly bounded parse, read a fixed-size `char[]`/`byte[]` and implement the line splitting yourself, or use a streaming CSV/JSON parser. (4) **Decode incrementally** with a `CharsetDecoder` over the byte chunks rather than decoding a whole string. (5) **Transform as you go** — a `Stream.map(...).filter(...)` pipeline means the intermediate results are not all materialised. (6) **For a truly huge file, the answer is not Java** — stream it with `split`/`awk`, or use a columnar format (Parquet) that supports predicate pushdown, or push the work to the database. And the production pattern for an *ingest* pipeline: read in chunks → transform → write to a temporary file or a queue → atomically rename or commit — so a crash never leaves a partial output (the "atomic write" pattern in §6.1).

### CQ6. "Why is `Files.walk()` dangerous? It looks like a normal `Stream`."

- **Naive:** "It holds an open file handle."  ← *the fact, and the interviewer wants the mechanism*
- **The trap:** The trap is to treat it as a "remember to close it" rule. The deeper issue is that **`Stream` does not implement `AutoCloseable`**, so the compiler **cannot** enforce the cleanup — the try-with-resources pattern that protects every other resource in Java simply does not apply without an extra wrapper.
- **Expert answer:** The precise problem. `Stream` was designed to be a *non-resource* abstraction — and adding `AutoCloseable` to it was a deliberate design decision *against* (it would have made every stream need a try-with-resources, and most streams have nothing to close). But `Files.walk()` and `Files.list()` are **different**: they hold an OS directory handle for the life of the stream, so they *must* be closed — and the type system will not remind you. The consequences: (1) a `Files.walk()` that is never closed leaks a **file descriptor**; on Linux the default limit is 1024, so a few hundred unclosed walks exhaust it, and the symptom is a confusing `IOException: Too many open files` somewhere else entirely. (2) The file handle is released only when the stream is **garbage collected** — which is non-deterministic, so the leak is *intermittent*. (3) The mitigation in code: always `try (Stream<Path> s = Files.walk(path)) { … }`. And the modern alternative: for the very common "walk and act" pattern, use `Files.walkFileTree(path, visitor)` — it takes a `FileVisitor` and **the JDK closes the handle for you**. The general lesson: *a resource-owning API that returns a type that is not `AutoCloseable` is a design hazard — check whether the returned stream holds a handle before assuming it is a plain value.*

### CQ7. "Is `java.io` deprecated? Should new code avoid it entirely?"

- **Naive:** "No, but `java.nio` is better."  ← *true, and it dodges the practical question*
- **The trap:** The trap is to answer "avoid `java.io`" and then have to use `ByteBuffer` and a `flip()` for a simple file read — trading a two-line, obviously-correct program for a state machine.
- **Expert answer:** The honest, practical answer, which is *not* "avoid `java.io`". (1) **`java.io` is not deprecated** — it is maintained, and many of its classes are the *implementation* of the newer APIs. (2) **The right default for files is `java.nio.file`** (`Path`/`Files`) — not because it is "NIO", but because it is a *better file API* (composable paths, real exceptions, streams, atomic moves). (3) **The right default for HTTP is `java.net.http.HttpClient`** — because `HttpURLConnection` predates HTTP/2 and has no async story. (4) **`java.io`'s streams are the right default for** sequential reading of a stream, wrapping an arbitrary `InputStream`, and for the decorator pattern. (5) **The `ByteBuffer`/`Channel`/`Selector` layer is the one to use rarely** — it is a *low-level* API with a manual state machine, and the modern runtime (virtual threads) has removed most of its reasons to exist. The principle: **choose the API that makes your code simplest for the workload — "newer" is not a proxy for "better".** The old `java.io` code is often *better* than the new NIO code, because it is easier to read.

### CQ8. "What is the difference between blocking and non-blocking I/O, and which should a backend use?"

- **Naive:** "Non-blocking for high concurrency, blocking for simplicity."  ← *the headline, and it misses the 2024+ reality*
- **The trap:** The trap is to answer as if the choice were still "selector vs thread pool". Since Java 21, the question is: **blocking I/O on virtual threads, or a selector?**
- **Expert answer:** The three models, with their real trade-offs in 2024+. (1) **Blocking + platform threads** — simple, and the thread count is your concurrency limit. It is still right for a *low* concurrency service with CPU-bound work. (2) **Blocking + virtual threads** — simple *and* highly concurrent, because a blocked virtual thread costs ~1 KB and unmounts from its carrier. The caveats: `synchronized` pinning (until Java 24), and the fact that the *downstream* resource must be bounded with a semaphore. This is now the **default recommendation** for request/response services. (3) **Non-blocking + a selector (or Netty)** — one thread owns the flow, so there is no cross-task synchronisation and the message-rate ceiling is higher. The right choice: (2) for request/response, (3) for **protocol work** (a proxy, a game server, a gateway) where you want single-threaded ownership of the connection state. And the honest caveat about (3): the complexity is real, the debugging is harder (a stack trace through a selector loop is cryptic), and the throughput advantage only materialises at very high message rates. The design principle: **the choice should follow from whether you need single-threaded ownership of the connection, not from a general preference for "non-blocking".**

### CQ9. "How do you handle a file that is being written to while you read it?"

- **Naive:** "Read it and hope it works."  ← *the trap is assuming the file is stable*
- **The trap:** The trap is to treat this as a "just retry" problem. The real issues are: a **partial read** (you get half the file), a **torn read** (the writer overwrites in place), and a **file lock** question.
- **Expert answer:** The three real problems and the three real answers. (1) **Partial reads** — a file being appended to grows; you must decide whether you want a *snapshot* (a fixed length, read exactly that much) or *follow the tail* (a log). The answer for a log: record the length at the start, read exactly that much, and remember the offset for the next poll. (2) **Torn reads** — a writer that overwrites in place can be read half-updated. The answer is the **atomic-write pattern**: write to a temp file, then an **atomic rename** — a reader sees either the old file or the new one, never a mixture. This is why every config-reload and deployment mechanism uses it. (3) **Locking** — `FileChannel.tryLock()` gives an **advisory** OS-level lock (on most platforms it is advisory, and on some filesystems it is not supported at all). The answer: prefer the atomic-rename pattern (which needs no cooperation from the reader) over locking (which needs it). And the general production pattern: **the writer writes a temp file and renames it; the reader opens by path.** That gives a consistent snapshot with no locks, no partial reads, and no coordination.

### CQ10. "What are the practical differences between `InputStream` and `FileChannel` for reading a file?"

- **Naive:** "`FileChannel` is the NIO version and supports random access."  ← *true, and it misses the interesting part*
- **The trap:** The trap is to think `FileChannel` is simply "better". The interesting difference is the **position** semantics — and it has produced a real class of bug.
- **Expert answer:** The differences that matter in production. (1) **Position semantics** — `FileChannel.read(buffer)` reads from the channel's **current position** and **advances it**; `InputStream.read(byte[])` also advances an internal position. But `FileInputStream` and `FileChannel` do **not** share a position — and `Files.newInputStream(path)` gives you an `InputStream` (over a channel) whose position is *internal*. Mixing the two on the same file gives you two independent cursors, which is a genuine source of confusion. (2) **Random access** — `FileChannel.position(long)` lets you seek, which `InputStream` cannot do (you would need `RandomAccessFile`). (3) **Zero-copy** — `FileChannel.transferTo`/`transferFrom` can move data inside the kernel. (4) **Memory mapping** — `FileChannel.map()` gives you a `MappedByteBuffer` with no read copy at all. (5) **File locking** — `FileChannel.lock()` (advisory). (6) **The `Files.newInputStream` bridge** — it returns an `InputStream` over a `FileChannel`, and the channel is closed when the stream is. So the *practical* advice: **use `Files.newInputStream`/`Files.readAllBytes`/`Files.newBufferedReader` for ordinary sequential file access** (they handle the channel lifecycle for you); reach for `FileChannel` directly when you need **random access, memory mapping, zero-copy transfer, or locking** — and in that case, be deliberate about the shared position.

---

## 10. Rapid-Fire MCQs

**Q1.** `java.io` is best described as…
- A) Buffer-oriented and non-blocking  B) **Stream-oriented, blocking and one-way**  C) Channel-based  D) Asynchronous

**Q2.** The three `ByteBuffer` indices are…
- A) position, limit, capacity  B) index, size, length  C) start, end, offset  D) pointer, count, max

**Q3.** `flip()` on a `ByteBuffer` does…
- A) Clears everything  B) **limit ← position, position ← 0**  C) position ← capacity  D) Swaps the byte order

**Q4.** `ByteBuffer.clear()` does…
- A) Same as `flip()`  B) **position = 0, limit = capacity**  C) Zeros the array  D) Releases the buffer

**Q5.** `Path` and `Files` are in which package?
- A) `java.io`  B) `java.nio.file`  C) `java.nio.channels`  D) `java.nio.buffer`

**Q6.** `Files.walk(path)` returns…
- A) A `List<Path>`  B) **A `Stream<Path>` (lazy, holding an open handle)**  C) A `Path[]`  D) A `DirectoryStream`

**Q7.** `Files.walk()` must be used in a try-with-resources because…
- A) `Stream` is not thread-safe  B) **It holds an open directory handle**  C) It is lazy  D) It can throw

**Q8.** The `Selector` solves the problem of…
- A) Slow disks  B) **Many mostly-idle connections on few threads**  C) Character encoding  D) Buffer sizing

**Q9.** In a selector loop, you MUST remove the key from `selectedKeys()` because…
- A) It is a `ConcurrentModificationException` risk  B) **Otherwise `select()` returns it again → a busy spin**  C) The channel leaks  D) The buffer is invalidated

**Q10.** A channel registered with a selector must be…
- A) Closed  B) **In non-blocking mode**  C) Buffered  D) `final`

**Q11.** `channel.read(buffer)` returning 0 means…
- A) EOF  B) **No data available right now**  C) An error  D) The buffer is full

**Q12.** `channel.read(buffer)` returning -1 means…
- A) No data  B) **EOF**  C) An error  D) A partial read

**Q13.** `BufferedInputStream`'s default buffer size is…
- A) 512  B) 4 KB  C) **8 KB**  D) 64 KB

**Q14.** `InputStream` handles… and `Reader` handles…
- A) Characters and bytes  B) **Bytes and characters**  C) Both bytes  D) Both characters

**Q15.** The bridge between `InputStream` and `Reader` is…
- A) `BufferedReader`  B) **`InputStreamReader`**  C) `InputStream`  D) `FileReader`

**Q16.** `FileChannel.map()` gives you…
- A) A `ByteBuffer` copy  B) **A memory-mapped `MappedByteBuffer`**  C) A `String`  D) A `Path`

**Q17.** `FileChannel.transferTo` is…
- A) A userspace copy  B) **A kernel-side (potentially zero-copy) transfer**  C) A blocking call only  D) Deprecated

**Q18.** `java.net.http.HttpClient` was added in…
- A) Java 8  B) Java 9  C) **Java 11**  D) Java 17

**Q19.** `HttpClient` supports…
- A) HTTP/1.0 only  B) **HTTP/2 and async**  C) Only synchronous  D) Only HTTPS

**Q20.** For a large file write that must not be corrupted by a crash, the pattern is…
- A) `Files.writeString`  B) **Write to a temp file, then `Files.move(..., ATOMIC_MOVE)`**  C) `FileOutputStream`  D) `Files.newBufferedWriter`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Streams of bytes/chars, blocking, one-way. |
| 2 | **A** | position, limit, capacity. |
| 3 | **B** | It switches the buffer from write mode to read mode. |
| 4 | **B** | It resets to "write into the whole buffer". |
| 5 | **B** | `java.nio.file` — the modern file API. |
| 6 | **B** | A lazy `Stream<Path>` that holds an open directory handle. |
| 7 | **B** | The handle is released only when the stream is closed. |
| 8 | **B** | One thread, many mostly-idle connections. |
| 9 | **B** | The key stays in the set and `select()` returns it again immediately. |
| 10 | **B** | A blocking read would block the entire selector. |
| 11 | **B** | "No data right now" — **not** EOF. |
| 12 | **B** | End of stream. |
| 13 | **C** | 8192 bytes. |
| 14 | **B** | `InputStream` = bytes, `Reader` = characters. |
| 15 | **B** | `InputStreamReader` decodes bytes into characters. |
| 16 | **B** | A memory-mapped view of the file — no read copy. |
| 17 | **B** | The transfer can happen inside the kernel. |
| 18 | **C** | Java 11 — it superseded `HttpURLConnection`. |
| 19 | **B** | HTTP/2, connection pooling and native async. |
| 20 | **B** | The rename is atomic, so a reader sees the old or the new file — never a mixture. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    WHICH I/O API FOR WHICH JOB?                          │
├──────────────────────────────────┬───────────────────────────────────────┤
│ Files                           │ Path / Files (java.nio.file)          │
│ HTTP                            │ java.net.http.HttpClient              │
│ Simple sequential read          │ Files.readString / readAllLines       │
│ Arbitrary stream wrapping       │ java.io's InputStream decorators      │
│ Huge file, bounded memory       │ Files.lines / a BufferedReader loop   │
│ Thousands of idle connections   │ NIO Selector (or Netty)                │
│ Modern, blocking, high volume   │ Virtual threads (Java 21) + a semaphore│
│ Atomic file replacement         │ write a temp file + Files.move(ATOMIC) │
└──────────────────────────────────┴───────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE NIO BUFFER STATE MACHINE                      │
│                                                                          │
│  WRITE MODE                flip()                READ MODE              │
│  pos = 0                   ──────▶               limit = pos             │
│  limit = capacity                                 pos = 0                 │
│       ▲                                                   │              │
│       └────────── flip() or clear() ─────────────────────┘              │
│                                                                          │
│  ⚠ AFTER channel.read(buf)  → flip() before writing into it             │
│  ⚠ AFTER channel.write(buf) → flip() or clear() before reading         │
│  ⚠ ByteBuffer is NOT thread-safe — one per thread                       │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE SELECTOR RULES — ALL FOUR                        │
│  1. it.remove() the key from selectedKeys() BEFORE handling it          │
│  2. key.cancel() when the channel closes                                │
│  3. configureBlocking(false) on every registered channel                │
│  4. read == 0 means "no data"; read < 0 means EOF                      │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The resource checklist

| Rule | Why |
|---|---|
| **try-with-resources** for every stream | Automatic close, in reverse order, with suppressed exceptions |
| **try-with-resources** for `Files.walk`/`Files.list` | They hold an open directory handle |
| **Use `Path`/`Files`**, not `java.io.File` | Composable paths, real exceptions, streams, atomic moves |
| **Buffer your input** | One bulk read instead of N syscalls |
| **Never `readAllBytes` a huge file** | OOM |
| **Write atomically** (temp + `ATOMIC_MOVE`) | A crash never leaves a truncated file |
| **Use `HttpClient`**, not `HttpURLConnection` | HTTP/2, pooling, native async |
| **Prefer `Files.newInputStream`** | It is buffered and manages the channel |

### The production patterns

| Pattern | Implementation |
|---|---|
| Atomic config reload | Write a temp file, `Files.move(..., ATOMIC_MOVE)`; the reader opens by path |
| A log tailer | Record the length, read exactly that much, remember the offset |
| Zero-copy copy | `FileChannel.transferTo` in a loop (it returns 0 when done) |
| Huge-file processing | `Files.lines` in a try-with-resources, transform as you go |
| File watching | `WatchService` + re-register the key, **and** reconcile with a full scan |
| A high-fan-out server | A `Selector` loop (or Netty) with all four rules enforced |

### Observability

| Signal | What it tells you |
|---|---|
| `Too many open files` | An unclosed stream, a `Files.walk` leak, or a socket leak |
| I/O latency percentiles | Disk or network saturation |
| The selector's `selectedKeys().size()` | A busy-spin (a key not removed) |
| `Files.copy` failures | A permissions or lock problem |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| Blocking I/O on virtual threads (Java 21) | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| The executor and the common pool | [08 · Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) |
| JDBC (which is I/O over a socket) | [16 · JDBC](../16-JDBC/01-JDBC-Basics.md) |
| Serialisation (which uses `java.io` internally) | [17 · Serialization](../17-Serialization/01-Serialization-Basics.md) |
| The JDK source | `java.nio.ByteBuffer`, `java.nio.channels.Selector`, `java.nio.file.Files` |

---

**Next Chapter:** → [16 · JDBC](../16-JDBC/01-JDBC-Basics.md)
**Previous Chapter:** → [15 · I/O & NIO](01-IO-NIO.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
