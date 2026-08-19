package com.pvp.travelmatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();


    public void sendHtmlEmail(
            String to,
            String subject,
            String htmlBody) {

        try {

            Map<String, Object> requestBody = Map.of(
                    "sender", Map.of(
                            "name", senderName,
                            "email", senderEmail
                    ),
                    "to", List.of(
                            Map.of(
                                    "email", to
                            )
                    ),
                    "subject", subject,
                    "htmlContent", htmlBody
            );

            String json =
                    objectMapper.writeValueAsString(requestBody);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    "https://api.brevo.com/v3/smtp/email"
                            ))
                            .header(
                                    "accept",
                                    "application/json"
                            )
                            .header(
                                    "api-key",
                                    brevoApiKey
                            )
                            .header(
                                    "content-type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Brevo status: " +
                            response.statusCode()
            );

            System.out.println(
                    "Brevo response: " +
                            response.body()
            );

            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {

                throw new RuntimeException(
                        "Brevo email failed: "
                                + response.body()
                );
            }

            System.out.println(
                    "Email sent successfully to: " + to
            );

        } catch (Exception e) {

            System.err.println(
                    "Email sending failed"
            );

            e.printStackTrace();

            throw new RuntimeException(
                    "Unable to send verification email",
                    e
            );
        }
    }


    public void sendOtpEmail(
            String email,
            String otp) {

        String html = """
                <html>
                <body style="font-family:Arial;
                background:#f4f6fb;padding:30px;">

                <div style="max-width:600px;margin:auto;
                background:white;border-radius:12px;
                box-shadow:0 10px 40px rgba(0,0,0,0.1);
                overflow:hidden;">

                <div style="background:#0d78e3;
                color:white;padding:20px;
                text-align:center;font-size:22px;">
                ✈ TravelMatch
                </div>

                <div style="padding:30px;text-align:center;">

                <h2>Email Verification</h2>

                <p>Your TravelMatch verification code is:</p>

                <div style="font-size:32px;font-weight:bold;
                letter-spacing:6px;margin:20px 0;">
                %s
                </div>

                <p>This code will expire in 10 minutes.</p>

                <p style="font-size:12px;color:#888;">
                If you didn't request this, you can safely
                ignore this email.
                </p>

                </div>

                </div>

                </body>
                </html>
                """.formatted(otp);

        sendHtmlEmail(
                email,
                "Your TravelMatch Verification Code",
                html
        );
    }
}
