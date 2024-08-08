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
import com.clinomic.configuration.ConfigurationResourceProvider;
import groovy.lang.GroovyObject;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScriptingSvcImpl implements IScriptingSvc {

	private static final Logger ourLog = LoggerFactory.getLogger(ConfigurationResourceProvider.class);
	private static final String CUSTOM_SCRIPT = "CustomScript";

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

	public void loadInterceptor(String interceptorName, String interceptorBody) {

		unloadInterceptor(interceptorName);

		GroovyObject groovyObject = groovyClassLoader.loadScriptObject(interceptorName, interceptorBody);
		interceptorService.registerInterceptor(groovyObject);

		ourLog.info("loaded interceptor with name : {}", interceptorName);

	}

	public void unloadInterceptor(String interceptorName) {
		List<Object> customInterceptors = listCustomInterceptors();

		customInterceptors.stream()
			.filter(interceptor -> interceptor.getClass().toString().contains(interceptorName))
			.forEach(interceptor -> {
				interceptorService.unregisterInterceptor(interceptor);
				ourLog.info("unloaded interceptor with name : {}", interceptorName);
			});
	}

	public void unloadInterceptors() {
		List<Object> customInterceptors = listCustomInterceptors();

		customInterceptors.stream().forEach(interceptor -> interceptorService.unregisterInterceptor(interceptor));

	}

	@NotNull
	public List<Object> listCustomInterceptors() {
		List<Object> interceptors = interceptorService.getAllRegisteredInterceptors();

		List<Object> customInterceptors = interceptors.stream().filter(interceptor -> Arrays.stream(interceptor.getClass().getAnnotations()).anyMatch(annotation -> annotation.toString().contains(CUSTOM_SCRIPT))).collect(Collectors.toList());

		return customInterceptors;
	}


}
