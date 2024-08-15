/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-07
 */

package com.clinomic.scripting;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import com.clinomic.scripting.loader.GroovyClassLoader;
import groovy.lang.GroovyObject;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InterceptorSvcImpl implements IScriptingSvc {

	private static final Logger ourLog = LoggerFactory.getLogger(InterceptorSvcImpl.class);
	private static final String CUSTOM_SCRIPT = "CustomScript";
	static final String INTERCEPTOR = "Interceptor";

	@Autowired
	IInterceptorService interceptorService;

	@Autowired
	AutowireCapableBeanFactory beanFactory;
	GroovyClassLoader groovyClassLoader;

	@PostConstruct
	void start() {
		groovyClassLoader = new GroovyClassLoader();
		groovyClassLoader.setBeanFactory(beanFactory);
	}

	public void loadCustomScript(String interceptorName, String interceptorBody) {

		unloadCustomScript(interceptorName);

		GroovyObject groovyObject = groovyClassLoader.loadScriptObject(interceptorName, interceptorBody);
		interceptorService.registerInterceptor(groovyObject);

		ourLog.info("Loaded Interceptor with name : {}", interceptorName);

	}

	public void unloadCustomScript(String interceptorName) {
		List<Object> customInterceptors = listCustomScripts();

		if (customInterceptors.isEmpty()) {
			ourLog.info("No provider found with name : {}", interceptorName);
			return;
		}

		customInterceptors.stream()
			.filter(interceptor -> interceptor.getClass().toString().contains(interceptorName))
			.forEach(interceptor -> {
				interceptorService.unregisterInterceptor(interceptor);
				ourLog.info("Unloaded Interceptor with name : {}", interceptorName);
			});
	}

	@NotNull
	public List<Object> listCustomScripts() {
		List<Object> interceptors = interceptorService.getAllRegisteredInterceptors();

		return interceptors.stream()
			.filter(interceptor -> {
				List<Annotation> annotations = Arrays.asList(interceptor.getClass().getAnnotations());
				long matchingAnnotations = annotations.stream()
					.filter(annotation -> annotation.toString().contains(CUSTOM_SCRIPT) || annotation.toString().contains(INTERCEPTOR))
					.count();
				return matchingAnnotations >= 2;
			})
			.collect(Collectors.toList());
	}

}
