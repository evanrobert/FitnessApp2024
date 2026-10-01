package Evan.Application.Fitness;

import Evan.Application.Fitness.Service.BrevoSender;
import Evan.Application.Fitness.Service.MailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brevo HTTPS email: request shape and error handling, against a local stand-in server. */
class BrevoSenderTests {
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private volatile int status = 201;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = (status == 201 ? "{\"messageId\":\"x\"}" : "{\"message\":\"sender not verified\"}").getBytes();
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BrevoSender sender(String key) {
        return new BrevoSender(key, "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email", new ObjectMapper());
    }

    @Test
    void sendsSenderRecipientBothBodiesAndHeaders() throws Exception {
        MailService.Email email = new MailService.Email("member@example.com", "Reset your password", "plain text", "<p>html</p>",
                Map.of("List-Unsubscribe", "<https://example.com/u>"));
        sender("synthetic-key").send(email, "Evan Fitness <sender@example.com>");

        assertThat(apiKey.get()).isEqualTo("synthetic-key");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.at("/sender/name").asText()).isEqualTo("Evan Fitness");
        assertThat(json.at("/sender/email").asText()).isEqualTo("sender@example.com");
        assertThat(json.at("/to/0/email").asText()).isEqualTo("member@example.com");
        assertThat(json.at("/subject").asText()).isEqualTo("Reset your password");
        assertThat(json.at("/htmlContent").asText()).isEqualTo("<p>html</p>");
        assertThat(json.at("/textContent").asText()).isEqualTo("plain text");
        assertThat(json.at("/headers/List-Unsubscribe").asText()).isEqualTo("<https://example.com/u>");
    }

    @Test
    void errorsFromBrevoAreRaised() {
        status = 400;
        MailService.Email email = new MailService.Email("member@example.com", "Hi", "t", "<p>h</p>", Map.of());
        assertThatThrownBy(() -> sender("synthetic-key").send(email, "sender@example.com"))
                .hasMessageContaining("400").hasMessageContaining("sender not verified");
    }

    @Test
    void enabledOnlyWithAKey() {
        assertThat(sender("").enabled()).isFalse();
        assertThat(sender("  ").enabled()).isFalse();
        assertThat(sender("synthetic-key").enabled()).isTrue();
    }
}
