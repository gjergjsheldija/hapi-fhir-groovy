package ca.uhn.fhir.jpa.starter.cr;

import ca.uhn.fhir.cr.config.dstu3.*;
import ca.uhn.fhir.jpa.starter.annotations.OnDSTU3Condition;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Conditional({OnDSTU3Condition.class, CrConfigCondition.class})
@Import({
	CrCommonConfig.class,
	CrDstu3Config.class,
	ApplyOperationConfig.class,
	ExtractOperationConfig.class,
	PackageOperationConfig.class,
	PopulateOperationConfig.class,
	QuestionnaireOperationConfig.class
})
public class StarterCrDstu3Config {

}