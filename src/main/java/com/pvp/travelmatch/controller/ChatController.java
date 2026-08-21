package com.pvp.travelmatch.controller;

import com.pvp.travelmatch.dto.ChatRequest;
import com.pvp.travelmatch.dto.ChatResponse;
import com.pvp.travelmatch.entity.Message;
import com.pvp.travelmatch.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final RestTemplate restTemplate;

    @PostMapping("/send/{receiverId}")
    public Message sendMessage(
            @PathVariable Long receiverId,
            @RequestBody String content
    ) {
        return chatService.sendMessage(receiverId, content);
    }

    @GetMapping("/{userId}")
    public List<Message> getConversation(@PathVariable Long userId) {
        return chatService.getConversation(userId);
    }


    @PostMapping("/chatbot")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {

        String message = request.get("message");

        System.out.println("User message: " + message);

        String flaskUrl = "https://travelmatch-chatbot-production.up.railway.app/get_response";

        Map<String, String> aiRequest = new HashMap<>();
        aiRequest.put("message", message);

        Map response = restTemplate.postForObject(
                flaskUrl,
                aiRequest,
                Map.class
        );

        System.out.println("Flask response: " + response);

        return response;
    }

//    @PostMapping("/chatbot")
//    public ChatResponse chat(@RequestBody ChatRequest request){
//
//        return chatService.askBot(request);
//
//    }


//    public ChatResponse chat(@RequestBody ChatRequest request) {
//
//        String reply = chatService.processMessage(request.getMessage());
//
//        return new ChatResponse(reply);
//    }


}
