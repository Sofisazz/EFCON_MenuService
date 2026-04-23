package com.example.menuservice.feignclient;

import com.example.menuservice.dto.*;
import com.example.menuservice.feignclient.configuration.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "inventory-service", configuration = FeignConfig.class)
public interface InventoryClient {

    @PostMapping("/api/v2/products/shopping-list")
    List<ProductStatusDto> generateShoppingList(@RequestParam Integer userId,
                                                @RequestBody List<String> productNames);

    @GetMapping("/api/v2/instances/expiring")
    List<TransferProductDto> getExpiring(@RequestParam Integer userId);

    @PostMapping("/api/v2/products/consume")
    void consumeProduct(@RequestBody ConsumeProductDto dto);

    @PostMapping("/api/v2/products/return")
    void returnProduct(@RequestBody ConsumeProductDto dto);

    @GetMapping("/api/v2/products/ids/{id}")
    ProductDto getProductsById(@RequestParam("userId") int userId,
                               @PathVariable("id") int productId);
}
