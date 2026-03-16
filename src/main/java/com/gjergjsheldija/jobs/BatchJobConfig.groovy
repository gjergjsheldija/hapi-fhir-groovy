package com.gjergjsheldija.jobs;



import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@Configuration
@EnableScheduling
class BatchJobConfig {

	@Scheduled(fixedRate = 60000) // 60000 milliseconds = 1 minute
	void printHelloWorld() {
		println "Hello World --->"
	}
}