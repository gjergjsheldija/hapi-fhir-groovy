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

import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyObject;
import lombok.SneakyThrows;
import lombok.ToString;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ToString
public class GroovyClassLoader extends groovy.lang.GroovyClassLoader {
	private static final Map<String, Object> CACHEMAP_MAP = new ConcurrentHashMap<>();

	AutowireCapableBeanFactory beanFactory;

	public GroovyClassLoader() {
		super();
	}

	public GroovyClassLoader setBeanFactory(AutowireCapableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		return this;
	}

	public GroovyClassLoader(ClassLoader loader, CompilerConfiguration config) {
		super(loader, config);
	}

	/**
	 * @param key
	 * @param source
	 * @param shouldCacheSource
	 * @return
	 * @throws CompilationFailedException
	 * @throws IOException
	 */
	public Class<?> parseClass(String key, String source, boolean shouldCacheSource) throws CompilationFailedException, IOException {

		GroovyCodeSource codeSource = new GroovyCodeSource(source, key, "");
		codeSource.setCachable(shouldCacheSource);

		if (shouldCacheSource) {
			CACHEMAP_MAP.put(key, codeSource.getName());
		}

		return super.parseClass(codeSource);
	}

	public void clearCache() {
		synchronized (this) {
			sourceCache.clear();
		}
	}

	/**
	 * @param key
	 */
	public void clearCache(String key) {
		Object value = CACHEMAP_MAP.get(key);
		synchronized (this) {
			if (sourceCache.containsKey(value)) sourceCache.remove(value);
			if (CACHEMAP_MAP.containsKey(key)) CACHEMAP_MAP.remove(key);
		}
	}

	@SneakyThrows
	@NotNull
	GroovyObject loadScriptObject(String name, String script) {
		Class<?> result = parseClass(name, script, true);
		GroovyObject groovyObject = (GroovyObject) result.getDeclaredConstructor().newInstance();

		beanFactory.autowireBean(groovyObject);

		return groovyObject;
	}

}