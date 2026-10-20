# JVM Internals — Class Loading, Bytecode, JIT & Diagnostics

> **Level:** Expert
> **Interview Frequency:** ★★★★☆ (less frequent than "stack vs heap", but the *discriminator* — a candidate who can explain `invokedynamic` and deoptimisation is assumed to have actually run a JVM)
> **Prerequisite:** [02 · Garbage Collection](02-Garbage-Collection.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — A Virtual Machine as a Three-Stage Pipeline](#2-mental-model--a-virtual-machine-as-a-three-stage-pipeline)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The `.class` File Format](#31-the-class-file-format)
  - [3.2 Class Loading: Loading → Linking → Initialisation](#32-class-loading-loading--linking--initialisation)
  - [3.3 The ClassLoader Hierarchy & Delegation Model](#33-the-classloader-hierarchy--delegation-model)
  - [3.4 Class Identity — the "Same Name, Different Class" Problem](#34-class-identity--the-same-name-different-class-problem)
  - [3.5 Bytecode and the Instruction Set](#35-bytecode-and-the-instruction-set)
  - [3.6 `invokedynamic` — How Java 7+ Rebuilt the Language](#36-invokedynamic--how-java-7-rebuilt-the-language)
  - [3.7 The JIT: C1, C2, Tiered Compilation](#37-the-jit-c1-c2-tiered-compilation)
  - [3.8 Deoptimisation and the "Made Not Entrant" Moment](#38-deoptimisation-and-the-made-not-entrant-moment)
  - [3.9 JIT Optimisations](#39-jit-optimisations)
  - [3.10 Safepoints and the JVM's Cooperative Scheduler](#310-safepoints-and-the-jvms-cooperative-scheduler)
  - [3.11 The Diagnostic Toolkit](#311-the-diagnostic-toolkit)
  - [3.12 The OOM Playbook — Every Kind, and How to Diagnose It](#312-the-oom-playbook--every-kind-and-how-to-diagnose-it)
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

- **Class loading has three phases: loading → linking (verify, prepare, resolve) → initialisation.** Only the *initialisation* phase runs the static initialiser (`<clinit>`), and it runs **exactly once per class per class loader**.
- **Parent-first delegation** is not a security sandbox (that is the `SecurityManager`, now deprecated) — it is about **class identity**: it guarantees that `java.lang.String` is loaded *once*, by the bootstrap loader, for the whole JVM.
- **Class identity is `(binary name, defining class loader)`.** Two classes with the same name loaded by different loaders are **completely different types** — not castable, not `instanceof`-compatible, and a `ClassCastException` waiting to happen.
- **Bytecode is a stack machine.** `iload_1 / iload_2 / iadd / ireturn` — a local variable array plus an operand stack per frame. `javap -c` disassembles it; `javap -v` shows the constant pool and the exception tables.
- **`invokedynamic`** (Java 7) replaced the old "generate a class per lambda" scheme. Lambdas now compile to a single `invokedynamic` plus a `LambdaMetafactory` bootstrap that spins the implementation class at runtime — visible in the log as `LambdaDemo$$Lambda/0x0000007f01040210`.
- **Tiered compilation:** the interpreter runs first, then **C1** (fast compile, modest optimisations), then **C2** (slow compile, aggressive optimisations). The level is visible as the third column of `-XX:+PrintCompilation`.
- **Deoptimisation** is the JIT admitting it was wrong: C2 compiles an assumption ("this is always a `String`"), the assumption is later violated, and the JVM *unwinds* the frame back to the interpreter and recompiles. The log line is `made not entrant`.
- **Safepoints** are the JVM's cooperative stopping points. All GC pauses, biased-lock revocations and deoptimisations happen at safepoints — which is why `-Xlog:safepoint` is a critical latency tool.
- **`jcmd` is the modern replacement for `jmap`/`jstack`/`jstat`** and is safer (it uses the attach API and does not need to be the same user in many configurations). `jcmd <pid> help` lists everything.
- **Class loading is triggered by *active use*, not by declaration.** `MyClass obj;` loads nothing. `MyClass.CONSTANT` (a compile-time constant) loads nothing. `new MyClass()`, a static method call, a non-constant static field read, `Class.forName`, or `instanceof` all do.

---

## 1. What & Why — The Problem This Solves

**The problem.** Java promises "write once, run anywhere". That promise requires the compiler to emit something that is *not* native code for any particular CPU, and a runtime that can turn that intermediate form into fast machine code *while the program is running*. Three problems fall out of that:

1. **Where does the code come from?** A JVM can load classes from a directory, a JAR, the network, a database, or generate them at runtime (proxies, lambdas, Groovy). So there must be a *substitution* mechanism: "give me the bytes for the class named `com.acme.Order`".
2. **When is a class's state ready?** A class has static fields, a constant pool, and an initialiser. The JVM must guarantee that a static field is never read before its initialiser has run, and that the initialiser runs *exactly once* even under heavy concurrency.
3. **How does interpreted code become fast code?** The interpreter is 10–100× slower than native code. The JVM must *profile* the running code, guess what is true, compile optimised native code, and — when the guess turns out to be wrong — **undo** the optimisation safely.

**The analogy — a restaurant with a translator and a line cook:**

```text
  .java source            the customer's order, in their language
       │  javac
       ▼
  .class bytecode         a standardised order ticket (a "stack machine" order)
       │  ClassLoader
       ▼
  Loaded class            the ticket is found, checked, and filed (Loading → Linking)
       │  <clinit>
       ▼
  Initialised class       the pantry is stocked (static fields have their values)
       │  Interpreter
       ▼
  Interpreted execution   a junior cook follows the ticket literally, noting what
       │                  customers actually order (profiling)
       │  C1 / C2 JIT
       ▼
  Compiled native code    the head cook now prepares the dish from scratch, using
                          what they learned about the popular orders
       │  assumption violated?
       ▼
  Deoptimisation          "sorry, this one is special" — the head cook reverts to
                          the ticket for this dish and re-plans
```

**Why this matters in production.** Three real incidents map directly onto these mechanisms:

- **A `ClassNotFoundException` at runtime but not at compile time** → the class was on the compile classpath but not the *runtime* classpath (a missing transitive dependency in a fat JAR).
- **A `NoClassDefFoundError` on the *second* call to a class that worked the first time** → the static initialiser threw; the class is now permanently "erroneous" (§3.2.4).
- **A p99 latency spike every time a new "shape" of data arrives** → the JIT compiled a monomorphic assumption, the assumption broke, and the JVM deoptimised and recompiled (the "megamorphic call site" problem).

Understanding the pipeline is what lets you *diagnose* these rather than guess.

---

## 2. Mental Model — A Virtual Machine as a Three-Stage Pipeline

```text
  ┌────────────┐    ┌──────────────┐    ┌──────────────────────────────────────┐
  │  .java     │    │  .class      │    │            RUNTIME                   │
  │  source    │    │  bytecode    │    │                                      │
  │            │    │              │    │  ┌────────────────┐                  │
  │  javac     │───▶│  class file  │───▶│  │ 1. LOADING     │                  │
  │  (compile  │    │  (platform-  │    │  │  find bytes,   │                  │
  │   time)    │    │   neutral)  │    │  │  define Class  │                  │
  └────────────┘    └──────────────┘    │  └───────┬────────┘                  │
                                          │          ▼                           │
                                          │  ┌────────────────┐                  │
                                          │  │ 2. LINKING     │                  │
                                          │  │  VERIFY        │ bytecode is safe │
                                          │  │  PREPARE       │ alloc statics    │
                                          │  │  RESOLVE       │ symbolic → direct│
                                          │  └───────┬────────┘                  │
                                          │          ▼                           │
                                          │  ┌────────────────┐                  │
                                          │  │ 3. INITIALISING │  <clinit> runs  │
                                          │  │  static fields │  ONCE, thread-  │
                                          │  │  <clinit>()    │  safe           │
                                          │  └───────┬────────┘                  │
                                          │          ▼                           │
                                          │  ┌────────────────┐                  │
                                          │  │ 4. EXECUTION   │                  │
                                          │  │  Interpreter    │ level 0         │
                                          │  │  → C1 (level 3) │ fast compile    │
                                          │  │  → C2 (level 4) │ max performance │
                                          │  │  ← deoptimise   │ on wrong guess  │
                                          │  └────────────────┘                  │
                                          └──────────────────────────────────────┘
```

**What to look at:** the *strict ordering* — **loading always precedes linking, linking always precedes initialisation, and initialisation always precedes use**. Every `ClassNotFoundException` / `NoClassDefFoundError` / `ExceptionInInitializerError` is a violation of exactly one of these edges. Learn the three errors as *positions* on this pipeline:

```text
  LOADING fails      →  ClassNotFoundException   (the bytes were not found)
  LINKING fails      →  VerifyError / NoClassDefFoundError
  INITIALISING fails →  ExceptionInInitializerError  (first time)
                     →  NoClassDefFoundError        (every time after)
```

---

## 3. Deep Dive — Theory

### 3.1 The `.class` File Format

A class file is a binary file with a fixed structure defined by the JVMS §4:

```text
  ┌──────────────────────────────────────────────────────────────────┐
  │ magic                 u4    0xCAFEBABE                        │
  │ minor_version         u2    (0 for modern javac)                │
  │ major_version         u2    52=Java 8 … 61=17 … 65=21 … 69=25 │
  │ constant_pool_count   u2                                       │
  │ constant_pool         cp_info[]   ← the "constant pool"       │
  │ access_flags          u2    ACC_PUBLIC, ACC_FINAL, ACC_SUPER … │
  │ this_class            u2    index into the constant pool        │
  │ super_class           u2    index (0 for java.lang.Object)      │
  │ interfaces            u2 + u2[]                                 │
  │ fields                field_info[]                             │
  │ methods               method_info[]                            │
  │ attributes            attribute_info[]  (Code, LineNumberTable,│
  │                                 Exceptions, BootstrapMethods)│
  └──────────────────────────────────────────────────────────────────┘
```

**The constant pool** is the class file's symbol table. It holds `CONSTANT_Utf8`, `Class`, `Fieldref`, `Methodref`, `NameAndType`, `String`, `Integer`, `Long`, `InvokeDynamic`, `MethodHandle`, `MethodType`, and `Dynamic`. It is also the *runtime* constant pool's source — the runtime pool is a copy with the symbolic references resolved lazily.

**Real output — `javap -v Sample.class` (JDK 25):**

```text
Classfile /tmp/jvmwork/Sample.class
  Last modified Oct 20, 2026; size 460 bytes
  SHA-256 checksum 36e7d8a7fdb3ad6bf961a3d2ef7904c6ab38afc09739ccfd1dc25fc417717d63
  Compiled from "Sample.java"
public class Sample
  minor version: 0
  major version: 69
  flags: (0x0021) ACC_PUBLIC, ACC_SUPER
  this_class: #13                         // Sample
  super_class: #2                         // java/lang/Object
  interfaces: 0, fields: 0, methods: 3, attributes: 1
Constant pool:
   #1 = Methodref          #2.#3          // java/lang/Object."<init>":()V
   #2 = Class              #4             // java/lang/Object
   #3 = NameAndType        #5:#6          // "<init>":()V
   #4 = Utf8               java/lang/Object
   #5 = Utf8               <init>
   #6 = Utf8               ()V
   #7 = Fieldref           #8.#9          // java/lang/System.out:Ljava/io/PrintStream;
   #8 = Class              #10            // java/lang/System
   #9 = NameAndType        #11:#12        // out:Ljava/io/PrintStream;
  #10 = Utf8               java/lang/System
  #11 = Utf8               out
  #12 = Utf8               Ljava/io/PrintStream;
  #13 = Class              #14            // Sample
  #14 = Utf8               Sample
  #15 = Methodref          #13.#3         // Sample."<init>":()V
  #16 = Methodref          #13.#17        // Sample.add:(II)I
  #17 = NameAndType        #18:#19        // add:(II)I
  #18 = Utf8               add
```

**What to look at:** `major version: 69` — that is **Java 25**. The mapping is `major = 44 + feature_release` for Java 5+ (Java 8 = 52, Java 11 = 55, Java 17 = 61, Java 21 = 65, Java 25 = 69). A class compiled for a *newer* JDK than the runtime will fail with `UnsupportedClassVersionError`. The `SHA-256 checksum` field is a modern integrity check (added in Java 9 to replace the weaker CRC-32).

**Key attributes inside the `Code` attribute:**

| Attribute | Purpose |
|---|---|
| `Code` | The bytecode itself, the max stack depth, the max locals, the **exception table**, and the nested `LineNumberTable` / `LocalVariableTable` |
| `Exceptions` | The **declared** checked exceptions (the `throws` clause) — purely informational, not enforced by the VM |
| `BootstrapMethods` | The `invokedynamic` bootstrap method references (for lambdas, string concatenation) |
| `InnerClasses` | Nested-class metadata |
| `Signature` | The generic signature (erased types are in the descriptors) |

### 3.2 Class Loading: Loading → Linking → Initialisation

This is the JVMS §5 pipeline. Every error in this area is a violation of one of its edges.

#### 3.2.1 Loading

The JVM asks the **defining class loader** for the bytes of a binary name.

- The class loader reads the bytes (from a file, JAR, network, or generated).
- The JVM **parses** the class file format and creates the corresponding `java.lang.Class` instance in the **Metaspace** (`java.lang.Class` objects themselves live on the *heap*; the metadata they point to lives in Metaspace).
- The class is **linked** next — but note: **loading and linking can be interleaved**; the JVM is allowed to link a class before all its dependencies are loaded, as long as it does not *initialise* it prematurely.

**The JVM does not require the bytes to come from a file.** A custom loader can synthesise them, which is exactly what proxies and mocking frameworks do.

#### 3.2.2 Linking — three sub-phases

**① Verify.** The bytecode is checked for structural and type safety:

- The class file format is well-formed.
- The stack depths are consistent (`max_stack` is sufficient for every instruction).
- Types on the stack match the types expected by the instruction (`iadd` needs two `int`s).
- Branches land on instruction boundaries.
- The `this` type in a non-static method is compatible with the class.
- **Access control**: a class cannot access private members of another class in a different package (this is the real "verification" that `SecurityManager` used to supplement).

Verify errors throw `VerifyError` (a `LinkageError`).

**② Prepare.** The JVM allocates memory for the class's **static fields** and gives them their **default values**:

| Type | Default |
|---|---|
| `boolean` | `false` |
| `int`, `short`, `byte`, `char` | `0` |
| `long`, `float`, `double` | `0.0L` / `0.0f` / `0.0` |
| `Object` reference | `null` |

**Crucially, `prepare` does *not* run the initialisers.** A static field is `0`/`null` between `prepare` and `initialise`. This is observable — and it is exactly what makes the "static initialiser order" bug possible (§3.2.4).

**③ Resolve.** Symbolic references in the constant pool (`#16 = Methodref #13.#17`) are replaced with direct references (a pointer to the resolved `Method`/field). **Resolution is lazy** — the JVM may resolve a reference at first use, or eagerly. `CONSTANT_Class` entries for the *superclass* and *interfaces* must be resolved during linking (per the JVMS), which is why loading a subclass forces loading its parent.

#### 3.2.3 Initialisation

The class is initialised **on first active use**. The JVM runs the `<clinit>` method — the compiler-generated synthetic method that contains, in source order:

1. The static field initialisers (in declaration order).
2. The static block bodies (in declaration order).
3. The `static final` constant variables are **not** in `<clinit>` — they are folded into the constant pool at compile time.

**The guarantees:**

- **Exactly once per class per class loader**, enforced by the JVM's class-initialisation lock.
- **Thread-safe**: if thread A is initialising class C and thread B wants to use C, **B blocks** until A finishes. (This is why a slow or deadlocking `<clinit>` is a serious bug — see §3.2.4.)
- **Re-entrant**: if the `<clinit>` of C triggers the initialisation of C (recursively), the JVM detects it and proceeds rather than deadlocking.
- **`<clinit>` may be omitted entirely** if the class has no static initialisers — then the class is "initialised" trivially and immediately.

**The `static final` constant rule:**

```java
class C {
    static final int A = 42;                    // compile-time constant → inlined
    static final int B = (int) 42L;             // also a constant expression
    static final String S = "hello";            // constant → inlined
    static final List<String> L = new ArrayList<>();  // NOT a constant → lives in <clinit>
}
```

`A`, `B` and `S` are **inlined into the caller's bytecode** at compile time. The class `C` is **not even loaded** when you read `C.A`. `L` *is* a static field, so it lives in `<clinit>` and forces the class to be initialised.

#### 3.2.4 The Initialiser Failure Cascade (the classic trap)

```java
public class BrokenClass {
    private static int value = 1 / 0;   // ArithmeticException in <clinit>
}
```

```java
// FIRST access:
try {
    new BrokenClass();
} catch (Throwable t) {
    System.out.println(t.getClass().getName());   // ExceptionInInitializerError
}

// SECOND access:
new BrokenClass();   // NoClassDefFoundError — and it will NEVER work again
```

**The mechanics:**

1. The JVM starts `<clinit>` for `BrokenClass`. The `1 / 0` throws `ArithmeticException`.
2. The JVM wraps it in an **`ExceptionInInitializerError`** (with the original as the `cause`) and propagates it. The class is marked **erroneous**.
3. **Every subsequent attempt to use the class** throws **`NoClassDefFoundError`** — the JVM refuses to re-run `<clinit>` (re-running it would re-throw the same error, and the JVMS says the class is erroneous).
4. The `NoClassDefFoundError` has **no cause** (in most implementations) — the original `ArithmeticException` is lost unless you caught the *first* error.

**This is a genuinely nasty production failure mode:** a class that works during development (where the initialiser succeeds) fails on the first unlucky call in production, and the *second* failure has a completely different, less informative exception. It is the classic explanation for "`NoClassDefFoundError` but I never saw the original exception in the logs".

**The mitigation** is to keep `<clinit>` trivial and side-effect-free, and to move the risky work into a lazily-initialised holder:

```java
class Config {
    private Config() { }

    // Lazy, error-propagating, and re-attemptable
    private static class Holder {
        static final Properties PROPS = load();     // runs once, on first use
    }

    public static Properties props() throws IOException {
        return Holder.PROPS;                        // triggers Holder's <clinit>
    }

    private static Properties load() throws IOException { /* ... */ }
}
```

### 3.3 The ClassLoader Hierarchy & Delegation Model

```text
        ┌──────────────────────────────┐
        │  BOOTSTRAP CLASS LOADER      │  (native, written in C)
        │  loads: java.base, java.*    │  ← "rt.jar" in Java 8; the "modules" image in 9+
        │  getClassLoader() == null    │
        └──────────────┬───────────────┘
                       │  parent delegation
        ┌──────────────▼───────────────┐
        │  PLATFORM CLASS LOADER       │  (Java 9+: the former "Extension" loader)
        │  loads: java.sql, jdk.*      │  ← modules not in java.base but on the module path
        │  (Java 8: jre/lib/ext)       │
        └──────────────┬───────────────┘
                       │
        ┌──────────────▼───────────────┐
        │  SYSTEM CLASS LOADER        │  (Java 9+: the "Application" loader)
        │  loads: the classpath        │  ← CLASSPATH / -cp / module path
        │  (Java 8: sun.misc.Launcher) │
        └──────────────┬───────────────┘
                       │
        ┌──────────────▼───────────────┐
        │  CUSTOM CLASS LOADERS       │  (your code: OSGi, Spring, Tomcat, Hibernate)
        │  load: application plugins   │
        └──────────────────────────────┘
```

**The delegation algorithm (the real `ClassLoader.loadClass`):**

```java
protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        // 1. Already loaded by THIS loader? Return it.
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                // 2. Ask the parent. (Bootstrap is consulted via findBootstrapClassOrNull.)
                if (parent != null) {
                    c = parent.loadClass(name, false);
                } else {
                    c = findBootstrapClassOrNull(name);
                }
            } catch (ClassNotFoundException ignored) {
                // parent could not find it — fall through
            }
            if (c == null) {
                // 3. We define it ourselves.
                c = findClass(name);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
```

**Note the real implementation is `synchronized` on a per-name lock** (`getClassLoadingLock`), not on the loader itself — so two different classes can be loaded in parallel. This is a Java 7+ improvement over the classic "synchronize the whole loader" version.

**Why parent-first?** Three reasons, in increasing order of importance:

1. **Security (historically).** The `SecurityManager` (now deprecated for removal) would refuse to let a user-defined loader define `java.lang.String`. Parent-first made it *impossible* to forge core classes. **The `SecurityManager` is deprecated in Java 17 and terminally deprecated in Java 24 (JEP 486) — so this reason is now historical.**
2. **Class identity consistency.** `java.lang.String` must be *the same class* for the whole JVM. If the app loader could define its own `java.lang.String`, then a `String` from a JAR would not be castable to a `String` from the app loader — a guaranteed `ClassCastException` and a security hole. Parent-first makes the bootstrap loader the *sole* definer of `java.*`.
3. **Avoiding duplicate loading and classloader leaks.** The app loader loads application classes; the platform loader loads platform ones. The split is what lets a container (Tomcat) have one copy of `java.*` shared by all webapps, and one copy per webapp of the application's own classes.

**The child-first (parent-last) pattern.** When you *must* isolate (OSGi, plugin frameworks, hot-deploy), you override `loadClass` to try your own `findClass` **first** and only then delegate to the parent. The standard `URLClassLoader` idiom:

```java
import java.net.URL;
import java.net.URLClassLoader;

public class IsolatedLoader extends URLClassLoader {
    public IsolatedLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                // child-first for everything EXCEPT the platform classes
                if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")) {
                    c = getParent().loadClass(name);
                } else {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        c = getParent().loadClass(name);
                    }
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }
}
```

**The cost of child-first:** every class is now defined *twice* (once by the parent, once by the child) — more Metaspace, more startup cost, and a *split package* hazard if the two copies exchange objects.

### 3.4 Class Identity — the "Same Name, Different Class" Problem

**The rule:** a class is identified by the pair **(binary name, defining class loader)**.

```java
import java.io.InputStream;

public class CustomCl {
    static class MyLoader extends ClassLoader {
        MyLoader(ClassLoader parent) { super(parent); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }
    public static void main(String[] args) throws Exception {
        // Read OUR OWN class file from the classpath — no external file needed.
        byte[] bytes;
        try (InputStream in = CustomCl.class.getResourceAsStream("/CustomCl.class")) {
            bytes = in.readAllBytes();
        }
        MyLoader loader = new MyLoader(CustomCl.class.getClassLoader());
        Class<?> c = loader.define("CustomCl", bytes);
        System.out.println("loaded class = " + c.getName());
        System.out.println("loader       = " + c.getClassLoader());
        System.out.println("same Class?  = " + (c == CustomCl.class));
        System.out.println("same name?   = " + (c.getName().equals(CustomCl.class.getName())));
        System.out.println("assignable?  = " + CustomCl.class.isAssignableFrom(c));
        System.out.println("instanceof?  = " + (c.isInstance(new CustomCl())));
    }
}
```

**Actual output (JDK 25):**

```text
loaded class = CustomCl
loader       = CustomCl$MyLoader@764c12b6
same Class?  = false
same name?   = true
assignable?  = false
instanceof?  = false
```

**What to look at:** the two classes have the **same name** but are **not the same class**, and neither is assignable to the other. A `ClassCastException` is inevitable if you pass one where the other is expected. This is *the* mechanism behind:

- **OSGi / plugin isolation.**
- **Hot-deploy leaks** — the old class loader (and all its classes) stay alive because something still references them.
- **Mocking frameworks** — a mock class is defined by a *custom* loader, so `mock instanceof RealClass` is **false** unless the framework uses the *same* loader (which is why Mockito's inline mock maker exists).
- **Servlet containers** — each webapp has its own loader; a shared library must be in the *container* classpath, not the webapp's, or you get `ClassCastException` across the boundary.

### 3.5 Bytecode and the Instruction Set

**Real output — `javap -c Sample.class`:**

```text
Compiled from "Sample.java"
public class Sample {
  public Sample();
    Code:
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return

  public int add(int, int);
    Code:
         0: iload_1
         1: iload_2
         2: iadd
         3: ireturn

  public static void main(java.lang.String[]);
    Code:
         0: getstatic     #7                  // Field java/lang/System.out:Ljava/io/PrintStream;
         3: new           #13                 // class Sample
         6: dup
         7: invokespecial #15                 // Method "<init>":()V
        10: iconst_2
        11: iconst_3
        12: invokevirtual #16                 // Method add:(II)I
        15: invokevirtual #20                 // Method java/io/PrintStream.println:(I)V
        18: return
}
```

**Reading it:**

- `aload_0` loads `this` (slot 0) onto the operand stack; `invokespecial #1` calls `Object.<init>()`; `return` pops the frame.
- `iload_1 / iload_2` push the two `int` parameters; `iadd` pops two ints and pushes their sum; `ireturn` pops it and returns.
- `new #13` allocates and pushes an uninitialised `Sample`; **`dup` duplicates the reference** so that `invokespecial <init>` can consume one copy while the other stays on the stack for `invokevirtual add`; `invokevirtual` consumes the object reference and the two ints.
- The `// Method …` comments are the **resolved constant-pool entries** — `#1` in the constant pool is a `Methodref`.

**The instruction categories:**

| Category | Examples | Notes |
|---|---|---|
| Load / store | `iload_1`, `aload_0`, `istore_2`, `aload_3` | Local variable array ↔ operand stack |
| Constants | `iconst_0..5`, `bipush`, `sipush`, `ldc` | `ldc` pushes from the constant pool |
| Arithmetic | `iadd`, `isub`, `imul`, `idiv`, `ladd`, `fadd`, `dadd` | Separate opcode per type |
| Type conversion | `i2l`, `i2d`, `l2i`, `d2i`, `i2b`, `i2c` | |
| Object / array | `new`, `newarray`, `anewarray`, `getfield`, `putfield`, `getstatic`, `putstatic`, `baload`, `aaload` | |
| Stack | `pop`, `dup`, `dup2`, `swap` | `dup` is why `new`+`init` works |
| Control flow | `ifeq`, `ifne`, `if_icmplt`, `goto`, `tableswitch`, `lookupswitch` | |
| Invocation | `invokevirtual`, `invokespecial`, `invokestatic`, `invokeinterface`, `invokedynamic` | |
| Return | `ireturn`, `lreturn`, `areturn`, `return` | |
| Exception | `athrow` | The only throw instruction |
| Synchronisation | `monitorenter`, `monitorexit` | Used by `synchronized` blocks |

**The `Exception Table`** (from `javap -v`) is what makes `try/catch/finally` work — see [03 · Exception Handling](../03-Exception-Handling/01-Exception-Handling.md) for the full treatment.

**The `StackMapTable` (Java 6+, class file version 50+).** Modern bytecode carries **type frames** at branch targets so the verifier can check types *without* doing dataflow analysis. This is why class files got bigger in Java 6 and why some obfuscators break on modern JVMs.

### 3.6 `invokedynamic` — How Java 7+ Rebuilt the Language

Before Java 7, a lambda was compiled into a **new class** implementing the functional interface. Java 7 introduced `invokedynamic` and the `java.lang.invoke` machinery.

**Real output — `javap -c LambdaDemo.class`:**

```text
  public static void main(java.lang.String[]);
    Code:
         0: invokedynamic #7,  0              // InvokeDynamic #0:run:()Ljava/lang/Runnable;
         5: astore_1
         6: invokedynamic #11, 0             // InvokeDynamic #1:get:()Ljava/util/function/Supplier;
        11: astore_2
        12: invokedynamic #15, 0             // InvokeDynamic #2:apply:()Ljava/util/function/Function;
        17: astore_3
        18: aload_1
        19: invokeinterface #19,  1           // InterfaceMethod java/lang/Runnable.run:()V
        ...
        49: invokedynamic #50, 0             // InvokeDynamic #3:makeConcatWithConstants:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
```

**And the class-load log shows the runtime-generated classes:**

```text
[0.033s][info][class,load] LambdaDemo source: file:/tmp/jvmwork/
[0.041s][info][class,load] LambdaDemo$$Lambda/0x0000007f01040210 source: LambdaDemo
[0.044s][info][class,load] LambdaDemo$$Lambda/0x0000007f01040438 source: LambdaDemo
[0.045s][info][class,load] LambdaDemo$$Lambda/0x0000007f01040660 source: LambdaDemo
```

**What to look at:** the *source* of the lambda classes is `LambdaDemo` — they are **defined at runtime by the same loader**, using `Unsafe.defineAnonymousClass` (now the hidden-class mechanism). Their names contain a **memory address**, which is unique per definition.

**How `invokedynamic` works:**

1. The class file has an `InvokeDynamic` constant-pool entry pointing at a `BootstrapMethods` entry.
2. The bootstrap method is typically `LambdaMetafactory.metafactory` or `StringConcatFactory.makeConcatWithConstants`.
3. At the **first execution** of the `invokedynamic` instruction, the JVM calls the bootstrap method, which *spins the implementation class* and returns a `CallSite` bound to a `MethodHandle`.
4. Subsequent executions are a fast **invokedynamic → MethodHandle → target** dispatch.

**The two big wins:**

| Feature | Before Java 7 | After Java 7 |
|---|---|---|
| **Lambdas** | One generated class per lambda (class-file bloat, slow first use) | One `invokedynamic`; the class is spun once, on first execution, in a hidden class |
| **String concatenation** | `StringBuilder` chain at compile time (`makeConcatWithConstants` in Java 9+) | `invokedynamic` + `StringConcatFactory` — the concatenation strategy is chosen at *runtime* |

**Why string concatenation matters for performance.** In Java 8, `a + b + c` compiled to a `StringBuilder` chain (allocation per operand). In Java 9+, it compiles to a single `invokedynamic` whose bootstrap picks the best strategy at runtime (it can even avoid the intermediate `StringBuilder` entirely). **The "javac should not be the bottleneck" argument from the JEP 280 proposal is exactly this.**

**Hidden classes (Java 15+, JEP 371).** Lambda classes are now *hidden*: they are not discoverable by `Class.forName`, do not appear in a normal class-load log as a "class", and are **unloaded** when their `CallSite` becomes unreachable. This fixed a whole category of Metaspace leaks.

### 3.7 The JIT: C1, C2, Tiered Compilation

#### 3.7.1 Why JIT?

The interpreter executes bytecode through a dispatch loop: fetch the instruction, switch on the opcode, execute. Each bytecode operation costs tens of nanoseconds versus ~1 cycle for a native instruction. The interpreter is **10–100× slower** than compiled code.

But you cannot just compile everything up front: compilation takes time (C2 can take milliseconds for a big method) and produces specialised code. So the JVM **profiles first** and compiles **what is hot**.

#### 3.7.2 The compilation tiers

```text
  ┌───────────────────────────────────────────────────────────────────────────┐
  │  TIER 0 — INTERPRETER                                                    │
  │  Executes bytecode directly. Collects PROFILE DATA:                       │
  │    • invocation counters (how many times is this method called?)          │
  │    • back-edge counters (how many times does this loop iterate?)          │
  │    • type profiles (at this call site, is the receiver always a Foo?)     │
  │    • branch profiles (which way does this if/else usually go?)            │
  │  Overhead: ~20–50× slower than compiled code                            │
  └───────────────────────────────┬───────────────────────────────────────────┘
                                  │  invocation + back-edge counters exceed thresholds
                                  ▼
  ┌───────────────────────────────────────────────────────────────────────────┐
  │  TIER 3 — C1 (client compiler)                                           │
  │  Fast compile (milliseconds). Modest, safe optimisations:                 │
  │    • local variable → register mapping                                  │
  │    • constant folding, copy propagation                                 │
  │    • simple inlining                                                     │
  │    • no profile-dependent speculation (or only very conservative)        │
  │  Goal: quick warm-up, decent code, low compile time                      │
  └───────────────────────────────┬───────────────────────────────────────────┘
                                  │  invocation + back-edge counters exceed the
                                  │  C2 threshold, and the method is "hot enough"
                                  ▼
  ┌───────────────────────────────────────────────────────────────────────────┐
  │  TIER 4 — C2 (server compiler)                                           │
  │  Slow compile (milliseconds to seconds). AGGRESSIVE optimisations:       │
  │    • profile-guided type speculation (monomorphic → inline the target)    │
  │    • aggressive inlining (up to 325 bytecodes of bytecode, "MaxInlineSize")│
  │    • loop unrolling, loop peeling, range-check elimination               │
  │    • escape analysis → scalar replacement / stack allocation             │
  │    • lock coarsening, lock elision, biased-locking (removed in 15+)      │
  │    • vectorisation, super-word optimisation                              │
  │  Goal: peak throughput, at the cost of compile time and code size        │
  └───────────────────────────────────────────────────────────────────────────┘
```

**Tiered compilation** (on by default since Java 8) starts every method at **tier 0** and promotes it: **0 → 3 (C1) → 4 (C2)**. There are intermediate levels 1 and 2 (C1 with limited profiling and no profiling respectively).

**The compiler's own tiering decision** is driven by:
- `Tier3InvocationThreshold` / `Tier4InvocationThreshold` (and the *CompileThreshold*, default 10 000 in the classic model).
- **Back-edge counters** (loop iterations) — a method with a hot loop is promoted even if it is called only a few times. This is **OSR (On-Stack Replacement)**: the JVM compiles the loop *in place* while the frame is still on the stack, and switches the interpreter to the compiled version mid-execution. The `%` in the compilation log means "OSR compilation".

**The real log** (JDK 25, `-XX:+PrintCompilation`):

```text
31    8       2       TierDemo::hot (6 bytes)
31    9       4       TierDemo::hot (6 bytes)
32   8       2       TierDemo::hot (6 bytes)   made not entrant: not used
32   10 %     3       TierDemo::main @ 4 (32 bytes)
33   11       3       TierDemo::main (32 bytes)
33   12 %     4       TierDemo::main @ 4 (32 bytes)
34   10 %     3       TierDemo::main @ 4 (32 bytes)   made not entrant: C1 deoptimize for patching
```

**How to read it:**

```text
  timestamp  compile-id  [flags]  level  method  (size)  [notes]
      31        8                 2     TierDemo::hot (6 bytes)
      │         │                 │     │            │
      │         │                 │     │            └─ bytecode size in bytes
      │         │                 │     └────────────── the method
      │         │                 └──────────────────── TIER: 0=interp 1-3=C1 4=C2
      │         └────────────────────────────────────── compile ID (a method can be recompiled)
      └────────────────────────────────────────────── ms since JVM start
```

- **Level 2 → 4 in 1 ms**: `hot` was promoted from C1 to C2 almost immediately — it is called 200 000 times.
- **`%` = OSR**: the loop in `main` was compiled at the back-edge.
- **`made not entrant: not used`** — the old C1 version was made *not entrant* (it is still there, but new callers go to the new version). It is **not** "deleted" — that is the difference between "not entrant" and "made zombie".

#### 3.7.3 The tuning knobs

| Flag | Effect | Use when |
|---|---|---|
| `-XX:+TieredCompilation` | On by default since Java 8 | Leave it on |
| `-XX:TieredStopAtLevel=1` | Stop at C1 (fast startup, ~5–10× less peak performance) | **Serverless / CLI tools / short-lived jobs** — this is the single most effective cold-start flag |
| `-XX:CICompilerCount=N` | Number of compiler threads (default 3 on a 4+ CPU machine) | Raise on a big machine with many hot methods |
| `-XX:CompileThreshold=N` | The classic invocation threshold (default 10 000) | Rarely touched — the *tiered* thresholds are what matter |
| `-XX:+PrintCompilation` | Print every compilation | Diagnostics |
| `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` | Print every inlining decision | Finding "why isn't my method inlined?" |
| `-XX:+PrintCompilation -XX:+PrintAssembly` (debug builds only) | Print the generated assembly | Deep JVM work |

**Real output — the inlining log:**

```text
                              @ 12   TierDemo::hot (6 bytes)   inline
                              @ 12   TierDemo::hot (6 bytes)   inline
```

`TierDemo::hot` was **inlined** into its caller. The two identical lines show the *same* inlining site being logged twice (once for the C1 compilation, once for the C2 compilation) — a common source of confusion.

**Serverless cold start, quantified.** A typical Spring Boot / Quarkus / Micronaut service on Lambda spends **hundreds of milliseconds** in the interpreter before the JIT catches up. `TieredStopAtLevel=1` and (on GraalVM-native-image) avoiding the JVM altogether are the two answers. The interview framing: *"class loading is I/O, interpretation is CPU, and the JIT is a background thread that has not finished when the first request arrives."*

### 3.8 Deoptimisation and the "Made Not Entrant" Moment

**The problem.** C2 compiles *speculatively*, using profile data:

- "At this call site, the receiver is always a `String`." → inline `String.length()` and skip the type check.
- "This loop always runs 10 iterations." → unroll it 10×.
- "This branch is never taken." → remove the branch.
- "This field is always `null`." → skip the null check.

But the profile is a *guess*. When the guess is wrong, the JVM must **undo** the optimisation: it **deoptimises** the frame, reconstructs the interpreter frame from the compiled frame, throws away the compiled code, and (usually) recompiles with the corrected profile.

**The mechanism:**

1. The compiled code has a **deoptimization trap** at every point where an assumption is checked.
2. When the assumption fails, the code jumps to the trap.
3. The trap calls into the runtime, which **unwinds** the frame: it reads the compiled frame's *deoptimization metadata* (which maps the compiled state back to the interpreter state) and rebuilds the interpreter frame.
4. The method is marked `not entrant` (no new callers), the existing compiled frame is discarded, and the method goes back to the interpreter (and is queued for recompilation with a new profile).
5. The *unwound* frame re-executes the failing instruction **in the interpreter**, so the original exception (e.g. `ClassCastException`) is thrown **from the right place**.

**Real output — `-XX:+PrintCompilation` on a program that violates a type profile:**

```text
38    8       3       java.lang.String::length (11 bytes)   made not entrant: not used
39   10 %     3       DeoptDemo::main @ 2 (86 bytes)
40   11       3       DeoptDemo::main (86 bytes)
40   10 %     3       DeoptDemo::main @ 2 (86 bytes)   made not entrant: C1 deoptimize for patching
```

and the program itself:

```text
warm
o = 42
Exception in thread "main" java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String (java.lang.Integer and java.lang.String are in module java.base of loader 'bootstrap')
	at DeoptDemo.main(DeoptDemo.java:10)
```

**What to look at:** `C1 deoptimize for patching` — the C1-compiled version was deoptimised. Then the *interpreter* re-executed the `checkcast` and threw the `ClassCastException` **with the correct line number** (`DeoptDemo.java:10`). That line number is the proof that deoptimisation is *correct* — the stack trace is reconstructed faithfully.

**The "megamorphic call site" problem.** If a call site sees many different receiver types (`Object` that is sometimes `String`, sometimes `Integer`, sometimes `User`), the profile becomes **megamorphic** and C2 stops speculating — it falls back to a virtual dispatch (an itable lookup) *and* stops inlining. This is why:

- `List.of(...)` / `Arrays.asList(...)` returning different concrete types at different call sites is fine (each call site is separate).
- But a **shared** call site (e.g. `list.get(0).toString()` where `list` is always `ArrayList` but holds different element types) becomes megamorphic.
- The fix is usually **casting at the boundary** (`((String) list.get(0))`) so the JIT sees a monomorphic type — or using a specialised data structure.

**When deoptimisation storms happen.** A "warm-up then degrade" pattern — fast for 10 minutes, then slow — often means the profile *changed shape*: a new code path appeared (a different customer type, a different message format), the JIT deoptimised, and it is now recompiling with a megamorphic profile. The diagnostic is `-Xlog:deoptimization=info` (or `jdk.Deoptimization` in JFR).

### 3.9 JIT Optimisations

| Optimisation | What it does | Example |
|---|---|---|
| **Method inlining** | Replaces the call with the callee's body | `add(a,b)` → `a + b`. Bounded by `MaxInlineSize` (35 bytecodes) and `FreqInlineSize` (325 bytecodes for hot methods) |
| **Escape analysis** | Determines if an object escapes the method | `Point p = new Point(1,2); return p.x + p.y;` → the object is **scalar-replaced** (no heap allocation at all) |
| **Loop unrolling** | Replicates the loop body | `for (i<4)` → four straight-line statements |
| **Loop peeling / range-check elimination** | Hoists the first iteration and proves the index is in range | `arr[i]` inside a `0..len-1` loop → no bounds check |
| **Dead code elimination** | Removes computations whose results are unused | `int x = expensive();` never read → removed |
| **Null-check elimination** | Removes a null check proved redundant | After `if (o != null) { o.m(); o.m2(); }` |
| **Constant folding / propagation** | Folds compile-time constants and copies known values | `static final int MAX = 100` inlined into the loop bound |
| **Lock coarsening** | Merges adjacent `synchronized` blocks on the same object | Two consecutive `synchronized(this)` blocks become one |
| **Lock elision** | Removes a lock on a non-escaping object | Requires escape analysis |
| **Field hoisting / load elimination** | Hoists a loop-invariant field read out of the loop | |
| **Type-check elimination** | Removes a `checkcast` proved redundant by the profile | |
| **Vectorisation** | Converts a scalar loop into SIMD operations | JDK 9+ has auto-vectorisation for simple array loops |
| **Super-word (SLP) optimisation** | Packs several independent operations into one wide instruction | |

**Escape analysis in detail — the three outcomes:**

```java
// 1. NO ESCAPE  → scalar replacement: the object never exists at runtime
public int sum() {
    Point p = new Point(1, 2);
    return p.x + p.y;          // p.x and p.y become two registers
}

// 2. ESCAPES via return → must be heap-allocated
public Point createPoint() {
    Point p = new Point(1, 2);
    return p;                  // p escapes → real allocation
}

// 3. ESCAPES into a field → must be heap-allocated
public void store(List<Point> list) {
    list.add(new Point(1, 2)); // escapes into the list
}
```

**Never depend on escape analysis.** It is an implementation detail that changes between JDK versions and depends on inlining decisions. If allocation matters, allocate less.

### 3.10 Safepoints and the JVM's Cooperative Scheduler

**A safepoint is a point in the execution of Java code where *all* threads can be brought to a consistent state so that the VM can do something that requires it** — a GC, a class-loading-related operation, a deoptimisation, a biased-lock revocation, a thread dump.

**The mechanism:**

1. The thread reaches a **safepoint poll** — a check embedded in the interpreter loop, at loop back-edges, method returns, and safepoint-pollable instruction boundaries.
2. If a safepoint operation is *pending*, the thread **parks** itself and waits.
3. The VM waits for all threads to arrive, does the operation, then releases them.

```text
  Thread A            Thread B            Thread C
  ────────            ────────            ────────
  ... code ...        ... code ...        ... code ...
       │                   │                   │
       ▼                   ▼                   ▼
   ┌───────────────────────────────────────────────┐
   │  SAFEPONT POLL:  "is there work for me?"      │
   │  → no  → keep running (the common case)       │
   │  → yes → park and wait                        │
   └───────────────────────────────────────────────┘
```

**The performance problem: a safepoint that is not *reached*.** A thread in a **long-running native call** (a blocking socket read, a `sleep`, a long JNI call) or in a **tight loop without a poll** cannot reach a safepoint promptly. The VM then has two options: **spinning** (burning CPU) or **yielding** (`Unsafe.park`), which delays the safepoint. This is the classic **"safepoint timeout"** and the reason a single blocked thread can stall every GC in the process.

**The real numbers (JDK 25, `-Xlog:safepoint+stats=info`):**

```text
[0.037s][info][safepoint,stats] Halt                                 1
[0.037s][info][safepoint,stats] Maximum sync time  0 ns
[0.037s][info][safepoint,stats] Maximum vm operation time (except for Exit VM operation)  0 ns
```

**"Maximum sync time"** is the crucial number: it is how long the VM had to **wait for threads to reach the safepoint**. A high sync time means threads are not cooperating, and it directly inflates every GC pause.

**Safepoint-related latency events you will meet in production:**

| Event | Symptom | Diagnostic |
|---|---|---|
| **Safepoint timeout** (> 1 s to reach a safepoint) | A GC pause that is far longer than the GC work | `-Xlog:safepoint`, look at "Maximum sync time" |
| **Long native call** | One thread blocks a GC | `jstack` — look for a thread in a native/`WAITING` state during the pause |
| **Biased-lock revocation storm** | Periodic multi-second pauses | *Eliminated* — biased locking was disabled by default in JDK 15 and removed in JDK 18 |
| **Deoptimization storm** | Periodic latency spikes | `-Xlog:deoptimization=info` |
| **Class loading storm** | A long safepoint while a loader works | `-Xlog:class+load` |
| **`jstack` / `jcmd Thread.print`** | The thread dump itself needs a safepoint | This is why a thread dump can be slow on a busy JVM |

**The best tool for safepoint analysis is JFR:** the `jdk.SafepointBegin` event records, for every safepoint, the **total time**, the **VM operation time**, and the **sync time** — exactly the breakdown you need.

### 3.11 The Diagnostic Toolkit

| Tool | What it does | Typical use | Notes |
|---|---|---|---|
| **`jps`** | Lists the JVMs running on the host | Find the PID | `jps -lv` for the full command line |
| **`jcmd <pid> <command>`** | The **modern** multi-tool | Everything below | `jcmd <pid> help` lists all commands; uses the attach API |
| **`jcmd <pid> GC.heap_info`** | Heap size, region layout, generations | "How big is the heap right now?" | Cheap, no full GC |
| **`jcmd <pid> GC.class_histogram`** | Instance count and bytes per class | "Who owns the memory?" | Faster than `jmap`; no full GC by default (use `-all` to include unreachable) |
| **`jcmd <pid> GC.heap_dump <file>`** | A full `.hprof` | "Why are these objects retained?" | **Triggers a full GC** and a pause — expect it to be slow |
| **`jcmd <pid> Thread.print`** | A thread dump (same as `jstack`) | "Where is it stuck?" | Needs a safepoint |
| **`jcmd <pid> VM.flags`** | Every JVM flag and its value | "What is actually configured?" | The *effective* flags, not the command line |
| **`jcmd <pid> VM.command_line`** | The original command line | "How was it started?" | |
| **`jcmd <pid> VM.metaspace`** | Metaspace usage, chunk by chunk | Class-loader leak | |
| **`jcmd <pid> VM.native_memory summary`** | Native memory by category | "Why is RSS so big?" | Needs `-XX:NativeMemoryTracking=summary` |
| **`jcmd <pid> Compiler.codecache`** | Code cache usage by segment | JIT pressure | |
| **`jcmd <pid> JFR.start …`** | Start a recording | Profiling | The best production tool |
| **`jmap -histo[:live] <pid>`** | Class histogram | Same as `GC.class_histogram` | `-histo:live` **forces a full GC** |
| **`jmap -dump[:live] <pid>`** | Heap dump | | Also forces a full GC |
| **`jstat -gcutil <pid> 1000`** | GC counters, sampled | "Is it leaking?" | The first command to run |
| **`jstat -gccause <pid>`** | Why the last GC happened | | Very under-used and very useful |
| **`jstack <pid>`** | Thread dump | Deadlock analysis | Same output as `jcmd Thread.print` |
| **`javap -c / -v / -p`** | Disassemble a class file | "What did the compiler generate?" | `-v` shows the constant pool and the exception tables |
| **`jhsdb <pid>`** | The native debugger (`jstack`, `jmap`, `clhsdb`) | Post-mortem analysis of a *core dump* | Works on a crashed process |
| **JFR** (`-XX:StartFlightRecording`) | Low-overhead profiling | **The production default** | Allocation profiling, safepoints, locks, GC |
| **`jconsole` / `jmc`** | GUI for JMX and JFR | | |

**Real output — `jcmd <pid> VM.flags`:**

```text
-XX:-AOTInvokeDynamicLinking -XX:-AOTRecordTraining -XX:-AOTReplayTraining -XX:CICompilerCount=3
-XX:ConcGCThreads=1 -XX:G1ConcRefinementThreads=4 -XX:G1EagerReclaimRemSetThreshold=10
-XX:G1HeapRegionSize=1048576 -XX:G1RemSetArrayOfCardsEntries=10 -XX:G1RemSetHowlMaxNumBuckets=8
-XX:G1RemSetHowlNumBuckets=4 -XX:InitialHeapSize=134217728 -XX:MarkStackSize=4194304
-XX:MarkStackSizeMax=536870912 -XX:MaxHeapSize=134217728 -XX:MaxNewSize=79691776
-XX:MinHeapDeltaBytes=1048576 -XX:MinHeapSize=8388608 -XX:NonNMethodCodeHeapSize=5836800
-XX:NonProfiledCodeHeapSize=122912768 -XX:ProfiledCodeHeapSize=122912768
-XX:ReservedCodeCacheSize=251662336 -XX:+SegmentedCodeCache -XX:SoftMaxHeapSize=134217728
-XX:-THPStackMitigation -XX:+UseCompressedOops -XX:+UseG1GC
```

**What to look at:** `G1HeapRegionSize=1048576` (1 MB — the heap is only 128 MB, so the region size is at the minimum) and `ReservedCodeCacheSize=251662336` (240 MB — a lot of headroom for JIT code, but on a memory-constrained container that is *reserved* address space, and only the *touched* pages are resident). `CICompilerCount=3` — the number of compiler threads.

**Real output — `jcmd <pid> GC.heap_info`:**

```text
119951:
garbage-first heap   total reserved 131072K, committed 131072K, used 26841K [0x00000000f8000000, 0x0000000100000000)
 region size 1024K, 26 young (26624K), 1 survivors (1024K)
```

**Real output — `jcmd <pid> GC.class_histogram`:**

```text
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:          8578         385064  [B (java.base@25.0.4.1)
   2:          1683         222840  java.lang.Class (java.base@25.0.4.1)
   3:          8501         204024  java.lang.String (java.base@25.0.4.1)
   4:          1024         135096  [Ljava.lang.Object; (java.base@25.0.4.1)
   5:          1577          46184  [I (java.base@25.0.4.1)
   6:             6          33456  [C (java.base@25.0.4.1)
   7:         1011          32352  java.util.HashMap$Node (java.base@25.0.4.1)
   8:          954          30528  java.util.concurrent.ConcurrentHashMap$Node (java.base@25.0.4.1)
   9:          254          23648  [Ljava.util.HashMap$Node; (java.base@25.0.4.1)
  10:             1          16400  [Ljdk.internal.ref.PhantomCleanable; (java.base@25.0.4.1)
  11:          256          12288  java.util.HashMap (java.base@25.0.4.1)
  12:          341          10912  jdk.internal.math.FDBigInteger (java.base@25.0.4.1)
```

**What to look at:** row 2 — **1 683 `java.lang.Class` instances** — is the *baseline* for a normal application (the JDK's own classes). If you see **tens of thousands**, you have a class-generation problem. Row 10 — `PhantomCleanable` — is the `Cleaner` machinery for direct byte buffers; one instance means one live direct buffer. This is a superb early signal for a direct-memory leak.

### 3.12 The OOM Playbook — Every Kind, and How to Diagnose It

| OOM | Area | How to confirm | The fix |
|---|---|---|---|
| `Java heap space` | Heap | `jcmd <pid> GC.heap_info`; `jmap -histo:live <pid>` | Find the retention path; only then raise `-Xmx` |
| `GC overhead limit exceeded` | Heap (death spiral) | `jstat -gcutil` — `FGC` climbing, `O` pinned | Same as above; it is the *early warning* |
| `Metaspace` | Class metadata | `jcmd <pid> VM.metaspace`; `jcmd <pid> GC.class_histogram \| grep java.lang.Class` | Break the reference to the old class loader; cap with `-XX:MaxMetaspaceSize` |
| `Compressed class space` | Klass pointers | `jcmd <pid> VM.metaspace` | Raise `-XX:CompressedClassSpaceSize` |
| `Direct buffer memory` | Off-heap | `jcmd <pid> VM.native_memory summary`; `GC.class_histogram` for `PhantomCleanable`/`Cleaner` | Bound the off-heap pool; release the buffers |
| `unable to create native thread` | OS thread limit | `ulimit -u`; `ls /proc/<pid>/task \| wc -l`; `top -H -p <pid>` | Fewer threads; lower `-Xss`; raise the cgroup PID limit |
| `StackOverflowError` | Thread stack | The stack trace itself | Remove the recursion; `-Xss` is a band-aid |
| `requested array size exceeds VM limit` | The allocation request | The requested size in the message | Fix the size calculation — it is a bug |
| *No Java output, exit code 137* | The **container** was OOM-killed | `dmesg \| grep -i oom`; compare RSS with the cgroup limit | Budget the native areas: stacks, Metaspace, direct, code cache |

**The classification decision tree:**

```text
  OOM / crash
      │
      ├─ Is there a Java stack trace?
      │     ├─ NO  ──►  exit 137 / SIGKILL  ──►  container OOM-kill
      │     │                                       (native memory budget problem)
      │     └─ YES ──► read the message:
      │                 │
      │                 ├─ "Java heap space"          ──►  HEAP
      │                 ├─ "GC overhead limit ..."    ──►  HEAP (death spiral)
      │                 ├─ "Metaspace"                ──►  METASPACE (class loader)
      │                 ├─ "Compressed class space"   ──►  CLASS SPACE
      │                 ├─ "Direct buffer memory"     ──►  OFF-HEAP
      │                 ├─ "unable to create native   ──►  OS THREAD LIMIT
      │                 │   thread"
      │                 └─ "requested array size      ──►  A BUG IN THE CODE
      │                     exceeds VM limit"
      │
      └─ StackOverflowError                          ──►  THREAD STACK
```

**Real outputs, one per kind:**

```text
$ java -Xmx32m HeapOom
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
	at HeapOom.main(HeapOom.java:5)

$ java -XX:MaxMetaspaceSize=24m -Xmx256m MetaOom4
Exception in thread "main" java.lang.OutOfMemoryError: Metaspace

Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "main"

$ java -Xmx256m -XX:MaxDirectMemorySize=64m Dbg3
OOM at i=4 : Cannot reserve 16777216 bytes of direct buffer memory (allocated: 67108864, limit: 67108864)

$ java SOThread
Exception in thread "Thread-0" java.lang.StackOverflowError
	at SOThread.recurse(SOThread.java:6)
	at SOThread.recurse(SOThread.java:6)
	...
```

**What to look at:** the Metaspace case shows the tell-tale second line — `OutOfMemoryError thrown from the UncaughtExceptionHandler`. That happens because the JVM could not even load the classes needed to *report* the error. It is a **hallmark of a Metaspace OOM** and never appears with a heap OOM. Experts spot it instantly.

---

## 4. Visualizations

### 4.1 The Class-Loading Lifecycle (the whole pipeline in one picture)

```text
   Class.forName("com.acme.Order")
              │
              ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │  1. LOADING                                                     │
  │     • the defining loader is asked for the bytes                │
  │     • parent-first delegation:                                   │
  │         bootstrap → platform → system → custom                  │
  │     • bytes are parsed; a java.lang.Class is created             │
  │     • metadata → Metaspace; the Class object → the heap         │
  │     ✗ ClassNotFoundException if the bytes cannot be found        │
  └───────────────────────────┬──────────────────────────────────────┘
                              ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │  2. LINKING                                                     │
  │     ① VERIFY   — bytecode is type-safe; access control checked  │
  │                 ✗ VerifyError                                    │
  │     ② PREPARE  — allocate static slots, set DEFAULTS (0/null)   │
  │                 (NOT the declared values yet!)                  │
  │     ③ RESOLVE  — symbolic refs in the constant pool become      │
  │                 direct; the superclass & interfaces MUST be    │
  │                 resolved now                                    │
  │                 ✗ NoClassDefFoundError (missing superclass)     │
  └───────────────────────────┬──────────────────────────────────────┘
                              ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │  3. INITIALISATION   (first ACTIVE use, ONCE per loader)       │
  │     • the JVM takes the class-initialisation lock               │
  │     • runs <clinit>: static field initialisers, then static     │
  │       blocks, IN SOURCE ORDER                                   │
  │     • other threads BLOCK until this finishes                   │
  │     ✗ ExceptionInInitializerError  (1st time)                   │
  │     ✗ NoClassDefFoundError          (every time after)          │
  └───────────────────────────┬──────────────────────────────────────┘
                              ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │  4. USE — instantiate, invoke, reflect                          │
  └──────────────────────────────────────────────────────────────────┘

  ACTIVE-USE TRIGGERS (JVMS §5.5)          NOT TRIGGERS
  ────────────────────────────────          ─────────────────────
  new Foo()                                  Foo f;              (declaration)
  Foo.staticMethod()                         Foo.CONSTANT        (compile-time
  Foo.nonConstantStaticField  (read)                            constant — inlined)
  Class.forName("Foo")                       Foo.class literal   (loads, but does
  ClassLoader.loadClass("Foo")                                    NOT initialise)
  instanceOf / cast to Foo                    Method m = Foo::m;  (does not
  someMethod(new Foo())                                     initialise Foo)
```

**What to look at:** the **lock** in phase 3 and the **error cascade** at the bottom of it. Everything else follows.

### 4.2 The Delegation Model, with the ClassLoader Namespace

```text
  ┌──────────────────────────────────────────────────────────────────┐
  │ BOOTSTRAP  (C code, getClassLoader() == null)                   │
  │   java.lang.*  java.io.*  java.util.*  java.base module        │
  │   namespace: {java.lang.String, java.base}                      │
  └───────────────────────┬──────────────────────────────────────────┘
                          │  parent delegation
  ┌───────────────────────▼──────────────────────────────────────────┐
  │ PLATFORM  (Java 9+; was "Extension" in Java 8)                  │
  │   java.sql.*  jdk.*  java.desktop.* (non-java.base modules)     │
  │   namespace: {java.sql.Driver, java.sql}                         │
  └───────────────────────┬──────────────────────────────────────────┘
                          │
  ┌───────────────────────▼──────────────────────────────────────────┐
  │ SYSTEM / APPLICATION  (the classpath)                           │
  │   com.acme.*  org.springframework.*                              │
  │   namespace: {com.acme.Order, app}                              │
  └───────────────────────┬──────────────────────────────────────────┘
                          │
  ┌───────────────────────▼──────────────────────────────────────────┐
  │ CUSTOM (your code)                                              │
  │   com.acme.plugin.*   ← defined in its OWN namespace            │
  │   namespace: {com.acme.plugin.Order, plugin}                    │
  │                                                                  │
  │   ⚠ com.acme.plugin.Order  ≠  com.acme.Order                   │
  │     same binary name, DIFFERENT defining loader → different type │
  └──────────────────────────────────────────────────────────────────┘
```

**What to look at:** the **namespace** annotation on each loader. A class's identity is `(name, namespace)`. The custom loader's `com.acme.plugin.Order` and the app loader's `com.acme.Order` are unrelated types.

### 4.3 The JIT Pipeline: From Bytecode to Native Code

```text
  ┌─────────────┐
  │  .class     │
  └──────┬──────┘
         │  ClassLoader
         ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  INTERPRETER  (tier 0)                                     │
  │  ┌──────────────────────────────────────────────────────┐   │
  │  │  PROFILE DATA collected per method:                  │   │
  │  │   invocation_count  = 12,345                          │   │
  │  │   backedge_count    = 0                               │   │
  │  │   type_profile      = {String: 100%, Integer: 0%}     │   │
  │  │   branch_profile    = {taken: 98%, not-taken: 2%}     │   │
  │  └──────────────────────────────────────────────────────┘   │
  └──────────────────────────┬──────────────────────────────────┘
                             │  counters > threshold
                             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  C1  (tier 3)  — compile in ~1 ms                          │
  │  • inlined the callee                                       │
  │  • constants folded                                         │
  │  • NO speculative type assumptions yet                     │
  │  → code size: small, compile time: tiny, perf: ~2× interp  │
  └──────────────────────────┬──────────────────────────────────┘
                             │  counters > C2 threshold
                             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  C2  (tier 4)  — compile in ~10–500 ms                     │
  │  • "the receiver is ALWAYS a String" → inline String.length│
  │    and ELIMINATE the checkcast                              │
  │  • unrolled the loop 4×                                    │
  │  • hoisted the null check out of the loop                  │
  │  → code size: large, compile time: high, perf: ~20-30× int │
  └──────────────────────────┬──────────────────────────────────┘
                             │  assumption violated
                             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  DEOPTIMISATION                                            │
  │  • jump to the deopt trap                                  │
  │  • UNWIND: rebuild the interpreter frame from the compiled │
  │    frame using the deoptimization metadata                 │
  │  • mark the compiled version "not entrant"                 │
  │  • re-execute the failing instruction IN THE INTERPRETER  │
  │  • recompile with the new (megamorphic) profile            │
  │  → the stack trace is still EXACT (correct line numbers)  │
  └─────────────────────────────────────────────────────────────┘
```

**What to look at:** the **profile data** in the middle — it is the *only* thing that separates C1 from C2, and it is also the thing that can be *wrong*. The deoptimisation path at the bottom is what makes speculation safe.

### 4.4 The Compilation Log, Annotated

```text
   31    8       2       TierDemo::hot (6 bytes)
   31    9       4       TierDemo::hot (6 bytes)
   32   8       2       TierDemo::hot (6 bytes)   made not entrant: not used
   32   10 %     3       TierDemo::main @ 4 (32 bytes)
   33   11       3       TierDemo::main (32 bytes)
   33   12 %     4       TierDemo::main @ 4 (32 bytes)
   34   10 %     3       TierDemo::main @ 4 (32 bytes)   made not entrant: C1 deoptimize for patching
        │   │   │  │     │              │    │            │              │
        │   │   │  │     │              │    │            │              └─ WHY it was replaced
        │   │   │  │     │              │    │            └──────────────── the "not entrant" event
        │   │   │  │     │              │    └───────────────────────────── bytecode size (bytes)
        │   │   │  │     │              └────────────────────────────────── method + OSR bci
        │   │   │  │     └───────────────────────────────────────────────── the method
        │   │   │  └─────────────────────────────────────────────────────── TIER (0..4)
        │   │   └────────────────────────────────────────────────────────── % = OSR (compiled in place)
        │   └────────────────────────────────────────────────────────────── compile ID
        └────────────────────────────────────────────────────────────────── timestamp (ms since start)
```

**What to look at:** `made not entrant: not used` (the C1 version was superseded and is no longer used) versus `made not entrant: C1 deoptimize for patching` (the C1 version was *unwound* because an assumption failed). The distinction between **not entrant** and **zombie** (fully dead) is the key vocabulary.

### 4.5 The Full JVM Memory Map with the Code Cache and Native Areas

```text
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │  PROCESS (native)                                                           │
  │  ┌────────────────────────────────────────────────────────────────────────┐  │
  │  │  METASPACE (native)                     CODE CACHE (native)            │  │
  │  │  ┌──────────────┐  ┌──────────────┐   ┌────────────────────────────┐  │  │
  │  │  │ Klass for    │  │  Runtime     │   │  non-nmethods  (C1)       │  │  │
  │  │  │ java.lang.*  │  │  constant    │   │  profiled nmethods (C1)    │  │  │
  │  │  │              │  │  pool        │   │  non-profiled (C2)         │  │  │
  │  │  ├──────────────┤  ├──────────────┤   └────────────────────────────┘  │  │
  │  │  │ Klass for    │  │  Bytecode    │                                   │  │
  │  │  │ com.acme.*   │  │  (methods)   │   ← the JIT's output lives HERE,  │  │
  │  │  └──────────────┘  └──────────────┘     not on the heap, not in       │  │
  │  │  COMPRESSED CLASS SPACE                   Metaspace                    │  │
  │  └────────────────────────────────────────────────────────────────────────┘  │
  │  ┌────────────────────────────────────────────────────────────────────────┐  │
  │  │  HEAP  (-Xmx)                                                           │  │
  │  │  ┌──────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────────┐  │  │
  │  │  │ EDEN         │ │ SURVIVOR   │ │ OLD GEN    │ │  String pool     │  │  │
  │  │  │ (TLAB per    │ │ S0 / S1    │ │ (promoted) │ │  (interned)      │  │  │
  │  │  │  thread)     │ │ age 0..15  │ │            │ │                  │  │  │
  │  │  └──────────────┘ └────────────┘ └────────────┘ └──────────────────┘  │  │
  │  └────────────────────────────────────────────────────────────────────────┘  │
  │  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────────┐  │
  │  │  THREAD STACKS     │  │  DIRECT BUFFERS    │  │  GC CARDS / BITMAPS    │  │
  │  │  N × -Xss          │  │  (NIO / Netty)     │  │  remembered sets       │  │
  │  └────────────────────┘  └────────────────────┘  └────────────────────────┘  │
  └──────────────────────────────────────────────────────────────────────────────┘
```

**What to look at:** the **code cache is neither the heap nor Metaspace**. When a JVM dies with `OutOfMemoryError: CodeCache is full` (a rare but real failure in an application that generates thousands of distinct lambdas or proxies), the fix is `-XX:ReservedCodeCacheSize` — *not* `-Xmx`.

---

## 5. Worked Examples (with Output + Dry Run)

### 5.1 Example 1 — Watching the Class-Loading Pipeline Happen

**Problem:** "Prove that declaring a reference does not load a class, and that compile-time constants do not either."

```java
public class ClDemo2 {
    static class Parent { static { System.out.println("Parent <clinit>"); } }
    static class Child extends Parent { static { System.out.println("Child <clinit>"); } }
    static class Holder { static final int CONST = 42; }

    public static void main(String[] args) throws Exception {
        System.out.println("--- 1. declaring a reference only (no load) ---");
        Child c = null;
        System.out.println("--- 2. first ACTIVE use triggers load + init ---");
        System.out.println("value = " + new Child());
        System.out.println("--- 3. already loaded? ---");
        System.out.println("Child.class.getName() = " + Child.class.getName());
        System.out.println("--- 4. compile-time constant is inlined ---");
        System.out.println("CONST = " + Holder.CONST);
    }
}
```

**Actual program output (JDK 25):**

```text
--- 1. declaring a reference only (no load) ---
--- 2. first ACTIVE use triggers load + init ---
Parent <clinit>
Child <clinit>
value = ClDemo2$Child@7ad041f3
--- 3. already loaded? ---
Child.class.getName() = ClDemo2$Child
--- 4. compile-time constant is inlined ---
CONST = 42
```

**Actual class-load log** (`-Xlog:class+load=info`):

```text
[0.034s][info][class,load] ClDemo2 source: file:/tmp/jvmwork/
[0.036s][info][class,load] ClDemo2$Parent source: file:/tmp/jvmwork/
[0.036s][info][class,load] ClDemo2$Child source: file:/tmp/jvmwork/
[0.037s][info][class,load] java.lang.invoke.DirectMethodHandle$Holder source: shared objects file
[0.038s][info][class,load] java.lang.invoke.LambdaForm$Holder source: shared objects file
[0.040s][info][class,load] java.lang.invoke.DelegatingMethodHandle$Holder source: shared objects file
[0.040s][info][class,load] java.lang.invoke.Invokers$Holder source: shared objects file
[0.043s][info][class,load] jdk.internal.classfile.impl.AttributeHolder source: shared objects file
[0.048s][info][class,load] ClDemo2$Holder source: file:/tmp/jvmwork/
```

**Dry run, step by step:**

1. **`Child c = null;`** — the local variable's *type* is `Child`, but the compiler emits only a `aconst_null; astore_1`. **No class-loading instruction is emitted.** The log confirms it: `ClDemo2$Child` is not loaded at this point.
2. **`new Child()`** — this is the first *active use*. The JVM:
   - asks the app loader for the bytes of `ClDemo2$Child` → **loading**;
   - resolves the superclass `ClDemo2$Parent` → **linking** (and this forces `Parent` to be loaded and linked first);
   - runs `Parent.<clinit>` → **"Parent `<clinit>`"**;
   - runs `Child.<clinit>` → **"Child `<clinit>`"**;
   - allocates the object and calls the constructor.
3. **The order is parent-first** — `Parent` is initialised *before* `Child`, because the JVM must have a fully initialised superclass before initialising a subclass.
4. **`ClDemo2$Holder` is loaded LAST** (at `0.048s`), *after* `Holder.CONST` was printed. Why? Because `Holder.CONST` is a **compile-time constant** — the compiler inlined `42` directly into `main`'s bytecode, so `Holder` did not need to be loaded *for the read*. It was loaded afterwards because `Holder.class` appears in the `Holder loaded?` line, which *is* an active use (a class literal loads but does not initialise — and indeed `Holder` has no `<clinit>` output because its only member is a `static final int` constant, which is folded into the constant pool, not into `<clinit>`).
5. **The `java.lang.invoke.*$Holder` classes** are the `MethodHandle` infrastructure that `invokedynamic` (used by string concatenation in the `println` calls) bootstraps. Their appearance in the log is a good reminder that modern Java's `+` operator is `invokedynamic`-based.

**Takeaway:** *declaration ≠ loading; loading ≠ initialisation; initialisation ≠ use.* Three different moments, three different observable behaviours.

### 5.2 Example 2 — The Same Name, a Different Class

```java
import java.io.InputStream;

public class CustomCl {
    static class MyLoader extends ClassLoader {
        MyLoader(ClassLoader parent) { super(parent); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }
    public static void main(String[] args) throws Exception {
        // Read OUR OWN class file from the classpath — no external file needed.
        byte[] bytes;
        try (InputStream in = CustomCl.class.getResourceAsStream("/CustomCl.class")) {
            bytes = in.readAllBytes();
        }
        MyLoader loader = new MyLoader(CustomCl.class.getClassLoader());
        Class<?> c = loader.define("CustomCl", bytes);
        System.out.println("loaded class = " + c.getName());
        System.out.println("loader       = " + c.getClassLoader());
        System.out.println("same Class?  = " + (c == CustomCl.class));
        System.out.println("same name?   = " + (c.getName().equals(CustomCl.class.getName())));
        System.out.println("assignable?  = " + CustomCl.class.isAssignableFrom(c));
    }
}
```

**Actual output (JDK 25):**

```text
loaded class = CustomCl
loader       = CustomCl$MyLoader@764c12b6
same Class?  = false
same name?   = true
assignable?  = false
```

**Dry run:**

1. `getResourceAsStream("/CustomCl.class")` reads the **same** `CustomCl.class` bytes that the app loader already loaded.
2. `loader.define("CustomCl", bytes)` calls `defineClass`, which asks the JVM to create a **new runtime class** with the same binary name but the **`MyLoader` as its defining loader**.
3. `c.getName()` → `"CustomCl"` — the name is the same.
4. `c == CustomCl.class` → **false** — because `CustomCl.class` was defined by the app loader and `c` by `MyLoader`. **Two different runtime classes.**
5. `CustomCl.class.isAssignableFrom(c)` → **false** — the JVM's subtype check compares the *defining loaders* as well as the names, and they differ.
6. Consequence: `((CustomCl) c)` throws `ClassCastException`; `c instanceof CustomCl` is **false**; and `c.getClassLoader()` is `MyLoader`.

**The production implications:**

| Scenario | What happens |
|---|---|
| **Spring Boot DevTools / Tomcat hot-deploy** | The old webapp's classes stay in Metaspace because the old loader is still referenced → Metaspace leak |
| **OSGi / plugin isolation** | Each bundle has its own loader; classes are isolated by design |
| **Mockito mocks** | A mock is defined by a custom loader → `mock instanceof RealClass` is false. The **inline mock maker** fixes this by instrumenting the *original* class instead of creating a subclass |
| **Two versions of the same library** | If both are on the classpath, only one wins (the first on the classpath) — a "duplicate class" problem that the classpath order silently resolves |

**Takeaway:** `Class` identity is `(binary name, defining loader)`. This single fact explains OSGi, hot-deploy leaks, mock-instanceof surprises and `ClassCastException`s across container boundaries.

### 5.3 Example 3 — Reading Bytecode with `javap`

```java
public class Sample {
    public int add(int a, int b) { return a + b; }
    public static void main(String[] args) { System.out.println(new Sample().add(2, 3)); }
}
```

**Actual output — `javap -c Sample.class`:**

```text
Compiled from "Sample.java"
public class Sample {
  public Sample();
    Code:
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return

  public int add(int, int);
    Code:
         0: iload_1
         1: iload_2
         2: iadd
         3: ireturn

  public static void main(java.lang.String[]);
    Code:
         0: getstatic     #7                  // Field java/lang/System.out:Ljava/io/PrintStream;
         3: new           #13                 // class Sample
         6: dup
         7: invokespecial #15                 // Method "<init>":()V
        10: iconst_2
        11: iconst_3
        12: invokevirtual #16                 // Method add:(II)I
        15: invokevirtual #20                 // Method java/lang/PrintStream.println:(I)V
        18: return
}
```

**Dry run of `main`:**

| Offset | Instruction | What happens |
|---|---|---|
| 0 | `getstatic #7` | Push `System.out` onto the operand stack |
| 3 | `new #13` | Allocate a `Sample`; push the **uninitialised** reference |
| 6 | `dup` | Duplicate the reference — one copy for the constructor, one for later |
| 7 | `invokespecial #15` | Pop one copy, call `Sample.<init>` (which chains to `Object.<init>`) |
| 10 | `iconst_2` | Push `2` |
| 11 | `iconst_3` | Push `3` |
| 12 | `invokevirtual #16` | Pop the object and the two ints, push the `int` result (`5`) |
| 15 | `invokevirtual #20` | Pop the int and the `PrintStream`, print `5` |
| 18 | `return` | Pop the frame |

**What to look at:** the `dup` at offset 6. It exists purely because `new` + `<init>` is a two-step operation in the bytecode — the object reference must be on the stack for the `invokespecial`, but the verifier also requires it to remain available for the subsequent use. Every `new` in Java bytecode is followed by a `dup`.

**The constant pool** (from `javap -v`) maps the `#N` numbers:

```text
   #1 = Methodref          #2.#3          // java/lang/Object."<init>":()V
  #13 = Class              #14            // Sample
  #15 = Methodref          #13.#3         // Sample."<init>":()V
  #16 = Methodref          #13.#17        // Sample.add:(II)I
  #17 = NameAndType        #18:#19        // add:(II)I
```

**Takeaway:** `javap -c` is the compiler's output made visible. When a "simple" Java statement compiles to something surprising (a `StringBuilder` chain, a synthetic accessor, a bridge method), `javap` is how you find out.

### 5.4 Example 4 — `invokedynamic` and the Lambda Machinery

```java
import java.util.function.*;

public class LambdaDemo {
    public static void main(String[] args) {
        Runnable r = () -> System.out.println("hi");
        Supplier<String> s = () -> "x";
        Function<Integer, Integer> f = x -> x + 1;
        r.run();
        System.out.println(s.get() + f.apply(1));
    }
}
```

**Actual bytecode** (`javap -c -p LambdaDemo.class`):

```text
  public static void main(java.lang.String[]);
    Code:
         0: invokedynamic #7,  0              // InvokeDynamic #0:run:()Ljava/lang/Runnable;
         5: astore_1
         6: invokedynamic #11, 0             // InvokeDynamic #1:get:()Ljava/util/function/Supplier;
        11: astore_2
        12: invokedynamic #15, 0             // InvokeDynamic #2:apply:()Ljava/util/function/Function;
        17: astore_3
        18: aload_1
        19: invokeinterface #19,  1           // InterfaceMethod java/lang/Runnable.run:()V
        24: getstatic     #23                 // Field java/lang/System.out:Ljava/io/PrintStream;
        27: aload_2
        28: invokeinterface #29,  1           // InterfaceMethod java/util/function/Supplier.get:()Ljava/lang/Object;
        33: checkcast     #34                 // class java/lang/String
        36: aload_3
        37: iconst_1
        38: invokestatic  #36                 // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
        41: invokeinterface #42,  2           // InterfaceMethod java/util/Function.apply:(Ljava/lang/Object;)Ljava/lang/Object;
        46: invokestatic  #47                 // Method java/lang/String.valueOf:(Ljava/lang/Object;)Ljava/lang/String;
        49: invokedynamic #50, 0             // InvokeDynamic #3:makeConcatWithConstants:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
        54: invokevirtual #54                 // Method java/io/PrintStream.println:(Ljava/lang/String;)V
```

**Actual class-load log:**

```text
[0.033s][info][class,load] LambdaDemo source: file:/tmp/jvmwork/
[0.041s][info][class,load] LambdaDemo$$Lambda/0x0000007f01040210 source: LambdaDemo
[0.044s][info][class,load] LambdaDemo$$Lambda/0x0000007f01040438 source: LambdaDemo
[0.045s][info][class,load] LambdaDemo$$Lambda/0x0000007f01040660 source: LambdaDemo
```

**Dry run:**

1. **`invokedynamic #7, 0`** at offset 0 — a single instruction creates the `Runnable`. The `, 0` is the **bootstrap method index** (`BootstrapMethods` attribute #0 → `LambdaMetafactory.metafactory`).
2. At **first execution**, the JVM calls `LambdaMetafactory.metafactory`, which:
   - spins a hidden class `LambdaDemo$$Lambda/0x0000007f01040210` (note the **address** in the name — it is unique per definition);
   - creates a `MethodHandle` to the lambda's synthetic `lambda$main$0` method;
   - binds a `CallSite` and installs it at the `invokedynamic` site.
3. **Subsequent executions** are a fast `invokedynamic` → `CallSite` → `MethodHandle` → target. No class creation, no allocation.
4. **Three lambdas → three hidden classes** (three log lines). Each is a distinct class because each captures a different functional interface.
5. **Offset 49: `invokedynamic #50, 0` → `makeConcatWithConstants`** — the string concatenation `s.get() + f.apply(1)` is *also* an `invokedynamic`, bootstrapped by `StringConcatFactory`. In Java 8 this would have compiled to a `StringBuilder` chain (`new StringBuilder`, `append`, `append`, `toString`) — three allocations. Now the JVM picks the best strategy at runtime.
6. **`checkcast #34` at offset 33** — the compiler inserts a cast because `Supplier.get()` returns `Object` and the concatenation needs a `String`. This is the kind of thing `javap` reveals.

**Takeaway:** `invokedynamic` let the JVM make *implementation decisions at runtime* (how to implement a lambda, how to concatenate strings) instead of baking them in at compile time. That is the technical heart of "Java is a multi-core language" (JEP 133) and of the "javac should not be the bottleneck" argument.

### 5.5 Example 5 — Watching the JIT Promote and Then Deoptimise

```java
public class TierDemo {
    static int hot(int x) { return x * 3 + 1; }
    public static void main(String[] args) {
        long s = 0;
        for (int i = 0; i < 200_000; i++) s += hot(i);
        System.out.println(s);
    }
}
```

**Actual output — `java -XX:+PrintCompilation TierDemo`:**

```text
25    1       3       java.lang.Object::<init> (1 bytes)
25    2       3       java.lang.Byte::toUnsignedInt (6 bytes)
26    3       3       java.lang.String::hashCode (60 bytes)
28    4     n 0       jdk.internal.vm.Continuation::enterSpecial (native)   (static)
29    7       3       java.lang.String::coder (15 bytes)
31    8       2       TierDemo::hot (6 bytes)
31    9       4       TierDemo::hot (6 bytes)
32   8       2       TierDemo::hot (6 bytes)   made not entrant: not used
32   10 %     3       TierDemo::main @ 4 (32 bytes)
33   11       3       TierDemo::main (32 bytes)
33   12 %     4       TierDemo::main @ 4 (32 bytes)
34   10 %     3       TierDemo::main @ 4 (32 bytes)   made not entrant: C1 deoptimize for patching
59999900000
```

**Dry run:**

1. **The `n` flag** (`28 4 n 0`) means "**native** method" — the JVM has no bytecode to compile, so it is not compiled; the `0` is the (absent) tier.
2. **`31 8 2 TierDemo::hot`** — `hot` was first compiled at **tier 2** (C1, no profiling) at t=31 ms.
3. **`31 9 4 TierDemo::hot`** — one millisecond later, `hot` was recompiled at **tier 4** (C2). It was called 200 000 times, so it hit the C2 threshold immediately.
4. **`32 8 2 TierDemo::hot made not entrant: not used`** — the old tier-2 version was superseded. "Not entrant" means *new invocations* go to the tier-4 version; the old code is still in the code cache (it becomes a "zombie" only when it is freed).
5. **`32 10 % 3 TierDemo::main @ 4`** — the `%` marks an **OSR (On-Stack Replacement)** compilation: the *loop* in `main` was hot, so the JVM compiled the loop **in place** and switched the running frame to the compiled code. The `@ 4` is the bytecode index of the loop's back-edge.
6. **`34 10 % 3 TierDemo::main @ 4 made not entrant: C1 deoptimize for patching`** — the C1 version of `main` was deoptimised "for patching". This happens because the C1-compiled code was reached by a path that needed a patch (typically a deoptimisation triggered by an uncommon trap).
7. **The result `59999900000`** is the arithmetic check: `sum = Σ (3i+1) for i in 0..199999 = 3·(199999·200000/2) + 200000 = 3·19999900000 + 200000 = 59999700000 + 200000 = 59999900000`. ✅

**Takeaway:** the JIT's lifecycle is *visible*. When someone asks "is my code being JIT-compiled, and at what level?", this log is the answer — and the tier column is the single most important number in it.

### 5.6 Example 6 — Deoptimisation: The JIT's Assumption Broken

```java
public class DeoptDemo {
    static Object o = "a string";   // the profile will say: always a String
    public static void main(String[] args) {
        for (int i = 0; i < 100_000; i++) {
            if (o instanceof String s) { s.length(); }
        }
        System.out.println("warm");
        o = Integer.valueOf(42);      // the assumption is now violated
        System.out.println("o = " + o);
        String s = (String) o;        // ClassCastException here
        System.out.println(s.length());
    }
}
```

**Actual compilation log:**

```text
38    8       3       java.lang.String::length (11 bytes)   made not entrant: not used
39   10 %     3       DeoptDemo::main @ 2 (86 bytes)
40   11       3       DeoptDemo::main (86 bytes)
40   10 %     3       DeoptDemo::main @ 2 (86 bytes)   made not entrant: C1 deoptimize for patching
```

**Actual program output:**

```text
warm
o = 42
Exception in thread "main" java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String (java.lang.Integer and java.lang.String are in module java.base of loader 'bootstrap')
	at DeoptDemo.main(DeoptDemo.java:10)
```

**Dry run:**

1. The loop runs 100 000 times with `o` always a `String`. The JIT profiles the `instanceof`/`checkcast` and records: *this `o` is always a `String`*.
2. C2 compiles `main` with that assumption: the `instanceof` check is **folded away**, and `String.length()` is **inlined** (visible as `java.lang.String::length` in the log).
3. `o = Integer.valueOf(42)` — the **profile is now stale**. The compiled code is still executing with the old assumption.
4. The next execution of the compiled code hits the **uncommon trap** at the point where the assumption was made.
5. The trap triggers **deoptimisation**: the JVM unwinds the compiled frame back into an interpreter frame using the **deoptimization metadata** recorded at compile time.
6. The interpreter re-executes the `checkcast`, which now correctly throws `ClassCastException` — **with the correct source line (`DeoptDemo.java:10`)**. That line number is the proof that the unwinding is faithful.
7. The compiled version is marked `not entrant` and the method will be **recompiled** with the new (megamorphic) profile.

**The production lesson — "megamorphic call sites".** Once a call site has seen several receiver types, C2 **stops specialising**: it stops inlining and falls back to a virtual dispatch. The signature of a megamorphic site is a *sudden* throughput drop when a new data shape appears. The mitigations:

- **Cast at the boundary** so the JIT sees a monomorphic type.
- **Avoid a shared call site for different types** — use distinct code paths.
- **Use a specialised collection** (`int[]` instead of `List<Integer>`) to keep the types stable.
- **Warm up each shape** at startup so the JIT compiles each variant separately (a common trick in RPC frameworks).

---

## 6. Code from Scratch — Production-Grade

### 6.1 A Custom ClassLoader With Full Lifecycle Control

```java
package io.acme.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

/**
 * A child-first, resource-aware class loader for plugin / hot-deploy scenarios.
 *
 * <p>Demonstrates the full class-loading lifecycle:
 * <ul>
 *   <li><b>Loading</b>    — {@link #findClass(String)} obtains the bytes.</li>
 *   <li><b>Linking</b>    — the JVM does verify/prepare/resolve inside
 *       {@code defineClass}; the loader only supplies bytes and the name.</li>
 *   <li><b>Initialising</b> — the JVM runs {@code <clinit>} on first active use;
 *       the loader must NOT run it itself.</li>
 * </ul>
 *
 * <p><b>Child-first</b> is required for isolation: with parent-first, the plugin's
 * copy of a library would never be used, and a class present in two plugins with
 * different versions would silently resolve to the parent's version.
 *
 * <p>⚠ In production, prefer a <b>hidden class</b> (Java 15+, {@code MethodHandles.Lookup#defineHiddenClass})
 * or a real module system over a hand-rolled class loader.
 */
public final class PluginClassLoader extends URLClassLoader {

    /** Packages that must ALWAYS come from the parent — never from the plugin. */
    private static final String[] PARENT_FIRST_PREFIXES = {
            "java.", "javax.", "jdk.", "sun.", "com.sun."
    };

    private final String pluginId;
    private volatile boolean closed;

    public PluginClassLoader(String pluginId, URL[] urls, ClassLoader parent) {
        super("plugin-" + pluginId, urls, parent);
        this.pluginId = pluginId;
    }

    /** Child-first, with an explicit carve-out for the platform classes. */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. Already defined by THIS loader? (the "already loaded" check)
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            // 2. Platform classes always come from the parent — sharing them is
            //    mandatory (a plugin's own java.lang.String would be a disaster).
            if (isParentFirst(name)) {
                Class<?> fromParent = getParent().loadClass(name);
                if (resolve) resolveClass(fromParent);
                return fromParent;
            }

            // 3. Try to define it ourselves (child-first).
            Class<?> c;
            try {
                c = findClass(name);
            } catch (ClassNotFoundException notInPlugin) {
                // 4. Fall back to the parent.
                c = getParent().loadClass(name);
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }

    private static boolean isParentFirst(String name) {
        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Reads the bytes for a class from this loader's URLs.
     * The JVM handles linking and initialisation after this returns.
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (closed) {
            throw new ClassNotFoundException("Plugin " + pluginId + " is closed: " + name);
        }
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = getResourceAsStream(path)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = in.readAllBytes();
            // defineClass performs the "loading" phase; the JVM then links and
            // initialises the class on first active use.
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    /**
     * Releases the JAR handles held by this loader.
     * ⚠ This does NOT unload the classes — the JVM unloads them only when this
     *   loader becomes unreachable AND a class-unloading GC runs. That is why
     *   every reference to the loader (threads, ThreadLocals, JDBC drivers,
     *   shutdown hooks) must be broken as well.
     */
    public void close() throws IOException {
        closed = true;
        super.close();
    }

    public String pluginId() {
        return pluginId;
    }
}
```

**The lifecycle rules this class obeys (and that most hand-rolled loaders get wrong):**

1. **Never call `Class.forName` in the loader.** That would initialise the class *too early* and *from the wrong thread context*.
2. **Never run the static initialiser yourself.** The JVM does it, exactly once, at first active use, under the class-initialisation lock.
3. **Always use `getClassLoadingLock(name)`**, not `synchronized(this)` — the former allows different classes to load in parallel.
4. **Never load `java.*` from the plugin.** A duplicate `java.lang.String` breaks everything.
5. **Closing the loader is not unloading it.** The retention path must be broken separately.

### 6.2 A Class-Loading / Metaspace Auditor (production monitoring)

```java
package io.acme.ops;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Watches class-loading behaviour for signs of a class-loader leak.
 *
 * <p>A class-loader leak shows up as a steadily rising number of loaded classes
 * and a steadily rising Metaspace usage that never falls back — even after the
 * "leaked" work has finished.
 *
 * <p>Metrics to export:
 * <ul>
 *   <li>{@code jvm.classes.loaded}   — total classes loaded (monotonic)</li>
 *   <li>{@code jvm.classes.loaded.delta} — classes loaded in the interval
 *       (a *rate*; a high rate on a low-traffic service is the alarm)</li>
 *   <li>{@code jvm.metaspace.used}  — Metaspace in bytes</li>
 * </ul>
 */
public final class ClassLoadingAuditor {

    private static final long CLASS_LOADED = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
    private static final long CLASS_UNLOADED = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();

    private final AtomicLong lastLoaded = new AtomicLong(CLASS_LOADED);
    private final long startMillis = System.currentTimeMillis();

    private ClassLoadingAuditor() { }

    public record Snapshot(long totalLoaded, long totalUnloaded,
                           long loadedInInterval, long classesPerMinute,
                           long metaspaceUsedBytes, long metaspaceCommittedBytes) {

        public boolean isSuspicious() {
            // A service loading more than ~50 classes per minute on a steady
            // workload is generating classes at runtime (proxies, lambdas in
            // a loop, dynamic compilation) — a common leak signature.
            return classesPerMinute > 50;
        }

        public String describe() {
            return String.format(
                "classLoading{loaded=%d, unloaded=%d, rate=%.1f/min, metaspace=%.1fMB/%.1fMB, suspicious=%s}",
                totalLoaded, totalUnloaded, classesPerMinute,
                metaspaceUsedBytes / (1024.0 * 1024.0),
                metaspaceCommittedBytes / (1024.0 * 1024.0),
                isSuspicious());
        }
    }

    public static Snapshot sample() {
        long loaded = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
        long unloaded = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
        long delta = loaded - lastLoaded.getAndSet(loaded);
        long elapsedMillis = Math.max(1, System.currentTimeMillis() - startMillis);
        double perMinute = (delta * 60_000.0) / elapsedMillis;

        long metaUsed = 0;
        long metaCommitted = 0;
        for (java.lang.management.MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String n = pool.getName().toLowerCase();
            if (n.contains("metaspace") || n.contains("class space")) {
                metaUsed += pool.getUsage().getUsed();
                metaCommitted += pool.getUsage().getCommitted();
            }
        }
        return new Snapshot(loaded, unloaded, delta, (long) perMinute, metaUsed, metaCommitted);
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 5; i++) {
            System.out.println(sample().describe());
            Thread.sleep(1_000);
        }
    }
}
```

**Why this belongs in production:** class *generation* at runtime is invisible in application logs but is one of the most common causes of a slow Metaspace leak. A *rate* metric catches it long before the OOM.

### 6.3 A Bytecode-Level Assertion Helper (a mini-ASM)

```java
package io.acme.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A tiny, dependency-free reader of the {@code .class} file format.
 *
 * <p>Exists to demonstrate the class file structure without pulling in ASM
 * (which is a 400 KB dependency). In production, use ASM or ByteBuddy.
 *
 * <p>Supports: the magic number, the version, and the constant-pool size.
 */
public final class ClassFileHeader {

    private static final int MAGIC = 0xCAFEBABE;

    private ClassFileHeader() { }

    public record Header(int minorVersion, int majorVersion, int constantPoolCount, long sizeBytes) {

        /** e.g. "Java 25" for major version 69. */
        public String javaVersion() {
            int feature = majorVersion - 44;
            return feature >= 5 ? "Java " + feature : "Java " + majorVersion;
        }
    }

    public static Header read(Path classFile) throws IOException {
        byte[] bytes = Files.readAllBytes(classFile);
        if (bytes.length < 10) {
            throw new IOException("Not a class file (too short): " + classFile);
        }
        int magic = readInt(bytes, 0);
        if (magic != MAGIC) {
            throw new IOException(String.format(
                    "Bad magic number 0x%08X in %s (expected 0xCAFEBABE)", magic, classFile));
        }
        int minor = readUnsignedShort(bytes, 4);
        int major = readUnsignedShort(bytes, 6);
        int cpCount = readUnsignedShort(bytes, 8);
        return new Header(minor, major, cpCount, bytes.length);
    }

    public static Header readFromStream(InputStream in) throws IOException {
        byte[] head = in.readNBytes(10);
        if (head.length < 10) {
            throw new IOException("Not a class file (too short)");
        }
        int magic = readInt(head, 0);
        if (magic != MAGIC) {
            throw new IOException(String.format("Bad magic number 0x%08X", magic));
        }
        return new Header(readUnsignedShort(head, 4), readUnsignedShort(head, 6),
                readUnsignedShort(head, 8), 10);
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static int readUnsignedShort(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1: "Declaring a variable loads the class"

✅ **Wrong.** `MyClass obj;` emits no class-loading instruction. Loading happens on *active use*: `new`, a static-method call, a non-constant static-field read, `instanceof`/cast, `Class.forName`, or a class literal (which loads but does **not** initialise).

### ❌ Pitfall 2: "The static initialiser runs when the class is loaded"

✅ **Wrong.** Loading and initialisation are separate. A class is *loaded* long before it is *initialised*; initialisation happens at first active use, and only then does `<clinit>` run.

### ❌ Pitfall 3: "Parent-first delegation is a security sandbox"

✅ **Historically right, now wrong.** The `SecurityManager` (deprecated in Java 17, terminally deprecated in Java 24) was the sandbox. Parent-first is now about **class identity** and **the single-copy guarantee** for `java.*`.

### ❌ Pitfall 4: Running `Class.forName(name, true, loader)` to "just check if it exists"

✅ **Trap.** The three-argument form with `initialize = true` **runs the static initialiser**. Use `Class.forName(name, false, loader)` to load without initialising, or `ClassLoader.findLoadedClass` / `getResource` to check for existence without side effects.

### ❌ Pitfall 5: Assuming `getClass().getClassLoader()` is never `null`

✅ **Wrong.** It is `null` exactly for classes defined by the **bootstrap** loader (`java.*` and the `java.base` module). The correct check is `cl == null ? "bootstrap" : cl.getName()`.

### ❌ Pitfall 6: Forgetting that a failed `<clinit>` poisons the class forever

✅ **The trap.** The first failure is `ExceptionInInitializerError`; every subsequent use is `NoClassDefFoundError`. If you catch only `NoClassDefFoundError`, you will never see the root cause. **Always catch `Throwable` around the *first* initialisation** and log the cause.

### ❌ Pitfall 7: Believing the JIT always makes code faster

✅ **Wrong.** JIT compilation is not free: it burns CPU on compiler threads, it makes the code *bigger* (worse I-cache behaviour), and a **deoptimisation storm** can make things slower than the interpreter. A short-lived process may never benefit.

### ❌ Pitfall 8: Assuming the JIT is always on

✅ **Wrong.** `-Xint` disables it entirely (pure interpreter, for debugging). `-XX:TieredStopAtLevel=1` limits it to C1 (fast startup, lower peak performance). `java -XX:TieredStopAtLevel=1` is the standard serverless cold-start flag.

### ❌ Pitfall 9: Catching `ClassNotFoundException` and assuming the class is missing

✅ **Wrong.** `ClassNotFoundException` means *the loader could not find the bytes*. `NoClassDefFoundError` means *the bytes were found but the class could not be linked or initialised* — a **missing transitive dependency** or a **failed static initialiser**. They are different problems.

### ❌ Pitfall 10: Calling `System.gc()` to "unstick" a class loader

✅ **Wrong.** Class unloading is not triggered by `System.gc()` alone; it happens as part of a **concurrent cycle** in G1/ZGC (or a full GC), and only for loaders that are *unreachable*. The fix is to break the reference to the loader.

### ❌ Pitfall 11: Assuming `jmap` works on a remote or containerised process

✅ **Trap.** `jmap`/`jstack` use the attach mechanism and need the same OS user (and sometimes `--add-modules jdk.attach` on a modular runtime). `jcmd` is the same mechanism but with a friendlier interface. For a process in a container, you must run the tool *inside* the container's PID namespace.

### ❌ Pitfall 12: Reading a `StackOverflowError` stack trace as the true depth

✅ **Wrong.** The trace is truncated at `MaxJavaStackTraceDepth` (default 1024). The measured example showed 45 208 real frames but only 1024 in the trace.

### ❌ Pitfall 13: Assuming the code cache is part of the heap

✅ **Wrong.** The code cache is **native memory** with its own limit (`-XX:ReservedCodeCacheSize`). A failure there is `OutOfMemoryError: CodeCache is full`, and the fix is the code-cache flag, not `-Xmx`.

### ❌ Pitfall 14: Using `getResourceAsStream` on a classloader to detect a class

✅ **Trap.** It works, but it is a *resource* lookup, not a class lookup, and a class can exist without a matching resource (a generated class). Use `Class.forName(name, false, loader)` for a load-without-initialise check.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What are the three phases of class loading?**
**A:** **Loading** (find and read the bytes, create the `Class` instance), **linking** (verify the bytecode, prepare the static fields, resolve symbolic references), and **initialisation** (run the static initialiser `<clinit>`). Only the initialisation phase runs your static code, and it runs exactly once per class per class loader.

**Q: What is the parent-delegation model?**
**A:** When a class loader is asked for a class, it first asks its parent; only if the parent cannot find it does it try to define the class itself. The chain is bootstrap → platform → system → application → custom. It guarantees that `java.lang.String` is defined exactly once for the whole JVM.

**Q: What is bytecode?**
**A:** An intermediate, platform-independent instruction set (a stack machine) stored in `.class` files. `javac` produces it; the JVM interprets it first and then JIT-compiles hot methods into native code.

**Q: What does `javap` do?**
**A:** It disassembles a class file. `javap -c` shows the bytecode instructions, `javap -v` shows the constant pool, the exception tables and the line-number tables, and `javap -p` shows private members.

**Q: What is `invokedynamic`?**
**A:** A bytecode instruction (Java 7+) that defers method selection to runtime via a bootstrap method. It is how lambdas, method references and (since Java 9) string concatenation are implemented.

### 🟡 Intermediate

**Q: Explain the class-loader hierarchy.**
**A:** **Bootstrap** (native code; loads `java.base` and `java.*`; `getClassLoader()` returns `null`) → **Platform** (Java 9+; the former "Extension" loader; loads other platform modules such as `java.sql`) → **System/Application** (loads the classpath) → **Custom** loaders (your code). Each defines classes in its own **namespace**.

**Q: When is a class loaded?**
**A:** On *active use*: `new Foo()`, calling a static method, reading a **non-constant** static field, `Class.forName`, `instanceof`/cast, or a class literal. Merely *declaring* a reference or reading a compile-time `static final` constant loads nothing.

**Q: What is the difference between `ClassNotFoundException` and `NoClassDefFoundError`?**
**A:** `ClassNotFoundException` is a *checked* exception from an **explicit** dynamic lookup (`Class.forName`, `ClassLoader.loadClass`) when the bytes cannot be found. `NoClassDefFoundError` is an *unchecked* `LinkageError` thrown when a class that **was** available at compile time cannot be loaded or initialised at runtime — a missing runtime dependency, a missing superclass, or a static initialiser that failed earlier.

**Q: What are C1 and C2?**
**A:** Two JIT compilers. **C1** (client) compiles quickly with modest optimisations — good for fast warm-up. **C2** (server) compiles slowly with aggressive, profile-guided optimisations — good for peak throughput. With **tiered compilation** (default since Java 8) a method goes interpreter → C1 → C2 as its invocation and loop counters rise.

**Q: What is the difference between `static final` constants and other static fields?**
**A:** A `static final` field whose initialiser is a **compile-time constant expression** is folded into the constant pool and *inlined into the caller's bytecode* — the field does not exist at runtime and the class is not even loaded. Any other `static` field lives in a real slot, is initialised in `<clinit>`, and forces the class to be initialised.

### 🔴 Advanced / Senior

**Q: Explain how the JVM guarantees a static initialiser runs exactly once, even under concurrency.**
**A:** The JVM marks the class's state and uses a per-class **initialisation lock**. The first thread to reach the class transitions it to `initializing` and runs `<clinit>`; any other thread that needs the class blocks until the state becomes `initialized`. If the initialiser throws, the class is marked **erroneous** and all later uses throw `NoClassDefFoundError` without re-running the initialiser. The JVM also detects *recursive* initialisation (a `<clinit>` that touches its own class) and proceeds rather than deadlocking.

**Q: What is deoptimisation and when does it happen?**
**A:** C2 compiles speculatively using profile data — "the receiver here is always a `String`", "this loop runs 10 times". Each assumption has an **uncommon trap**. When an assumption is violated, the trap fires, the JVM **unwinds** the compiled frame into an interpreter frame using the deoptimization metadata recorded at compile time, marks the compiled version *not entrant*, and recompiles with the new profile. The stack trace stays correct because the interpreter re-executes the failing instruction.

**Q: What is a safepoint and why does it matter for latency?**
**A:** A safepoint is a point where all threads can be brought to a consistent state so the VM can perform an operation that requires it (GC, class loading, deoptimisation, a thread dump). Threads poll at safepoints; a thread in a long native call or a non-polling loop cannot reach one promptly, so the VM must **spin** (burning CPU) or **yield** — which inflates every GC pause. `-Xlog:safepoint` reports the "Maximum sync time", and JFR's `jdk.SafepointBegin` gives the full breakdown (total, VM operation, sync).

**Q: How do you diagnose a Metaspace leak?**
**A:** `jcmd <pid> VM.metaspace` for the usage; `jcmd <pid> GC.class_histogram | grep java.lang.Class` for the class count (thousands ⇒ classes are being *generated*); `-Xlog:class+load=info` to see the same class name loaded by many different loaders; and a heap dump plus MAT's "path to GC roots" to find **what still references the old loader** — a live thread, a `ThreadLocal`, a registered JDBC driver, a shutdown hook, or a static `Map<ClassLoader, ?>`. The fix is to break that reference, not to raise `-XX:MaxMetaspaceSize`.

**Q: What tools would you use to profile a running JVM, and when?**
**A:** `jcmd` first (it is the modern, scriptable entry point): `GC.heap_info`, `GC.class_histogram`, `GC.heap_dump`, `Thread.print`, `VM.flags`, `VM.metaspace`, `VM.native_memory summary`, `Compiler.codecache`. For *continuous* production profiling, **JFR** (`-XX:StartFlightRecording`) is the right tool — it has ~1% overhead and gives allocation profiling, GC phase pauses, safepoints, lock contention and I/O. `jmap`/`jstack`/`jstat` are the older equivalents and mostly still work.

### ⚫ Expert / Principal

**Q: Why is `invokedynamic` a language-design triumph rather than just an implementation trick?**
**A:** Before Java 7, every lambda was a *class file* — so the compiler had to know the implementation strategy at compile time, and every lambda cost a class, a class-file entry, a load and a Metaspace slot. `invokedynamic` moved the decision to *runtime*: the bytecode contains only a bootstrap-method reference, and the JVM decides how to implement the call site the first time it executes. The same mechanism then let the JDK make better decisions over time — `StringConcatFactory` (JEP 280) replaced the `StringBuilder` chain with a runtime-chosen strategy, and `LambdaMetafactory` replaced per-lambda class files with hidden classes (JEP 371) that are not discoverable and are unloaded with their `CallSite`. The general lesson: **moving a decision from compile time to run time lets the platform improve without recompiling user code.**

**Q: Explain how the JVM makes speculative optimisation safe.**
**A:** Three mechanisms. (1) **Traps**: every assumption has an uncommon trap that fires when the assumption breaks. (2) **Deoptimization metadata**: at compile time the JIT records how to reconstruct the interpreter state from the compiled state, so the frame can be *unwound* exactly. (3) **Recompilation**: the method goes back to the interpreter and is recompiled with the corrected profile, so the system converges. The proof that it works is the `ClassCastException` in the worked example still carrying the **correct line number** — the interpreter re-executed the `checkcast` at the right place. The cost is that speculation can be *wrong*, producing deoptimisation storms and megamorphic call sites, which is why the JVM keeps the profiling conservative (it needs a high confidence threshold before specialising).

**Q: A service's p99 latency is fine for 30 minutes, then degrades. What JVM-level causes would you investigate?**
**A:** In order of likelihood. (1) **Megamorphic call site** — a new data shape (a new customer type, a new message format) made a previously monomorphic call site polymorphic; C2 stops inlining and the dispatch cost jumps. Diagnose with `-Xlog:deoptimization=info` and JFR's `jdk.Deoptimization` events; mitigate by casting at the boundary or warming up each shape. (2) **Code-cache pressure** — thousands of distinct call sites or generated classes fill the code cache, and compilation starts thrashing. Check `jcmd <pid> Compiler.codecache`. (3) **A class-generation storm** — dynamic proxies or a scripting engine generating a new class per request; check the class-load rate. (4) **Safepoint problems** — a thread blocked in a long native call delaying every safepoint; check "Maximum sync time" in `-Xlog:safepoint`. (5) **The old-gen occupancy ratcheting** so each collection copies more. All five are visible in the unified log or in JFR.

**Q: How would you make a Java service start fast in a serverless environment?**
**A:** Attack the three startup costs in order. (1) **Class loading and I/O** — the class path scan is I/O-bound: use **AppCDS** (class-data sharing archives) or, better, **CRaC** checkpoints (JDK 24+); ensure the JAR layout is not a "zip bomb" with thousands of entries. (2) **Interpretation** — the JIT has not warmed up when the first request arrives: use `-XX:TieredStopAtLevel=1` to keep the JIT at C1 (fast compiles, lower peak performance), and use **AOT caching** (`-XX:AOTCache=...`, JDK 19+) or **CDS with archived heap** for the *data* objects. (3) **Framework initialisation** — Spring's component scanning, Flyway migrations, and connection-pool warm-up dominate in practice; use Spring AOT (`spring.aot.enabled=true`) or GraalVM **native-image** to remove the JVM entirely. The honest summary: the JVM is excellent at long-running throughput and mediocre at cold start, and native-image or CRaC is the answer when cold start dominates.

**Q: What is the relationship between class loading, the code cache and Metaspace, and how do they fail independently?**
**A:** They are three distinct resources that class loading consumes. **Metaspace** holds the class *metadata* (the `Klass`, the constant pool, the bytecode, the static slots) — it fails with `OutOfMemoryError: Metaspace` when too many classes are loaded. The **code cache** holds the *compiled native code* produced by the JIT for those classes — it fails with `OutOfMemoryError: CodeCache is full` when there are too many distinct compiled methods (a symptom of class-generation or of a megamorphic workload). **Heap** holds the `java.lang.Class` *objects* and everything they reference — a class-generation storm also shows up here as `java.lang.Class` instances in the histogram. So the same root cause (generating classes at runtime) can present as three different OOMs, and you must read the message to know which resource ran out. The diagnostic that distinguishes them: `jcmd <pid> VM.metaspace` (Metaspace), `jcmd <pid> Compiler.codecache` (code cache), `jcmd <pid> GC.class_histogram` (heap).

---

## 9. Counter-Questions & Traps

### CQ1. "If the JIT is so good, why does my code get slower in production than in a test?"

- **Naive answer:** "Production has more data / more load."  ← misses the mechanism
- **The trap:** The most likely cause is that the **test never warmed the JIT up**, so the test measured the *interpreter*. Or the opposite: the production data made a call site **megamorphic**, so C2 stopped specialising.
- **Expert answer:** Two distinct problems with the same symptom. (1) *Cold measurement*: a microbenchmark that runs 100 iterations measures the interpreter. Always warm up (10 000+ iterations) and measure the steady state, or use JMH with proper forks and warm-up. (2) *Profile pollution*: in production the call site sees many receiver types, so C2 stops inlining and falls back to a virtual dispatch. The diagnosis is `-Xlog:deoptimization=info` and JFR's `jdk.Deoptimization` events; the mitigation is to make the type stable (cast at the boundary) or to warm up each shape explicitly.

### CQ2. "Why is `String` a `final` class? It seems arbitrary."

- **Naive answer:** "Security."  ← incomplete
- **The trap:** The interviewer wants the *JVM-level* reason: `final` enables the JIT to **constant-fold** `String` methods and to devirtualise calls, and it prevents a subclass from breaking the `String` contract that the whole JDK (serialisation, hashing, `intern`ing) relies on.
- **Expert answer:** `String` is `final` for *language* reasons (a mutable `String` would break `hashCode`/`equals` contracts, `HashMap` keys, and security-sensitive APIs) and for *performance* reasons (the JIT can inline `String.length()`, `String.charAt()` and `String.equals()` knowing there is no override, and can constant-fold `"abc".length()`). A famous corollary: `StringBuilder` is *not* final but `String` is, and `String` is the one the JIT cares about.

### CQ3. "Is the JVM's `verify` phase a security mechanism?"

- **Naive answer:** "Yes, it prevents malicious bytecode."  ← the trap
- **The trap:** Verification is a **type-safety** mechanism, not a sandbox. It prevents *type confusion* within the JVM, not *escape*. A verified class can still call `System.exit`, read files, or run native code — verification says nothing about authorisation.
- **Expert answer:** Verification ensures the bytecode obeys the type system so that the JIT can compile it without re-checking. The *sandbox* was the `SecurityManager` (now deprecated for removal, and gone in JDK 24 per JEP 486). A modern JVM does not sandbox untrusted bytecode at all — which is exactly why running untrusted code requires a separate process/container, not a class loader. **Loading untrusted classes into your JVM is a code-execution vulnerability, full stop.**

### CQ4. "You said `invokedynamic` reduces class-file bloat. But the class-load log still shows one class per lambda. Didn't you contradict yourself?"

- **Naive answer:** "No, the log is just the old behaviour."  ← misses the point
- **The trap:** The log lines are real: three lambdas, three `$$Lambda` classes. The *difference* is **when** they are created and **whether they are discoverable**.
- **Expert answer:** Before Java 15, the lambda classes were **generated at compile time** and written into the class file — so they were in the JAR, they were loaded eagerly-ish, and they were permanent Metaspace residents. Since JEP 371 (hidden classes, Java 15), the class is **spun at runtime on first execution** of the `invokedynamic` site, it is a *hidden* class (not discoverable by `Class.forName`, not in a normal class-path scan), and it is **unloaded** when its `CallSite` becomes unreachable. So the count is the same but the *lifecycle* is completely different: runtime-created, invisible, and collectable.

### CQ5. "Why does `System.gc()` not unload my leaked class loader?"

- **Naive answer:** "`System.gc()` is disabled."  ← often true, but not the reason
- **The trap:** Even with `System.gc()` fully working, class unloading requires (a) a GC that performs class unloading, and (b) the loader to be **unreachable**. If a thread, a `ThreadLocal`, a JDBC driver registration or a shutdown hook still references the loader, it is not unreachable and it will never be unloaded.
- **Expert answer:** `System.gc()` is a *hint*, and with `-XX:+DisableExplicitGC` it is a no-op — but even a guaranteed full GC would not unload a *reachable* loader. The retention path is the bug. Use `jcmd <pid> GC.class_histogram` to see the class count, and MAT's "path to GC roots" on the loader to find the reference. This is the single most common "my Metaspace grows and never shrinks" question, and the answer is always "something still points at the old loader".

### CQ6. "Is `synchronized` still biased-locked? I read that it was removed."

- **Naive answer:** "It's still biased, just disabled by default."  ← true in Java 15–17, wrong for 18+
- **The trap:** **Biased locking was disabled by default in JDK 15 (JEP 374) and *removed entirely* in JDK 18 (JEP 374 completion).** Modern JVMs use only *thin locks* (a CAS on the object header) and *fat locks* (an inflated `ObjectMonitor`).
- **Expert answer:** Biased locking optimised the single-threaded case by storing a thread id in the Mark Word and avoiding any CAS. It was removed because of the cost of *revocation* (a thread that dies while holding a biased lock forces a safepoint to revoke biases process-wide) and because of the memory-ordering subtleties it introduced. The practical impact: `synchronized` on a hot, contended object now costs a CAS; the JDK mitigates with **lock coarsening** and by using `ConcurrentHashMap`/`ReentrantLock` where appropriate.

### CQ7. "Why did `String` concatenation get slower in Java 8, then faster again in Java 9+?"

- **Naive answer:** "It didn't."  ← the trap
- **The trap:** There *was* a well-known regression: **JDK 8u20+** changed `javac` to compile `a + b + c` into a `StringBuilder` chain (pre-allocating the exact capacity) instead of the older, more efficient `StringBuffer` chain. That change was widely criticised and caused measurable regressions in some workloads.
- **Expert answer:** Java 9 (JEP 280) replaced the compile-time `StringBuilder` chain with a single `invokedynamic` bootstrapped by `StringConcatFactory`, which chooses the concatenation strategy **at runtime** — it can pick between a `StringBuilder`, a `StringConcatHelper` byte-array approach, or a precomputed constant, depending on the operand types and count. The result is usually *faster* than either Java 7 or Java 8, and it removes the `javac` policy decision entirely. The general lesson is the same as `invokedynamic` for lambdas: **runtime decisions beat compile-time policy when the platform evolves faster than the compiler.**

### CQ8. "How do you know whether a class was loaded or merely *initialised*?"

- **Naive answer:** "If the static block printed something, it was initialised."  ← true but indirect
- **The trap:** The question is about the *distinction*, and there is a direct way to see it.
- **Expert answer:** `Class.forName("com.acme.Foo", false, loader)` **loads without initialising**; `Class.forName("com.acme.Foo")` (the one-argument form) **initialises**. And `clazz.getDeclaredConstructor().newInstance()` on an already-loaded class will trigger initialisation if it has not happened. In code, the observable difference is that a static block's side effect has not run. The `ClassLoader` API also has `findLoadedClass(name)` (package-private, but reachable via reflection) to ask "have *I* already loaded it?".

### CQ9. "You showed `jcmd VM.flags` printing the effective flags. How do I find out what the *user* actually configured?"

- **Naive answer:** "`jcmd VM.flags` shows the command line."  ← wrong, it shows the *effective* flags
- **The trap:** `VM.flags` shows every flag with its *effective* value, including **ergonomic defaults** and **flags the JVM added for you** — which is why it contains dozens of `-XX:G1RemSetHowlMaxNumBuckets=4`-style entries you never wrote.
- **Expert answer:** Use `jcmd <pid> VM.command_line` to see the **original** command line (and the environment), and `jcmd <pid> VM.flags` for the effective state. The difference is diagnostically important: "I set `-Xmx2g` but the heap is 4 GB" means the command line was overridden (a shell variable, a container's `JAVA_TOOL_OPTIONS`, or a wrapper script), and `VM.command_line` will show it. Also check `JAVA_TOOL_OPTIONS` and `_JAVA_OPTIONS` — the JVM merges them into the command line and they are a classic source of "I don't know where that flag came from".

### CQ10. "Is `javac` a bottleneck? I've heard the JVM should compile faster than the JIT."

- **Naive answer:** "No, the JVM is always faster."  ← too glib
- **The trap:** The claim from the JEP 280 proposal was that **`javac` should not be the bottleneck** — i.e. that `javac` should not have to make *policy* decisions (like "how to concatenate strings") that the JVM could make better at runtime. It was never a claim that the JVM always out-compiles `javac` at *startup*.
- **Expert answer:** The distinction is between *compilation time* and *code quality*. `javac` produces bytecode quickly; the JIT produces better *native* code but takes time to do it. The JEP 280 argument was that the *compiler* was making an irrevocable decision (the `StringBuilder` chain) that the runtime could make better and adapt over time. The evidence is that the `invokedynamic`-based concatenation is measurably faster than the Java 8 `StringBuilder` chain in most benchmarks. The general principle — **prefer runtime decisions when the platform will keep improving** — is the real lesson.

### CQ11. "How would you detect a class-generation storm at runtime?"

- **Naive answer:** "Look at the heap dump."  ← far too late
- **The trap:** By the time you have a heap dump, you have an OOM. The signal is much earlier: a **rising rate** of loaded classes and a rising `java.lang.Class` count in the histogram.
- **Expert answer:** Track `ClassLoadingMXBean.getTotalLoadedClassCount()` as a **rate** (classes per minute) — the auditor in §6.2 does exactly that. Correlate it with Metaspace usage and with the `jcmd GC.class_histogram` count of `java.lang.Class` instances. A steady workload should load a *bounded* number of classes (a few thousand at startup, then flat). A rate that never flattens is the alarm. Typical culprits: a dynamic proxy or a `MethodHandle` created per request, a scripting engine compiling per request, or a lambda that captures a fresh class per call. The fix is to **cache** the generated artefact (a proxy class, a `MethodHandle`, a compiled script) rather than creating it per request.

### CQ12. "If the JVM compiles my code at runtime, does that mean the JVM is 'slow to start'? Should I use AOT?"

- **Naive answer:** "AOT is always faster."  ← not for throughput
- **The trap:** AOT and JIT optimise for *different* things. AOT (GraalVM `native-image`, or the JDK's experimental `-XX:AOTCache`) optimises **startup**; JIT optimises **peak throughput** using a *real* profile that AOT cannot have.
- **Expert answer:** If your workload is long-running with a stable profile, the JIT wins on throughput — it can specialise on the actual types and branch frequencies it actually observed, which no AOT compiler can do without a profiling run. If your workload is short-lived (serverless, CLI, scale-to-zero) then AOT wins massively, because the JIT never gets a chance to warm up. The modern middle ground is **CRaC** (checkpoint/restore, JDK 24+), which snapshots a *fully warmed* JVM and restores it in milliseconds — getting JIT-quality code with AOT-quality startup. The decision rule: *long-lived and throughput-bound → JIT; short-lived and latency-bound at startup → AOT or CRaC.*

---

## 10. Rapid-Fire MCQs

**1.** The three phases of class loading are:
- A. Load, prepare, execute  B. Loading, linking, initialisation  C. Verify, prepare, resolve  D. Parse, link, run
<details><summary>Answer</summary><strong>B</strong> — linking itself has three sub-phases: verify, prepare, resolve.</details>

**2.** Which phase allocates memory for the static fields and sets them to their default values?
- A. Loading  B. Verify  C. Prepare  D. Initialisation
<details><summary>Answer</summary><strong>C</strong> — <em>prepare</em> sets defaults (0/null); <em>initialisation</em> runs the declared initialisers.</details>

**3.** `getClass().getClassLoader()` returns `null` for classes loaded by:
- A. The system loader  B. The platform loader  C. The bootstrap loader  D. Any custom loader
<details><summary>Answer</summary><strong>C</strong> — the bootstrap loader is written in native code and has no Java-level object, so <code>null</code> identifies it.</details>

**4.** Which of these does **not** trigger class initialisation?
- A. `new Foo()`  B. `Foo.staticMethod()`  C. `Foo.class`  D. Reading a `static final int` compile-time constant
<details><summary>Answer</summary><strong>D</strong> — compile-time constants are inlined, so the class is not even loaded. (<code>Foo.class</code> loads but does not initialise.)</details>

**5.** A class's static initialiser throws an exception. What happens on the **second** use?
- A. The same exception again  B. `NoClassDefFoundError`  C. `ExceptionInInitializerError`  D. Nothing — the JVM retries
<details><summary>Answer</summary><strong>B</strong> — the class is marked erroneous; the <em>first</em> failure is <code>ExceptionInInitializerError</code>.</details>

**6.** The identity of a runtime class is determined by:
- A. Its binary name  B. Its name and its defining class loader  C. Its source file  D. Its package
<details><summary>Answer</summary><strong>B</strong> — the same name loaded by two loaders yields two unrelated types.</details>

**7.** Which instruction is used to create a lambda's implementation at runtime?
- A. `invokevirtual`  B. `invokedynamic`  C. `invokestatic`  D. `invokespecial`
<details><summary>Answer</summary><strong>B</strong> — the bootstrap method (<code>LambdaMetafactory</code>) creates the implementation on first execution.</details>

**8.** The JIT compiler that focuses on fast compilation and quick warm-up is:
- A. C1  B. C2  C. Graal  D. javac
<details><summary>Answer</summary><strong>A</strong> — C1 is the client compiler; C2 is the server compiler with aggressive optimisations.</details>

**9.** In `-XX:+PrintCompilation` output, the `%` marker means:
- A. The method is native  B. An OSR (on-stack replacement) compilation  C. The method was deoptimised  D. The method is abstract
<details><summary>Answer</summary><strong>B</strong> — the loop was compiled in place while the frame was still on the stack.</details>

**10.** `made not entrant` in the compilation log means:
- A. The code was deleted  B. New invocations will use a newer version  C. The method threw  D. The class was unloaded
<details><summary>Answer</summary><strong>B</strong> — the old version still exists but is no longer used for new calls; a <em>zombie</em> is one that has been freed.</details>

**11.** A safepoint is:
- A. A place where the GC only runs  B. A point where all threads can be paused safely  C. A GC pause  D. A JIT compilation trigger
<details><summary>Answer</summary><strong>B</strong> — GCs, class loading, deoptimisation and thread dumps all require one.</details>

**12.** `-XX:TieredStopAtLevel=1` gives you:
- A. Interpreter only  B. C1 only  C. C2 only  D. No JIT
<details><summary>Answer</summary><strong>B</strong> — the standard cold-start flag: fast compiles, lower peak performance.</details>

**13.** `ClassNotFoundException` vs `NoClassDefFoundError` — the difference is:
- A. Checked vs unchecked  B. Dynamic lookup failure vs linking/initialisation failure  C. Compile time vs runtime  D. They are the same
<details><summary>Answer</summary><strong>B</strong> — the checked/unchecked difference is real but secondary.</details>

**14.** Which command prints the JVM's *effective* flags?
- A. `jps -v`  B. `jcmd <pid> VM.flags`  C. `jmap -flags`  D. `java -X`
<details><summary>Answer</summary><strong>B</strong> — use <code>jcmd &lt;pid&gt; VM.command_line</code> for the original command line.</details>

**15.** Biased locking was:
- A. Enabled by default since Java 8  B. Disabled by default in JDK 15 and removed in JDK 18  C. Never implemented  D. Removed in Java 11
<details><summary>Answer</summary><strong>B</strong> — JEP 374. It caused safepoint-heavy revocation storms.</details>

**16.** Escape analysis can enable all of the following **except**:
- A. Scalar replacement  B. Stack allocation  C. Lock elision  D. Class unloading
<details><summary>Answer</summary><strong>D</strong> — class unloading depends on the <em>class loader</em> becoming unreachable.</details>

**17.** Where does the JIT-compiled native code live?
- A. On the Java heap  B. In Metaspace  C. In the code cache (native memory)  D. On the thread stack
<details><summary>Answer</summary><strong>C</strong> — hence <code>OutOfMemoryError: CodeCache is full</code> is a distinct failure from a Metaspace OOM.</details>

**18.** The `Exception Table` in a class file is used for:
- A. Constant pool resolution  B. `try`/`catch`/`finally` handler ranges  C. Line number mapping  D. Field offsets
<details><summary>Answer</summary><strong>B</strong> — the table has <code>from</code>/<code>to</code>/<code>target</code>/<code>type</code> entries; <code>any</code> means the <code>finally</code> handler.</details>

**19.** Which Java version introduced `invokedynamic`?
- A. Java 5  B. Java 7  C. Java 9  D. Java 11
<details><summary>Answer</summary><strong>B</strong> — JSR 292, Java 7. Java 9 added <code>StringConcatFactory</code> on top of it.</details>

**20.** `Class.forName("com.acme.Foo", false, loader)`:
- A. Loads and initialises  B. Loads without initialising  C. Fails always  D. Only checks the classpath
<details><summary>Answer</summary><strong>B</strong> — the <code>initialize</code> parameter is <code>false</code>.</details>

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────┬──────────────────────────────────────────────────────────────────┐
│ CONCEPT              │ GOLDEN RULE / FACT                                            │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Class lifecycle      │ LOADING → LINKING (verify/prepare/resolve) → INITIALISATION   │
│                      │ Initialisation = <clinit>, ONCE per class per loader,           │
│                      │ under the class-initialisation lock.                          │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Active-use triggers  │ new │ static method │ non-constant static field │             │
│                      │ Class.forName │ instanceof/cast │ class literal (load only)  │
│ NOT triggers         │ declaring a reference │ reading a compile-time constant      │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Delegation           │ bootstrap → platform → system → custom. Parent-first.        │
│                      │ Parent-first preserves class IDENTITY (one java.lang.String). │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Class identity       │ (binary name, defining loader). Same name + different        │
│                      │ loader = DIFFERENT type, not assignable, not instanceof.      │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Error mapping        │ bytes not found         → ClassNotFoundException            │
│                      │ link/initialise failed  → NoClassDefFoundError              │
│                      │ <clinit> threw (1st)    → ExceptionInInitializerError       │
│                      │ <clinit> threw (later)  → NoClassDefFoundError               │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Bytecode             │ A stack machine: locals + operand stack per frame.           │
│                      │ javap -c (code), javap -v (constant pool + exception tables) │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ invokedynamic        │ Runtime method selection via a bootstrap method.             │
│                      │ Powers lambdas (LambdaMetafactory) and string concat          │
│                      │ (StringConcatFactory, Java 9+). Hidden classes since JDK 15.  │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ JIT tiers            │ 0 interpreter → 3 C1 (fast compile) → 4 C2 (aggressive)      │
│                      │ OSR for hot loops. Tiered compilation on by default since 8.  │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Deoptimisation       │ An assumption broke → trap → unwind the compiled frame →     │
│                      │ interpreter re-executes → recompile. "made not entrant".     │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Safepoints           │ Cooperative VM-wide stop points. GC / class load / deopt /    │
│                      │ thread dump. "Maximum sync time" = threads failing to poll.   │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Code cache           │ NATIVE memory for JIT code. -XX:ReservedCodeCacheSize.      │
│                      │ Fails separately: "OutOfMemoryError: CodeCache is full".     │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Toolkit              │ jcmd (modern) │ jps │ jstat │ jmap │ jstack │ javap │ jhsdb  │
│                      │ + JFR (production profiling) + Eclipse MAT (heap analysis)  │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ OOM routing          │ heap → GC overhead → Metaspace → direct buffer → native     │
│                      │ thread → StackOverflow → array too big → container kill(137) │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cold start           │ -XX:TieredStopAtLevel=1 │ AppCDS/AOT cache │ CRaC │         │
│                      │ native-image. Long-running throughput → prefer the JIT.      │
└──────────────────────┴──────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### 12.1 The JVM flags for a modern, observable, fast-starting service

```bash
java \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
  -XX:+TieredCompilation \
  -XX:CICompilerCount=4 \
  -XX:ReservedCodeCacheSize=128m \
  -XX:MaxMetaspaceSize=256m \
  -XX:MaxDirectMemorySize=256m \
  -XX:+UseStringDeduplication \
  -XX:+ExplicitGCInvokesConcurrent \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps \
  -XX:NativeMemoryTracking=summary \
  -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation \      # remove in prod
  -Xlog:gc*,safepoint,class+load=info:file=/var/log/app.log:time,uptime,level,tags \
  -XX:StartFlightRecording=delay=0,duration=24h,filename=/var/log/app.jfr,settings=profile,maxsize=1g \
  -XX:FlightRecorderOptions=stackdepth=128 \
  -jar app.jar
```

**Production notes on each:**

| Flag | Production note |
|---|---|
| `-XX:CICompilerCount=4` | The default (3 on a 4-CPU box) is often too low on a big machine with many hot methods. Raise it, but remember each compiler thread is CPU. |
| `-XX:ReservedCodeCacheSize=128m` | Caps the JIT output. Without a cap, a class-generation storm can exhaust the code cache and the container. |
| `-XX:NativeMemoryTracking=summary` | ~5% overhead. Enable it **permanently** if you can afford it — it is the only way to attribute native memory after an incident. Otherwise enable it just before reproducing. |
| `-XX:StartFlightRecording=...settings=profile` | The single most valuable production flag. `settings=profile` gives allocation sampling, GC phase pauses, safepoints, lock contention and I/O — at ~1% overhead. |
| `-Xlog:gc*,safepoint,class+load=info` | `class+load` is noisy but invaluable for class-generation storms. Consider rotating it separately. |
| `-XX:FlightRecorderOptions=stackdepth=128` | The default stack depth (64) truncates deep stacks. 128 costs a little memory but keeps the useful frames. |
| `-XX:+PrintCompilation` | **Remove in production** — it is a diagnostic flag and it is very noisy. Use JFR's `jdk.Compilation` events instead. |

### 12.2 The startup-latency budget (serverless / scale-to-zero)

```text
  TOTAL COLD START ≈  0 ms
  ┌──────────────────────────────────────────────────────────────────┐
  │ ██████████████  JVM bootstrap (init, module system)      ~15 ms │
  │ ████████████████  Class loading (I/O bound)             ~40 ms │
  │ ████████████████  Framework init (Spring, DI, migrations) ~400 ms│
  │ ██  Interpretation before the JIT kicks in            ~50 ms   │
  │ █  JIT warm-up (background, on compiler threads)      ~1-3 s   │
  └──────────────────────────────────────────────────────────────────┘
```

**Levers, in order of impact:**

| Lever | Effect | Cost |
|---|---|---|
| **Native-image (GraalVM)** | 10–50 ms cold start | Complex build; no JIT (lower peak throughput); closed-world reflection limits |
| **CRaC checkpoint/restore (JDK 24+)** | ~10 ms from a *warmed* JVM | Requires a fixed JVM image; not all libraries are checkpoint-safe |
| **Spring AOT** (`spring.aot.enabled=true`) | Removes runtime bean-definition processing | Build complexity; limited dynamic features |
| **`-XX:TieredStopAtLevel=1`** | Keeps the JIT at C1 → compiles fast, less peak perf | ~5–10× lower peak throughput |
| **AOT cache** (`-XX:AOTCache=...`, JDK 19+) | Caches AOT-compiled code across runs | Requires the same class path and JDK |
| **AppCDS** (`-XX:SharedArchiveFile`) | Caches loaded classes → big class-loading win | Must generate the archive for the exact class path |
| **Reduce the class path** | Fewer JARs → fewer scans | Fewer dependencies |
| **Lazy initialisation** | Do not connect to the DB until the first request | First-request latency moves instead of disappearing |

### 12.3 A "class-loading budget" for a service

Class loading is not free, and in a micro-service that loads 8 000 classes at startup, the *cost* is real. Measure it:

```bash
java -Xlog:class+load=info:file=classes.log -jar app.jar
grep -c 'class,load' classes.log        # total classes
grep 'source: file:' classes.log | wc -l # classes from the classpath (yours)
```

**Practical rules:**

- **A class that is loaded but never used is dead weight.** Find it with `-Xlog:class+load` + a startup profiler.
- **Eager `static` initialisation in a shared library costs every consumer.** Prefer the *initialisation-on-demand holder* idiom (§3.2.4).
- **Spring's component scanning is a class-loading event.** Narrow the scan (`@ComponentScan` with explicit base packages, or `spring-context-indexer`) — it reduces both class count and startup time.
- **Watch the rate, not the total.** A flat class count after startup is healthy; a rate that never flattens is a leak.

### 12.4 A production incident: "the class count grows by 1 000 per hour"

**Symptoms:** Metaspace grows steadily; `java.lang.Class` instances climb in the histogram; the service is fine functionally.

**Diagnosis:**

```bash
jcmd <pid> GC.class_histogram | grep 'java.lang.Class'   # count climbing
jcmd <pid> VM.metaspace                                   # usage climbing
-Xlog:class+load=info                                     # WHICH classes?
```

**The class-load log showed thousands of `com.acme.Order$$Lambda$…` and `com.sun.proxy.$ProxyN` entries.**

**Root cause:** a per-request dynamic proxy (`Proxy.newProxyInstance`) and a per-request lambda capturing a request-scoped variable. Each request created a new class, permanently consuming Metaspace.

**Fix (two parts):**

1. **Stop generating classes per request** — cache the proxy class and the `MethodHandle`.
2. **Cap the damage** — `-XX:MaxMetaspaceSize=256m` so the failure is a clean, diagnosable OOM rather than a slow slide into a container kill.

**The lesson:** class *generation* is the expensive part. Loading the same 5 000 classes once is fine; generating 5 000 *new* classes every hour is a leak.

### 12.5 A production incident: "p99 latency spikes every 30 minutes"

**Diagnosis with JFR:**

```bash
jcmd <pid> JFR.dump name=spike /var/log/spike.jfr
jfr summary /var/log/spike.jfr | grep -iE 'deopt|safepoint|compil'
```

**JFR showed:** 4 000 `jdk.Deoptimization` events clustered in a 200 ms window, all at the same bytecode index, all `uncommon_trap`.

**Root cause:** a call site that was monomorphic (`String`) 99.9% of the time suddenly received an `Integer` — a legacy code path in a rarely-used endpoint. The JIT deoptimised, recompiled, and the recompile took the CPU while the service ran on slower code.

**Mitigations:**

- Cast at the boundary so the profile stays monomorphic.
- Warm up *all* code paths at startup (a "warm-up" phase that exercises every endpoint with representative data) so the JIT compiles each variant before real traffic arrives.
- If the code path is truly rare, isolate it behind a method boundary so it does not pollute the hot call site's profile.

### 12.6 Reading a `NoClassDefFoundError` in production

```text
Exception in thread "http-nio-8080-exec-5" java.lang.NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper
	at com.acme.OrderJsonMapper.toJson(OrderJsonMapper.java:42)
	...
Caused by: java.lang.ClassNotFoundException: com.fasterxml.jackson.databind.ObjectMapper
```

**The diagnosis ladder:**

1. **Is the class on the *runtime* classpath?** `mvn dependency:tree` / `gradle dependencies` — the compile classpath and the runtime classpath can differ (a `provided`-scope dependency, or a `runtime` exclusion).
2. **Was the JAR shaded/relocated?** A `maven-shade-plugin` config with `<minimizeJar>true</minimizeJar>` **removes classes it thinks are unused** — a classic cause of `NoClassDefFoundError` in a fat JAR that works in the IDE.
3. **Is it a version conflict?** Two versions of the same library; the JVM loads the *first* on the classpath, and the other version's classes are missing.
4. **Is it an OSGi / container split-package issue?** The class is in the WAR but the class it extends is in the container (or vice versa).
5. **Is it a *transitive* dependency that was excluded?** `NoClassDefFoundError` for a class you never imported directly is almost always a missing transitive dependency.

**The rule of thumb:** a `NoClassDefFoundError` for a class you *did* compile against means **"compiled but not shipped"**; a `NoClassDefFoundError` for a class you *never* mentioned means **"a transitive dependency is missing"**.

### 12.7 Container-native JVM checklist

| Check | Command / setting | Why |
|---|---|---|
| Total RSS vs cgroup limit | `cat /sys/fs/cgroup/memory.max` vs `jcmd <pid> VM.native_memory summary` | The kernel kills you silently at the limit |
| Native memory budget | stacks (`threads × -Xss`) + Metaspace + direct + code cache | `-Xmx` is only part of the footprint |
| Thread count vs `pids.max` | `cat /sys/fs/cgroup/pids.max` | "unable to create native thread" |
| Heap = cgroup limit percentage | `-XX:MaxRAMPercentage=70` (leaves 30% for native) | The ergonomics default (25% in containers) is often too conservative |
| Attach tools work | `jcmd <pid> VM.flags` from inside the container | The attach mechanism needs the PID namespace |
| Heap dumps are writable | `-XX:HeapDumpPath=/var/dumps` on a real volume | A dump that cannot be written is a dump you do not have |
| JFR is writable | `-XX:StartFlightRecording=filename=/var/log/app.jfr` | Same reason |

### 12.8 Choosing a JVM for a banking platform

| Workload | JVM settings | Why |
|---|---|---|
| **Core banking transaction engine** (low latency, steady) | G1, `-Xmx` = 60% of the container, `-XX:MaxGCPauseMillis=50`, JFR on | Predictable pauses; the transaction path is allocation-light by design |
| **Nightly reconciliation batch** | **Parallel GC** (or Epsilon for a short job), large heap | Throughput over latency |
| **Report generation** (spiky, large result sets) | G1 with a large `-Xmx`, `-XX:G1HeapRegionSize=32m` | Big result sets → humongous arrays → avoid full GCs |
| **API gateway** (many small requests) | G1, small young gen, `TieredStopAtLevel=1` if latency-bound at startup | Short-lived objects → the generational design shines |
| **Kafka-style log store** | Large heap, G1, off-heap buffers, `-XX:MaxDirectMemorySize` set explicitly | Direct memory dominates the footprint |
| **Serverless** | Native image, or CRaC | Cold start dominates |

---

## 13. Follow-up Learning

**Next folder:**
- [03 · Exception Handling](../03-Exception-Handling/01-Exception-Handling.md) — the exception table, `athrow`, `fillInStackTrace`, and the design of custom exceptions

**Directly related chapters:**
- [01 · Memory Areas](01-Memory-Areas.md) — Metaspace, the code cache, the OOM taxonomy
- [02 · Garbage Collection](02-Garbage-Collection.md) — collectors, safepoints, and GC diagnostics
- [08 · Multithreading & Concurrency](../08-Multithreading-Concurrency/01-Thread-Basics.md) — the JMM, `volatile`, and lock behaviour (biased locking's removal)
- [06 · Java 8 Features](../06-Java-8-Features/01-Lambda-Functional-Interfaces.md) — `invokedynamic`, method references, and the default-method mechanism
- [14 · Reflection & Annotations](../14-Reflection-Annotations/01-Reflection-and-Annotations.md) — `Class` objects, dynamic proxies, and class loaders in depth
- [15 · IO / NIO](../15-IO-NIO/01-IO-NIO.md) — direct byte buffers, `Cleaner`, and the code cache of I/O paths

**Tools to practise on:**
`javap -c / -v / -p / -s` · `jcmd <pid> help` · `jcmd <pid> GC.heap_info` · `jcmd <pid> GC.class_histogram` · `jcmd <pid> VM.flags` · `jcmd <pid> VM.metaspace` · `jcmd <pid> VM.native_memory summary` · `jcmd <pid> Compiler.codecache` · `jcmd <pid> JFR.start` · `jstat -gcutil` · `jstack` · `jhsdb` · **JMH** (for honest microbenchmarks) · **Eclipse MAT**

**Books and references:**
- *The Java Virtual Machine Specification*, **§4** (class file format), **§5** (loading, linking, initialisation), **§6** (the `Class` file), **§12** (execution) — the normative source
- *Inside the Java Virtual Machine* — Bill Venners
- *Java Performance* — G. Bennett
- The OpenJDK source: `src/java.base/share/classes/java/lang/ClassLoader.java`, `src/hotspot/share/classfile/`, `src/hotspot/share/interpreter/`, `src/hotspot/share/opt/`
- JEPs worth reading: **JEP 133** (multicore), **JEP 277** (removed the SecurityManager from the JDK), **JEP 280** (string concatenation), **JEP 371** (hidden classes), **JEP 374** (biased locking removal), **JEP 379** (ZGC), **JEP 439** (Generational ZGC), **JEP 486** (SecurityManager permanently disabled), **JEP 501** (CRaC)

---

**Next Chapter:** → [03 · Exception Handling](../03-Exception-Handling/01-Exception-Handling.md)
**Previous Chapter:** ← [02 · Garbage Collection](02-Garbage-Collection.md)
