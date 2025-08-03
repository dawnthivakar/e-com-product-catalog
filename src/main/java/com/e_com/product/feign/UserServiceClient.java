package com.e_com.product.feign;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// TODO: Try to add Service Discovery with Eureka or Consul
// The name of the Feign client is "user-service" and it will use the URL specified in the application properties file under "user.service.url"
@FeignClient(name = "user-service", url = "${user.service.url}")
public interface UserServiceClient {

    @GetMapping("/api/users/{userId}")
    UserDto getUserById(@PathVariable("userId") Long userId);

    @Data
    class UserDto {

        private Long id;
        private String name;
        private String email;

    }
}
