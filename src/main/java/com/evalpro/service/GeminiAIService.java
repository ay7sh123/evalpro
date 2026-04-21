package com.evalpro.service;

import com.evalpro.controller.teacher.CreateExamController.QuestionData;
import com.evalpro.controller.teacher.CreateExamController.QuestionData.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GeminiAIService {

    // ✅ ABSOLUTE FINAL - Google's exact official format
    private static final String API_KEY = "AIzaSyBSx0IYA0wZONvMvSijnRlbkRjLRht0Nqw";

    // ✅ Try these ONE BY ONE (comment/uncomment):

    // Option 1 (Most likely to work):
    private static final String MODEL_NAME = "gemini-pro";

    // Option 2 (If option 1 fails):
    // private static final String MODEL_NAME = "gemini-1.5-flash-001";

    // Option 3 (Backup):
    // private static final String MODEL_NAME = "gemini-1.0-pro";

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + API_KEY;

    /**
     * Generate questions from a topic
     */
    public static List<QuestionData> generateQuestionsFromTopic(
            String topic, int mcqCount, int theoryCount, int codingCount) {
        System.out.println("🤖 AI: Generating questions for topic: " + topic);
        System.out.println("🔧 Using model: " + MODEL_NAME);
        return callGeminiAndParse(buildPrompt(topic, mcqCount, theoryCount, codingCount));
    }

    /**
     * Generate questions from a URL
     */
    public static List<QuestionData> generateQuestionsFromURL(
            String url, int mcqCount, int theoryCount, int codingCount) {
        System.out.println("🤖 AI: Generating from URL: " + url);
        System.out.println("🔧 Using model: " + MODEL_NAME);
        return callGeminiAndParse("Read content from: " + url + "\n\n" + buildPromptSuffix(mcqCount, theoryCount, codingCount));
    }

    private static String buildPrompt(String topic, int mcq, int theory, int coding) {
        return "Generate exam questions on topic: " + topic + "\n\n" + buildPromptSuffix(mcq, theory, coding);
    }

    private static String buildPromptSuffix(int mcq, int theory, int coding) {
        return "Generate EXACTLY: " + mcq + " MCQ, " + theory + " Theory, " + coding + " Coding questions.\n\n" +
                "Return ONLY a JSON array starting with [ and ending with ]. No markdown, no explanation.\n\n" +
                "Format:\n[\n" +
                "  {\"type\":\"MCQ\",\"question\":\"Q?\",\"optionA\":\"A\",\"optionB\":\"B\",\"optionC\":\"C\",\"optionD\":\"D\",\"correct\":\"A\",\"marks\":2},\n" +
                "  {\"type\":\"Theory\",\"question\":\"Q?\",\"modelAnswer\":\"Answer\",\"marks\":5},\n" +
                "  {\"type\":\"Coding\",\"question\":\"Q?\",\"inputFormat\":\"N\",\"outputFormat\":\"result\",\"sampleInput\":\"5\",\"sampleOutput\":\"120\",\"testCases\":[{\"input\":\"5\",\"output\":\"120\"}],\"marks\":10}\n]\n\n" +
                "Start with [, end with ]. Nothing else.";
    }

    private static List<QuestionData> callGeminiAndParse(String prompt) {
        List<QuestionData> questions = new ArrayList<>();
        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            System.out.println("📡 Calling: " + API_URL);

            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("📥 HTTP Status: " + responseCode);

            if (responseCode != 200) {
                StringBuilder err = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) err.append(line);
                }
                System.out.println("❌ Error Response: " + err);
                System.out.println("\n💡 TRY THIS: Go to https://aistudio.google.com/app/apikey");
                System.out.println("   Click 'Get API Key' → Copy new key → Replace in code");
                return questions;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) response.append(line);
            }

            System.out.println("✅ Response received (" + response.length() + " chars)");

            JSONObject jsonResponse = new JSONObject(response.toString());

            if (!jsonResponse.has("candidates") || jsonResponse.getJSONArray("candidates").length() == 0) {
                System.out.println("❌ No candidates in response");
                return questions;
            }

            String text = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            System.out.println("=== RAW AI RESPONSE ===\n" + text + "\n=======================");

            text = text.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            int startIdx = text.indexOf('[');
            int endIdx   = text.lastIndexOf(']');
            if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) {
                System.out.println("❌ No JSON array found in response!");
                return questions;
            }
            text = text.substring(startIdx, endIdx + 1);

            JSONArray arr;
            try {
                arr = new JSONArray(text);
            } catch (Exception ex) {
                System.out.println("❌ JSON parse failed: " + ex.getMessage());
                return questions;
            }

            System.out.println("✅ Parsed " + arr.length() + " questions");

            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject q   = arr.getJSONObject(i);
                    String type    = q.optString("type", "MCQ").trim();
                    int marks      = q.optInt("marks", 2);
                    String qText   = q.has("question") ? q.getString("question") : q.optString("question_text", "");
                    if (qText.isEmpty()) continue;

                    System.out.println("  Q" + (i+1) + ": " + type + " | " + marks + " marks");

                    if (type.equalsIgnoreCase("MCQ")) {
                        questions.add(new QuestionData(qText,
                                q.optString("optionA","Option A"),
                                q.optString("optionB","Option B"),
                                q.optString("optionC","Option C"),
                                q.optString("optionD","Option D"),
                                q.optString("correct","A").trim().toUpperCase(), marks));

                    } else if (type.equalsIgnoreCase("Theory")) {
                        questions.add(new QuestionData(qText, q.optString("modelAnswer",""), marks));

                    } else if (type.equalsIgnoreCase("Coding")) {
                        List<TestCase> tc = new ArrayList<>();
                        if (q.has("testCases")) {
                            JSONArray tca = q.getJSONArray("testCases");
                            for (int j = 0; j < tca.length(); j++) {
                                JSONObject t = tca.getJSONObject(j);
                                tc.add(new TestCase(t.optString("input",""), t.optString("output","")));
                            }
                        }
                        questions.add(new QuestionData(qText,
                                q.optString("inputFormat",""),
                                q.optString("outputFormat",""),
                                q.optString("sampleInput",""),
                                q.optString("sampleOutput",""),
                                tc, marks));
                    }
                } catch (Exception ex) {
                    System.out.println("⚠️ Q" + (i+1) + " parse error: " + ex.getMessage());
                }
            }

            System.out.println("✅ Final: " + questions.size() + " questions generated");

        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
        return questions;
    }
}