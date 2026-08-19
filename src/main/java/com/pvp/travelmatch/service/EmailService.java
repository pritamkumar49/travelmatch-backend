package com.pvp.travelmatch.service;

import com.resend.Resend;
import com.resend.SendEmailRequest;
import com.resend.SendEmailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;

    public void sendHtmlEmail(
            String to,
            String subject,
            String htmlBody) {

        try {

            Resend resend = new Resend(resendApiKey);

            SendEmailRequest request = SendEmailRequest.builder()
                    .from("TravelMatch <onboarding@resend.dev>")
                    .to(to)
                    .subject(subject)
                    .html(htmlBody)
                    .build();

            SendEmailResponse response =
                    resend.emails().send(request);

            System.out.println(
                    "Email sent successfully. ID: "
                            + response.getId()
            );

        } catch (Exception e) {

            System.err.println("Email sending failed");
            e.printStackTrace();

            throw new RuntimeException(
                    "Unable to send verification email",
                    e
            );
        }
    }

    public void sendOtpEmail(String email, String otp) {

        String html = """
                <html>
                <body style="font-family:Arial;background:#f4f6fb;padding:30px;">

                <div style="max-width:600px;margin:auto;background:white;
                border-radius:12px;
                box-shadow:0 10px 40px rgba(0,0,0,0.1);
                overflow:hidden;">

                <div style="background:#0d78e3;color:white;
                padding:20px;text-align:center;font-size:22px;">
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
                If you didn't request this, you can safely ignore this email.
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
