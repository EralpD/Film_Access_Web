package com.example;

// Hatanızı çözecek olan kritik import satırları:
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // Bu sınıfın bir Spring MVC Controller olduğunu belirtir
public class HelloController {

    @GetMapping("/api/hello") // Tarayıcıdan bu adrese istek atacağız
    public String sayHello() {
        return "Tebrikler! Spring Boot 3.5.16 ve Spring MVC altyapısı başarıyla kuruldu.";
    }
}
