package com.gzhu.equipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeviceBorrowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceBorrowApplication.class, args);
    }
}
