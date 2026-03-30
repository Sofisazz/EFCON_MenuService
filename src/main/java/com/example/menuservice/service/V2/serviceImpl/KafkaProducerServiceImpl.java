package com.example.menuservice.service.V2.serviceImpl;

import com.example.menuservice.dto.ConsumeProductDto;
import com.example.menuservice.service.V2.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {
    private final KafkaTemplate<String, ConsumeProductDto> kafkaTemplate;

    @Override
    public void sendEatenProducts(ConsumeProductDto product) {
        kafkaTemplate.send("eaten_products", product);
    }

    @Override
    public void sendReturnedProducts(ConsumeProductDto product) {
        kafkaTemplate.send("returned_products", product);
    }
}
