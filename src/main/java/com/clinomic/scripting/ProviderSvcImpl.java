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

import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.method.BaseMethodBinding;
import com.clinomic.scripting.loader.GroovyClassLoader;
import groovy.lang.GroovyObject;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProviderSvcImpl implements IScriptingSvc {

	private static final Logger ourLog = LoggerFactory.getLogger(ProviderSvcImpl.class);
	static final String CUSTOM_SCRIPT = "CustomScript";
	static final String PROVIDER = "Provider";
	static final String TYPE = "Provider";

	@Autowired
	@Lazy
	RestfulServer fhirServer;

	@Autowired
	AutowireCapableBeanFactory beanFactory;

	GroovyClassLoader groovyClassLoader;

	@PostConstruct
	void start() {
		groovyClassLoader = new GroovyClassLoader();
		groovyClassLoader.setBeanFactory(beanFactory);
	}

	public void loadCustomScript(String providerName, String providerBody) {

		unloadCustomScript(providerName);

		GroovyObject groovyObject = groovyClassLoader.loadScriptObject(providerName, providerBody);
		fhirServer.registerProvider(groovyObject);

		ourLog.info("loaded Provider with name : {}", providerName);

	}

	public void unloadCustomScript(String providerName) {
		List<Object> customProviders = listCustomScripts();

		if (customProviders.isEmpty()) {
			ourLog.info("No provider found with name : {}", providerName);
			return;
		}

		removeServerBinding(customProviders);
		removeProvider(providerName, customProviders);
	}

	private void removeProvider(String providerName, List<Object> customProviders) {
		customProviders.stream()
			.filter(provider -> provider.getClass().toString().contains(providerName))
			.forEach(provider -> {
				fhirServer.unregisterProvider(provider);
				ourLog.info("Unloaded Provider with name : {}", providerName);
			});
	}

	private void removeServerBinding(List<Object> customProviders) {
		List<BaseMethodBinding> serverBindings = fhirServer.getServerBindings();
		Iterator<BaseMethodBinding> iterator = serverBindings.iterator();
		while (iterator.hasNext()) {
			BaseMethodBinding serverBinding = iterator.next();
			if (customProviders.stream()
				.map(Object::getClass)
				.map(Class::getName)
				.anyMatch(name -> name.equals(serverBinding.getMethod().getDeclaringClass().getName()))) {
				iterator.remove();
			}
		}
	}

	@NotNull
	public List<Object> listCustomScripts() {
		Collection<Object> providers = fhirServer.getPlainProviders();

		return providers.stream()
			.filter(provider -> {
				List<Annotation> annotations = Arrays.asList(provider.getClass().getAnnotations());
				long matchingAnnotations = annotations.stream()
					.filter(annotation -> annotation.toString().contains(CUSTOM_SCRIPT) || annotation.toString().contains(PROVIDER))
					.count();
				return matchingAnnotations >= 2;
			})
			.collect(Collectors.toList());
	}

}