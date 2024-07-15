package com.aslan.pulmocarefhir;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import javax.servlet.ServletException;

@SpringBootApplication
public class FhirServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FhirServerApplication.class, args);
    }
}