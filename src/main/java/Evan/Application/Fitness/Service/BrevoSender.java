package Evan.Application.Fitness.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends email through Brevo's HTTPS API. Used where outgoing SMTP is blocked
 * (e.g. Render's free plan); enabled by setting BREVO_API_KEY.
 */
@Component
public class BrevoSender {
    private static final Pattern NAMED = Pattern.compile("^\\s*\"?([^\"<]*?)\"?\\s*<([^>]+)>\\s*$");

    private final String apiKey;
    private final URI endpoint;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public BrevoSender(@Value("${app.mail.brevo-api-key:}") String apiKey,
                       @Value("${app.mail.brevo-url:https://api.brevo.com/v3/smtp/email}") String endpoint,
                       ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = URI.create(endpoint);
        this.json = json;
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    /** Throws on any failure so the caller logs it. */
    public void send(MailService.Email email, String from) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", address(from));
        body.put("to", List.of(Map.of("email", email.to())));
        body.put("subject", email.subject());
        body.put("htmlContent", email.html());
        body.put("textContent", email.text());
        if (!email.headers().isEmpty()) {
            body.put("headers", email.headers());
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("api-key", apiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            // Brevo's error body names the problem (e.g. unverified sender); it holds no member data.
            throw new IOException("Brevo returned " + response.statusCode() + ": " + response.body());
        }
    }

    /** "Evan Fitness <me@example.com>" -> {name, email}; a bare address -> {email}. */
    static Map<String, String> address(String from) {
        Matcher m = NAMED.matcher(from == null ? "" : from);
        if (m.matches()) {
            return m.group(1).isBlank() ? Map.of("email", m.group(2).trim()) : Map.of("name", m.group(1).trim(), "email", m.group(2).trim());
        }
        return Map.of("email", from == null ? "" : from.trim());
    }
}
