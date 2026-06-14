package com.pdftoolkit.pdf_toolkit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PdfToolkitApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfToolkitApplication.class, args);
    }
}
