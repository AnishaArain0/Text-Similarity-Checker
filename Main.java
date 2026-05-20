import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class Main {
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        // Initialize high-performance light HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Routing/Endpoint configuration
        server.createContext("/api/compare", new PlagiarismHandler());
        
        // Default executor
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10)); 
        System.out.println("Text Similarity Checker Server running on port " + PORT);
        server.start();
    }

    // ALGORITHM: LONGEST COMMON SUBSEQUENCE (LCS) - Dynamic Programming
    public static SimilarityResult computeLCS(String text1, String text2) {
        int m = text1.length();
        int n = text2.length();
        
        // DP Table
        int[][] dp = new int[m + 1][n + 1];

        // Fill DP table
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        int lcsLength = dp[m][n];

        // Backtracking to find exact matching characters
        StringBuilder lcsString = new StringBuilder();
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                lcsString.append(text1.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }

        // Reverse string because we backtracked
        String matchedText = lcsString.reverse().toString();

        // Calculate similarity percentage: (2 * LCS) / (Total Length) * 100
        double similarity = 0.0;
        if ((m + n) > 0) {
            similarity = (2.0 * lcsLength) / (m + n) * 100.0;
        }

        return new SimilarityResult(lcsLength, similarity, matchedText);
    }

    // Custom Data Record for Result
    record SimilarityResult(int lcsLength, double similarityPercentage, String matchedText) {}

    // HTTP ROUTE HANDLER
    static class PlagiarismHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                
                // Extract strings
                String text1 = extractJsonValue(body, "text1");
                String text2 = extractJsonValue(body, "text2");

                if (text1.isEmpty() || text2.isEmpty()) {
                    sendResponse(exchange, "{\"status\":\"error\",\"message\":\"Please enter both texts\"}", 400);
                    return;
                }

                // Fire Algorithm
                SimilarityResult result = computeLCS(text1, text2);
                
                // Manually construct JSON to avoid heavy libraries
                String jsonResponse = String.format(
                    "{\"status\":\"success\", \"lcsLength\":%d, \"similarity\":%.2f, \"matchedText\":\"%s\"}",
                    result.lcsLength(), result.similarityPercentage(), escapeJson(result.matchedText())
                );

                sendResponse(exchange, jsonResponse, 200);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // High performance Regex based JSON extractor
    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\n", "\n").replace("\\\"", "\"");
        }
        return "";
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
    }

    private static void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}