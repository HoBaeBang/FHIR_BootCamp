package com.aslan.pulmocarefhir.controller;

import com.aslan.pulmocarefhir.service.PatientService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.text.ParseException;
import java.util.Map;

@Controller
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private static final Logger logger = LoggerFactory.getLogger(PatientController.class);

    @Autowired
    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public String showForm() {
        return "patientForm";
    }

    @GetMapping("/query")
    public String showQueryForm() {
        return "patientQuery";
    }

    @PostMapping
    public String createPatient(@RequestParam Map<String, String> requestData, Model model) {
        try {
            String response = patientService.createPatient(requestData);
            model.addAttribute("response", response);
            return "result";
        } catch (ParseException e) {
            model.addAttribute("error", "Invalid birthDate format. Please use 'yyyy-MM-dd'.");
            return "error";
        } catch (Exception e) {
            model.addAttribute("error", "Error creating patient: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/query")
    public String queryPatient(@RequestParam("patientId") String patientId, Model model) {
        try {
            String response = patientService.queryPatient(patientId);
            model.addAttribute("response", response);
            return "result";
        } catch (Exception e) {
            model.addAttribute("error", "Error querying patient: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/downloadExcel")
    public ResponseEntity<byte[]> downloadExcel() {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Patient Data");
            // Example data
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Name");
            headerRow.createCell(2).setCellValue("BirthDate");
            // You can add more data from your response as needed

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient_data.xlsx")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            logger.error("Error while creating Excel file: ", e);
            return ResponseEntity.status(500).build();
        }
    }
}