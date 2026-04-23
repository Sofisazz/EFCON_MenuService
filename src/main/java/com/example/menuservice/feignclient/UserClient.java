package com.example.menuservice.feignclient;
import com.example.menuservice.dto.UserRestrictionsDto;
import com.example.menuservice.feignclient.configuration.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", configuration = FeignConfig.class)
public interface UserClient {

     @GetMapping("/api/v1/users/{id}/restrictions")
     UserRestrictionsDto getUserRestrictions(@PathVariable int id);

     @GetMapping("/api/v1/users/{id}/exists")
     boolean checkUserExists(@PathVariable("id") int userId);
}
