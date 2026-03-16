package com.gjergjsheldija.scripting;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoEncounter;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import com.gjergjsheldija.configuration.Configuration;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class OutputCalculationUnitTest {

	@Mock
	private RequestDetails requestDetails;
	@Mock
	private IBundleProvider bundleProvider;
	@Mock
	IFhirResourceDao<Configuration> myConfigurationDao;
	@Mock
	IFhirResourceDaoObservation<Observation> myObservationDao;
	@Mock
	IFhirResourceDaoEncounter<Encounter> myEncounterDao;
	// @InjectMocks
	// OutputCalculationForTests outputCalculationForTests;
	private ReferenceParam encounter;
	private NumberParam interval;
	private String bundleJson;
	private String resetTime;


	@BeforeEach
	void setUp() {
		encounter = new ReferenceParam("Encounter/e3465cac-0e29-4dbe-bd93-0286b904a64f");
		interval = new NumberParam(5);
		resetTime = "10:00:00"; // Local Time
		bundleJson = "{\n  \"resourceType\": \"Bundle\",\n  \"id\": \"1644503b-f76d-4034-aab0-8d44c0aa3636\",\n  \"meta\": {\n    \"lastUpdated\": \"2025-09-28T18:55:06.725 02:00\"\n  },\n  \"type\": \"searchset\",\n  \"total\": 7,\n  \"link\": [\n    {\n      \"relation\": \"self\",\n      \"url\": \"http://localhost:8080/fhir/Observation?_count=1000&_format=json&category=https://fhir.demo/CodeSystem/mona-observation-category|output&encounter=Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n    }\n  ],\n  \"entry\": [\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/6f1786fc-2458-4ef0-9705-db6c6e658000\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"6f1786fc-2458-4ef0-9705-db6c6e658000\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:03.718 02:00\",\n          \"source\": \"#1p6lLRQbmxprBDro\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.demo/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.demo/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.demo/CodeSystem/mona-output-factor\",\n              \"code\": \"6baae568-97ee-4e8e-bc90-ed50880c318f\",\n              \"display\": \"Urin -70 ml was recorded at 2025-09-23 10:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"364202003\"\n            }\n          ],\n          \"text\": \"Urin -70 ml was recorded at 2025-09-23 10:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-23T10:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -70.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/8369912c-178c-42c5-9193-46135f805b45\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"8369912c-178c-42c5-9193-46135f805b45\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:03.851 02:00\",\n          \"source\": \"#DDDaUKbfufB46CYA\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.demo/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.demo/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.demo/CodeSystem/mona-output-factor\",\n              \"code\": \"6baae568-97ee-4e8e-bc90-ed50880c318f\",\n              \"display\": \"Urin -50 ml was recorded at 2025-09-23 13:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"364202003\"\n            }\n          ],\n          \"text\": \"Urin -50 ml was recorded at 2025-09-23 13:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-23T13:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -50.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/7af7448e-4152-4956-b029-792db8acd3fc\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"7af7448e-4152-4956-b029-792db8acd3fc\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:04.021 02:00\",\n          \"source\": \"#teovShkITCUUnwb1\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.demo/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.demo/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.demo/CodeSystem/mona-output-factor\",\n              \"code\": \"6baae568-97ee-4e8e-bc90-ed50880c318f\",\n              \"display\": \"Urin -20 ml was recorded at 2025-09-23 17:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"364202003\"\n            }\n          ],\n          \"text\": \"Urin -20 ml was recorded at 2025-09-23 17:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-23T17:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -20.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/5c8ed5bc-ec9e-41a5-9c5c-d386dd637a39\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"5c8ed5bc-ec9e-41a5-9c5c-d386dd637a39\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:04.142 02:00\",\n          \"source\": \"#1p9O5B2TpBKOnkr9\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.demo/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.demo/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.demo/CodeSystem/mona-output-factor\",\n              \"code\": \"6baae568-97ee-4e8e-bc90-ed50880c318f\",\n              \"display\": \"Urin -10 ml was recorded at 2025-09-23 22:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"364202003\"\n            }\n          ],\n          \"text\": \"Urin -10 ml was recorded at 2025-09-23 22:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-23T22:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -10.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/182a9f5e-85c6-4e2c-8291-12cb1a3e8bc5\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"182a9f5e-85c6-4e2c-8291-12cb1a3e8bc5\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:04.304 02:00\",\n          \"source\": \"#CK4WUa11uEgoFSxv\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.demo/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.demo/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.demo/CodeSystem/mona-output-factor\",\n              \"code\": \"6baae568-97ee-4e8e-bc90-ed50880c318f\",\n              \"display\": \"Urin -5 ml was recorded at 2025-09-24 03:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"364202003\"\n            }\n          ],\n          \"text\": \"Urin -5 ml was recorded at 2025-09-24 03:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-24T03:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -5.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/552ac98d-7052-4dea-aafb-1bc4a60d48e3\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"552ac98d-7052-4dea-aafb-1bc4a60d48e3\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:04.475 02:00\",\n          \"source\": \"#UQdonqogXr07a4UM\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.mona.icu/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.mona.icu/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.mona.icu/CodeSystem/mona-output-factor\",\n              \"code\": \"6baae568-97ee-4e8e-bc90-ed50880c318f\",\n              \"display\": \"Urin -25 ml was recorded at 2025-09-24 08:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"364202003\"\n            }\n          ],\n          \"text\": \"Urin -25 ml was recorded at 2025-09-24 08:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-24T08:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -25.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/8bd2393a-1a23-43ae-ac75-18b6843c1325\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"8bd2393a-1a23-43ae-ac75-18b6843c1325\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:04.664 02:00\",\n          \"source\": \"#ljghPATGPqQItlcW\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.mona.icu/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.mona.icu/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.mona.icu/CodeSystem/mona-output-factor\",\n              \"code\": \"8012afcc-1772-4765-9f18-46cd5e0f96a3\",\n              \"display\": \"Stuhl -100 ml was recorded at 2025-09-24 07:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"706697005\"\n            }\n          ],\n          \"text\": \"Stuhl -100 ml was recorded at 2025-09-24 07:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-24T07:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -100.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/ac90356b-08bf-4720-a9b7-399577f3e957\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"ac90356b-08bf-4720-a9b7-399577f3e957\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T16:08:04.664 02:00\",\n          \"source\": \"#ljghPATGPqQItlcW\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.mona.icu/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.mona.icu/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.mona.icu/CodeSystem/mona-output-factor\",\n              \"code\": \"8012afcc-1772-4765-9f18-46cd5e0f96a3\",\n              \"display\": \"Stuhl -100 ml was recorded at 2025-09-24 10:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"706697005\"\n            }\n          ],\n          \"text\": \"Stuhl -100 ml was recorded at 2025-09-24 10:00:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-24T07:00:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -100.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    },\n    {\n      \"fullUrl\": \"http://localhost:8080/fhir/Observation/0ca43377-b560-4186-a3c9-29ac69046254\",\n      \"resource\": {\n        \"resourceType\": \"Observation\",\n        \"id\": \"0ca43377-b560-4186-a3c9-29ac69046254\",\n        \"meta\": {\n          \"versionId\": \"1\",\n          \"lastUpdated\": \"2025-09-24T11:53:04.664 02:00\",\n          \"source\": \"#ljghPATGPqQItlcW\"\n        },\n        \"extension\": [\n          {\n            \"url\": \"https://fhir.mona.icu/StructureDefinition/lastChangedBy\",\n            \"valueReference\": {\n              \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n            }\n          }\n        ],\n        \"status\": \"final\",\n        \"category\": [\n          {\n            \"coding\": [\n              {\n                \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n                \"code\": \"exam\",\n                \"display\": \"Exam\"\n              }\n            ],\n            \"text\": \"Exam\"\n          },\n          {\n            \"coding\": [\n              {\n                \"system\": \"https://fhir.mona.icu/CodeSystem/mona-observation-category\",\n                \"code\": \"output\",\n                \"display\": \"Output\"\n              }\n            ],\n            \"text\": \"Output\"\n          }\n        ],\n        \"code\": {\n          \"coding\": [\n            {\n              \"system\": \"https://fhir.mona.icu/CodeSystem/mona-output-factor\",\n              \"code\": \"8012afcc-1772-4765-9f18-46cd5e0f96a3\",\n              \"display\": \"Stuhl -100 ml was recorded at 2025-09-24 10:00:00 00:00.\"\n            },\n            {\n              \"system\": \"http://snomed.info/sct\",\n              \"code\": \"706697005\"\n            }\n          ],\n          \"text\": \"Stuhl -100 ml was recorded at 2025-09-24 11:53:00 00:00.\"\n        },\n        \"subject\": {\n          \"reference\": \"Patient/88d0cf61-8b30-44e8-b8c6-f36df792abea\"\n        },\n        \"encounter\": {\n          \"reference\": \"Encounter/f2a4e1fa-18fa-4181-b2e6-a527efba20fb\"\n        },\n        \"effectiveDateTime\": \"2025-09-24T11:53:00Z\",\n        \"performer\": [\n          {\n            \"reference\": \"Practitioner/a3ccf1af-decb-4d7f-a3b3-84f1d2dd0840\"\n          }\n        ],\n        \"valueQuantity\": {\n          \"value\": -100.0,\n          \"unit\": \"ml\"\n        }\n      },\n      \"search\": {\n        \"mode\": \"match\"\n      }\n    }\n  ]\n}";
	}

	public static List<Observation> extractObservationsFromBundle(Bundle bundle) {
		List<Observation> observations = new ArrayList<>();
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			Resource resource = entry.getResource();
			if (resource instanceof Observation) {
				observations.add((Observation) resource);
			}
		}
		return observations;
	}

	public Bundle readBundleFromFile(String filePath) throws IOException {
		String json = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
		FhirContext fhirContext = FhirContext.forR4();
		IParser jsonParser = fhirContext.newJsonParser();
		return jsonParser.parseResource(Bundle.class, json);
	}

	public Bundle getBundle(String bundleJson) {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8));
		FhirContext fhirContext = FhirContext.forR4();
		IParser jsonParser = fhirContext.newJsonParser();
		return jsonParser.parseResource(Bundle.class, inputStream);
	}


	public Bundle removeIdsFromBundle(Bundle bundle) {
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			entry.setFullUrl(null);
			entry.getResource().setId((IdType) null);
		}
		return bundle;
	}

	// @Test
	// void testNoOutputRecords() {
	//     // Encounter with Zero Output Records.
	//     List<Observation> observations = new ArrayList<>();
	//     Date encounterStartDate = Date.from(Instant.parse("2025-09-19T18:00:00Z"));
	//     DateParam dateFrom = new DateParam(null, Date.from(Instant.parse("2025-09-10T18:00:00Z")));
	//     DateParam dateTo = new DateParam(null, Date.from(Instant.parse("2025-09-15T18:00:00Z")));
	//
	//     when(myObservationDao.searchForResources(any(), any())).thenReturn(observations);
	//     when(bundleProvider.getAllResources()).thenReturn(List.of(new Configuration().setBody(new StringType(resetTime))));
	//     when(myEncounterDao.read(any(), any())).thenReturn(new Encounter().setPeriod(new Period().setStart(encounterStartDate)));
	//     when(myConfigurationDao.search(any(), any())).thenReturn(bundleProvider);
	//
	//     Bundle result = outputCalculationForTests.calculatedOutput(encounter, interval, dateFrom, dateTo, requestDetails);
	//     Assertions.assertEquals(0, result.getEntry().size());
	// }

	@Test
	void testStartFromAndDateToAreAfterEncounterEnd() {
		Bundle bundle = getBundle(bundleJson);
		List<Observation> observations = extractObservationsFromBundle(bundle);
		Date encounterStartDate = Date.from(Instant.parse("2025-09-19T18:00:00Z"));
		Date encounterEndDate = Date.from(Instant.parse("2025-09-27T18:00:00Z"));
		Period encounterPeriod = new Period().setStart(encounterStartDate);
		encounterPeriod.setEnd(encounterEndDate);
		DateParam dateFrom = new DateParam(null, Date.from(Instant.parse("2025-09-28T23:00:00Z")));
		DateParam dateTo = new DateParam(null, Date.from(Instant.parse("2025-09-29T18:00:00Z")));

		when(bundleProvider.getAllResources()).thenReturn(
			bundle.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource)
				.map(resource -> (IBaseResource) resource)
				.toList()
		);
		when(myObservationDao.searchForResources(any(), any())).thenReturn(observations);
		when(bundleProvider.getAllResources()).thenReturn(List.of(new Configuration().setBody(new StringType(resetTime))));
		when(myEncounterDao.read(any(), any())).thenReturn(new Encounter().setPeriod(encounterPeriod));
		when(myConfigurationDao.search(any(), any())).thenReturn(bundleProvider);

		// Bundle result = outputCalculationForTests.calculatedOutput(encounter, interval, dateFrom, dateTo, requestDetails);
		// Assertions.assertEquals(0, result.getEntry().size());
	}

	@Test
	void testStartFromAndDateToAreBeforeEncounterStart() {
		Bundle bundle = getBundle(bundleJson);
		List<Observation> observations = extractObservationsFromBundle(bundle);
		Date encounterStartDate = Date.from(Instant.parse("2025-09-19T18:00:00Z"));
		DateParam dateFrom = new DateParam(null, Date.from(Instant.parse("2025-09-10T18:00:00Z")));
		DateParam dateTo = new DateParam(null, Date.from(Instant.parse("2025-09-15T18:00:00Z")));

		when(bundleProvider.getAllResources()).thenReturn(
			bundle.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource)
				.map(resource -> (IBaseResource) resource)
				.toList()
		);
		when(myObservationDao.searchForResources(any(), any())).thenReturn(observations);
		when(bundleProvider.getAllResources()).thenReturn(List.of(new Configuration().setBody(new StringType(resetTime))));
		when(myEncounterDao.read(any(), any())).thenReturn(new Encounter().setPeriod(new Period().setStart(encounterStartDate)));
		when(myConfigurationDao.search(any(), any())).thenReturn(bundleProvider);

		// Bundle result = outputCalculationForTests.calculatedOutput(encounter, interval, dateFrom, dateTo, requestDetails);
		// Assertions.assertEquals(0, result.getEntry().size());
	}

	@Test
	void testInProgressEncounterCalculations() throws IOException {
		//
		//		 * documented outputs:
		//		 * timestamp,            value, current value
		//		 * 2025-09-23T10:00:00Z, -70,    -70
		//		 * 2025-09-23T13:00:00Z, -50,    -120
		//		 * 2025-09-23T17:00:00Z, -20,    -140
		//		 * 2025-09-23T22:00:00Z, -10,    -150
		//		 * 2025-09-24T03:00:00Z, -5,     -155
		//		 * 2025-09-24T07:00:00Z, -100,   -255
		//		 * 2025-09-24T07:00:00Z, -100,   -355
		//		 * 2025-09-24T08:00:00Z, -25,    -380 or -25 if 08:00 is the reset time
		//		 * 2025-09-24T11:53:00Z, -100,   -480 or -125 if 08:00 is the reset time
		//
		Calendar fixedCalendar = Calendar.getInstance();
		fixedCalendar.set(2025, Calendar.SEPTEMBER, 27, 10, 5, 45);

		// outputCalculationForTests.currentTimestamp = fixedCalendar;
		Bundle bundle = getBundle(bundleJson);
		List<Observation> observations = extractObservationsFromBundle(bundle);
		Date encounterStartDate = Date.from(Instant.parse("2025-09-19T18:00:00Z"));
		DateParam dateFrom = new DateParam(null, Date.from(Instant.parse("2025-09-21T17:00:00Z")));
		DateParam dateTo = new DateParam(null, Date.from(Instant.parse("2025-09-24T18:00:00Z")));


		when(bundleProvider.getAllResources()).thenReturn(
			bundle.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource)
				.map(resource -> (IBaseResource) resource)
				.toList()
		);
		when(myObservationDao.searchForResources(any(), any())).thenReturn(observations);
		when(myEncounterDao.read(any(), any())).thenReturn(new Encounter().setPeriod(new Period().setStart(encounterStartDate)));
		when(bundleProvider.getAllResources()).thenReturn(List.of(new Configuration().setBody(new StringType(resetTime))));
		when(myConfigurationDao.search(any(), any())).thenReturn(bundleProvider);

		// 5 minutes interval
		FhirContext ctx = FhirContext.forR4();
		// Bundle actualBundle = outputCalculationForTests.calculatedOutput(encounter, interval, dateFrom, dateTo, requestDetails);
		// actualBundle = removeIdsFromBundle(actualBundle);
		// String actual = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualBundle);
		// Bundle expectedBundle = readBundleFromFile("src/test/resources/scripts/Response5MinOutputCalculation.json");
		// expectedBundle = removeIdsFromBundle(expectedBundle);
		// String expected = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedBundle);
		//
		// Assertions.assertEquals(expectedBundle.getEntry().size(), actualBundle.getEntry().size());
		// Assertions.assertEquals(actual, expected);
		//
		// // 30 minutes interval
		// actualBundle = outputCalculationForTests.calculatedOutput(encounter, new NumberParam(30), dateFrom, dateTo, requestDetails);
		// actualBundle = removeIdsFromBundle(actualBundle);
		// actual = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualBundle);
		// expectedBundle = readBundleFromFile("src/test/resources/scripts/Response30MinOutputCalculation.json");
		// expectedBundle = removeIdsFromBundle(expectedBundle);
		// expected = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedBundle);
		//
		// Assertions.assertEquals(expectedBundle.getEntry().size(), actualBundle.getEntry().size());
		// Assertions.assertEquals(actual, expected);
		//
		// // 6 hours interval
		// actualBundle = outputCalculationForTests.calculatedOutput(encounter, new NumberParam(6 * 60), dateFrom, dateTo, requestDetails);
		// actualBundle = removeIdsFromBundle(actualBundle);
		// actual = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualBundle);
		// expectedBundle = readBundleFromFile("src/test/resources/scripts/Response360MinOutputCalculation.json");
		// expectedBundle = removeIdsFromBundle(expectedBundle);
		// expected = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedBundle);
		//
		// Assertions.assertEquals(expectedBundle.getEntry().size(), actualBundle.getEntry().size());
		// Assertions.assertEquals(actual, expected);
		//
		// // 1 Day interval
		// actualBundle = outputCalculationForTests.calculatedOutput(encounter, new NumberParam(24 * 60), dateFrom, dateTo, requestDetails);
		// actualBundle = removeIdsFromBundle(actualBundle);
		// actual = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(actualBundle);
		// expectedBundle = readBundleFromFile("src/test/resources/scripts/Response1440MinOutputCalculation.json");
		// expectedBundle = removeIdsFromBundle(expectedBundle);
		// expected = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(expectedBundle);

		// Assertions.assertEquals(expectedBundle.getEntry().size(), actualBundle.getEntry().size());
		// Assertions.assertEquals(actual, expected);
	}

}
