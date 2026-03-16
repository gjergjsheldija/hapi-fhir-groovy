/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-09-16
 */

package com.gjergjsheldija.cache;

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
