package Evan.Application.Fitness.Service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Sends account and summary emails.
 *
 * With SMTP configured (spring.mail.host) messages go out in the background, so
 * how long a send takes never reveals whether an account exists. Without SMTP,
 * dev/local/test profiles print each message (links included) to the log and keep
 * the last few in memory; production only logs that nothing was sent.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final int OUTBOX_SIZE = 50;

    private final ObjectProvider<JavaMailSender> senders;
    private final TemplateEngine templates;
    private final Executor executor;
    private final String from;
    private final String brandName;
    private final String baseUrl;
    private final boolean logContents;
    private final Deque<Email> outbox = new ArrayDeque<>();

    public MailService(ObjectProvider<JavaMailSender> senders, TemplateEngine templates,
                       @Qualifier("applicationTaskExecutor") Executor executor,
                       @Value("${app.mail.from}") String from, @Value("${app.brand-name}") String brandName,
                       @Value("${app.base-url}") String baseUrl, @Value("${app.mail.log-contents:false}") boolean logContents) {
        this.senders = senders;
        this.templates = templates;
        this.executor = executor;
        this.from = from;
        this.brandName = brandName;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.logContents = logContents;
    }

    public record Email(String to, String subject, String text, String html, Map<String, String> headers) {
    }

    /** Absolute link for emails. Built from configuration, never from the request's Host header. */
    public String link(String path) {
        return baseUrl + path;
    }

    public boolean isConfigured() {
        return senders.getIfAvailable() != null;
    }

    /** No SMTP, and messages are printed to the log (dev/local/test). */
    public boolean isLogMode() {
        return logContents && !isConfigured();
    }

    /**
     * Renders templates/email/{template}.html with the given variables (plus brandName
     * and appUrl) and sends it with the plain-text alternative.
     */
    public void send(String to, String subject, String template, Map<String, Object> vars, String text,
                     Map<String, String> headers) {
        Context context = new Context();
        context.setVariables(vars);
        context.setVariable("brandName", brandName);
        context.setVariable("appUrl", baseUrl);
        context.setVariable("subject", subject);
        String html = templates.process("email/" + template, context);
        deliver(new Email(to, subject, text + "\n\n— " + brandName, html, headers));
    }

    private void deliver(Email email) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            if (logContents) {
                log.info("Email not sent (SMTP not configured). To: {} | Subject: {}\n{}", email.to(), email.subject(), email.text());
                synchronized (outbox) {
                    outbox.addFirst(email);
                    while (outbox.size() > OUTBOX_SIZE) {
                        outbox.removeLast();
                    }
                }
            } else {
                log.warn("Email '{}' not sent: SMTP is not configured (set SPRING_MAIL_HOST)", email.subject());
            }
            return;
        }
        executor.execute(() -> {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(email.to());
                helper.setSubject(email.subject());
                helper.setText(email.text(), email.html());
                for (Map.Entry<String, String> header : email.headers().entrySet()) {
                    message.setHeader(header.getKey(), header.getValue());
                }
                sender.send(message);
                log.info("Email '{}' sent", email.subject());
            } catch (Exception e) {
                // Never log the address or body: they identify the member.
                log.warn("Email '{}' could not be sent: {}", email.subject(), e.getMessage());
            }
        });
    }

    /** Messages captured while SMTP is not configured (dev/local/test only), newest first. */
    public List<Email> recent() {
        synchronized (outbox) {
            return List.copyOf(outbox);
        }
    }
}
