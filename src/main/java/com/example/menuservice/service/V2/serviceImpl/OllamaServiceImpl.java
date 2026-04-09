package com.example.menuservice.service.V2.serviceImpl;

import com.example.menuservice.dto.OllamaDto;
import com.example.menuservice.service.V2.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OllamaServiceImpl implements OllamaService {

    private final RestClient restClient;

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.model}")
    private String modelName;

    private static final Map<String, String> translationCache = new ConcurrentHashMap<>();

    @Override
    public String generateResponse(String prompt) {

        if (translationCache.containsKey(prompt)) {
            return translationCache.get(prompt);
        }

        try {

            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "prompt", prompt,
                    "stream", false
            );

            OllamaDto ollamaResponse = restClient.post()
                    .uri(ollamaUrl)
                    .body(requestBody)
                    .retrieve()
                    .body(OllamaDto.class);

            if (ollamaResponse != null && ollamaResponse.getResponse() != null) {
                String result = ollamaResponse.getResponse().trim();

                if (!result.isEmpty()) {
                    translationCache.put(prompt, result);
                    return result;
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }
}