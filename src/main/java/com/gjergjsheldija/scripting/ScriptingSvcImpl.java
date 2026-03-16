/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-07
 */

package com.gjergjsheldija.scripting;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import com.gjergjsheldija.configuration.ConfigurationResourceProvider;
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

	public void loadCustomScript(String interceptorName, String interceptorBody) {

		unloadCustomScript(interceptorName);

		GroovyObject groovyObject = groovyClassLoader.loadScriptObject(interceptorName, interceptorBody);
		interceptorService.registerInterceptor(groovyObject);

		ourLog.info("loaded interceptor with name : {}", interceptorName);

	}

	public void unloadCustomScript(String interceptorName) {
		List<Object> customInterceptors = listCustomScripts();

		customInterceptors.stream()
				.filter(interceptor -> interceptor.getClass().toString().contains(interceptorName))
				.forEach(interceptor -> {
					interceptorService.unregisterInterceptor(interceptor);
					ourLog.info("unloaded interceptor with name : {}", interceptorName);
				});
	}

	public void unloadInterceptors() {
		List<Object> customInterceptors = listCustomScripts();

		customInterceptors.stream().forEach(interceptor -> interceptorService.unregisterInterceptor(interceptor));

	}

	@NotNull
	public List<Object> listCustomScripts() {
		List<Object> interceptors = interceptorService.getAllRegisteredInterceptors();

		List<Object> customInterceptors = interceptors.stream()
				.filter(interceptor -> Arrays.stream(interceptor.getClass().getAnnotations())
						.anyMatch(annotation -> annotation.toString().contains(CUSTOM_SCRIPT)))
				.collect(Collectors.toList());

		return customInterceptors;
	}

}
