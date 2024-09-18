/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Mark Finegan <mfinegan@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-09-16
 */

package com.clinomic.cache;

import ca.uhn.fhir.jpa.search.cache.ISearchResultCacheSvc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(prefix = "hapi.fhir", name = "cache_type", havingValue = "INFINISPAN")
public class InfinispanConfig {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InfinispanConfig.class);

	// Here we override this bean so as to use Infinispan instead of the DB for storing search results.
   // (... if the hapi.fhir.cache_type property is set to "infinispan", that is.)
   @ConditionalOnProperty(prefix = "hapi.fhir", name = "cache_type", havingValue = "INFINISPAN")
   @Bean
	@Primary
   public ISearchResultCacheSvc searchResultCacheSvc() {

		log.info("Overriding DatabaseCache with InfinispanCache.");

   	return new InfinispanSearchResultCacheSvcImpl();
   }
}
