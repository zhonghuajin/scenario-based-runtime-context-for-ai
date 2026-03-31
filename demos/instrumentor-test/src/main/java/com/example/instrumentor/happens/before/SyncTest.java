package com.example.instrumentor.happens.before;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class SyncTest {

    private int sharedData = 0;
    private volatile boolean volatileFlag = false;

    public static void main(String[] args) throws Exception {
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

    private static void keepAlive() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void testThreadStartAndJoin() throws InterruptedException {
        sharedData = 1; 

        Thread t = new Thread(() -> {
            System.out.println("Thread-Start read sharedData: " + sharedData);
            sharedData = 2; 
        });

        t.start();
        t.join(); 
        
        System.out.println("Thread-Join read sharedData: " + sharedData);
    }

    private void testVolatile() throws InterruptedException {
        sharedData = 0;
        volatileFlag = false;

        Thread writer = new Thread(() -> {
            sharedData = 3;       
            volatileFlag = true;  
        });

        Thread reader = new Thread(() -> {
            while (!volatileFlag) { 
                Thread.yield(); 
            }
            System.out.println("Volatile read sharedData: " + sharedData);
        });

        reader.start();
        writer.start();

        writer.join();
        reader.join();
    }

    private void testSynchronized() throws InterruptedException {
        final Object monitor = new Object();
        sharedData = 0;

        Thread writer = new Thread(() -> {
            synchronized (monitor) {
                sharedData = 4; 
            } 
        });

        Thread reader = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            
            synchronized (monitor) { 
                System.out.println("Synchronized read sharedData: " + sharedData);
            }
        });

        writer.start();
        reader.start();

        writer.join();
        reader.join();
    }

    private void testLockAndCondition() throws InterruptedException {
        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        sharedData = 0;

        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                while (sharedData == 0) {
                    condition.await(); 
                }
                System.out.println("Condition await read sharedData: " + sharedData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });

        Thread signaler = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            lock.lock();
            try {
                sharedData = 5; 
                condition.signal(); 
            } finally {
                lock.unlock();
            }
        });

        waiter.start();
        signaler.start();

        waiter.join();
        signaler.join();
    }

    private void testCountDownLatch() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        sharedData = 0;

        Thread worker = new Thread(() -> {
            // BUG INTRODUCED HERE: 
            // Calling countDown() before writing to sharedData breaks the happens-before guarantee.
            // The waiter thread might wake up and read sharedData before it is updated to 6.
            latch.countDown(); 
            sharedData = 6; 
        });

        Thread waiter = new Thread(() -> {
            try {
                latch.await(); 
                System.out.println("CountDownLatch read sharedData: " + sharedData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        waiter.start();
        worker.start();

        worker.join();
        waiter.join();
    }

    private void testCompletableFuture() throws Exception {
        sharedData = 0;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            sharedData = 7; 
        });

        future.get();
        System.out.println("CompletableFuture read sharedData: " + sharedData);
    }
}