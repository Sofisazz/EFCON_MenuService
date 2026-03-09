package com.example.menuservice.service;

import com.example.menuservice.dto.KafkaProductDto;
import com.example.menuservice.dto.RecipeDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface KafkaConsumerService {

    @SuppressWarnings("unused")
    List<KafkaProductDto> getCachedData();
    List<RecipeDto> getExpiringProducts();
}
