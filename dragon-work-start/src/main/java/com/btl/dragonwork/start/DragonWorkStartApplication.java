package com.btl.dragonwork.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.btl.dragonwork")
public class DragonWorkStartApplication {

    public static void main(String[] args) {
        SpringApplication.run(DragonWorkStartApplication.class, args);
    }

}
