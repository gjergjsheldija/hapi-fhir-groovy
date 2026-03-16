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

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.entity.Search;
import ca.uhn.fhir.jpa.model.dao.JpaPid;
import ca.uhn.fhir.jpa.search.cache.ISearchResultCacheSvc;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.Validate;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class InfinispanSearchResultCacheSvcImpl implements ISearchResultCacheSvc {
	private static final Logger ourLog = LoggerFactory.getLogger(InfinispanSearchResultCacheSvcImpl.class);
	private BasicCacheContainer myRemoteCacheManager;
	private BasicCache<Object, Object> myCache;

	@Value("${searchcache.infinispan.cache.name:}")
	private String myInfinispanCacheName;

	@Value("${searchcache.infinispan.create.cache}")
	private Boolean myForceCreation;

	@Value("${searchcache.infinispan.servers:}")
	private String myInfinispanServers;

	@Value("${searchcache.infinispan.protocol_version:PROTOCOL_VERSION_23}")
	private HotrodProtocolVersionEnum myProtocolVersion;

	@Value("${searchcache.infinispan.enable_security}")
	private Boolean mySecurityEnabled;

	@Value("${searchcache.infinispan.username}")
	private String myUsername;

	@Value("${searchcache.infinispan.password}")
	private String myPassword;

	@Autowired
	public JpaStorageSettings myStorageSettings;

	public InfinispanSearchResultCacheSvcImpl() {
	}

	public InfinispanSearchResultCacheSvcImpl(BasicCacheContainer theBasicCacheContainer) {
		this.myRemoteCacheManager = theBasicCacheContainer;
	}

	@PostConstruct
	public void init() {
		if (this.myRemoteCacheManager == null) {
			this.myRemoteCacheManager = new InfinispanRemoteCacheManager(
				this.myInfinispanServers,
				this.myProtocolVersion,
				this.mySecurityEnabled,
				this.myUsername,
				this.myPassword,
				this.myInfinispanCacheName,
				this.myForceCreation);
		}

		this.myCache = this.myRemoteCacheManager.getCache(this.myInfinispanCacheName);
		Validate.notNull(this.myCache, "Error fetching remote infinispan cache named '%s'", new Object[]{this.myInfinispanCacheName});
	}

	public void storeResults(Search theSearch, List<JpaPid> thePreviouslyStoredResourcePids, List<JpaPid> theNewResourcePids, RequestDetails theRequestDetails, RequestPartitionId theRequestPartitionId) {
		List<Long> concatenatedList = new ArrayList();
		concatenatedList.addAll(JpaPid.toLongList(thePreviouslyStoredResourcePids));
		concatenatedList.addAll(JpaPid.toLongList(theNewResourcePids));
		this.myCache.put(theSearch.getUuid(), concatenatedList, this.myStorageSettings.getExpireSearchResultsAfterMillis(), TimeUnit.MILLISECONDS);
	}

	public List<JpaPid> fetchResultPids(Search theSearch, int theFrom, int theTo, RequestDetails theRequestDetails, RequestPartitionId theRequestPartitionId) {
		List<JpaPid> allValues = this.fetchAllResultPids(theSearch, theRequestDetails, theRequestPartitionId);
		if (allValues == null) {
			return null;
		} else {
			int allValuesSize = allValues.size();
			return theTo > allValuesSize ? allValues.subList(theFrom, allValuesSize) : allValues.subList(theFrom, theTo);
		}
	}

	public List<JpaPid> fetchAllResultPids(Search theSearch, RequestDetails theRequestDetails, RequestPartitionId theRequestPartitionId) {
		List<Long> longs = (List) this.myCache.get(theSearch.getUuid());
		return longs == null ? null : (List) longs.stream().map((t) -> {
			return JpaPid.fromId(t);
		}).collect(Collectors.toList());
	}
}