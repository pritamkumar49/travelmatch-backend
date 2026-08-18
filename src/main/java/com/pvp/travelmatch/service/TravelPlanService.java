package com.pvp.travelmatch.service;

import com.pvp.travelmatch.dto.MatchResponse;
import com.pvp.travelmatch.dto.TravelPlanRequest;
import com.pvp.travelmatch.entity.TravelPlan;
import com.pvp.travelmatch.entity.User;
import com.pvp.travelmatch.repository.TravelPlanRepository;
import com.pvp.travelmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TravelPlanService {

    private final TravelPlanRepository travelPlanRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public TravelPlan createPlan(TravelPlanRequest request) {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TravelPlan plan = TravelPlan.builder()
                .fromLocation(request.getFromLocation())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .budget(request.getBudget())
                .travelType(request.getTravelType())
                .createdAt(LocalDateTime.now())
                .user(user)
                .build();

        TravelPlan savedPlan = travelPlanRepository.save(plan);

        String dashboardLink = "http://localhost:4200/dashboard";

        String htmlEmail = """
<html>
<body style="font-family:Arial;background:#f4f6fb;padding:30px;">

<div style="max-width:600px;margin:auto;background:white;border-radius:12px;
box-shadow:0 10px 40px rgba(0,0,0,0.1);overflow:hidden;">

<div style="background:#0d78e3;color:white;padding:20px;text-align:center;font-size:22px;">
✈ TravelMatch
</div>

<div style="padding:30px;text-align:center;">

<h2>Your Trip is Live 🌍</h2>

<p>Hello <b>%s</b>,</p>

<p>Your travel plan has been successfully created!</p>

<div style="margin:25px 0;padding:20px;background:#f7f9ff;border-radius:8px;text-align:left;">

<p><b>From:</b> %s</p>
<p><b>Destination:</b> %s</p>
<p><b>Start Date:</b> %s</p>
<p><b>End Date:</b> %s</p>
<p><b>Budget:</b> ₹ %s</p>
<p><b>Travel Style:</b> %s</p>

</div>

<p>We are now showing your trip to compatible travelers.</p>

<a href="%s"
style="display:inline-block;margin-top:20px;padding:14px 28px;
background:#ff5a3d;color:white;text-decoration:none;
border-radius:6px;font-weight:bold;">
View Dashboard
</a>

<p style="margin-top:30px;font-size:13px;color:#888;">
Keep an eye on your inbox for match requests 👀
</p>

</div>

</div>

</body>
</html>
""".formatted(
                user.getName(),
                plan.getFromLocation(),
                plan.getDestination(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getBudget(),
                plan.getTravelType(),
                dashboardLink
        );

        emailService.sendHtmlEmail(
                user.getEmail(),
                "Trip Confirmed: " + plan.getDestination() + " ✈",
                htmlEmail
        );

        return savedPlan;
    }

//    public List<TravelPlan> findMatches(Long planId) {
//
//        TravelPlan myPlan = travelPlanRepository.findById(planId)
//                .orElseThrow(() -> new RuntimeException("Plan not found"));
//
//        List<TravelPlan> candidates = travelPlanRepository.findMatchingPlans(
//                myPlan.getDestination(),
//                myPlan.getStartDate(),
//                myPlan.getEndDate(),
//                myPlan.getUser().getId()
//        );
//
//        // 🔥 Budget Filtering (within 30% difference)
//        return candidates.stream()
//                .filter(plan -> {
//                    double myBudget = myPlan.getBudget();
//                    double otherBudget = plan.getBudget();
//
//                    double difference = Math.abs(myBudget - otherBudget);
//                    return difference <= myBudget * 0.3;
//                })
//                .toList();
//    }




    public List<MatchResponse> findMatches(Long planId) {

        TravelPlan myPlan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        List<TravelPlan> candidates = travelPlanRepository.findMatchingPlans(
                myPlan.getDestination(),
                myPlan.getStartDate(),
                myPlan.getEndDate(),
                myPlan.getUser().getId()
        );

        return candidates.stream()
                .map(plan -> {

                    int score = 0;

                    // 1️⃣ Destination match
                    score += 40;

                    // 2️⃣ Date overlap %
                    long totalDays = myPlan.getStartDate().until(myPlan.getEndDate()).getDays();
                    long overlapStart =
                            plan.getStartDate().isAfter(myPlan.getStartDate())
                                    ? plan.getStartDate().toEpochDay()
                                    : myPlan.getStartDate().toEpochDay();

                    long overlapEnd =
                            plan.getEndDate().isBefore(myPlan.getEndDate())
                                    ? plan.getEndDate().toEpochDay()
                                    : myPlan.getEndDate().toEpochDay();

                    long overlapDays = overlapEnd - overlapStart;

                    if (overlapDays > 0 && totalDays > 0) {
                        double overlapPercent = (double) overlapDays / totalDays;
                        score += (int) (overlapPercent * 30);
                    }

                    // 3️⃣ Budget similarity
                    double budgetDiff = Math.abs(myPlan.getBudget() - plan.getBudget());
                    double budgetPercent = 1 - (budgetDiff / myPlan.getBudget());
                    score += (int) (budgetPercent * 20);

                    // 4️⃣ Travel type
                    if (myPlan.getTravelType().equalsIgnoreCase(plan.getTravelType())) {
                        score += 10;
                    }

                    return new MatchResponse(plan, score);
                })
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .toList();
    }


    public List<TravelPlan> getMyPlans() {
        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return travelPlanRepository.findByUser(user);
    }
}
