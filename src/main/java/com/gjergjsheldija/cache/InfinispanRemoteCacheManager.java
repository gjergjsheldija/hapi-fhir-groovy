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

import ca.uhn.fhir.context.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.infinispan.client.hotrod.DefaultTemplate;
import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class InfinispanRemoteCacheManager implements BasicCacheContainer {
	private static final Logger ourLog = LoggerFactory.getLogger(InfinispanRemoteCacheManager.class);
	private final RemoteCacheManager myRemoteCacheManager;

	public InfinispanRemoteCacheManager(
		String theInfinispanServers,
		HotrodProtocolVersionEnum theProtocolVersion,
		Boolean theSecurityEnabled,
		String theUsername,
		String thePassword,
		String theCache,
		Boolean theForcedCreation) {
		ConfigurationBuilder builder = new ConfigurationBuilder();
		builder.addServers(theInfinispanServers);
		if (theSecurityEnabled == true) {
			builder.security().authentication().username(theUsername).password(thePassword);
		}
		if (HotrodProtocolVersionEnum.getDeprecatedVersions().contains(theProtocolVersion)) {
			throw new ConfigurationException("Hotrod protocol version " + theProtocolVersion + " is no longer supported.");
		} else {
			builder.version(ProtocolVersion.valueOf(theProtocolVersion.name()));
			ourLog.info("Creating RemoteCacheManager for infinispan servers: {}", theInfinispanServers);
			this.myRemoteCacheManager = new RemoteCacheManager(builder.build());
			if (theForcedCreation == true) {
				this.myRemoteCacheManager.administration().getOrCreateCache(theCache, DefaultTemplate.DIST_SYNC);
			}
			ourLog.info("Manager created.  Protocol version: {}", this.myRemoteCacheManager.getConfiguration().version());
		}
	}

	public <K, V> BasicCache<K, V> getCache() {
		return this.myRemoteCacheManager.getCache();
	}

	public <K, V> BasicCache<K, V> getCache(String theCacheName) {
		return this.myRemoteCacheManager.getCache(theCacheName);
	}

	public Set<String> getCacheNames() {
		throw new UnsupportedOperationException();
	}

	public void start() {
		this.myRemoteCacheManager.start();
	}

	@PreDestroy
	public void stop() {
		this.myRemoteCacheManager.stop();
	}
}