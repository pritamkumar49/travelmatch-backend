package com.pvp.travelmatch.service;

import com.pvp.travelmatch.entity.*;
import com.pvp.travelmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MatchRequestService {

    private final RestTemplate restTemplate;
    private final MatchRequestRepository matchRequestRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final UserRepository userRepository;
    private final TravelPartnerRepository travelPartnerRepository;
    private final EmailService emailService;


    public List<?> findMatches(Long planId) {

        // 🔹 1. Get logged-in user
        Long userId = getLoggedInUserId();

        // 🔹 2. Get current user's plan
        TravelPlan targetPlan = travelPlanRepository.findById(planId).get();

        // 🔹 3. Get other users' plans
        List<TravelPlan> otherPlans =
                travelPlanRepository.findByUserIdNot(userId);

        // 🔹 4. Convert to AI format
        Map<String, Object> targetUser = convertToAIUser(targetPlan);

        List<Map<String, Object>> users = otherPlans.stream()
                .map(this::convertToAIUser)
                .toList();

        // 🔹 5. Call Python API
        Map<String, Object> request = Map.of(
                "target_user", targetUser,
                "users", users
        );

        ResponseEntity<List> response = restTemplate.postForEntity(
                "https://travelmatch-chatbot-production.up.railway.app/recommend",
                request,
                List.class
        );

        return response.getBody();
    }

    private Long getLoggedInUserId() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String email = auth.getName(); // logged-in user's email

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return user.getId();
    }


    private Map<String, Object> convertToAIUser(TravelPlan plan) {

        return Map.of(
                "user_id", plan.getUser().getId(),
                "source", plan.getFromLocation(),
                "destination", plan.getDestination(),
                "budget", plan.getBudget(),
                "travel_type", plan.getTravelType()
        );
    }

    // Send Match Request
    public MatchRequest sendRequest(Long travelPlanId) {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TravelPlan plan = travelPlanRepository.findById(travelPlanId)
                .orElseThrow(() -> new RuntimeException("Travel plan not found"));

        User receiver = plan.getUser();

        if (sender.getId().equals(receiver.getId())) {
            throw new RuntimeException("You cannot send request to yourself");
        }

        // 🔥 CHECK DUPLICATE
        Optional<MatchRequest> existing =
                matchRequestRepository.findBySenderIdAndTravelPlanId(
                        sender.getId(),
                        travelPlanId
                );

        if (existing.isPresent()) {

            MatchRequest request = existing.get();

            if (request.getStatus().equals("PENDING")) {
                throw new RuntimeException("Request already sent and pending");
            }

            if (request.getStatus().equals("ACCEPTED")) {
                throw new RuntimeException("You are already matched with this user");
            }

            if (request.getStatus().equals("REJECTED")) {
                throw new RuntimeException("Your previous request was rejected");
            }
        }

        MatchRequest request = MatchRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .travelPlan(plan)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

//        return matchRequestRepository.save(request);


        MatchRequest saved = matchRequestRepository.save(request);

        String reviewLink = "https://travelmatch49.netlify.app/requests";

// HTML Email
        String htmlEmail = """
<html>
<body style="font-family:Arial;background:#f4f6fb;padding:30px;">

<div style="max-width:600px;margin:auto;background:white;border-radius:12px;
box-shadow:0 10px 40px rgba(0,0,0,0.1);overflow:hidden;">

<div style="background:#0d78e3;color:white;padding:20px;text-align:center;font-size:22px;">
✈ TravelMatch
</div>

<div style="padding:30px;text-align:center;">

<h2>New Travel Request 🎒</h2>

<p>Hello <b>%s</b>,</p>

<p><b>%s</b> wants to join your trip!</p>

<div style="margin:25px 0;padding:20px;background:#f7f9ff;border-radius:8px;">

<div style="font-size:18px;font-weight:bold;">
Destination: %s
</div>

<p style="margin-top:10px;color:#555;">
Someone is interested in traveling with you.
Review their request and decide if you want to explore together.
</p>

</div>

<a href="%s"
style="display:inline-block;padding:14px 28px;background:#ff5a3d;
color:white;text-decoration:none;border-radius:6px;font-weight:bold;">
Review Request
</a>

<p style="margin-top:30px;font-size:13px;color:#888;">
Adventure starts with one decision 🌍
</p>

</div>

</div>

</body>
</html>
""".formatted(
                receiver.getName(),
                sender.getName(),
                plan.getDestination(),
                reviewLink
        );

// Send HTML Email
        emailService.sendHtmlEmail(
                receiver.getEmail(),
                "New Adventure Request from " + sender.getName() + " ✈",
                htmlEmail
        );

        return saved;
    }

    // Accept / Reject
//    public MatchRequest updateStatus(Long requestId, String status) {
//
//        MatchRequest request = matchRequestRepository.findById(requestId)
//                .orElseThrow(() -> new RuntimeException("Request not found"));
//
//        request.setStatus(status);
//
//        MatchRequest updated = matchRequestRepository.save(request);
//
//        // 🔥 If ACCEPTED → create TravelPartner
////        if (status.equals("ACCEPTED")) {
////
////            TravelPartner partner = TravelPartner.builder()
////                    .userOne(request.getSender())
////                    .userTwo(request.getReceiver())
////                    .travelPlan(request.getTravelPlan())
////                    .createdAt(LocalDateTime.now())
////                    .build();
////
////            travelPartnerRepository.save(partner);
////        }
//
//
//
//        if (status.equals("ACCEPTED")) {
//
//            TravelPartner partner = TravelPartner.builder()
//                    .userOne(request.getSender())
//                    .userTwo(request.getReceiver())
//                    .travelPlan(request.getTravelPlan())
//                    .createdAt(LocalDateTime.now())
//                    .build();
//
//            travelPartnerRepository.save(partner);
//
//            User sender = request.getSender();
//            User receiver = request.getReceiver();
//            String destination = request.getTravelPlan().getDestination();
//
//            // EMAIL TO SENDER (The one who asked)
//            String senderChatLink = "http://localhost:4200/chat/" + receiver.getId();
//            String senderBody = "Pack your bags, " + sender.getName() + "!\n\n" +
//                    "Great news! " + receiver.getName() + " has accepted your request to travel to " + destination + ".\n\n" +
//                    "Your journey together starts now. Say hello and start planning the details:\n" +
//                    senderChatLink + "\n\n" +
//                    "Adventure is better together,\n" +
//                    "The TravelMatch Team ✈";
//
//            emailService.sendEmail(sender.getEmail(), "Request Accepted! 🎒 Destination: " + destination, senderBody);
//
//            // --- EMAIL TO RECEIVER (The one who hosted/posted) ---
//            String receiverChatLink = "http://localhost:4200/chat/" + sender.getId();
//            String receiverBody = "It's a Match, " + receiver.getName() + "!\n\n" +
//                    "You’ve officially confirmed " + sender.getName() + " as your travel partner for " + destination + ".\n\n" +
//                    "Don't leave them hanging! Reach out and break the ice:\n" +
//                    receiverChatLink + "\n\n" +
//                    "Wishing you an incredible trip,\n" +
//                    "The TravelMatch Team ✈";
//
//            emailService.sendEmail(receiver.getEmail(), "New Travel Partner Confirmed! 🤝", receiverBody);
//        }
//
//        return updated;
//    }


public MatchRequest updateStatus(Long requestId, String status) {

    MatchRequest request = matchRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));

    request.setStatus(status);

    MatchRequest updated = matchRequestRepository.save(request);

    if (status.equals("ACCEPTED")) {

        TravelPartner partner = TravelPartner.builder()
                .userOne(request.getSender())
                .userTwo(request.getReceiver())
                .travelPlan(request.getTravelPlan())
                .createdAt(LocalDateTime.now())
                .build();

        travelPartnerRepository.save(partner);

        User sender = request.getSender();
        User receiver = request.getReceiver();
        String destination = request.getTravelPlan().getDestination();

        String senderChatLink = "https://travelmatch49.netlify.app/chat/" + receiver.getId();
        String receiverChatLink = "https://travelmatch49.netlify.app/chat/" + sender.getId();

        // -------- HTML TEMPLATE FOR SENDER --------
        String senderHtml = """
        <html>
        <body style="font-family:Arial;background:#f4f6fb;padding:30px;">
        <div style="max-width:600px;margin:auto;background:white;border-radius:10px;overflow:hidden;
        box-shadow:0 10px 40px rgba(0,0,0,0.1);">

        <div style="background:#0d78e3;color:white;padding:20px;text-align:center;font-size:22px;">
        ✈ TravelMatch
        </div>

        <div style="padding:30px;text-align:center;">
        <h2>Request Accepted 🎉</h2>

        <p>Hello <b>%s</b>,</p>

        <p><b>%s</b> has accepted your travel request!</p>

        <div style="margin:20px 0;font-size:18px;">
        Destination: <b>%s</b>
        </div>

        <a href="%s"
        style="display:inline-block;padding:14px 26px;background:#ff5a3d;
        color:white;text-decoration:none;border-radius:6px;font-weight:bold;">
        Start Chat
        </a>

        <p style="margin-top:30px;font-size:13px;color:#888;">
        Adventure is better together 🌍
        </p>

        </div>
        </div>
        </body>
        </html>
        """.formatted(sender.getName(), receiver.getName(), destination, senderChatLink);


        // -------- HTML TEMPLATE FOR RECEIVER --------
        String receiverHtml = """
        <html>
        <body style="font-family:Arial;background:#f4f6fb;padding:30px;">
        <div style="max-width:600px;margin:auto;background:white;border-radius:10px;overflow:hidden;
        box-shadow:0 10px 40px rgba(0,0,0,0.1);">

        <div style="background:#0d78e3;color:white;padding:20px;text-align:center;font-size:22px;">
        ✈ TravelMatch
        </div>

        <div style="padding:30px;text-align:center;">
        <h2>New Travel Partner 🤝</h2>

        <p>Hello <b>%s</b>,</p>

        <p>You have confirmed <b>%s</b> as your travel partner.</p>

        <div style="margin:20px 0;font-size:18px;">
        Destination: <b>%s</b>
        </div>

        <a href="%s"
        style="display:inline-block;padding:14px 26px;background:#ff5a3d;
        color:white;text-decoration:none;border-radius:6px;font-weight:bold;">
        Open Chat
        </a>

        <p style="margin-top:30px;font-size:13px;color:#888;">
        Have an amazing journey ✈
        </p>

        </div>
        </div>
        </body>
        </html>
        """.formatted(receiver.getName(), sender.getName(), destination, receiverChatLink);


        // SEND EMAILS
        emailService.sendHtmlEmail(
                sender.getEmail(),
                "Your Travel Request Was Accepted 🎉",
                senderHtml
        );

        emailService.sendHtmlEmail(
                receiver.getEmail(),
                "New Travel Partner Confirmed 🤝",
                receiverHtml
        );
    }

    return updated;
}

    // View Incoming Requests
    public List<MatchRequest> getMyRequests() {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return matchRequestRepository.findByReceiver(user);
    }
}
