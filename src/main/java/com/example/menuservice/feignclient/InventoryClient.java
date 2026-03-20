package com.example.menuservice.feignclient;

import com.example.menuservice.dto.ConsumeProductDto;
import com.example.menuservice.dto.ProductAvailabilityDto;
import com.example.menuservice.dto.ProductStatusDto;
import com.example.menuservice.dto.TransferProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/v2/products/check")
    List<ProductAvailabilityDto> checkAvailability(@RequestParam Integer userId,
                                                   @RequestBody List<String> productNames);

    @PostMapping("/api/v2/products/shopping-list")
    List<ProductStatusDto> generateShoppingList(@RequestParam Integer userId,
                                                @RequestBody List<String> productNames);

    @GetMapping("/api/v2/instances/expiring")
    List<TransferProductDto> getExpiring(@RequestParam Integer userId);

    @PostMapping("/api/v2/products/consume")
    void consumeProduct(@RequestBody ConsumeProductDto dto);

    @PostMapping("/api/v2/products/return")
    void returnProduct(@RequestBody ConsumeProductDto dto);
}
