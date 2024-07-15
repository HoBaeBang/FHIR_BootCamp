package com.aslan.pulmocarefhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfiguration {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4(); // 혹은 원하는 FHIR 버전으로 변경
    }

    @Bean
    public IGenericClient fhirClient(FhirContext fhirContext) {
        return fhirContext.newRestfulGenericClient("http://localhost:8080/fhir");
    }
}