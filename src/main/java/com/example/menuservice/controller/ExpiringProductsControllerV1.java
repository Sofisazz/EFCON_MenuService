package com.example.menuservice.controller;

import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.service.KafkaConsumerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/plans")
public class ExpiringProductsControllerV1 {

    private final KafkaConsumerService kafkaConsumerService;



    @GetMapping("/expiring")
    public List<RecipeDto> getExpiring() {
        return kafkaConsumerService.getExpiringProducts() ;
    }

}
