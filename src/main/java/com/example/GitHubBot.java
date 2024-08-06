package com.example;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.example.Utils.*;

public class GitHubBot {
    private static final AtomicBoolean stopFlag = new AtomicBoolean(false);
    static final AtomicBoolean rateLimitExceeded = new AtomicBoolean(false);
    static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        if (GITHUB_TOKEN == null || GITHUB_TOKEN.isEmpty()) {
            System.err.println(RED + "GitHub token is not set. Please set the GITHUB_TOKEN environment variable." + RESET);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopFlag.set(true);
            System.out.println(YELLOW + "\nShutdown signal received. Stopping the bot..." + RESET);
        }));

        printRateLimit();
        displayMenu();
    }

    private static void displayMenu() {
        System.out.println(BLUE + "GitHub Bot Interface Panel" + RESET);
        System.out.println("1- Start the bot");
        System.out.println("2- Undo actions or operations");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Choose an option: ");
        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                System.out.println(BLUE + "Starting GitHub bot..." + RESET);
                scheduleTask(0);
                break;
            case 2:
                System.out.println(YELLOW + "Undoing actions or operations..." + RESET);
                undoOperations();
                break;
            default:
                System.err.println(RED + "Invalid choice. Exiting..." + RESET);
                break;
        }
    }

    private static void scheduleTask(long delay) {
        scheduler.schedule(() -> {
            List<JsonNode> repositories = fetchAllRepositories();
            System.out.println("Fetched " + PURPLE + repositories.size() + RESET + " repositories.");

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (JsonNode repo : repositories) {
                if (stopFlag.get() || rateLimitExceeded.get()) break;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> processRepository(repo), executorService);
                futures.add(future);

                try {
                    // Introduce delay between operations to avoid being flagged as spam
                    Thread.sleep(5000); // 5 seconds delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                allOf.get();
            } catch (InterruptedException | ExecutionException e) {
                logError("Error waiting for tasks to complete", e);
            }

            if (rateLimitExceeded.get()) {
                System.out.println(YELLOW + "Rescheduling all follow operations after 1 hour due to rate limit exceeded..." + RESET);
                scheduleTask(3600); // Reschedule after 1 hour
            } else {
                shutdownExecutorServices();
            }
        }, delay, TimeUnit.SECONDS);
    }
}
