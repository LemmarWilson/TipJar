package org.lemmar.dev;

// Standard Java Imports
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

// JSON Processing (Jackson)
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Excel Handling (Apache POI)
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// Email Handling (Jakarta Mail)
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// SMS Handling (Twilio)
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

// AWS Lambda Imports
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

// Logging with SLF4J
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TipOfTheDay implements RequestHandler<Object, String> {

    // Create an SLF4J logger
    private static final Logger logger = LoggerFactory.getLogger(TipOfTheDay.class);

    // Environment variables
    private static final String EMAIL_USER = System.getenv("EMAIL_USER");
    private static final String EMAIL_PASSWORD = System.getenv("EMAIL_PASSWORD");
    private static final String EMAIL_PORT = System.getenv("EMAIL_PORT");
    private static final String EMAIL_HOST = System.getenv("EMAIL_HOST");
    private static final String EMAIL_ADDRESS = System.getenv("EMAIL_ADDRESS");
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String TWILIO_ACCOUNT_SID = System.getenv("TWILIO_ACCOUNT_SID");
    private static final String TWILIO_AUTH_TOKEN = System.getenv("TWILIO_AUTH_TOKEN");
    private static final String TWILIO_PHONE_NUMBER = System.getenv("TWILIO_PHONE_NUMBER");
    private static final String PHONE_NUMBER = System.getenv("PHONE_NUMBER");

    // Reusable HTTP client and JSON mapper
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Simple class to hold prompt data
    public record Prompt(String topic, String prompt) {}

    /**
     * Loads prompts from the given Excel file and sheet name.
     *
     * @param sheetName Name of the sheet.
     * @return List of Prompt objects.
     */
    public static List<Prompt> loadPrompts(String sheetName) {
        logger.info("Starting to load prompts from Excel file sheet: {}", sheetName);
        List<Prompt> prompts = new ArrayList<>();
        try (InputStream is = TipOfTheDay.class.getResourceAsStream("/prompts.xlsx");
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet(sheetName);
            // The first row is a header; start at row index 1.
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Cell topicCell = row.getCell(0);
                    Cell promptCell = row.getCell(1);
                    if (topicCell != null && promptCell != null) {
                        String topic = topicCell.getStringCellValue();
                        String prompt = promptCell.getStringCellValue();
                        prompts.add(new Prompt(topic, prompt));
                        logger.info("Loaded prompt: {}", topic);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error loading prompts: {}", e.getMessage(), e);
        }

        logger.info("Finished loading {} prompts from Excel file.", prompts.size());
        return prompts;
    }

    /**
     * Generates a tip using the OpenAI API.
     *
     * @param promptList List of prompts to choose from.
     * @return A string containing the topic and generated tip.
     */
    public static String tipGenerator(List<Prompt> promptList) {
        try {
            // Select a random prompt
            Prompt selected = promptList.get(new Random().nextInt(promptList.size()));
            String topic = selected.topic;
            String fullPrompt = selected.prompt + " Return the answer with 75 - 100 words, and after that, include an example.";

            logger.info("Selected topic: {}", topic);

            // Build the JSON payload
            String requestBody = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "model", "gpt-4o-mini",
                    "store", true,
                    "messages", List.of(Map.of("role", "user", "content", fullPrompt))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            logger.info("Request payload: {}", requestBody);
            logger.info("Waiting for response from OpenAI API...");

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String content = extractContent(response.body());
            logger.info("Received and processed response from OpenAI API for topic: {}", topic);

            return "Topic: " + topic + "\n\n" + content;
        } catch (IOException | InterruptedException e) {
            logger.error("Error generating tip: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts the generated content from the OpenAI API JSON response.
     *
     * @param jsonResponse The JSON response as a string.
     * @return The generated text content.
     */
    private static String extractContent(String jsonResponse) throws IOException {
        // Switch to debug if this is too noisy
        logger.debug("OpenAI API response: {}", jsonResponse);

        JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode messageNode = choices.get(0).path("message");
            return messageNode.path("content").asText();
        }
        return "";
    }

    /**
     * Sends an email using the JavaMail API.
     *
     * @param subject The email subject.
     * @param body    The email body.
     * @param toEmail The recipient’s email address.
     */
    public static void sendEmail(String subject, String body, String toEmail) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", EMAIL_HOST);
        props.put("mail.smtp.port", EMAIL_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_USER, EMAIL_PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_USER));
            message.setRecipients(RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(body);

            logger.info("Sending email with subject '{}' to {}", subject, toEmail);
            Transport.send(message);
            logger.info("Email successfully sent to {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Error sending email: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends a text message using the Twilio Java SDK.
     *
     * @param body          The SMS message content.
     * @param toPhoneNumber The recipient’s phone number.
     */
    public static void sendText(String body, String toPhoneNumber) {
        try {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            String messageBody = "Tip of the day!\n\n" + body;
            logger.info("Sending text message to phone number: {}", toPhoneNumber);

            Message message = Message.creator(
                    new PhoneNumber(toPhoneNumber),
                    new PhoneNumber(TWILIO_PHONE_NUMBER),
                    messageBody
            ).create();

            logger.info("Text message sent successfully with SID: {}", message.getSid());
        } catch (Exception e) {
            logger.error("Error sending text message: {}", e.getMessage(), e);
        }
    }

    /**
     * The Lambda handler method.
     *
     * @param input   The input to the Lambda (ignored in this example).
     * @param context The Lambda execution context.
     * @return The generated tip, or an error message.
     */
    @Override
    public String handleRequest(Object input, Context context) {

        logger.info("Application started. Loading prompts from Excel file.");
        // List<Prompt> prompts = loadPrompts("prompts");
        List<Prompt> htmlPrompts = loadPrompts("htmlcss");

        String tip = tipGenerator(htmlPrompts);
        if (tip != null) {
            logger.info("Dispatching tips via email and text.");
            sendEmail("Tip of the Day", tip, EMAIL_ADDRESS);
            sendText(tip, PHONE_NUMBER);
            logger.info("Tip dispatched: {}", tip);
        } else {
            return "Failed to generate one or more tips. Aborting notification dispatch.";
        }

        return "Tip of the Day dispatched!";
    }
//======== UNCOMMENT THIS SECTION FOR LOCAL TESTING ========================

//    public static void main(String[] args) {
//
//        logger.info("Starting local test...");
//
//        // 1. Load prompts
//        logger.info("Loading prompts from Excel file (sheet: htmlcss).");
//        var htmlPrompts = TipOfTheDay.loadPrompts("htmlcss");
//
//        // 2. Generate tip
//        var tip = TipOfTheDay.tipGenerator(htmlPrompts);
//        if (tip != null) {
//            logger.info("Dispatching tips via email and text.");
//
//            // 3. Send the email
//            TipOfTheDay.sendEmail("Tip of the Day", tip, EMAIL_ADDRESS);
//
//            // 4. Send the text message
//            TipOfTheDay.sendText(tip, PHONE_NUMBER);
//
//            logger.info("Tip dispatched: {}", tip);
//        } else {
//            logger.error("Failed to generate one or more tips. Aborting notification dispatch.");
//            return;
//        }
//
//        logger.info("Tip of the Day dispatched successfully!");
//    }
}
