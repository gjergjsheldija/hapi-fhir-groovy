package com.clinomic.auditevent;

/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-06-10
 */

import ca.uhn.fhir.rest.api.server.IPreResourceShowDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.storage.interceptor.balp.BalpAuditCaptureInterceptor;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditContextServices;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditEventSink;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AuditEventInterceptorTest {

	@Mock
	private IBalpAuditEventSink mockAuditEventSink;

	@Mock
	private IBalpAuditContextServices mockContextServices;

	@Mock
	private BalpAuditCaptureInterceptor mockBalpAuditCaptureInterceptor;

	@InjectMocks
	private AuditEventInterceptor auditEventInterceptor;

	@Before
	public void setUp() {
		auditEventInterceptor = new AuditEventInterceptor(mockAuditEventSink, mockContextServices, true);
		auditEventInterceptor.balpAuditCaptureInterceptor = mockBalpAuditCaptureInterceptor;
	}

	@Test
	public void testResourceCreated() {
		IBaseResource mockResource = mock(IBaseResource.class);
		ServletRequestDetails mockRequestDetails = mock(ServletRequestDetails.class);

		auditEventInterceptor.hookStoragePrecommitResourceCreated(mockResource, mockRequestDetails);

		verify(mockBalpAuditCaptureInterceptor, times(1)).hookStoragePrecommitResourceCreated(mockResource, mockRequestDetails);
	}

	@Test
	public void testResourceDeleted() {
		IBaseResource mockResource = mock(IBaseResource.class);
		ServletRequestDetails mockRequestDetails = mock(ServletRequestDetails.class);

		auditEventInterceptor.hookStoragePrecommitResourceDeleted(mockResource, mockRequestDetails);

		verify(mockBalpAuditCaptureInterceptor, times(1)).hookStoragePrecommitResourceDeleted(mockResource, mockRequestDetails);
	}

	@Test
	public void testResourceUpdated() {
		IBaseResource mockOldResource = mock(IBaseResource.class);
		IBaseResource mockResource = mock(IBaseResource.class);
		ServletRequestDetails mockRequestDetails = mock(ServletRequestDetails.class);

		auditEventInterceptor.hookStoragePrecommitResourceUpdated(mockOldResource, mockResource, mockRequestDetails);

		verify(mockBalpAuditCaptureInterceptor, times(1)).hookStoragePrecommitResourceUpdated(mockOldResource, mockResource, mockRequestDetails);
	}

	@Test
	public void testResourceListed() {
		IPreResourceShowDetails mockDetails = mock(IPreResourceShowDetails.class);
		ServletRequestDetails mockRequestDetails = mock(ServletRequestDetails.class);

		auditEventInterceptor.hookStoragePreShowResources(mockDetails, mockRequestDetails);

		// Verify that no interaction with balpAuditCaptureInterceptor occurred
		verifyNoInteractions(mockBalpAuditCaptureInterceptor);
	}
}
