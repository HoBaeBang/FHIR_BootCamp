package com.aslan.pulmocarefhir.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ITransactionTyped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final FhirContext fhirContext;  // FHIR 컨텍스트 객체
    private final IGenericClient client;  // FHIR 클라이언트 객체
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Patient> patientMap = new HashMap<>();  // 환자 정보를 저장하는 맵
    private final Map<String, List<Observation>> observationMap = new HashMap<>();  // 관찰 정보를 저장하는 맵
    private static final Logger logger = LoggerFactory.getLogger(PatientController.class);  // 로그 출력을 위한 로거 객체

    public ServiceController(FhirContext fhirContext, IGenericClient client) {
        this.fhirContext = fhirContext;
        this.client = client;
    }

    // POST 요청을 처리하는 메서드, 환자와 관찰 정보를 생성
    @PostMapping
    public ResponseEntity<String> createPatient(@RequestBody Map<String, Object> requestData) {
        // 새로운 Patient 리소스를 생성
        Patient patient = new Patient();
        String patientId = UUID.randomUUID().toString();
        patient.setId(patientId);

        // 요청 데이터에서 사용자 이름을 가져와 설정
        patient.addName().setFamily((String) requestData.get("username")).addGiven((String) requestData.get("username"));

        // 생년월일 문자열을 Date 객체로 변환
        String birthDateString = (String) requestData.get("birthDate");
        try {
            Date birthDate = new SimpleDateFormat("yyyy-MM-dd").parse(birthDateString);
            patient.setBirthDate(birthDate);
        } catch (ParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid birthDate format. Please use 'yyyy-MM-dd'.");
        }

        // 요청 데이터에서 주소와 결혼 상태를 가져와 설정
        patient.addAddress().setText((String) requestData.get("address"));
        patient.setMaritalStatus(new CodeableConcept().setText((String) requestData.get("maritalStatus")));

        // 생성된 Patient 리소스를 로그에 출력
        logger.info("Created Patient: {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient));

        // 새로운 Device 리소스를 생성
        Device device = new Device();
        String deviceId = UUID.randomUUID().toString();
        device.setId(deviceId);
        device.setManufacturer("Example Manufacturer");

        // deviceName 필드를 사용하여 모델 이름 설정
        Device.DeviceDeviceNameComponent deviceNameComponent = new Device.DeviceDeviceNameComponent();
        deviceNameComponent.setName("Model XYZ");
        deviceNameComponent.setType(Device.DeviceNameType.MODELNAME);
        device.addDeviceName(deviceNameComponent);

        // 장치 유형 설정
        CodeableConcept type = new CodeableConcept();
        type.addCoding(new Coding().setSystem("http://snomed.info/sct").setCode("127783003").setDisplay("Spirometry"));
        device.setType(type);

        // 생성된 Device 리소스를 로그에 출력
        logger.info("Created Device: {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(device));

        // 요청 데이터에서 의료 데이터를 가져와 Observation 리소스를 생성
        List<Observation> observations = new ArrayList<>();
        observations.add(createObservation("19868-9", "Forced vital capacity [Volume] Respiratory system by Spirometry", (Double) requestData.get("FVC"), patient, "L", device));
        observations.add(createObservation("19926-5", "FEV1/FVC", (Double) requestData.get("FEV1FVC"), patient, "%", device));

        // 생성된 Observation 리소스를 로그에 출력
        for (Observation observation : observations) {
            logger.info("Created Observation: {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(observation));
        }

        // 환자와 관찰 정보를 맵에 저장
        patientMap.put(patientId, patient);
        observationMap.put(patientId, observations);

        // 번들 리소스를 생성
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        // Patient 리소스를 번들에 추가
        Bundle.BundleEntryComponent patientEntry = bundle.addEntry();
        patientEntry.setFullUrl("urn:uuid:" + patient.getId());
        patientEntry.setResource(patient);
        patientEntry.getRequest().setUrl("Patient").setMethod(Bundle.HTTPVerb.POST);

        // Device 리소스를 번들에 추가
        Bundle.BundleEntryComponent deviceEntry = bundle.addEntry();
        deviceEntry.setFullUrl("urn:uuid:" + device.getId());
        deviceEntry.setResource(device);
        deviceEntry.getRequest().setUrl("Device").setMethod(Bundle.HTTPVerb.POST);

        // Observation 리소스를 번들에 추가
        for (Observation observation : observations) {
            observation.getSubject().setReference("urn:uuid:" + patient.getId());
            observation.setDevice(new Reference("urn:uuid:" + device.getId()));  // Device 참조 설정
            Bundle.BundleEntryComponent observationEntry = bundle.addEntry();
            observationEntry.setFullUrl("urn:uuid:" + observation.getId());
            observationEntry.setResource(observation);
            observationEntry.getRequest().setUrl("Observation").setMethod(Bundle.HTTPVerb.POST);
        }

        // 번들 리소스를 로그에 출력
        logger.info("Created Bundle: {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

        //번들 리소스 JPA Server에 전송
        ITransactionTyped<Bundle> transaction = client.transaction().withBundle(bundle);
        Bundle responseBundle = transaction.encodedJson().execute();  // 여기를 수정

        // 서버로부터의 응답 번들을 로그에 출력
        logger.info("Response Bundle: {}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));

        // 응답 번들 JSON 문자열로 반환
        String responseBundleJson = fhirContext.newJsonParser().encodeResourceToString(responseBundle);

        // 번들 리소스를 JSON 문자열로 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(responseBundleJson);
    }

    // 특정 환자 정보를 조회하는 GET 메서드
    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatient(@PathVariable String id) {
        Patient patient = patientMap.get(id);

        if (patient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        return ResponseEntity.ok(patient);
    }

    // Observation 리소스를 생성하는 메서드
    private Observation createObservation(String code, String display, Double value, Patient patient, String unit) {
        return createObservation(code, display, value, patient, unit, null);
    }

    private Observation createObservation(String code, String display, Double value, Patient patient, String unit, Device device) {
        Observation observation = new Observation();
        observation.setId(UUID.randomUUID().toString());
        observation.setStatus(Observation.ObservationStatus.FINAL);

        // category 필드 추가
        observation.getCategory().add(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("vital-signs")
                .setDisplay("Vital Signs")));

        // LOINC 코드 설정
        observation.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode(code)
                .setDisplay(display);

        // 값 설정
        observation.setValue(new Quantity()
                .setValue(value)
                .setUnit(unit)
                .setSystem("http://unitsofmeasure.org")
                .setCode(unit));

        // 대상 환자 설정
        observation.getSubject().setReference("Patient/" + patient.getId());

        // 유효 시간 설정
        observation.setEffective(new DateTimeType(new Date()));

        // 장치 참조 설정
        if (device != null) {
            observation.setDevice(new Reference("Device/" + device.getId()));
        }

        return observation;
    }
    @PostMapping("/to-excel")
    public ResponseEntity<byte[]> convertToExcel(@RequestBody String fhirData) {
        logger.info("Received Data: {}", fhirData);
        try {
            JsonNode root = objectMapper.readTree(fhirData);
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("FHIR Data");

            // Header Row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ResourceType");
            headerRow.createCell(1).setCellValue("ID");
            headerRow.createCell(2).setCellValue("Name/Status");
            headerRow.createCell(3).setCellValue("BirthDate/Category");
            headerRow.createCell(4).setCellValue("Address/Code");
            headerRow.createCell(5).setCellValue("MaritalStatus/Display");
            headerRow.createCell(6).setCellValue("Value");
            headerRow.createCell(7).setCellValue("Unit");
            headerRow.createCell(8).setCellValue("Device");

            // Data Rows
            JsonNode entries = root.path("entry");
            int rowIndex = 1;
            Iterator<JsonNode> elements = entries.elements();
            while (elements.hasNext()) {
                JsonNode entry = elements.next();
                JsonNode resource = entry.path("resource");

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(resource.path("resourceType").asText());
                row.createCell(1).setCellValue(resource.path("id").asText());

                if (resource.path("resourceType").asText().equals("Patient")) {
                    row.createCell(2).setCellValue(resource.path("name").get(0).path("family").asText());
                    row.createCell(3).setCellValue(resource.path("birthDate").asText());
                    row.createCell(4).setCellValue(resource.path("address").get(0).path("text").asText());
                    row.createCell(5).setCellValue(resource.path("maritalStatus").path("text").asText());
                } else if (resource.path("resourceType").asText().equals("Observation")) {
                    row.createCell(2).setCellValue(resource.path("status").asText());

                    JsonNode categoryNode = resource.path("category");
                    String category = categoryNode.isArray() && categoryNode.size() > 0
                            ? categoryNode.get(0).path("coding").get(0).path("display").asText()
                            : "";
                    row.createCell(3).setCellValue(category);

                    JsonNode codeNode = resource.path("code").path("coding");
                    String code = codeNode.isArray() && codeNode.size() > 0
                            ? codeNode.get(0).path("code").asText()
                            : "";
                    String display = codeNode.isArray() && codeNode.size() > 0
                            ? codeNode.get(0).path("display").asText()
                            : "";
                    row.createCell(4).setCellValue(code);
                    row.createCell(5).setCellValue(display);

                    JsonNode valueNode = resource.path("valueQuantity");
                    row.createCell(6).setCellValue(valueNode.path("value").asDouble());
                    row.createCell(7).setCellValue(valueNode.path("unit").asText());

                    JsonNode deviceNode = resource.path("device");
                    row.createCell(8).setCellValue(deviceNode.path("reference").asText());
                } else if (resource.path("resourceType").asText().equals("Device")) {
                    row.createCell(2).setCellValue(resource.path("manufacturer").asText());
                    row.createCell(3).setCellValue(resource.path("deviceName").get(0).path("name").asText());
                    row.createCell(4).setCellValue(resource.path("type").path("coding").get(0).path("display").asText());
                } else {
                    row.createCell(2).setCellValue("");
                    row.createCell(3).setCellValue("");
                    row.createCell(4).setCellValue("");
                    row.createCell(5).setCellValue("");
                    row.createCell(6).setCellValue("");
                    row.createCell(7).setCellValue("");
                    row.createCell(8).setCellValue("");
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            byte[] excelData = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "fhir_data.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}