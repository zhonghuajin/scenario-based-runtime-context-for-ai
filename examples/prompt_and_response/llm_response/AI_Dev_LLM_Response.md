# New Feature Development Guidance Plan

## 1. Core Idea
To add a Semaphore-based test scenario, we will introduce a new method `testSemaphore()` that demonstrates the Happens-Before guarantee provided by `java.util.concurrent.Semaphore`. Specifically, calling `release()` on a Semaphore happens-before a successful `acquire()` on that same Semaphore. We will spawn a writer thread that updates `sharedData` and releases a permit, and a reader thread that acquires the permit before reading `sharedData`. Finally, we will hook this new test into the sequential execution flow in the `main` method.

## 2. Key Hook Point (Where to modify)
- **File**: `com/example/instrumentor/happens/before/SyncTest.java`
- **Function**: `public static void main(String[] args)` (to register the new test) and a new method `private void testSemaphore()` (to implement the logic).
- **Reasoning**: The `main` method acts as the test suite runner, executing each synchronization scenario sequentially. Adding the call here ensures it runs as part of the standard test suite. The new method isolates the Semaphore logic, maintaining the clean, modular structure established by the existing tests.

## 3. Code Implementation (How to modify)

Here are the complete methods to be updated and added to `com/example/instrumentor/happens/before/SyncTest.java`:

```java
    // 🛠️ [Modified] Complete main method with the new Semaphore test hooked in
    public static void main(String[] args) throws Exception 
    {
        SyncTest test = new SyncTest();
        System.out.println("--- Testing Thread Start/Join Rule ---");
        test.testThreadStartAndJoin();
        System.out.println("\n--- Testing Volatile Variable Rule ---");
        test.testVolatile();
        System.out.println("\n--- Testing Monitor Lock (synchronized) Rule ---");
        test.testSynchronized();
        System.out.println("\n--- Testing JUC Lock & Condition Rule ---");
        test.testLockAndCondition();
        System.out.println("\n--- Testing CountDownLatch Rule ---");
        test.testCountDownLatch();
        System.out.println("\n--- Testing CompletableFuture Rule ---");
        test.testCompletableFuture();
        
        // ✨ [Added] Hooking in the new Semaphore test scenario
        System.out.println("\n--- Testing Semaphore Rule ---");
        test.testSemaphore();
        
        System.out.println("\nAll tests completed. The program will keep running...");
        keepAlive();
    }

    // ✨ [Added] Complete new method for the Semaphore scenario
    private void testSemaphore() throws InterruptedException 
    {
        // Initialize Semaphore with 0 permits so the reader must wait for the writer
        java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(0);
        sharedData = 0;
        
        Thread writer = new Thread(() -> {
            sharedData = 8; // Write to shared variable
            semaphore.release(); // Release a permit, establishing a happens-before edge
        });
        
        Thread reader = new Thread(() -> {
            try 
            {
                semaphore.acquire(); // Acquire the permit, waiting if necessary
                System.out.println("Semaphore read sharedData: " + sharedData);
            } catch (InterruptedException e) {
                // Handle interruption appropriately
            }
        });
        
        reader.start();
        writer.start();
        
        writer.join();
        reader.join();
    }
```

## 4. Potential Risks and Notes
- **Permit Initialization**: The `Semaphore` must be initialized with `0` permits. If initialized with `1` or more, the reader thread might acquire a permit and read `sharedData` before the writer thread updates it, causing a visibility failure or race condition.
- **Import Statements**: Ensure `java.util.concurrent.Semaphore` is imported at the top of the file if you prefer not to use the fully qualified name provided in the snippet.
- **InterruptedException Handling**: The `acquire()` method throws `InterruptedException`. In the provided code, it is caught and ignored to match the existing pattern in the trace data (e.g., `testLockAndCondition`), but in a production environment, thread interruption should be handled more robustly (e.g., restoring the interrupt flag).
- **Sequential Execution**: Because the `main` method uses `.join()` in all its test methods, the tests run strictly sequentially. The new `testSemaphore()` will safely execute without interfering with the `sharedData` state of the preceding `testCompletableFuture()` or the subsequent `keepAlive()` state.