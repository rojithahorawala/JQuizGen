package com.quizgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JQuizGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(JQuizGenApplication.class, args);
    }
}
