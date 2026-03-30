package com.example.menuservice.service.V2;

import com.example.menuservice.dto.ConsumeProductDto;

public interface KafkaProducerService {
    void sendEatenProducts(ConsumeProductDto product);
    void sendReturnedProducts(ConsumeProductDto product);
}
