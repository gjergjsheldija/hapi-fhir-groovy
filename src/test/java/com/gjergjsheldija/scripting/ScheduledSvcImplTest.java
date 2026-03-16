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

package com.gjergjsheldija.scripting;

import com.gjergjsheldija.scripting.loader.GroovyClassLoader;
import groovy.lang.GroovyObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.TaskScheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledSvcImplTest {
	@Mock
	private TaskScheduler taskScheduler;

	@Mock
	private AutowireCapableBeanFactory beanFactory;

	@Mock
	private ScheduledFuture<?> scheduledFuture;

	@InjectMocks
	private ScheduledSvcImpl scheduledSvc;

	@BeforeEach
	void setUp() {
		scheduledSvc.start();
	}

	@Test
	void testLoadCustomScript() {
		String interceptorName = "testInterceptor";
		String interceptorBody = """
			 package scripting
			
			 import org.springframework.scheduling.annotation.EnableScheduling
			 import org.springframework.scheduling.annotation.Scheduled
			
			 @EnableScheduling
			 class BatchJobConfig {
				  @Scheduled(fixedRate = 60000L)
				  void printHelloWorld() {
						print("test");
				  }
			 }
			""";

		scheduledSvc.loadCustomScript(interceptorName, interceptorBody);

		verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Long.class));
	}

	@Test
	void testUnloadCustomScript() {
		String interceptorName = "testInterceptor";

		scheduledSvc.tasks.put(interceptorName, scheduledFuture);

		scheduledSvc.unloadCustomScript(interceptorName);

		verify(scheduledFuture).cancel(true);
		assertFalse(scheduledSvc.tasks.containsKey(interceptorName));
	}

	@Test
	void testListCustomScripts() {
		scheduledSvc.tasks.put("script1", mock(ScheduledFuture.class));
		scheduledSvc.tasks.put("script2", mock(ScheduledFuture.class));

		List<Object> scripts = scheduledSvc.listCustomScripts();

		assertEquals(2, scripts.size());
		assertTrue(scripts.contains("script1"));
		assertTrue(scripts.contains("script2"));
	}

	@Test
	void testFindScheduledMethod() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		String interceptorName = "testInterceptor";
		String interceptorBody = """
			 package scripting
			
			 import org.springframework.scheduling.annotation.EnableScheduling
			 import org.springframework.scheduling.annotation.Scheduled
			
			 @EnableScheduling
			 class BatchJobConfig {
				  @Scheduled(fixedRate = 60000L)
				  void printHelloWorld() {
						print("test");
				  }
			 }
			""";


		Method scheduledMethod = ScheduledSvcImpl.class.getDeclaredMethod("findScheduledMethod", GroovyObject.class);
		scheduledMethod.setAccessible(true);

		GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
		groovyClassLoader.setBeanFactory(beanFactory);
		GroovyObject go = groovyClassLoader.loadScriptObject(interceptorName, interceptorBody);

		Method result = (Method) scheduledMethod.invoke(scheduledSvc, go);

		assertNotNull(result);
		assertEquals("public void scripting.BatchJobConfig.printHelloWorld()", result.toString());
	}

}