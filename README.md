# FHIR_BootCamp

## 1. 소개
> 본 프로젝트는 2024년 한국 의료 정보원에서 진행한 FHIR BootCamp의 프로젝트 일환으로 진행되었다.
> 프로젝트 구성은 아래와 같으며 각 시스템의 작동방법 및 구성을 설명한다.

## 2. 아키텍처
<img width="600" height="350" alt="FHIR_system_architecture" src="https://github.com/user-attachments/assets/867bb419-b7e2-47aa-8c7f-e24094fe166a">


## 3. Projcet 구성
### 3.1 HAPI FHIR JPAServer Starter
HAPI FHIR에서 제공하는 코드로 기본적인 FHIR 서버와 저장소를 구축한다.
해당 서버를 커스텀 하여 프로젝트를 진행하려 하였으나 3일 안에 FHIR를 이해하고 진행하는 것은 무리가 있다는  
강사분의 피드백에 따라 해당 서버를 docker-compose를 통해서 local환경에 구축을 진행하고 DB에 저장하고  
환자 정보를 조회하는 용도로 사용 하였다.

### 3.2 respiratory_fhir_data
#### 3.2.1 서버 설명
3.1 에서 구축한 JPA Server에 FHIR 형식으로 Request를 요청하는 서버로 Client로 부터온 데이터를 바탕으로  
FHIR Resource 형식으로 변환하여 전송한다. 실질적으로 FHIR Resource에 대해서 확인하여 변환하는 작업을 진행하였다.
FHIR Resource를 만들어서 FHIR 형식에 적합한지를 확인하기 위해 FHIR 검증기를 이용하여 생성된 FHIR 형식을 검증하였다.

#### 3.2.2 FHIR Resources
본 프로젝트에서 개발한 FHIR converter는 환자정보와 환자의 호흡기정보를 받아 기록하는 형식으로 사용한 Resource 3가지와, 이를 묶기 위한 번들로 구성하였다.
1. Patient Resource
2. Observation Resource
3. Device Resource
4. Bundle Resource

Observation Resource를 개발하면서 구성요소에 대해서 많이 고민하였고 Resource의 스타일중 code 스타일이라는 부분이 있었고, 그 부분을 맞추기 위해 각 측정되는 항목의 코드가 필요하였다.
국제적으로 사용되는 코드로는 loinc 코드가 있었고 프로젝트에서 수집하 FVC (Forced Vital Capacity, 강제 폐활량), FEV1/FVC 비율에 해당하는 로잉크 코드를 확인하여 적용하였다.
그 외의 항목들에 대해서는 loinc 코드로 찾을 수 없었고 이유를 찾아보니 loinc의 경우는 국제적으로 통용되는 코드 이고 국내의 경우 EDI code라는 것을 사용한다는 것을 알게되었다.
EDI에 대한 부분등은 프로젝트를 심화있게 다룰때 다시 고민해볼 사항인것 같다.

-  Loinc Code 검색 : <https://loinc.org/search/>
-  FHIR Loinc API 설명: <https://loinc.org/fhir/>
-  FHIR Loinc API : <https://fhir.loinc.org>

#### 3.2.3 FHIR Validation
사용자 정보를 받아 FHIR Resource로 변경한 정보가 올바른지 검증이 필요 하였고, 1차적으로 해당 사이트에 들어가서 직접 error를 확인하면서 검증하였다.
마지막에 시간이 1시간 정도 남아서 자동으로 validation하는 로직을 추가하려 하였고, 해당 내용을 찾다보니 fhir-validator-app을 찾을 수 있었고
로직적으로 리소스를 생성 후 바로 검증하고 JPA Server로 전송하면 좋을것 같다는 생각에 진행하였지만 시간적 여유가 없어 구축은 하였지만 아직 api 연동은 진행하지 못하였다.
- FHIR Resource 검증기 : <https://inferno.healthit.gov/validator/>

### 3.3 FHIR Validation App
FHIR Resource의 검증을 통해 데이터의 신뢰성 확보가 필요하여 지금까지는 FHIR Resource 검증기를 이용해서 수동으로 하였지만
FHIR Resource의 생성과 동시에 유효성을 검증하게 하기 위하여 FHIR Validation App을 pull 받아 Server 부분만 API 서버로 이용하기 위해서
구축을 진행하였다. 하지만 앞서 말한것과 같이 시간상 제약으로 인하여 구축은 완료 하였으나, 연동은 아직 하지 못한 상태이다.

## 4. 결과물(이미지)
> 1. FHIR Data 생성 및 저장
>
> > 웹페이지를 통해서 환자 정보와 호흡기 정보를 받아서 FHIR Data를 생성하고 생성한 FHIR Data를 화면에 표출시켜주고, 그 정보를 JPA Server에 전송하여 저장하는 기능을 개발하였다.  
> > 해당 과정에서 생성된 FHIR Resource의 검증은 FHIR 검증기를 통해 진행하였다.
> >
> > <img width="600" height="350" alt="FHIR_Data_Processing(2)" src="https://github.com/user-attachments/assets/b3d091ca-f91f-493d-9c94-3157b9111240">
> 2. FHIR Data 조회 및 Export
>
> > 1번의 기능에서 저장하였을경우 환자의 id를 확인 할 수 있게 되는데, 환자의 Id를 통해서 기록된 호흡기 정보를 조회한다.
> > 조회된 정보는 Download as Excel 버튼을 통해서 엑셀의 형태로 다운로드 할 수 있다.
> >
> ><img width="600" height="350" alt="FHIR_Data_Processing(1)" src="https://github.com/user-attachments/assets/b2f0ffd3-b09a-4658-8dab-6207d3d90e60">

## 5. 마무리
> FHIR에 대하여 학습하면서 FHIR Resources에 대해 학습하는 과정을 통해 의료 데이터를 어떻게 좀 더 효율적으로 관리하고 표준화가 가능할까 라는 생각하게 되는 시간이었고,
> 지속적으로 개발되고 있는 웨어러블 디바이스의 생채신호 등과 함께 FHIR 또한 빠르게 성장해야 FHIR 분야의 선두 주자인 미국 만큼은 아니라도 국내의 여러 병원의 데이터를 통합하고
> 나아가 국내의 의료데이터 통신의 표준이되어 통신의 표준이 된 후 CDM(Common Data Model)과 함께 사용하며 의학 연구의 효율성과 확장성을 기대 할 수 잇을것 같다.
