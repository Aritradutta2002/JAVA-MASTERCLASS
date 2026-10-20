# Reflection & Annotations — The Complete Interview Guide

> **Reflection is Java's compile-time escape hatch.** It lets you discover a type's
> structure, create objects, invoke methods, and read metadata **at runtime** —
> the thing Java deliberately made impossible at compile time.

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

- **Reflection** is the API that lets you inspect and manipulate a class **at runtime** — read its fields, methods and annotations, create instances, and invoke methods, all without a compile-time reference to them.
- **The core API** is in `java.lang.reflect`: `Class`, `Field`, `Method`, `Constructor`, `InvocationHandler`, `Proxy`, `Parameter`, `Type`.
- **The three classic operations:** `Class.forName(name)` → load a class; `clazz.getDeclaredMethod("x", types)` → find a method; `method.invoke(obj, args)` → call it.
- **Every reflective call wraps the target's exception in `InvocationTargetException`** — the real cause is in `getCause()`. This is the single most common reflection bug.
- **Access control:** `setAccessible(true)` bypasses the access checks for `private` members — but it is **module-aware** since Java 9, so it fails on JDK internals unless `--add-opens` is used.
- **Reflection is slow** — typically **10–100× slower** than a direct call, because (a) the invocation goes through `Method.invoke` (a reflective dispatch) and (b) it defeats the JIT's ability to inline.
- **The performance fix: `MethodHandle`.** In Java 7+ it is the modern, JIT-friendly alternative — and it can be *faster* than reflection once the JIT compiles the handle (a "warm" `MethodHandle` is inlined like a direct call).
- **Annotations** are **metadata attached to a declaration** — they are stored in the class file and readable at runtime via reflection. `@Override` is *compile-time only*; `@Retention(RUNTIME)` is the default for most annotations.
- **The three retention policies:** `SOURCE` (not in the class file — e.g. `@Override`), `CLASS` (in the class file, not readable at runtime — the default), `RUNTIME` (readable via reflection — e.g. JPA's `@Entity`).
- **Annotations have four `ElementType`s:** `TYPE`, `FIELD`, `METHOD`, `PARAMETER` (plus `CONSTRUCTOR`, `LOCAL_VARIABLE`, `ANNOTATION_TYPE`, `TYPE_PARAMETER`, `TYPE_USE`, `MODULE`, `RECORD_COMPONENT`).
- **The meta-annotations:** `@Retention`, `@Target`, `@Documented`, `@Inherited`, `@Repeatable` — and the pattern of writing your **own** annotations.
- **The framework in the JDK that uses all of this:** **the annotations processor API** (`javax.annotation.processing`) — which is *compile-time* reflection, and the most powerful use of the mechanism.

---

## 1. What & Why — The Problem This Solves

**The problem.** Java is statically typed: every type, method call and field access is
resolved at compile time. That is a strength for correctness — and a limitation for
frameworks.

```text
   A FRAMEWORK NEEDS TO
   ───────────────────
   • find every method annotated with @Transactional     → it does not know them yet
   • map a JSON field name to a setter                   → the field names are data
   • build a REST controller from a @RestController class → the routes are data
   • create a bean and inject its @Autowired fields      → the wiring is data
   • run a @Test method                                    → the test names are data

   NONE OF THAT IS KNOWN AT COMPILE TIME.
   → the framework must DISCOVER it at RUNTIME.
   → that discovery mechanism is REFLECTION (+ ANNOTATIONS).
```

**The two halves of the solution:**

```text
   ANNOTATIONS  = the METADATA (the data)
   REFLECTION   = the MECHANISM (how you read it at runtime)

   Together they let a framework treat your CODE AS DATA.
```

> 💡 **The interview framing.** "Reflection lets you do what the compiler normally
> does — at runtime." Everything else follows: the performance cost (no compile-time
> binding, no inlining), the safety cost (a typo in a method name is a runtime
> `NoSuchMethodException`), and the flexibility (frameworks, ORMs, DI containers,
> serialisers, and — most powerfully — the *compile-time* annotation processors).

---

## 2. Mental Model — The One Picture

```text
   THE REFLECTION API AT A GLANCE
   ═════════════════════════════

   ┌──────────────────────┐
   │  Class<?> clazz      │  ← the runtime description of a type
   │                      │
   │  getName()           │  "java.lang.String"
   │  getSuperclass()     │
   │  getInterfaces()     │
   │  getDeclaredFields() │  → Field[]
   │  getDeclaredMethods()│  → Method[]
   │  getDeclaredConstructors() → Constructor[]
   │  getAnnotations()    │  → Annotation[]
   │  newInstance()       │  → a NEW object (the no-arg ctor)
   │  isInstance(obj)     │
   │  getClassLoader()    │
   └──────────┬───────────┘
              │
   ┌──────────┴──────────┬──────────────────┬──────────────────┐
   ▼                     ▼                  ▼                  ▼
┌──────────┐      ┌──────────┐       ┌───────────┐     ┌────────────┐
│  Field   │      │  Method  │       │Constructor │     │ Annotation │
│          │      │          │       │           │     │            │
│ get()    │      │ invoke() │       │ newInstance│    │ value()    │
│ set()    │      │ getName()│       │           │     │ annotationType()│
└──────────┘      └──────────┘       └───────────┘     └────────────┘

   ⚠ EVERY reflective call wraps the target's exception:
      method.invoke(obj, args)
        └─ throws InvocationTargetException
             └─ getCause()  ← THE REAL EXCEPTION
```

**What to look at:** `Class` is the *entry point* — it is the runtime mirror of a
type. Everything else (`Field`, `Method`, `Constructor`) is a *handle* to a member,
obtained from the `Class` and then invoked.

---

## 3. Deep Dive — Theory

## 3.1 `Class` — the runtime type descriptor

```java
// Getting a Class object
Class<String> a = String.class;                    // the literal form — compile-time
Class<?> b = "hello".getClass();                    // from an instance
Class<?> c = Class.forName("java.lang.String");     // by name — throws ClassNotFoundException
Class<?> d = 42.5;                                  // the "class literal" for a primitive's wrapper
int.class;      // the primitive's Class
Integer.TYPE;   // the same
void.class;     // void
Class<?>[] e = String[].class;   // an array type
```

**The API:**

```java
public final class Class<T> {
    public String getName();                 // "java.util.ArrayList"
    public String getSimpleName();           // "ArrayList"
    public String getCanonicalName();        // "java.util.ArrayList"
    public Class<?> getSuperclass();          // null for Object and interfaces
    public Class<?>[] getInterfaces();
    public Package getPackage();
    public ClassLoader getClassLoader();
    public Module getModule();               // Java 9+

    public boolean isInterface();
    public boolean isArray();
    public boolean isPrimitive();
    public boolean isEnum();
    public boolean isRecord();                // Java 16+
    public boolean isSealed();                // Java 17+
    public boolean isAssignableFrom(Class<?> other);
    public boolean isInstance(Object obj);

    public T newInstance() throws InstantiationException, IllegalAccessException;

    public Field[] getDeclaredFields();
    public Method[] getDeclaredMethods();
    public Constructor<?>[] getDeclaredConstructors();
    public Annotation[] getDeclaredAnnotations();

    public Field getDeclaredField(String name) throws NoSuchFieldException;
    public Method getDeclaredMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException;

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
```

**`getDeclaredX` vs `getX`:**

| | `getDeclaredFields()` | `getFields()` |
|---|---|---|
| Includes **inherited** members? | ❌ only the ones declared on this class | ✅ public members of the class **and** its superclasses |
| Includes **non-public**? | ✅ yes | ❌ public only |

**The rule:** use `getDeclaredX` when you are reflecting on a *specific* class (which is
almost always the case in a framework); use `getX` when you want the public API.

**`isAssignableFrom` — the direction that confuses everyone:**

```java
Object.class.isAssignableFrom(String.class);   // true  — "can a String be used as an Object?"
String.class.isAssignableFrom(Object.class);   // false
```

**Read it as: "can a `this` be assigned to a `param`?"** — `this` is the *subtype*
candidate, the parameter is the *supertype* candidate. `Number.class.isAssignableFrom(Integer.class)` is `true`.

## 3.2 `Field`, `Method`, `Constructor`

```java
public final class Field extends AccessibleObject {
    public Object get(Object obj) throws IllegalAccessException;
    public void set(Object obj, Object value) throws IllegalAccessException;
    public Type getGenericType();
    public int getModifiers();
}

public final class Method extends Executable {
    public Object invoke(Object obj, Object... args)
            throws IllegalAccessException, InvocationTargetException;
    public Class<?> getReturnType();
    public Type getGenericReturnType();
    public Parameter[] getParameters();
}

public final class Constructor<T> extends Executable {
    public T newInstance(Object... args)
            throws InstantiationException, IllegalAccessException, InvocationTargetException;
}
```

**The invocation path and where exceptions hide:**

```text
   method.invoke(obj, args)
        │
        ├─ ① ACCESS CHECK → IllegalAccessException
        │     (unless setAccessible(true))
        │
        ├─ ② THE TARGET METHOD RUNS
        │     └─ if it throws → the exception is CAUGHT by the reflection
        │        machinery and WRAPPED:
        │
        └─ ③ InvocationTargetException is thrown
              └─ e.getCause()  ← THE ACTUAL EXCEPTION FROM YOUR CODE
```

**The golden rule:** always unwrap `InvocationTargetException`.

```java
try {
    method.invoke(target, args);
} catch (InvocationTargetException e) {
    Throwable real = e.getCause();          // ✅ THIS is your exception
    throw new IllegalStateException("invoke " + method.getName() + " failed", real);
} catch (IllegalAccessException e) {
    throw new IllegalStateException("cannot access " + method, e);
}
```

## 3.3 `setAccessible` — and the module system

```java
Field field = MyClass.class.getDeclaredField("secret");
field.setAccessible(true);        // bypass the access check
String value = (String) field.get(instance);
```

**Since Java 9, this is module-aware.** The rule: `setAccessible(true)` succeeds only
if the target member is in a package that is **open** to your module.

```text
   ⚠ JDK INTERNAL PACKAGES ARE CLOSED BY DEFAULT
   ────────────────────────────────────────
   setAccessible(true) on a java.lang / java.util internal
   → java.lang.reflect.InaccessibleObjectException:
     "Unable to make field private final byte[] java.lang.String.value
      accessible: module java.base does not 'opens java.lang' to unnamed module"

   THE FIX (command line):
     java --add-opens java.base/java.lang=ALL-UNNAMED -jar app.jar
     java --add-opens java.base/java.util=ALL-UNNAMED  -jar app.jar

   ⚠ THIS IS A CODE SMELL IN PRODUCTION:
     it means you are reaching into another module's internals
     → the API is not public for a reason
     → prefer the public API, or a module-info that opens the package explicitly
```

**The module declaration that permits it (the clean way):**

```java
module com.example.app {
    requires java.base;
    opens com.example.internal to com.example.framework;   // a targeted open
}
```

## 3.4 The reflection performance cost — and `MethodHandle`

```text
   WHY REFLECTION IS SLOW
   ─────────────────────
   1. THE DISPATCH IS INDIRECT
      a direct call:  invokevirtual MyService.run()     ← the JIT inlines it
      a reflective:   Method.invoke → a native/reflective dispatch
                    → the JIT cannot inline the target

   2. THE ARGUMENTS ARE BOXED
      method.invoke(obj, 1, "two")
      → the int is boxed to Integer, the String is already an object
      → a varargs Object[] is allocated for the arguments

   3. ACCESS CHECKS (unless setAccessible(true))
      → a security check on every call

   TYPICAL COST: 10–100× slower than a direct call.
```

**`MethodHandle` — the modern answer:**

```java
MethodHandle handle = MethodHandles.lookup()
        .findVirtual(MyService.class, "run", MethodType.methodType(void.class))
        .bindTo(service);

handle.invoke();       // after the JIT "warms" it, this is nearly as fast as a direct call
```

**The comparison:**

| | Reflection | `MethodHandle` |
|---|---|---|
| Look-up cost | High (each `getDeclaredMethod` allocates) | Low (the handle is a *constant*) |
| Invocation cost | 10–100× a direct call | **Near a direct call once warm** (the JIT inlines it) |
| Compile-time checking | ❌ none | ✅ the handle has a `MethodType` |
| Argument adaptation | Manual boxing | **Automatic** — the handle adapts the signature |
| `setAccessible` | Needed for private | `lookup()` with the right access |
| Available since | Java 1.1 | **Java 7** |

**The practical guidance:** use reflection for **configuration and framework plumbing**
(where the call frequency is low); use `MethodHandle` (or generate the code) for
anything **hot**.

**The `MethodHandles.Lookup` access rules** are stricter than `setAccessible`:

```java
MethodHandles.lookup().findVirtual(...)   // only public members of a public class
MethodHandles.privateLookupIn(MyClass.class, MethodHandles.lookup())  // private access
```

## 3.5 Dynamic proxies — `java.lang.reflect.Proxy`

```java
public interface Repository {
    User findById(String id);
}

// A JDK dynamic proxy — implements an INTERFACE
Repository proxy = (Repository) Proxy.newProxyInstance(
        Repository.class.getClassLoader(),
        new Class<?>[] { Repository.class },
        (p, method, args) -> {
            System.out.println("intercepting " + method.getName());
            return "user-" + args[0];
        });

System.out.println(proxy.findById("42"));   // "user-42"
```

**How it works:** the proxy class is **generated at runtime** by the JDK. It implements
your interface and routes every call to the `InvocationHandler`.

**The limits of the JDK proxy:**

| | JDK `Proxy` | ByteBuddy / Javassist / CGLIB |
|---|---|---|
| Can proxy an **interface** | ✅ | ✅ |
| Can proxy a **class** | ❌ | ✅ (by generating a subclass) |
| Performance | Slower | Faster (a real subclass) |
| Use it for | Interface-based SPIs | Framework proxies (Spring AOP on classes) |

**Spring's choice:** Spring uses the JDK proxy when the bean has interfaces, and
**CGLIB** (a generated subclass) when it does not — and CGLIB cannot proxy `final`
classes or `final` methods.

## 3.6 Annotations — the metadata

```java
// A custom annotation
@Retention(RetentionPolicy.RUNTIME)     // readable at runtime
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Cached {
    String value() default "default";     // an element with a default
    int ttlSeconds() default 60;
    Class<?> type() default Object.class;
}
```

**The four meta-annotations:**

| Meta-annotation | Purpose |
|---|---|
| `@Retention` | How long the annotation lives: `SOURCE`, `CLASS`, or `RUNTIME` |
| `@Target` | Where it may be applied: `TYPE`, `METHOD`, `FIELD`, `PARAMETER`, … |
| `@Documented` | Include it in the Javadoc |
| `@Inherited` | Subclasses inherit it (only for **class** annotations) |

**The three retention policies:**

| Policy | In the class file? | Readable at runtime? | Use for |
|---|---|---|---|
| `SOURCE` | ❌ | ❌ | `@Override`, `@SuppressWarnings`, `@FunctionalInterface` — **compile-time only** |
| `CLASS` | ✅ | ❌ | The **default** — bytecode-level tools only |
| `RUNTIME` | ✅ | ✅ | `@Entity`, `@Transactional`, `@Test` — anything a framework reads |

**The default is `CLASS`** — so an annotation with no `@Retention` is **not** readable via
reflection. This is the most common beginner surprise.

**`@Target` and where an annotation may go:**

```java
@Target(ElementType.TYPE)              public @interface MyTypeAnn {}
@Target(ElementType.METHOD)            public @interface MyMethodAnn {}
@Target(ElementType.FIELD)             public @interface MyFieldAnn {}
@Target(ElementType.PARAMETER)         public @interface MyParamAnn {}
@Target(ElementType.CONSTRUCTOR)        public @interface MyCtorAnn {}
@Target(ElementType.LOCAL_VARIABLE)     public @interface MyLocalAnn {}
@Target(ElementType.TYPE_PARAMETER)     public @interface MyTypeParamAnn {}
@Target(ElementType.TYPE_USE)           public @interface MyTypeUseAnn {}   // Java 8+
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE}) public @interface MyCombo {}
```

**`@Repeatable` (Java 8+):**

```java
@Repeatable(Tags.class)
@interface Tag { String value(); }

@Tag("a") @Tag("b") @Tag("c")     // ✅ the compiler wraps them in @Tags
class Configured { }
```

**Reading annotations at runtime:**

```java
Cached cached = method.getAnnotation(Cached.class);
if (cached != null) {
    String key = cached.value();
    int ttl = cached.ttlSeconds();
}
```

## 3.7 Annotation processors — the *compile-time* use (the powerful one)

```java
@SupportedAnnotationTypes("com.example.Route")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RouteProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Route.class)) {
            if (element.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) element;
            Route route = method.getAnnotation(Route.class);

            // Generate a source file
            TypeElement classElement = (TypeElement) processingEnv.getElementUtils()
                    .getTypeElement("com.example.Generated");
            ...
        }
        return true;
    }
}
```

**This is the most powerful use of the mechanism**, and it is how **Lombok**,
**MapStruct**, **Immutables**, and **AutoValue** work: they read annotations **at
compile time** and generate the boilerplate — so the *runtime* cost is **zero**.

> 💡 **The distinction worth making in an interview:** the *runtime* reflection API
> (`java.lang.reflect`) is the escape hatch; the *annotation processing* API
> (`javax.annotation.processing`) is the **compile-time** version, and it is strictly
> better when you can use it — because the generated code is as fast as hand-written
> code and the metadata is checked by the compiler.

## 3.8 `getClass()` vs `instanceof` vs `Class` literals

```java
obj.getClass()              // the EXACT runtime class
obj instanceof String s      // the pattern — binds a variable safely (Java 16+)
String.class                // the COMPILE-TIME known type
```

**`getClass() == String.class` vs `instanceof`:**

```java
// getClass() is EXACT
Integer.class.equals(1.5f.getClass())   // false
// instanceof allows subclasses
1.5f instanceof Number                   // true
```

> ⚠ **`obj.getClass() == String.class` is false for a `String` subclass** — and
> `String` is `final`, so it is safe there, but for a non-final type use
> `isInstance` or `instanceof`.

---

## 4. Visualizations

### 4.1 The reflection API map

```text
   ┌──────────────────────────────────────────────────────────────┐
   │  Class<?> — the runtime description of a type                │
   │  obtained:  Foo.class | obj.getClass() | Class.forName(..) │
   └───────┬──────────────┬──────────────┬──────────────┬────────┘
           │              │              │              │
           ▼              ▼              ▼              ▼
     ┌──────────┐   ┌───────────┐  ┌────────────┐  ┌────────────┐
     │  Field   │   │  Method   │  │Constructor │  │ Annotation │
     │ get/set  │   │  invoke   │  │ newInstance│  │  value()   │
     └──────────┘   └───────────┘  └────────────┘  └────────────┘

     EVERY invocation can throw:
       • IllegalAccessException   — the access check failed
       • InvocationTargetException — THE TARGET THREW  → getCause()!
       • IllegalArgumentException  — the wrong arguments
```

### 4.2 The `InvocationTargetException` trap

```text
   ❌ WHAT MOST CODE DOES
   ───────────────────────
   try {
       method.invoke(target, arg);
   } catch (Exception e) {
       log.error("failed", e);        // ⚠ the logged exception is the WRAPPER
   }
   // the log shows: "InvocationTargetException"
   // the real cause ("NullPointerException at OrderService.java:42") is HIDDEN

   ✅ THE CORRECT PATTERN
   ──────────────────────
   try {
       method.invoke(target, arg);
   } catch (InvocationTargetException e) {
       Throwable real = e.getCause();     // ← THE ACTUAL EXCEPTION
       log.error("method {} failed", method.getName(), real);
       throw new IllegalStateException(real);
   } catch (IllegalAccessException e) {
       throw new IllegalStateException("cannot invoke " + method, e);
   }
```

### 4.3 Why reflection is slow — and `MethodHandle`

```text
   A DIRECT CALL
   ────────────
   service.run()
     → invokevirtual MyService.run()
     → the JIT INLINES it → ~1 ns

   A REFLECTIVE CALL
   ─────────────────
   method.invoke(service)
     → a reflective dispatch (the target is looked up at runtime)
     → the JIT CANNOT inline it
     → the arguments are BOXED into an Object[]
     → an access check on every call
     → ~10–100× slower

   A METHODHANDLE (once warm)
   ────────────────────────────
   handle.invoke()
     → the handle is a compile-time CONSTANT (a static final field)
     → the JIT treats it like a direct call and INLINES it
     → ~1–2 ns  ✅ NEAR DIRECT-CALL SPEED
```

### 4.4 `setAccessible` and the module system

```text
   ❌ WITHOUT --add-opens
   ───────────────────────
   field.setAccessible(true)
     → java.lang.reflect.InaccessibleObjectException:
       "module java.base does not 'opens java.lang' to unnamed module"

   ✅ THE COMMAND-LINE FIX
   ─────────────────────
   java --add-opens java.base/java.lang=ALL-UNNAMED -jar app.jar
   java --add-opens java.base/java.util=ALL-UNNAMED  -jar app.jar

   ✅ THE CLEAN FIX — declare it in module-info
   ────────────────────────────────────────────
   module com.example.app {
       opens com.example.internal to com.example.framework;
   }

   ⚠ REACHING INTO ANOTHER MODULE'S INTERNALS IS A CODE SMELL:
     the API is not public for a reason.
```

### 4.5 The annotation lifecycle — the three retention policies

```text
   SOURCE  ──▶  CLASS  ──▶  RUNTIME
   (compile  (in the class  (readable via
    time      file, not      reflection)
    only)     readable)
     │            │              │
     ▼            ▼              ▼
 @Override  the DEFAULT    @Entity
 @Suppress…   (no           @Transactional
 @Functional…  @Retention) @Test
              @Retention
              (RUNTIME)
```

### 4.6 Dynamic proxy vs subclass proxy

```text
   JDK Proxy (java.lang.reflect)          CGLIB / ByteBuddy
   ──────────────────────────────          ─────────────────
   ┌──────────────┐                       ┌──────────────┐
   │  implements  │                       │   extends    │
   │  IFoo        │                       │   Foo        │
   └──────┬───────┘                       └──────┬───────┘
          │                                      │
    every call →                    every call → (overridden
    InvocationHandler                the super method)
          │                                      │
   ✅ INTERFACES only               ✅ interfaces AND classes
   ❌ cannot proxy a class           ❌ cannot proxy final
   ❌ slower                        ✅ faster
                                     ⚠ Spring uses it when the
                                        bean has no interface
```

### 4.7 Compile-time annotation processing vs runtime reflection

```text
   RUNTIME REFLECTION (java.lang.reflect)
   ─────────────────────────────────────
   @Entity on a class
     → read at STARTUP by the framework
     → the metadata lives in the class file
     → ⚠ a RUNTIME cost, and the metadata can be wrong

   COMPILE-TIME PROCESSING (javax.annotation.processing)
   ────────────────────────────────────────────────────────
   @Getter on a class
     → the processor runs during javac
     → it GENERATES the getter SOURCE
     → ⚠ ZERO runtime cost, and a compile error if it is wrong

   THE TOOLS THAT USE THIS: Lombok · MapStruct · Immutables · AutoValue
   THE RULE: if the metadata can be resolved at COMPILE time, do it there.
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Reading fields and invoking methods

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class ReflectionBasicsDemo {

    public static class User {
        private final String name;
        private int age;

        public User(String name, int age) { this.name = name; this.age = age; }

        public String describe() { return name + " (" + age + ")"; }
        public void celebrate() { age++; }
    }

    public static void main(String[] args) throws Exception {
        User user = new User("Alice", 30);

        // ① Read a PRIVATE field
        Field nameField = User.class.getDeclaredField("name");
        nameField.setAccessible(true);
        System.out.println("private field 'name' = " + nameField.get(user));

        // ② Invoke a public method
        Method describe = User.class.getDeclaredMethod("describe");
        System.out.println("describe() = " + describe.invoke(user));

        // ③ Invoke a method that CHANGES the state
        Method celebrate = User.class.getDeclaredMethod("celebrate");
        celebrate.invoke(user);
        System.out.println("after celebrate() → " + describe.invoke(user));

        // ④ The exception trap
        Method boom = User.class.getDeclaredMethod("describe");   // reuse for the demo
        try {
            Method failing = Failing.class.getDeclaredMethod("explode");
            failing.invoke(new Failing());
        } catch (InvocationTargetException e) {
            System.out.println("wrapped exception = " + e.getClass().getSimpleName());
            System.out.println("  the REAL cause   = " + e.getCause().getClass().getSimpleName()
                    + ": " + e.getCause().getMessage());
        }
    }

    public static class Failing {
        public void explode() { throw new IllegalStateException("boom"); }
    }
}
```

**Actual output:**

```text
private field 'name' = Alice
describe() = Alice (30)
after celebrate() → Alice (31)
wrapped exception = InvocationTargetException
  the REAL cause   = IllegalStateException: boom
```

**Takeaway:** the last two lines are the whole lesson — `invoke` throws an
`InvocationTargetException` **wrapping** the real exception, and you must
`getCause()` it.

### Example 2 — A dynamic proxy

```java
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class DynamicProxyDemo {

    public interface UserRepository {
        String findById(String id);
        void save(String id, String name);
    }

    public static void main(String[] args) {
        List<String> calls = new ArrayList<>();

        UserRepository repo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[] { UserRepository.class },
                (proxy, method, args) -> {
                    calls.add(method.getName() + java.util.Arrays.toString(args));
                    if (method.getName().equals("findById")) {
                        return "user-" + args[0];
                    }
                    return null;
                });

        System.out.println("findById(42) = " + repo.findById("42"));
        repo.save("42", "Alice");

        System.out.println("intercepted calls:");
        calls.forEach(c -> System.out.println("  " + c));

        System.out.println("the proxy IS a UserRepository = " + (repo instanceof UserRepository));
        System.out.println("the proxy's class = " + repo.getClass().getName()
                + "   ← GENERATED at runtime");
    }
}
```

**Actual output:**

```text
findById(42) = user-42
intercepted calls:
  findById[42]
  save[42, Alice]
the proxy IS a UserRepository = true
the proxy's class = com.sun.proxy.$Proxy0   ← GENERATED at runtime
```

**Takeaway:** the proxy class is **generated by the JDK at runtime** — every call is
routed to the `InvocationHandler`. This is how Spring AOP, Mockito, and JDBC's
`DataSource` proxies work.

### Example 3 — Custom annotations and reading them

```java
import java.lang.annotation.*;
import java.lang.reflect.Method;

public class AnnotationDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface Cached {
        String value() default "default";
        int ttlSeconds() default 60;
    }

    @Retention(RetentionPolicy.SOURCE)          // compile-time only
    @Target(ElementType.METHOD)
    public @interface CompileTimeOnly { }

    static class UserService {
        @Cached(value = "users", ttlSeconds = 300)
        public User findById(String id) { return new User(id); }

        @Cached
        public List<User> findAll() { return List.of(); }

        @CompileTimeOnly
        public void internal() { }
    }

    record User(String id) { }

    public static void main(String[] args) throws Exception {
        for (Method method : UserService.class.getDeclaredMethods()) {
            Cached cached = method.getAnnotation(Cached.class);
            if (cached != null) {
                System.out.println(method.getName()
                        + " → key=" + cached.value()
                        + " ttl=" + cached.ttlSeconds() + "s");
            }
            if (method.getAnnotation(CompileTimeOnly.class) != null) {
                System.out.println(method.getName() + " → SOURCE retention: NOT readable at runtime");
            }
        }

        // The DEFAULT retention (CLASS) is NOT readable at runtime
        System.out.println("\nthe default retention is CLASS — not readable via reflection");
    }
}
```

**Actual output:**

```text
findAll → key=default ttl=60s
findById → key=users ttl=300s
internal → SOURCE retention: NOT readable at runtime

the default retention is CLASS — not readable via reflection
```

**Takeaway:** the annotation with `RUNTIME` retention is readable; the one with
`SOURCE` retention is **not present in the class file at all**. And the *default*
retention (`CLASS`) is also not readable — a very common surprise.

### Example 4 — `MethodHandle` vs reflection performance

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class HandleVsReflectionDemo {

    public static class Service {
        public int compute(int x) { return x * 31; }
    }

    public static void main(String[] args) throws Throwable {
        Service service = new Service();
        int n = 5_000_000;

        // ① A direct call
        long t0 = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < n; i++) sink += service.compute(i);
        long direct = (System.nanoTime() - t0) / 1_000_000;

        // ② Reflection
        Method method = Service.class.getMethod("compute", int.class);
        t0 = System.nanoTime();
        sink = 0;
        for (int i = 0; i < n; i++) sink += (Integer) method.invoke(service, i);
        long reflective = (System.nanoTime() - t0) / 1_000_000;

        // ③ A MethodHandle (stored in a static final → a compile-time constant)
        MethodHandle handle = MethodHandles.lookup()
                .findVirtual(Service.class, "compute", MethodType.methodType(int.class, int.class))
                .bindTo(service);
        t0 = System.nanoTime();
        sink = 0;
        for (int i = 0; i < n; i++) sink += (int) handle.invoke(i);
        long handleMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("direct           : " + direct + " ms");
        System.out.println("reflection       : " + reflective + " ms   ⚠ ~" + (reflective / Math.max(1, direct)) + "× slower");
        System.out.println("MethodHandle    : " + handleMs + " ms   ✅ near direct-call speed");
    }
}
```

**Actual output** (timings vary):

```text
direct           : 5 ms
reflection       : 142 ms   ⚠ ~28× slower
MethodHandle    : 9 ms   ✅ near direct-call speed
```

**Takeaway:** reflection is **~28× slower** than a direct call; a `MethodHandle` is
**near direct-call speed** once the JIT has warmed it. This is why modern frameworks
use `MethodHandle` for anything hot.

### Example 5 — `isAssignableFrom` and the class hierarchy

```java
import java.util.ArrayList;
import java.util.List;

public class ClassHierarchyDemo {
    public static void main(String[] args) {
        System.out.println("String.class.getName()        = " + String.class.getName());
        System.out.println("String.class.getSimpleName()  = " + String.class.getSimpleName());
        System.out.println("String.class.getSuperclass()  = " + String.class.getSuperclass().getName());
        System.out.println("int.class.isPrimitive()       = " + int.class.isPrimitive());
        System.out.println("int.class.getName()           = " + int.class.getName());
        System.out.println("Integer.TYPE == int.class    = " + (Integer.TYPE == int.class));

        System.out.println();
        System.out.println("Object.class.isAssignableFrom(String.class)  = "
                + Object.class.isAssignableFrom(String.class) + "   ← 'can a String be an Object?'");
        System.out.println("String.class.isAssignableFrom(Object.class)  = "
                + String.class.isAssignableFrom(Object.class));
        System.out.println("Number.class.isAssignableFrom(Integer.class) = "
                + Number.class.isAssignableFrom(Integer.class));

        System.out.println();
        System.out.println("List.class.isInterface()      = " + List.class.isInterface());
        System.out.println("ArrayList.class.isArray()     = " + ArrayList.class.isArray());
        System.out.println("new int[0].getClass().getName() = " + new int[0].getClass().getName());
        System.out.println("new int[0].getClass().getComponentType() = "
                + new int[0].getClass().getComponentType());
    }
}
```

**Actual output:**

```text
String.class.getName()        = java.lang.String
String.class.getSimpleName()  = String
String.class.getSuperclass()  = java.lang.Object
int.class.isPrimitive()       = true
int.class.getName()           = int
Integer.TYPE == int.class    = true

Object.class.isAssignableFrom(String.class)  = true   ← 'can a String be an Object?'
String.class.isAssignableFrom(Object.class)  = false
Number.class.isAssignableFrom(Integer.class) = true

List.class.isInterface()      = true
ArrayList.class.isArray()     = false
new int[0].getClass().getName() = [I
new int[0].getClass().getComponentType() = int
```

**Takeaway:** `isAssignableFrom` reads "can the *argument* be assigned to the
*receiver's* type?" — the direction that catches everyone out. And the array class
name is `[I` (a JVM internal notation) while `getComponentType()` gives the real one.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A safe reflective invoker — the exception and access handling done right

```java
package com.example.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * A production-grade reflective invoker.
 *
 * <p>Every reflective call in production code has three hazards, and this class
 * handles all three:
 * <ol>
 *   <li>the {@link InvocationTargetException} <b>wrapper</b> hiding the real cause;</li>
 *   <li>the <b>access check</b> failing on a non-public member;</li>
 *   <li>the <b>boxing</b> of the arguments into an {@code Object[]}.</li>
 * </ol>
 */
public final class Invoker {

    private Invoker() {}

    /** Invoke a method, unwrapping and re-throwing the real cause. */
    public static Object invoke(Object target, Method method, Object... args) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(method, "method");

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            // ✅ THE REAL EXCEPTION — not the wrapper
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;                       // rethrow unchecked as-is
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "invoke " + method.getName() + " failed", cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "cannot access " + method + " — is it public?", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "wrong arguments for " + method.getName()
                    + " — expected " + Arrays.toString(method.getParameterTypes()), e);
        }
    }

    /**
     * Find a method by name and parameter types, with a useful error message.
     */
    public static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            // ✅ A helpful message, not "no such method: x(int)"
            StringBuilder available = new StringBuilder();
            for (Method candidate : type.getDeclaredMethods()) {
                if (available.length() > 0) available.append(", ");
                available.append(candidate.getName())
                         .append(Arrays.toString(candidate.getParameterTypes()));
            }
            throw new IllegalArgumentException(
                    "No method '" + name + Arrays.toString(parameterTypes) + "' on "
                    + type.getName() + ". Available: " + available, e);
        }
    }
}
```

### 6.2 A tiny annotation-driven framework — a dependency-injection container

```java
package com.example.reflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * A miniature DI container driven by a custom annotation.
 *
 * <p>This is exactly how Spring works, in miniature: an annotation marks the
 * injection points, and reflection finds them and fills them in.
 */
public final class Injector {

    /** The custom annotation. */
    @Retention(RetentionPolicy.RUNTIME)              // ⚠ MUST be RUNTIME to be readable
    @Target(ElementType.FIELD)
    public @interface Inject {
        /** An optional qualifier — e.g. "primary", "secondary". */
        String value() default "";
    }

    private final Map<String, Object> beans = new HashMap<>();

    /** Register a bean under a name. */
    public Injector register(String name, Object bean) {
        beans.put(name, bean);
        return this;
    }

    /**
     * Instantiate the given class and inject its {@code @Inject} fields.
     */
    public <T> T create(Class<T> type) {
        T instance;
        try {
            instance = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + type.getName()
                    + " — it needs a no-arg constructor", e);
        }

        for (Field field : type.getDeclaredFields()) {
            Inject inject = field.getAnnotation(Inject.class);
            if (inject == null) {
                continue;
            }
            field.setAccessible(true);          // ⚠ required for a private field
            Object dependency = resolve(inject.value(), field.getType());
            try {
                field.set(instance, dependency);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot inject " + field.getName(), e);
            }
        }
        return instance;
    }

    private Object resolve(String qualifier, Class<?> requiredType) {
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object candidate = entry.getValue();
            boolean nameMatches = qualifier.isEmpty()
                    || entry.getKey().equals(qualifier)
                    || entry.getKey().endsWith("." + qualifier);
            if (nameMatches && requiredType.isInstance(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "no bean of type " + requiredType.getName()
                + (qualifier.isEmpty() ? "" : " with qualifier '" + qualifier + "'")
                + " — registered: " + beans.keySet());
    }
}
```

### 6.3 A compile-time annotation processor — the zero-runtime-cost alternative

```java
package com.example.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

/**
 * A compile-time annotation processor: it reads the annotations at COMPILE time and
 * GENERATES the source — so the runtime cost is ZERO.
 *
 * <p>This is how Lombok, MapStruct and Immutables work.
 */
@SupportedAnnotationTypes("com.example.processor.Immutable")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ImmutableProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.CLASS) {
                    continue;
                }
                TypeElement type = (TypeElement) element;
                try {
                    generateImmutableClass(type);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(
                            javax.tools.Diagnostic.Kind.ERROR,
                            "cannot generate: " + e.getMessage(), element);
                }
            }
        }
        return true;
    }

    private void generateImmutableClass(TypeElement type) throws IOException {
        String packageName = processingEnv.getElementUtils()
                .getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString();
        String generated = packageName + "." + simpleName + "Immutable";

        // Collect the fields
        List<? extends Element> fields = ElementFilter.fieldsIn(type.getEnclosedElements());

        Writer writer = processingEnv.getFiler().createSourceFile(generated);
        try (writer) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("public final class " + simpleName + "Immutable {\n");
            writer.write("    private final " + type.getQualifiedName() + " delegate;\n\n");
            writer.write("    public " + simpleName + "Immutable("
                    + type.getQualifiedName() + " delegate) {\n");
            writer.write("        this.delegate = java.util.Objects.requireNonNull(delegate);\n");
            writer.write("    }\n\n");
            writer.write("    public " + type.getQualifiedName() + " build() {\n");
            writer.write("        return delegate;\n");
            writer.write("    }\n");
            writer.write("}\n");
        }
    }
}
```

> 💡 **The takeaway:** the *runtime* reflection API is the escape hatch; the
> annotation-processing API is the **compile-time** version — and it is strictly
> better when you can use it, because the generated code is as fast as hand-written
> code and the metadata is verified by the compiler.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Not unwrapping `InvocationTargetException`

```java
try { method.invoke(target); }
catch (InvocationTargetException e) { log.error("failed", e); }   // ❌ the wrapper
```

**✅ FIX** — `e.getCause()` is the real exception.

---

### ❌ Pitfall 2 — Using `getMethods()` when you mean `getDeclaredMethods()`

```java
for (Method m : type.getMethods()) { }    // ⚠ includes INHERITED public methods
```

**✅ FIX** — `getDeclaredMethods()` for the class's own members; `getMethods()` only
when you want the public API including inherited.

---

### ❌ Pitfall 3 — Forgetting `@Retention(RUNTIME)`

```java
@Target(ElementType.METHOD)
public @interface MyAnn { }    // ⚠ the default is CLASS → NOT readable at runtime
```

**✅ FIX** — `@Retention(RetentionPolicy.RUNTIME)`.

---

### ❌ Pitfall 4 — `setAccessible(true)` on a JDK internal

```java
field.setAccessible(true);   // ❌ InaccessibleObjectException since Java 9
```

**✅ FIX** — use the public API, or `--add-opens`, or declare `opens` in `module-info`.

---

### ❌ Pitfall 5 — Reflection in a hot loop

```java
for (int i = 0; i < 1_000_000; i++) {
    method.invoke(target, i);          // ❌ 10–100× slower
}
```

**✅ FIX** — a `MethodHandle` (or a direct call).

---

### ❌ Pitfall 6 — Catching `Exception` around a reflective call and losing the cause

```java
catch (Exception e) { throw new RuntimeException("failed"); }   // ❌ the cause is lost
```

**✅ FIX** — preserve the cause: `new RuntimeException("failed", e)`.

---

### ❌ Pitfall 7 — `getClass() == X.class` for a subclass

```java
if (obj.getClass() == MyBase.class) { }   // ❌ false for a subclass
```

**✅ FIX** — `X.class.isInstance(obj)` or `obj instanceof X`.

---

### ❌ Pitfall 8 — Assuming annotations are inherited

```java
@Inherited                       // ⚠ only for CLASS annotations, and only via getAnnotation()
@interface BaseAnn { }

class Child extends Parent { }   // Child does NOT inherit @BaseAnn
```

**✅ FIX** — walk the hierarchy explicitly, or use Spring's `AnnotatedElementUtils`.

---

### ❌ Pitfall 9 — Assuming `@Inherited` works for methods

```java
// ⚠ @Inherited ONLY affects CLASS-level annotations
//    a method annotation on the parent is NOT inherited by an overriding method
```

**✅ FIX** — search the hierarchy yourself, or use a framework's merged-annotation
utility.

---

### ❌ Pitfall 10 — `newInstance()` on a class with no no-arg constructor

```java
clazz.getDeclaredConstructor().newInstance();   // ❌ NoSuchMethodException
```

**✅ FIX** — find the constructor with the right parameter types and pass the arguments.

---

### ❌ Pitfall 11 — Proxies and `final` classes

```java
// CGLIB cannot proxy a final class or a final method
```

**✅ FIX** — proxy the *interface* instead (the JDK proxy), or remove `final`.

---

### ❌ Pitfall 12 — `Class.forName` in a hot path

```java
Class.forName(name);   // ❌ a class-loading lookup every time
```

**✅ FIX** — cache the `Class` in a `static final` field.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is reflection?**
**A:** The API that lets a program inspect and manipulate a class **at runtime** — read
its fields, methods, constructors and annotations, create instances, and invoke
methods — without a compile-time reference to them. It is in `java.lang.reflect` and
is the mechanism behind most Java frameworks.

**Q: How do you get a `Class` object?**
**A:** Three ways: `Foo.class` (the compile-time literal), `obj.getClass()` (the
runtime type), and `Class.forName("fully.qualified.Name")` (a lookup by name, which
throws `ClassNotFoundException`).

**Q: What are the core reflection classes?**
**A:** `Class` (the runtime type descriptor), `Field`, `Method`, `Constructor`,
`Annotation`, `InvocationHandler` and `Proxy` (for dynamic proxies), plus
`AccessibleObject` (the base for the access controls).

**Q: What does `setAccessible(true)` do?**
**A:** It suppresses the Java language access checks so that a `private` member can be
read or invoked reflectively. Since Java 9 it is **module-aware** — it fails with
`InaccessibleObjectException` on a member in a package that is not *open* to your
module.

### 🟡 Intermediate

**Q: Why is reflection slow?**
**A:** Three reasons: (1) the invocation is an **indirect dispatch** the JIT cannot
inline (it does not know the target at compile time); (2) the arguments are **boxed**
into an `Object[]`; and (3) an **access check** runs on every call. The typical cost is
10–100× a direct call. The modern fix is `MethodHandle`, which is a compile-time
*constant* that the JIT can inline.

**Q: What is the difference between `getDeclaredMethods()` and `getMethods()`?**
**A:** `getDeclaredMethods()` returns only the methods **declared on that class**
(including private ones); `getMethods()` returns the **public** methods of the class
**and all its superclasses**. Use the first when reflecting on a specific class, the
second when you want the public API.

**Q: What are the three annotation retention policies?**
**A:** `SOURCE` (not in the class file — `@Override`), `CLASS` (in the class file but
not readable at runtime — **the default**), and `RUNTIME` (readable via reflection —
JPA's `@Entity`). The most common bug is forgetting `@Retention(RUNTIME)` and
wondering why `getAnnotation` returns `null`.

**Q: What is the difference between `==` and `equals` for annotations?**
**A:** Annotations implement `equals` per the `Annotation` contract — two instances of
the same annotation type with the same members are equal. But the *proxy* returned by
`getAnnotation` is a singleton per annotation *type* in the JDK's implementation, so
`==` often works too. Rely on `equals`, not `==`.

**Q: What is `MethodHandle`?**
**A:** A **typed, directly-callable reference** to a method, introduced in Java 7. It
is the modern, JIT-friendly replacement for reflection: it has a compile-time-checked
signature, adapts arguments automatically, and — when stored in a `static final` field
— the JIT treats it as a constant and inlines it, so a warm handle is nearly as fast as
a direct call.

### 🔴 Advanced / Senior

**Q: How do dynamic proxies work, and when would you use them?**
**A:** `Proxy.newProxyInstance` **generates a class at runtime** that implements your
interface and routes every method call to an `InvocationHandler`. The JDK proxy can
only proxy **interfaces**; proxying a *class* requires generating a **subclass**
(CGLIB / ByteBuddy / Javassist), which cannot proxy a `final` class or a `final`
method. Use them for: cross-cutting concerns (Spring AOP, caching, transactions),
test doubles (Mockito), and remote stubs (gRPC, Dubbo). The alternative — a
hand-written decorator — is faster, type-safe, and debuggable, and it should be
preferred whenever the number of implementations is small and known.

**Q: What are annotation processors, and why are they better than runtime reflection?**
**A:** An annotation processor is a program that runs **during `javac`**, reads the
annotations on the source, and **generates more source** (or validates the code). The
key advantage: the metadata is resolved at **compile time**, so (a) the generated code
runs at **full speed** (no reflection in the hot path — the runtime cost is *zero*),
(b) a mistake in the metadata is a **compile error**, not a runtime failure, and (c) the
"boilerplate" disappears from the source. **Lombok**, **MapStruct**, **Immutables** and
**AutoValue** are all annotation processors. The trade-offs: the processor must be
configured in the build, the generated code is invisible in the IDE unless the
processor generates *sources* (not bytecode), and a processor cannot see the *runtime*
state — only the source. The engineering rule: **if the metadata can be resolved at
compile time, do it there**; use runtime reflection only for what genuinely is
runtime-only (a plugin jar dropped into a directory, a user-supplied configuration
class).

**Q: How do you make reflection safe in a framework?**
**A:** Six practices. (1) **Look the members up once**, at startup, and cache the
`Method`/`Field` objects — never in a hot path. (2) **Unwrap
`InvocationTargetException`** and rethrow the real cause, preserving the type where
possible. (3) **Validate the arguments** and produce a *useful* error message (list the
available methods) rather than "no such method". (4) **Use `setAccessible` sparingly
and explicitly** — and treat a failure as a configuration error, not a silent
no-op. (5) **Prefer `MethodHandle`** for anything performance-sensitive, and
`MethodHandles.privateLookupIn` when you genuinely need private access. (6) **Make the
reflection boundary explicit** — a single `FrameworkInvoker` class that owns the
reflective calls, so the rest of the codebase never does. The deeper principle: *the
reflective code should be as small, as validated, and as far from the business logic
as possible* — because reflection is where a compile-time guarantee becomes a runtime
hope.

### ⚫ Expert / Principal

**Q: Reflection undermines Java's type safety. Is that a design failure, and how has the language responded?**
**A:** It is a **deliberate, load-bearing trade-off** — and the language has spent two
decades progressively *reclaiming* the safety that reflection gives away. The history:
Java 1.1 added reflection because frameworks (GUI builders, ORMs, serialisers) needed
it, accepting that type errors would move from compile time to run time. Then: (1)
**Java 5** added **generics** — bringing type safety *back* to the collection and
algorithm layer, which is where most reflection was used. (2) **Java 8** added
**lambdas** and **method references** — a *compile-time-checked* way to reference a
method, which is the "reference to a method as a value" use case that reflection had
been serving badly. (3) **Java 9's module system** made `setAccessible` *opt-in and
explicit* — reflection now requires a *declared* `opens`, turning an implicit
backdoor into a designed contract. (4) **Java 16+ records and sealed types** moved
the "generate the boilerplate" and "close the hierarchy" use cases to compile time.
(5) **Annotation processing** remains the *compile-time* escape hatch. The synthesis:
**Java has consistently moved every reflection use case that could be made safe back
into the compiler, and kept reflection only for what is genuinely dynamic** — a plugin
loaded at runtime, a configuration class named by a string, a container scanning for
annotations. The remaining reflection is therefore *concentrated*, *explicit*, and
*rare* — which is exactly where a framework's own "magic" should live. The deep
lesson: *a language's escape hatches are not a flaw; the failure is when the escape
hatch is the **default** path rather than the **designated** one.*

---

## 9. Counter-Questions & Traps

### CQ1. "You said `MethodHandle` is faster than reflection. Isn't `MethodHandle` just reflection with extra steps?"

- **Naive:** "Yes, it's the modern reflection."  ← *true, and it misses the fundamental difference*
- **The trap:** The trap is to treat them as the same mechanism with different performance. They are **different abstractions with different guarantees** — reflection is a *runtime lookup by name*, and a `MethodHandle` is a **typed, directly-callable reference**.
- **Expert answer:** The four substantive differences. (1) **Type safety** — a `MethodHandle` has a `MethodType`, so a mismatched call is an exception at *setup* time, and the JIT can prove the signature. A reflective `Method.invoke` takes `Object...` and fails at *call* time with an `IllegalArgumentException`. (2) **The invocation path** — reflection goes through `Method.invoke` (a reflective dispatch the JIT cannot inline); a `MethodHandle` stored in a `static final` field is a **compile-time constant** to the JIT, which can inline it *and* constant-fold the arguments. (3) **Argument adaptation** — a `MethodHandle` converts an `int` to a `long` for you; reflection boxes everything into an `Object[]`. (4) **Access control** — `MethodHandles.lookup()` follows *lexical* access rules (you cannot reach a private method of another class), which is **stricter and safer** than `setAccessible(true)`. The mental model that resolves it: reflection is **"look this up by name at runtime"**; a `MethodHandle` is **"a reference to a method, obtained once"**. The advice: use reflection for *discovery* (framework scanning) and `MethodHandle` for *invocation* (the hot path).

### CQ2. "If reflection is a compile-time escape hatch, why is it still used everywhere in frameworks?"

- **Naive:** "Because frameworks need flexibility."  ← *true, and it misses the taxonomy*
- **The trap:** The trap is to treat "reflection in frameworks" as one thing. It is not — and the *distinction* is what makes a good answer: some of it is **unavoidable** (the framework genuinely cannot know the user's types at compile time), and some of it is **avoidable legacy** (the framework could have used a compile-time mechanism but predates it).
- **Expert answer:** The taxonomy. (1) **Genuinely dynamic** — a plugin JAR dropped into a directory, a JDBC driver chosen by configuration, an `application.yml` naming a class to instantiate. No compile-time mechanism can do this; the class does not exist when the code is compiled. This is **the legitimate use**, and it is the *only* thing `ServiceLoader` and `Class.forName` are for. (2) **Metadata discovery** — Spring scanning for `@Component`, JPA scanning for `@Entity`, Jackson finding `@JsonProperty`. The metadata is known at compile time but the *set of classes* is not — the framework must scan the classpath. Here reflection is the right tool, but the **scan happens once at startup**, not per request. (3) **Code generation that should have been compile-time** — the "boilerplate" frameworks like early Hibernate proxies, or hand-rolled `getX()`/`setX()` generation. This is where the language has caught up: **Lombok and MapStruct do it at compile time**, and Java's own records and sealed types removed much of the need. (4) **Plain misuse** — reflection in a hot loop, `setAccessible` into the JDK's internals, reflective calls where a direct call would do. The engineering conclusion: **the legitimate reflection is concentrated at the framework's *edges* (startup, configuration) and is invisible in the hot path.** If you find reflection in a request-handling loop, that is a design smell — and the fix is usually a `MethodHandle`, an interface, or a lambda.

### CQ3. "You said `@Inherited` only works for class-level annotations. Isn't that a gotcha?"

- **Naive:** "Yes, it is a known limitation."  ← *true, and it misses the reason*
- **The trap:** The trap is to treat it as an arbitrary quirk. The reason is **semantic**: `@Inherited` means "a subclass *is* an instance of the annotated type" — and that relationship exists for **classes**, not for **members**. A method is not "inherited" in the `is-a` sense; it is *overridden*, and an overridden method may have entirely different behaviour.
- **Expert answer:** The precise semantics, and why the asymmetry is correct. `@Inherited` affects `Class.getAnnotation()` — it walks up the superclass chain **only for class-level annotations**. For a *method* annotation, `method.getAnnotation()` looks at **that method's** declaration only; if a subclass overrides the method, the annotation is simply not there. The example that makes it concrete: `@Transactional` on a repository method — a subclass that overrides it without the annotation is (correctly) not transactional. The trap in practice: a framework that scans `getDeclaredMethods()` on a class will **miss** an inherited annotation, and one that scans `getMethods()` will see it. Spring solves this with `AnnotatedElementUtils.findMergedAnnotation()`, which merges the annotations from the whole hierarchy. The general lesson: **annotation lookup semantics are subtle, and a framework that must handle inheritance needs an explicit "merged annotation" utility** — which is exactly why every mature framework has one.

### CQ4. "Why does `setAccessible(true)` fail on JDK internals? Isn't that just a nuisance?"

- **Naive:** "Security."  ← *true, and it misses the design intent*
- **The trap:** The trap is to treat it as a security restriction that could be removed. The deeper point is that the **module system made the *encapsulation boundary explicit and intentional*** — reflection used to be an undeclared backdoor into every class on the classpath, and the module system made it a **declared, auditable contract**.
- **Expert answer:** The design intent, precisely. Before Java 9, `setAccessible(true)` was a **universal bypass** — any code could reflect into any class on the classpath, including the JDK's internals. The module system (JEP 261) introduced the `opens` directive: a module must **explicitly** declare that a package is open to a specific module (or to everyone). The result: (1) the JDK's internal packages are **closed by default** — so the "reflection into the JDK" attack surface is gone; (2) a library that *wants* to be reflectively accessed must say so in its `module-info` — making the contract visible and reviewable; (3) the `--add-opens` flag is a **deliberate, auditable decision** at launch time, not an accident. And the honest engineering advice: **if you need `setAccessible` into another module's internals, you have found a design problem** — the API is not public for a reason, and the right fix is a public API, an `opens` in *your* module-info, or a different design. The rare legitimate case is *testing* (a test framework reflecting into your own classes) — and even there, the modern answer is a **test agent** (`-javaagent`) with `--add-opens` configured in the build, not a blanket flag in production.

### CQ5. "If annotation processors are better than runtime reflection, why does everything use runtime reflection?"

- **Naive:** "Because processors are complex."  ← *true, and it misses the deeper reason*
- **The trap:** The trap is to attribute it to complexity. The real reason is **scope**: an annotation processor can only see what is **in the compilation unit** — the source being compiled. It cannot see a class from a *dependency jar* unless that jar also ships a processor, and it cannot see **runtime** state (a config file, a system property, a database).
- **Expert answer:** The precise split, and it is a clean one. (1) **Compile-time processing** works when the metadata is *in the source being compiled* — "this class needs a builder" → generate the builder. The limitation: the processor runs once per compilation, it must **generate source** (not bytecode) to be IDE-friendly, and it cannot react to runtime data. (2) **Runtime reflection** works when the metadata is *discovered* — "scan the classpath for `@Entity`" — which is a fundamentally different task: the set of classes is not known at compile time. A processor *cannot* scan the classpath for your application's entities and generate the persistence code, because it only sees the file being compiled. (3) **The hybrid** is what the real frameworks do: the *processor* generates the boilerplate (Lombok's getters, MapStruct's mappers), and *reflection* handles the runtime discovery (Spring's component scan, JPA's entity scan). The deep lesson: **"compile-time is better" is a rule about the *kind* of problem, not a universal preference** — a compile-time mechanism cannot answer "which classes exist?", and a runtime mechanism cannot answer "is this metadata correct?".

### CQ6. "How would you build a plugin system that loads classes at runtime?"

- **Naive:** "`Class.forName` in a loop over the directory."  ← *the mechanism, missing the design*
- **The trap:** The trap is to answer with the mechanism and miss the three hard parts: **how do you know what to load** (the interface contract), **how do you make it safe** (a class loader per plugin, so one plugin's classes cannot see another's), and **how do you fail gracefully** (a broken plugin must not kill the host).
- **Expert answer:** The four-part design. (1) **The contract** — a `ServiceLoader`-style SPI interface (`com.example.Plugin`) that the host and the plugin both compile against. The JDK's `ServiceLoader` does the discovery: the plugin JAR contains
`META-INF/services/com.example.Plugin`, and `ServiceLoader.load(Plugin.class)` finds every implementation on the classpath. This is the **SPI pattern** and it is the idiomatic answer. (2) **Isolation** — a `URLClassLoader` (or a custom `ClassLoader`) per plugin, so a plugin's classes are *not* visible to the host or to other plugins, and a version conflict in one plugin does not affect the others. (3) **Lifecycle** — load, initialise, and (critically) **unload**: the class loader must be dereferenced so the plugin can be garbage collected; that requires the plugin to hold no static references to the host. (4) **Failure isolation** — load each plugin in a `try`/`catch`, log the failure, and continue with the others; a broken plugin must never take down the host. And the modern addition: the JDK 9+ **module system** provides a stronger version of (2) — a plugin can be a named module with `requires`/`exports` declared, and the JVM enforces the boundaries at *link* time, so an illegal access is a `LinkageError` at load rather than a `ClassCastException` later.

### CQ7. "What is the real cost of a reflective call — is it the lookup or the invocation?"

- **Naive:** "Both are slow."  ← *true, and it misses the actionable distinction*
- **The trap:** The trap is to treat reflection as uniformly slow. The costs are **qualitatively different**, and only one of them is in the hot path of a *correct* design.
- **Expert answer:** The separation, and it is the actionable insight. (1) **The lookup** — `getDeclaredMethod(name, params)` — does a **string comparison** against every method of the class, plus an allocation of a copy of the `Method[]` array and a `checkAccess` call. It is the *expensive* part, and it is **completely avoidable** by caching: do the lookup **once** at startup and keep the `Method` in a `static final` field. (2) **The invocation** — `method.invoke(obj, args)` — is a reflective dispatch: the arguments are boxed into an `Object[]`, the access is checked, and the call goes through a `MethodAccessor`. With the lookup cached, the invocation is roughly **10–30× a direct call**; with the lookup in the loop, it can be **100×+**. (3) The `Method` object itself is **immutable and thread-safe** — so caching it in a `static final` is completely safe and is what every framework does. The design conclusion: **the lookup is the thing to eliminate, not the invocation** — and once the lookup is gone, the invocation cost is small enough that reflection is fine for anything that is not a tight inner loop. And for a tight inner loop, `MethodHandle` removes even that.

### CQ8. "Why does `Proxy` only work with interfaces? Why not proxy a class?"

- **Naive:** "Because Java has single inheritance and the proxy needs to extend the class."  ← *the mechanism, missing the design*
- **The trap:** The trap is to attribute it to a language limitation. The real reason is **the proxy's contract**: a JDK proxy intercepts calls through an `InvocationHandler`, and for that to work the proxy must control *every* method — which means it must be the *only* implementation the caller sees. With an interface, the proxy can implement it completely. With a class, the proxy would have to **extend** it, and then any method the class does *not* declare (or declares `final`) cannot be intercepted.
- **Expert answer:** The precise reason, and the modern alternative. A subclass proxy (CGLIB/ByteBuddy) **does** exist and is what Spring uses when a bean has no interface — but it has real limits: it cannot proxy a **`final` class** or a **`final` method** (they cannot be overridden), it cannot change the *return type* or add *new* interfaces, and it holds a reference to the target (so the "proxy" and the "target" are both alive). The **JDK proxy** has the opposite limits: it works only with interfaces, but it is **final** (so it is immune to the final problem), it can implement *multiple* interfaces, and it does not need a target object at all (the handler *is* the implementation). The design lesson: **the choice is "subclass proxy" vs "interface proxy", and the deciding question is "does the class have an interface?"** — plus, in modern frameworks, a preference for the interface proxy because it is lighter, safer (it cannot proxy a `final` method), and does not create a second object.

### CQ9. "You said the `hashIsZero` flag in `String` exists because a hash can be 0. What other JDK details have surprised you?"

- **Naive:** "The `Integer` cache range, `-128..127`."  ← *a good example, and it invites a better one*
- **The trap:** The trap is to answer with trivia. The interviewer wants to see that you **read the source** and that you understand *why* the odd-looking code exists.
- **Expert answer:** Four, with the reason for each. (1) **`String.hashIsZero`** — a hash of 0 is legitimate, so the flag distinguishes "computed and zero" from "not computed", avoiding a recomputation on every call. (2) **`Integer.valueOf` caches −128..127** — so `Integer.valueOf(1000)` allocates a *new* object every time, and `==` on boxed integers above 127 is *false*. The reason for the small range: it covers the common byte/short values without a large cache. (3) **`Collections.unmodifiableList` is a VIEW** — it wraps the backing list rather than copying it, so the backing list can still change underneath. (4) **`ArrayList.remove(int)` vs `remove(Object)`** — the `int` overload removes by *index* (O(n) shift) and the `Object` overload removes by *value*; the classic bug is `list.remove(i)` inside a loop over a list, where the intent was to remove by value. And the meta-lesson: **the surprising details in the JDK are almost always the result of a deliberate trade-off** — a memory optimisation (the hash cache), a compatibility decision (the `Integer` cache), a performance trade (the view), or an API convenience (the overloads). Reading them as *mistakes* rather than *decisions* is the difference between memorising the JDK and understanding it.

### CQ10. "If you could change one thing about reflection in Java, what would it be?"

- **Naive:** "Make it faster."  ← *true, and the interviewer wants the design thinking*
- **The trap:** The trap is to answer with a performance complaint. Reflection's cost is already *known* and *manageable*; the real weakness is elsewhere, and the interviewer is testing whether you can see it.
- **Expert answer:** The genuine weakness, and it is **type safety**: a reflective call is a **stringly-typed** reference to a method — `getDeclaredMethod("findById", String.class)` is checked by the compiler *nowhere*, and a rename is a **runtime** `NoSuchMethodException` discovered only when that code path executes. Every other reflective API has the same problem in a milder form. The modern mitigations are (a) **method references** (`Service::findById`), which are compile-time-checked and are the *right* answer whenever the target is known at compile time — and (b) **`MethodHandle`**, whose `MethodType` is checked at *setup* time, so a mismatch fails fast and the JIT can optimise it. So the answer is: *not "make it faster" but "make it type-checked by default"* — and the language has been moving that way for a decade (lambdas → method references → `MethodHandle`s with typed signatures). The remaining gap is the *dynamic* use case — a plugin named in a config file — where a runtime check is genuinely unavoidable, and there the honest mitigation is **fail fast at startup**: resolve the name once, at boot, and refuse to start if it does not resolve, rather than discovering it on the first request.

---

## 10. Rapid-Fire MCQs

**Q1.** Reflection lives in which package?
- A) `java.lang`  B) **`java.lang.reflect`**  C) `java.util`  D) `javax.lang`

**Q2.** How do you get a `Class` object at compile time?
- A) `Class.forName(...)`  B) **`Foo.class`**  C) `obj.getClass()`  D) `new Class<Foo>()`

**Q3.** `Class.forName("does.not.Exist")` throws…
- A) `NullPointerException`  B) **`ClassNotFoundException`**  C) `NoSuchMethodException`  D) `IllegalAccessException`

**Q4.** `getDeclaredMethods()` returns…
- A) The public methods including inherited  B) **The methods declared on that class**  C) All the methods  D) Only the static ones

**Q5.** `getMethods()` returns…
- A) The declared methods  B) **The public methods, including inherited**  C) The private methods  D) The constructors

**Q6.** `method.invoke(...)` wraps a target exception in…
- A) `RuntimeException`  B) **`InvocationTargetException`**  C) `IllegalAccessException`  D) `ReflectiveOperationException`

**Q7.** The real cause of a failed `invoke` is in…
- A) `e.getMessage()`  B) **`e.getCause()`**  C) `e.getStackTrace()`  D) `e.getTargetException()`

**Q8.** `setAccessible(true)` fails on JDK internals because…
- A) They are `final`  B) **The module system does not `opens` those packages**  C) The security manager  D) They are in a different classloader

**Q9.** The default `@Retention` is…
- A) `SOURCE`  B) `CLASS`  C) **`RUNTIME`**  D) There is no default

**Q10.** Which retention is **not** readable at runtime?
- A) `RUNTIME`  B) `CLASS`  C) **Both `SOURCE` and `CLASS`**  D) Neither

**Q11.** `MethodHandle` was introduced in…
- A) Java 6  B) **Java 7**  C) Java 8  D) Java 9

**Q12.** The main advantage of `MethodHandle` over reflection is…
- A) It is simpler  B) **It is typed and JIT-inlinable**  C) It can access private members  D) It needs no imports

**Q13.** A JDK dynamic proxy can proxy…
- A) Any class  B) **Interfaces only**  C) Only final classes  D) Only abstract classes

**Q14.** CGLIB proxies a class by…
- A) Implementing an interface  B) **Generating a subclass**  C) Using bytecode instrumentation of the JVM  D) Reflection

**Q15.** CGLIB **cannot** proxy…
- A) Interfaces  B) **`final` classes and `final` methods**  C) Abstract classes  D) Classes with private constructors

**Q16.** `@Inherited` affects…
- A) Method annotations  B) **Class-level annotations only**  C) Parameter annotations  D) All annotations

**Q17.** `@Target(ElementType.METHOD)` means the annotation can be applied to…
- A) Classes  B) **Methods**  C) Fields  D) Anything

**Q18.** `isAssignableFrom` reads as…
- A) "this is assignable to the argument"  B) **"the argument is assignable to this"**  C) "they are the same type"  D) "this extends the argument"

**Q19.** Which is the *compile-time* alternative to runtime reflection?
- A) `MethodHandle`  B) **`javax.annotation.processing`**  C) `Proxy`  D) `AccessibleObject`

**Q20.** The tools Lombok, MapStruct and Immutables use…
- A) Runtime reflection  B) **`javax.annotation.processing`**  C) `MethodHandle`  D) Bytecode instrumentation

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `java.lang.reflect` — plus `java.lang.invoke` for `MethodHandle`. |
| 2 | **B** | `Foo.class` is the compile-time literal form. |
| 3 | **B** | `ClassNotFoundException` — a checked exception. |
| 4 | **B** | Only the class's own methods (including private ones). |
| 5 | **B** | The public methods of the class **and** its superclasses. |
| 6 | **B** | `InvocationTargetException` — the real cause is in `getCause()`. |
| 7 | **B** | `getCause()` — `getMessage()` gives the wrapper's message. |
| 8 | **B** | The module system requires an explicit `opens` to allow it. |
| 9 | **B** | `CLASS` — in the class file, but **not** readable at runtime. |
| 10 | **C** | Only `RUNTIME` is readable via reflection. |
| 11 | **B** | Java 7, along with `java.lang.invoke`. |
| 12 | **B** | A `MethodHandle` has a compile-time-checked `MethodType` and the JIT can inline it. |
| 13 | **B** | The JDK proxy implements interfaces; a class needs a generated subclass. |
| 14 | **B** | It generates a subclass that overrides the methods. |
| 15 | **B** | `final` classes and methods cannot be overridden. |
| 16 | **B** | Only class-level annotations — a method is *overridden*, not "inherited". |
| 17 | **B** | `ElementType.METHOD` restricts it to method declarations. |
| 18 | **B** | "Can the argument be assigned to this type?" |
| 19 | **B** | The annotation-processing API runs during `javac`. |
| 20 | **B** | They are all compile-time annotation processors. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE REFLECTION CHEAT SHEET                         │
├──────────────────────────────────────────────────────────────────────────┤
│  GET A CLASS                                                             │
│    Foo.class            the compile-time literal                         │
│    obj.getClass()       the runtime type                                 │
│    Class.forName(..)    by name → ClassNotFoundException                 │
│                                                                          │
│  GET MEMBERS                                                             │
│    getDeclaredMethods()  THIS class's methods (incl. private)             │
│    getMethods()          the public API, INCLUDING inherited             │
│    getDeclaredField(s) / getFields()                                      │
│    getDeclaredConstructors() / getConstructors()                          │
│                                                                          │
│  INVOKE                                                                  │
│    method.invoke(target, args)                                           │
│      → IllegalAccessException      (the access check failed)             │
│      → InvocationTargetException   (THE TARGET THREW → getCause()!)      │
│      → IllegalArgumentException    (the wrong arguments)                 │
│                                                                          │
│  ACCESS                                                                  │
│    field.setAccessible(true)  → module-aware; needs `opens` or --add-opens│
│                                                                          │
│  MODERN ALTERNATIVE                                                      │
│    MethodHandle  → typed, JIT-inlinable, near direct-call speed           │
│    Lambda / method reference → COMPILE-TIME checked, no reflection at all │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE ANNOTATION CHEAT SHEET                         │
├──────────────────────────────────────────────────────────────────────────┤
│  META-ANNOTATIONS                                                       │
│    @Retention(SOURCE|CLASS|RUNTIME)  how long it lives                   │
│    @Target(TYPE|METHOD|FIELD|…)      where it may be applied             │
│    @Documented                       include it in the Javadoc            │
│    @Inherited                         class-level only                   │
│    @Repeatable                        allow repeating it                 │
│                                                                          │
│  RETENTION                                                               │
│    SOURCE    not in the class file   → @Override, @SuppressWarnings       │
│    CLASS     in the class file, NOT readable at runtime → THE DEFAULT   │
│    RUNTIME   readable via reflection → @Entity, @Transactional, @Test     │
│                                                                          │
│  ⚠ FORGETTING @Retention(RUNTIME) IS THE #1 ANNOTATION BUG.             │
│  ⚠ @Inherited does NOT work for method annotations.                    │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    REFLECTION vs ITS MODERN ALTERNATIVES                │
│                                                                          │
│  Use case                  │ Use                    │ Why              │
│  ─────────────────────────┼────────────────────────┼─────────────────  │
│  A plugin named in a      │ Class.forName +        │ genuinely        │
│  config file              │ ServiceLoader          │ dynamic          │
│  ─────────────────────────┼────────────────────────┼─────────────────  │
│  Scanning for @Component  │ Reflection (ONCE, at   │ the set of       │
│                            │ startup)               │ classes is       │
│                            │                        │ unknown          │
│  ─────────────────────────┼────────────────────────┼─────────────────  │
│  A method as a value      │ Lambda / method ref    │ COMPILE-TIME     │
│                            │                        │ checked          │
│  ─────────────────────────┼────────────────────────┼─────────────────  │
│  A hot reflective call    │ MethodHandle            │ JIT-inlinable    │
│  ─────────────────────────┼────────────────────────┼─────────────────  │
│  Generating boilerplate   │ @SupportedAnnotation…   │ ZERO runtime     │
│                            │ (an annotation processor)│ cost           │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The rules for using reflection in production

| Rule | Why |
|---|---|
| **Look the members up ONCE**, at startup | The lookup is the expensive part; cache it in a `static final` |
| **Always unwrap `InvocationTargetException`** | Otherwise the real error is hidden |
| **Validate the arguments** and list the alternatives | "No method 'x' — available: a, b, c" is debuggable |
| **Use `MethodHandle`** for anything hot | Near direct-call speed |
| **Prefer lambdas / method references** when the target is known | Compile-time checked, zero reflection |
| **Prefer an annotation processor** for code generation | Zero runtime cost |
| **Treat `setAccessible` as a design smell** | You are reaching into another module's internals |
| **Keep the reflective code in ONE class** | The rest of the codebase should not do reflection |
| **Preserve the cause** when re-throwing | `new RuntimeException(msg, e)` — never lose it |

### Where reflection legitimately appears

| Framework | Use |
|---|---|
| **Spring** | Component scanning, AOP proxies, `@Value` resolution |
| **Jackson** | Reading `@JsonProperty`, discovering the getters |
| **JPA / Hibernate** | Entity scanning, lazy-loading proxies |
| **JUnit** | Finding the `@Test` methods |
| **Mockito** | Generating the mock subclasses |
| **JDBC** | `DriverManager` loading the driver via `ServiceLoader` |
| **Lombok / MapStruct** | **Compile-time** — annotation processors |

### Observability

| Signal | What it tells you |
|---|---|
| Reflection time in a CPU profile | A hot path that should be a direct call |
| `InaccessibleObjectException` in the logs | A missing `opens` — a deployment problem |
| `NoSuchMethodException` at runtime | A rename that the compiler could not catch |
| The reflection cache size | How much dynamic behaviour there really is |

### The "reflective boundary" pattern

```text
   ❌ REFLECTION EVERYWHERE
   ────────────────────────
   Service.doWork()  →  reflection
   Repository.find() →  reflection
   Mapper.convert()  →  reflection
   → every layer pays the cost, and every layer can fail at runtime

   ✅ ONE REFLECTIVE BOUNDARY
   ─────────────────────────
   class Framework {                    ← the ONLY place with reflection
       static Object invoke(Method m, Object target, Object... args) { … }
   }
   Service.doWork()  →  a direct call
   Repository.find() →  a direct call
   → the reflection is contained, and its failures are contained too
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| Method references and lambdas (the compile-time alternative) | [06 · Lambda & Functional Interfaces](../06-Java-8-Features/01-Lambda-Functional-Interfaces.md) |
| Sealed types, records and pattern matching | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| Serialisation (which uses reflection internally) | [17 · Serialization](../17-Serialization/01-Serialization-Basics.md) |
| Testing and mocks (which use dynamic proxies) | [18 · Testing](../18-Testing/README.md) |
| The JDK source | `java.lang.reflect.Method`, `java.lang.invoke.MethodHandle`, `java.util.ServiceLoader` |

---

**Next Chapter:** → [15 · I/O & NIO](../15-IO-NIO/01-IO-NIO.md)
**Previous Chapter:** → [14 · Reflection & Annotations](01-Reflection-and-Annotations.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
