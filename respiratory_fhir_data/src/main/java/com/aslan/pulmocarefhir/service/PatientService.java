package com.aslan.pulmocarefhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ITransactionTyped;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class PatientService {

    private final FhirContext fhirContext;
    private final IGenericClient client;
    private final Map<String, Patient> patientMap = new HashMap<>();
    private final Map<String, List<Observation>> observationMap = new HashMap<>();

    @Autowired
    public PatientService(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
        this.client = fhirContext.newRestfulGenericClient("http://localhost:8080/fhir");
    }

    // 환자와 관련된 정보를 생성하고 서버에 저장하는 메서드
    public String createPatient(Map<String, String> requestData) throws ParseException {
        // 새로운 Patient 객체 생성 및 ID 설정
        Patient patient = new Patient();
        String patientId = UUID.randomUUID().toString();
        patient.setId(patientId);
        patient.addName().setFamily(requestData.get("username")).addGiven(requestData.get("username"));

        // 생년월일 설정
        String birthDateString = requestData.get("birthDate");
        Date birthDate = new SimpleDateFormat("yyyy-MM-dd").parse(birthDateString);
        patient.setBirthDate(birthDate);

        // 주소와 결혼 상태 설정
        patient.addAddress().setText(requestData.get("address"));
        patient.setMaritalStatus(new CodeableConcept().setText(requestData.get("maritalStatus")));

        // 새로운 Device 객체 생성 및 설정
        Device device = createDevice();

        // 요청 데이터에서 Observation 객체들을 생성
        List<Observation> observations = createObservations(requestData, patient, device);

        // 환자와 관찰 정보를 맵에 저장
        patientMap.put(patientId, patient);
        observationMap.put(patientId, observations);

        // 번들 생성 및 리소스 추가
        Bundle bundle = createBundle(patient, device, observations);

        // 번들을 서버에 전송하고 응답 받기
        ITransactionTyped<Bundle> transaction = client.transaction().withBundle(bundle);
        Bundle responseBundle = transaction.encodedJson().execute();

        // 응답을 JSON 문자열로 변환하여 반환
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle);
    }

    // 환자 정보를 조회하는 메서드
    public String queryPatient(String patientId) {
        // FHIR 서버에서 환자 정보를 조회하기 위한 URL 생성
        String url = "http://localhost:8080/fhir/Patient/" + patientId + "/$everything";

        // URL을 통해 FHIR 서버에 검색 요청
        Bundle bundle = client.search()
                .byUrl(url)
                .returnBundle(Bundle.class)
                .execute();

        // 응답을 JSON 문자열로 변환하여 반환
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    // Observation 객체를 생성하는 메서드
    private Observation createObservation(String code, String display, Double value, Patient patient, String unit, Device device) {
        Observation observation = new Observation();
        observation.setId(UUID.randomUUID().toString());
        observation.setStatus(Observation.ObservationStatus.FINAL);

        observation.getCategory().add(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("vital-signs")
                .setDisplay("Vital Signs")));

        observation.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode(code)
                .setDisplay(display);

        observation.setValue(new Quantity()
                .setValue(value)
                .setUnit(unit)
                .setSystem("http://unitsofmeasure.org")
                .setCode(unit));

        observation.getSubject().setReference("Patient/" + patient.getId());
        observation.setEffective(new DateTimeType(new Date()));

        if (device != null) {
            observation.setDevice(new Reference("Device/" + device.getId()));
        }

        return observation;
    }

    // Device 객체를 생성하는 메서드
    private Device createDevice() {
        Device device = new Device();
        String deviceId = UUID.randomUUID().toString();
        device.setId(deviceId);
        device.setManufacturer("Example Manufacturer");

        Device.DeviceDeviceNameComponent deviceNameComponent = new Device.DeviceDeviceNameComponent();
        deviceNameComponent.setName("Model XYZ");
        deviceNameComponent.setType(Device.DeviceNameType.MODELNAME);
        device.addDeviceName(deviceNameComponent);

        CodeableConcept type = new CodeableConcept();
        type.addCoding(new Coding().setSystem("http://snomed.info/sct").setCode("127783003").setDisplay("Spirometry"));
        device.setType(type);

        return device;
    }

    // Observation 객체 리스트를 생성하는 메서드
    private List<Observation> createObservations(Map<String, String> requestData, Patient patient, Device device) {
        List<Observation> observations = new ArrayList<>();
        observations.add(createObservation("19868-9", "Forced vital capacity [Volume] Respiratory system by Spirometry", Double.valueOf(requestData.get("FVC")), patient, "L", device));
        observations.add(createObservation("19926-5", "FEV1/FVC", Double.valueOf(requestData.get("FEV1FVC")), patient, "%", device));
        return observations;
    }

    // 번들 객체를 생성하고 리소스를 추가하는 메서드
    private Bundle createBundle(Patient patient, Device device, List<Observation> observations) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        Bundle.BundleEntryComponent patientEntry = bundle.addEntry();
        patientEntry.setFullUrl("urn:uuid:" + patient.getId());
        patientEntry.setResource(patient);
        patientEntry.getRequest().setUrl("Patient").setMethod(Bundle.HTTPVerb.POST);

        Bundle.BundleEntryComponent deviceEntry = bundle.addEntry();
        deviceEntry.setFullUrl("urn:uuid:" + device.getId());
        deviceEntry.setResource(device);
        deviceEntry.getRequest().setUrl("Device").setMethod(Bundle.HTTPVerb.POST);

        for (Observation observation : observations) {
            observation.getSubject().setReference("urn:uuid:" + patient.getId());
            observation.setDevice(new Reference("urn:uuid:" + device.getId()));
            Bundle.BundleEntryComponent observationEntry = bundle.addEntry();
            observationEntry.setFullUrl("urn:uuid:" + observation.getId());
            observationEntry.setResource(observation);
            observationEntry.getRequest().setUrl("Observation").setMethod(Bundle.HTTPVerb.POST);
        }

        return bundle;
    }
}