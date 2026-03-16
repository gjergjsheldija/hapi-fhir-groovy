/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-09-12
 */

package com.gjergjsheldija.jobs;

import ca.uhn.fhir.batch2.coordinator.JobDefinitionRegistry;
import ca.uhn.fhir.batch2.model.JobDefinition;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping(value = "/control/jobs-registered", produces = MediaType.APPLICATION_JSON_VALUE)
public class RegisteredJobs {

	@Autowired
	private JobDefinitionRegistry jobRegistry;

	@GetMapping
	public List<JobDefinitionType> getAllRegistredJobs() {
		List<String> jobList = jobRegistry.getJobDefinitionIds();

		return jobList.stream()
			.map(jobId -> jobRegistry.getJobDefinition(jobId, 1))
			.filter(java.util.Optional::isPresent)
			.map(java.util.Optional::get)
			.map(this::convertToJobDefinitionType)
			.collect(Collectors.toList());
	}

	private JobDefinitionType convertToJobDefinitionType(JobDefinition<?> jobDefinition) {
		JobDefinitionType jobDefinitionType = new JobDefinitionType();
		jobDefinitionType.setDefinitionId(jobDefinition.getJobDefinitionId());
		jobDefinitionType.setDescription(jobDefinition.getJobDescription());

		List<Map.Entry<String, String>> steps = jobDefinition.getSteps().stream()
				.map(step -> Map.entry(
						 step.getStepId(),
						step.getStepDescription()
				))
				.collect(Collectors.toList());

		jobDefinitionType.setSteps(steps);

		return jobDefinitionType;
	}
}

@Data
class JobDefinitionType {
	private String definitionId;
	private String description;
	private List<Map.Entry<String, String>> steps = new ArrayList<>();

}