# 15 — I/O & NIO · Interview Notes Index

> A classic "do you actually use Java" question. The answer that impresses is knowing
> **when NIO is faster and when it is slower** — not just reciting the class names.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐ | [01 · I/O & NIO](01-IO-NIO.md) | Streams vs Channels, Buffers, Selectors, NIO.2 file API, async I/O |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Difference between `InputStream` and `Reader`? | Bytes vs characters. The `Reader` layer does charset decoding. |
| Why wrap in `BufferedInputStream`? | Avoids a syscall per byte — this is the single biggest I/O perf lever. |
| `File` vs `Path`? | `File` is legacy (and has a bad `equals`); `Path`/`Files` is the modern NIO.2 API. |
| What is a `Selector`? | The multiplexing engine — one thread watches many channels. |
| `ByteBuffer` direct vs heap? | Direct avoids a copy during I/O but is slower for random access; heap buffers are copied out by the JDK. |
| Is NIO faster than IO? | **Only** for high-concurrency / many small files. For one big sequential read, plain `IO` with buffering is equal or better. |
| What is `FileChannel.map`? | Memory-mapped file — zero-copy reads from the page cache. Huge win for random access. |
| `Files.lines` — what is the trap? | It returns a **Stream backed by an open file handle** — you must use try-with-resources or you leak the FD. |

---

## 🧠 The Mental Model

```
  CLASSIC IO (blocking)              NIO (non-blocking / selectable)
  ─────────────────────              ───────────────────────────────
  FileInputStream                     FileChannel
      │                                   │
      ▼                                   ▼
  InputStream ──▶ [bytes]             ByteBuffer  ◀── Channel reads into it
      │                                   │
      ▼                                   ▼
  (no multiplexing)                  Selector ──▶ many channels, one thread

  Best for: one big file, simple       Best for: 10,000 sockets, chat servers,
  sequential access                   low thread overhead
```

> **Caption:** NIO's win is **not** raw throughput — it is **scalability**: one selector thread
> can manage thousands of connections instead of one thread per connection.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Character encoding** — `new InputStreamReader(in, StandardCharsets.UTF_8)`; never rely on the platform default.
- [ ] **File descriptor leaks** — `Files.lines`, `DirectoryStream`, and `Scanner` all hold OS resources.
- [ ] **`try-with-resources` everywhere.**
- [ ] **`WatchService`** for hot-reload features.
- [ ] **Large file streaming** — never `Files.readAllBytes()` on a 2 GB file.

---

**Related folders:** → [16 · JDBC](../16-JDBC/README.md) · ← [14 · Reflection & Annotations](../14-Reflection-Annotations/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
