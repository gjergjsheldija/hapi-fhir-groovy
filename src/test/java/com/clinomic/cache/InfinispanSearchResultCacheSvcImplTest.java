/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-09-16
 */

package com.clinomic.cache;

import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.entity.Search;
import ca.uhn.fhir.jpa.model.dao.JpaPid;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfinispanSearchResultCacheSvcImplTest {

	@Mock
	private BasicCacheContainer mockCacheContainer;

	@Mock
	private BasicCache<Object, Object> mockCache;

	@Mock
	private JpaStorageSettings mockStorageSettings;

	private InfinispanSearchResultCacheSvcImpl cacheService;

	@BeforeEach
	void setUp() {
		cacheService = new InfinispanSearchResultCacheSvcImpl(mockCacheContainer);
		ReflectionTestUtils.setField(cacheService, "myInfinispanCacheName", "testCache");
		cacheService.myStorageSettings = mockStorageSettings;
		when(mockCacheContainer.getCache(anyString())).thenReturn(mockCache);
		cacheService.init();

		// Verify that getCache was called with the correct cache name
		verify(mockCacheContainer).getCache("testCache");
	}

	@Test
	void testStoreResults() {
		Search search = new Search();
		search.setUuid("test-uuid");
		List<JpaPid> previousPids = Arrays.asList(JpaPid.fromId(1L), JpaPid.fromId(2L));
		List<JpaPid> newPids = Arrays.asList(JpaPid.fromId(3L), JpaPid.fromId(4L));

		when(mockStorageSettings.getExpireSearchResultsAfterMillis()).thenReturn(1000L);

		cacheService.storeResults(search, previousPids, newPids, null, null);

		verify(mockCache).put(eq("test-uuid"), anyList(), eq(1000L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	void testFetchResultPids() {
		Search search = new Search();
		search.setUuid("test-uuid");
		List<Long> storedPids = Arrays.asList(1L, 2L, 3L, 4L);

		when(mockCache.get("test-uuid")).thenReturn(storedPids);

		List<JpaPid> result = cacheService.fetchResultPids(search, 1, 3, null, null);

		assertEquals(2, result.size());
		assertEquals(2L, result.get(0).getId());
		assertEquals(3L, result.get(1).getId());
	}

	@Test
	void testFetchResultPidsWithInvalidRange() {
		Search search = new Search();
		search.setUuid("test-uuid");
		List<Long> storedPids = Arrays.asList(1L, 2L, 3L, 4L);

		when(mockCache.get("test-uuid")).thenReturn(storedPids);

		List<JpaPid> result = cacheService.fetchResultPids(search, 1, 10, null, null);

		assertEquals(3, result.size());
		assertEquals(2L, result.get(0).getId());
		assertEquals(3L, result.get(1).getId());
		assertEquals(4L, result.get(2).getId());
	}

	@Test
	void testFetchAllResultPids() {
		Search search = new Search();
		search.setUuid("test-uuid");
		List<Long> storedPids = Arrays.asList(1L, 2L, 3L, 4L);

		when(mockCache.get("test-uuid")).thenReturn(storedPids);

		List<JpaPid> result = cacheService.fetchAllResultPids(search, null, null);

		assertEquals(4, result.size());
		assertEquals(1L, result.get(0).getId());
		assertEquals(2L, result.get(1).getId());
		assertEquals(3L, result.get(2).getId());
		assertEquals(4L, result.get(3).getId());
	}

	@Test
	void testFetchAllResultPidsWithNullCache() {
		Search search = new Search();
		search.setUuid("test-uuid");

		when(mockCache.get("test-uuid")).thenReturn(null);

		List<JpaPid> result = cacheService.fetchAllResultPids(search, null, null);

		assertNull(result);
	}
}