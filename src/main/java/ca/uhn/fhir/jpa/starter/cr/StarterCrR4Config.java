package ca.uhn.fhir.jpa.starter.cr;

import ca.uhn.fhir.cr.config.r4.*;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Conditional({OnR4Condition.class, CrConfigCondition.class})
@Import({
	CrCommonConfig.class,
	CrR4Config.class,
	ApplyOperationConfig.class,
	ExtractOperationConfig.class,
	PackageOperationConfig.class,
	PopulateOperationConfig.class,
	QuestionnaireOperationConfig.class
})
public class StarterCrR4Config {

}