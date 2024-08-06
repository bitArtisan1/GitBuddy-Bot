package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.io.JsonEOFException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.GitHubBot.rateLimitExceeded;
import static com.example.GitHubBot.scheduler;

public class Utils {
    static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");
    static final String BASE_URL = "https://api.github.com";
    static final HttpClient httpClient = HttpClient.newBuilder().build();
    static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    static final File jsonFile = new File("operations.json");

    // ANSI escape codes for colors
    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String PURPLE = "\u001B[35m";
    static final String ORANGE = "\u001B[38;5;208m"; // ANSI color code for orange

    static void printRateLimit() {
        try {
            String url = BASE_URL + "/rate_limit";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + GITHUB_TOKEN)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode rate = root.get("rate");
                int limit = rate.get("limit").asInt();
                int remaining = rate.get("remaining").asInt();
                long reset = rate.get("reset").asLong();

                LocalDateTime resetTime = LocalDateTime.ofEpochSecond(reset, 0, java.time.ZoneOffset.UTC);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                System.out.println(ORANGE + "Rate Limit: " + limit + " requests/hour" + RESET);
                System.out.println(ORANGE + "Remaining: " + remaining + " requests" + RESET);
                System.out.println(ORANGE + "Reset Time: " + resetTime.format(formatter) + RESET);
            } else {
                System.err.println(RED + "Failed to fetch rate limit information: " + response.body() + RESET);
            }
        } catch (IOException | InterruptedException e) {
            logError("Error fetching rate limit information", e);
        }
    }

    static List<JsonNode> fetchAllRepositories() {
        List<JsonNode> repositories = new ArrayList<>();
        int page = 1;
        try {
            String yesterday = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_DATE);
            String query = URLEncoder.encode(String.format("created:>%s stars:<4", yesterday), StandardCharsets.UTF_8);

            while (true) {
                String url = String.format("%s/search/repositories?q=%s&per_page=100&page=%d", BASE_URL, query, page);

                System.out.println("Searching for repositories with query: " + PURPLE + URLDecoder.decode(query, StandardCharsets.UTF_8) + RESET + " (Page " + page + ")");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + GITHUB_TOKEN)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode items = root.get("items");
                    if (items == null || !items.isArray() || items.size() == 0) {
                        break;
                    }
                    items.forEach(repositories::add);
                    page++;
                } else {
                    System.err.println(RED + "Error fetching repositories: " + response.body() + RESET);
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            logError("Error fetching repositories", e);
        }
        return repositories;
    }

    static void processRepository(JsonNode repo) {
        String fullName = repo.get("full_name").asText();
        String owner = repo.get("owner").get("login").asText();

        CompletableFuture<Boolean> starFuture = CompletableFuture.supplyAsync(() -> starRepository(fullName));
        CompletableFuture<Boolean> followFuture = CompletableFuture.supplyAsync(() -> followUser(owner));

        starFuture.thenCombine(followFuture, (starred, followed) -> {
            if (starred) {
                System.out.println("Starred " + GREEN + fullName + RESET);
                saveOperation("star", fullName);
            }
            if (followed) {
                System.out.println("Followed " + GREEN + owner + RESET);
                saveOperation("follow", owner);
            }
            return null;
        }).join();
    }

    static boolean starRepository(String fullName) {
        if (isOperationRecorded("star", fullName)) {
            System.out.println("Repository " + GREEN + fullName + RESET + " is already starred.");
            return false;
        }

        String[] repoParts = fullName.split("/");
        if (repoParts.length != 2) {
            System.err.println(RED + "Invalid repository name: " + fullName + RESET);
            return false;
        }
        String owner = repoParts[0];
        String repo = repoParts[1];
        String url = String.format("%s/user/starred/%s/%s", BASE_URL, owner, repo);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                .header("Accept", "application/vnd.github.v3+json")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                saveOperation("star", fullName);
                return true;
            } else {
                System.err.println(RED + "Failed to star repository " + fullName + ": " + response.body() + RESET);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logError("Error starring repository " + fullName, e);
            return false;
        }
    }

    static boolean followUser(String username) {
        if (rateLimitExceeded.get()) {
            return false;
        }

        if (isOperationRecorded("follow", username)) {
            System.out.println("User " + GREEN + username + RESET + " is already followed.");
            return false;
        }

        String url = String.format("%s/user/following/%s", BASE_URL, username);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                .header("Accept", "application/vnd.github.v3+json")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                saveOperation("follow", username);
                return true;
            } else if (response.statusCode() == 429 || response.body().contains("secondary rate limit")) { // Handle rate limit exceeded
                System.err.println(YELLOW + "Rate limit exceeded while trying to follow user " + username + ". Rescheduling all follow operations after 1 hour..." + RESET);
                rateLimitExceeded.set(true);
                return false;
            } else {
                System.err.println(RED + "Failed to follow user " + username + ": " + response.body() + RESET);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logError("Error following user " + username, e);
            return false;
        }
    }

    static boolean isOperationRecorded(String operation, String target) {
        try {
            if (!jsonFile.exists() || jsonFile.length() == 0) {
                return false;
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(jsonFile);
            } catch (JsonEOFException e) {
                return false;
            }

            if (root.has(operation)) {
                ArrayNode arrayNode = (ArrayNode) root.get(operation);
                for (JsonNode node : arrayNode) {
                    if (node.asText().equals(target)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            logError("Error checking operation record", e);
        }
        return false;
    }

    static synchronized void saveOperation(String operation, String target) {
        try {
            if (!jsonFile.exists()) {
                jsonFile.createNewFile();
            }

            ObjectNode root;
            if (jsonFile.length() > 0) {
                root = (ObjectNode) objectMapper.readTree(jsonFile);
            } else {
                root = objectMapper.createObjectNode();
            }

            ArrayNode arrayNode;
            if (root.has(operation)) {
                arrayNode = (ArrayNode) root.get(operation);
            } else {
                arrayNode = objectMapper.createArrayNode();
                root.set(operation, arrayNode);
            }

            if (!isOperationRecorded(operation, target)) {
                arrayNode.add(target);
            }

            objectMapper.writeValue(jsonFile, root);
        } catch (IOException e) {
            logError("Error saving operation", e);
        }
    }

    static synchronized void deleteOperation(String operation, String target) {
        try {
            if (!jsonFile.exists() || jsonFile.length() == 0) {
                return;
            }

            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonFile);
            if (root.has(operation)) {
                ArrayNode arrayNode = (ArrayNode) root.get(operation);
                for (int i = 0; i < arrayNode.size(); i++) {
                    if (arrayNode.get(i).asText().equals(target)) {
                        arrayNode.remove(i);
                        break;
                    }
                }
            }

            objectMapper.writeValue(jsonFile, root);
        } catch (IOException e) {
            logError("Error deleting operation", e);
        }
    }

    static void undoOperations() {
        try {
            if (!jsonFile.exists() || jsonFile.length() == 0) {
                System.out.println(RED + "No operations to undo." + RESET);
                return;
            }

            JsonNode root = objectMapper.readTree(jsonFile);

            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            if (root.has("star")) {
                ArrayNode stars = (ArrayNode) root.get("star");
                for (JsonNode node : stars) {
                    futures.add(unstarRepository(node.asText()));
                }
            }

            if (root.has("follow")) {
                ArrayNode follows = (ArrayNode) root.get("follow");
                for (JsonNode node : follows) {
                    futures.add(unfollowUser(node.asText()));
                }
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                allOf.get();
                // Clear the operations file after undoing
                objectMapper.writeValue(jsonFile, objectMapper.createObjectNode());
                System.out.println(YELLOW + "All operations have been undone." + RESET);
            } catch (InterruptedException | ExecutionException e) {
                logError("Error undoing operations", e);
            }
        } catch (IOException e) {
            logError("Error undoing operations", e);
        }
    }

    static CompletableFuture<Boolean> unstarRepository(String fullName) {
        return CompletableFuture.supplyAsync(() -> {
            String[] repoParts = fullName.split("/");
            if (repoParts.length != 2) {
                System.err.println(RED + "Invalid repository name: " + fullName + RESET);
                return false;
            }
            String owner = repoParts[0];
            String repo = repoParts[1];
            String url = String.format("%s/user/starred/%s/%s", BASE_URL, owner, repo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + GITHUB_TOKEN)
                    .header("Accept", "application/vnd.github.v3+json")
                    .DELETE()
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 204) {
                    System.out.println("Unstarred " + GREEN + fullName + RESET);
                    deleteOperation("star", fullName);
                    return true;
                } else {
                    System.err.println(RED + "Failed to unstar repository " + fullName + ": " + response.body() + RESET);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                logError("Error unstarring repository " + fullName, e);
                return false;
            }
        }, executorService).thenApply(success -> {
            if (success) {
                try {
                    // Introduce delay between operations to avoid being flagged as spam
                    Thread.sleep(5000); // 5 seconds delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return success;
        });
    }

    static CompletableFuture<Boolean> unfollowUser(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String url = String.format("%s/user/following/%s", BASE_URL, username);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + GITHUB_TOKEN)
                    .header("Accept", "application/vnd.github.v3+json")
                    .DELETE()
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 204) {
                    System.out.println("Unfollowed " + GREEN + username + RESET);
                    deleteOperation("follow", username);
                    return true;
                } else {
                    System.err.println(RED + "Failed to unfollow user " + username + ": " + response.body() + RESET);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                logError("Error unfollowing user " + username, e);
                return false;
            }
        }, executorService).thenApply(success -> {
            if (success) {
                try {
                    // Introduce delay between operations to avoid being flagged as spam
                    Thread.sleep(5000); // 5 seconds delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return success;
        });
    }

    static void logError(String message, Exception e) {
        System.err.println(RED + message + RESET);
        e.printStackTrace();
    }

    static void shutdownExecutorServices() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
