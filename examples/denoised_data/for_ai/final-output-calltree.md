# Thread Traces

> **Data Schema & Legend:**
> This section represents the execution call tree for each thread.
> - **Trace**: The linear sequence of executed basic block IDs.
> - **Call Tree**: Hierarchical execution flow. Each node contains the method signature, source file, executed block IDs, and pruned source code.

## Thread-3 (Order: 1)
**Trace:** [1,5,7,11,17,27,32,2,3]

- **`public static void main(String[] args) throws Exception`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [1]
    ```java
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
        System.out.println("\nAll tests completed. The program will keep running...");
        keepAlive();
    }
    ```
    *Calls:*
    - **`private void testThreadStartAndJoin() throws InterruptedException`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [5]
        ```java
        private void testThreadStartAndJoin() throws InterruptedException 
        {
            sharedData = 1;
            Thread t = new Thread(() -> {
            });
            t.start();
            t.join();
            System.out.println("Thread-Join read sharedData: " + sharedData);
        }
        ```
    - **`private void testVolatile() throws InterruptedException`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [7]
        ```java
        private void testVolatile() throws InterruptedException 
        {
            sharedData = 0;
            volatileFlag = false;
            Thread writer = new Thread(() -> {
            });
            Thread reader = new Thread(() -> {
            });
            reader.start();
            writer.start();
            writer.join();
            reader.join();
        }
        ```
    - **`private void testSynchronized() throws InterruptedException`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [11]
        ```java
        private void testSynchronized() throws InterruptedException 
        {
            final Object monitor = new Object();
            sharedData = 0;
            Thread writer = new Thread(() -> {
            });
            Thread reader = new Thread(() -> {
            });
            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }
        ```
    - **`private void testLockAndCondition() throws InterruptedException`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [17]
        ```java
        private void testLockAndCondition() throws InterruptedException 
        {
            Lock lock = new ReentrantLock();
            Condition condition = lock.newCondition();
            sharedData = 0;
            Thread waiter = new Thread(() -> {
            });
            Thread signaler = new Thread(() -> {
            });
            waiter.start();
            signaler.start();
            waiter.join();
            signaler.join();
        }
        ```
    - **`private void testCountDownLatch() throws InterruptedException`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [27]
        ```java
        private void testCountDownLatch() throws InterruptedException 
        {
            CountDownLatch latch = new CountDownLatch(1);
            sharedData = 0;
            Thread worker = new Thread(() -> {
                // BUG INTRODUCED HERE:
                // Calling countDown() before writing to sharedData breaks the happens-before guarantee.
            });
            Thread waiter = new Thread(() -> {
            });
            waiter.start();
            worker.start();
            worker.join();
            waiter.join();
        }
        ```
    - **`private void testCompletableFuture() throws Exception`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [32]
        ```java
        private void testCompletableFuture() throws Exception 
        {
            sharedData = 0;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            });
            future.get();
            System.out.println("CompletableFuture read sharedData: " + sharedData);
        }
        ```
    - **`private static void keepAlive()`**
        *File:* `com/example/instrumentor/happens/before/SyncTest.java`
        *Blocks:* [2,3]
        ```java
        private static void keepAlive() 
        {
            CountDownLatch latch = new CountDownLatch(1);
            try 
            {
                latch.await();
            } catch (InterruptedException e) {
            }
        }
        ```

---

## Thread-36 (Order: 2)
**Trace:** [6]

- **`Thread t = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [6]
    ```java
    Thread t = new Thread(() -> 
    {
        System.out.println("Thread-Start read sharedData: " + sharedData);
        sharedData = 2;
    });
    ```

---

## Thread-38 (Order: 3)
**Trace:** [9,10]

- **`Thread reader = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [9,10]
    ```java
    Thread reader = new Thread(() -> 
    {
        while (!volatileFlag) 
        {
            Thread.yield();
        }
        System.out.println("Volatile read sharedData: " + sharedData);
    });
    ```

---

## Thread-37 (Order: 4)
**Trace:** [8]

- **`Thread writer = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [8]
    ```java
    Thread writer = new Thread(() -> 
    {
        sharedData = 3;
        volatileFlag = true;
    });
    ```

---

## Thread-39 (Order: 5)
**Trace:** [12,13]

- **`Thread writer = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [12,13]
    ```java
    Thread writer = new Thread(() -> 
    {
        synchronized (monitor) 
        {
            sharedData = 4;
        }
    });
    ```

---

## Thread-40 (Order: 6)
**Trace:** [14,15,16]

- **`Thread reader = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [14,15,16]
    ```java
    Thread reader = new Thread(() -> 
    {
        try 
        {
            Thread.sleep(50);
        } catch (InterruptedException e) 
        {
        }
        synchronized (monitor) 
        {
            System.out.println("Synchronized read sharedData: " + sharedData);
        }
    });
    ```

---

## Thread-41 (Order: 7)
**Trace:** [18,19,20,22]

- **`Thread waiter = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [18,19,20,22]
    ```java
    Thread waiter = new Thread(() -> 
    {
        lock.lock();
        try 
        {
            while (sharedData == 0) 
            {
                condition.await();
            }
            System.out.println("Condition await read sharedData: " + sharedData);
        } catch (InterruptedException e) {
        } finally 
        {
            lock.unlock();
        }
    });
    ```

---

## Thread-42 (Order: 8)
**Trace:** [23,24,25,26]

- **`Thread signaler = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [23,24,25,26]
    ```java
    Thread signaler = new Thread(() -> 
    {
        try 
        {
            Thread.sleep(50);
        } catch (InterruptedException e) 
        {
        }
        lock.lock();
        try 
        {
            sharedData = 5;
            condition.signal();
        } finally 
        {
            lock.unlock();
        }
    });
    ```

---

## Thread-46 (Order: 9)
**Trace:** [29,30]

- **`Thread waiter = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [29,30]
    ```java
    Thread waiter = new Thread(() -> 
    {
        try 
        {
            latch.await();
            System.out.println("CountDownLatch read sharedData: " + sharedData);
        } catch (InterruptedException e) {
        }
    });
    ```

---

## Thread-45 (Order: 10)
**Trace:** [28]

- **`Thread worker = new Thread(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [28]
    ```java
    Thread worker = new Thread(() -> 
    {
        // BUG INTRODUCED HERE:
        // Calling countDown() before writing to sharedData breaks the happens-before guarantee.
        // The waiter thread might wake up and read sharedData before it is updated to 6.
        latch.countDown();
        sharedData = 6;
    });
    ```

---

## Thread-47 (Order: 11)
**Trace:** [33]

- **`CompletableFuture<Void> future = CompletableFuture.runAsync(() ->`**
    *File:* `com/example/instrumentor/happens/before/SyncTest.java`
    *Blocks:* [33]
    ```java
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> 
    {
        sharedData = 7;
    });
    ```

---

