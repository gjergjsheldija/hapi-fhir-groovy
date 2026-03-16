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
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ScheduledSvcImpl implements IScriptingSvc {
	private static final Logger ourLog = LoggerFactory.getLogger(ScheduledSvcImpl.class);
	static final String SCHEDULED_SCRIPT = "EnableScheduling";
	static final String TYPE = "Scheduled Job";
	final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

	@Autowired
	private TaskScheduler taskScheduler;

	@Autowired
	AutowireCapableBeanFactory beanFactory;
	GroovyClassLoader groovyClassLoader;

	@PostConstruct
	void start() {
		groovyClassLoader = new GroovyClassLoader();
		groovyClassLoader.setBeanFactory(beanFactory);
	}

	@Override
	public void loadCustomScript(String interceptorName, String interceptorBody) {
		unloadCustomScript(interceptorName);

		GroovyObject groovyObject = groovyClassLoader.loadScriptObject(interceptorName, interceptorBody);

		scheduleGroovyTask(groovyObject, interceptorName);

		ourLog.info("Loaded Scheduled task with name : {}", interceptorName);
	}

	@Override
	public void unloadCustomScript(String interceptorName) {
		ScheduledFuture<?> future = tasks.remove(interceptorName);
		if (future != null) {
			future.cancel(true);
			ourLog.info("Unloaded Scheduled task with name : {}", interceptorName);
		}
	}

	@SneakyThrows
	public void scheduleGroovyTask(GroovyObject groovyObject, String taskName) {
		Method targetMethod = findScheduledMethod(groovyObject);

		if (targetMethod != null) {
			Scheduled annotation = targetMethod.getAnnotation(Scheduled.class);
			Runnable task = () -> {
				try {
					targetMethod.invoke(groovyObject);
				} catch (Exception e) {
					e.printStackTrace();
				}
			};

			ScheduledFuture<?> future = null;

			if (!annotation.cron().isEmpty()) {
				String cronExpression = annotation.cron();
				Trigger cronTrigger = new CronTrigger(cronExpression);
				future = taskScheduler.schedule(task, cronTrigger);
			} else if (annotation.fixedRate() > 0) {
				long fixedRate = annotation.fixedRate();
				future = taskScheduler.scheduleAtFixedRate(task, fixedRate);
			} else if (annotation.fixedDelay() > 0) {
				long fixedDelay = annotation.fixedDelay();
				future = taskScheduler.scheduleWithFixedDelay(task, fixedDelay);
			} else if (annotation.initialDelay() > 0) {
				long initialDelay = annotation.initialDelay();
				long fixedRate = annotation.fixedRate();
				PeriodicTrigger trigger = new PeriodicTrigger(fixedRate, TimeUnit.MILLISECONDS);
				trigger.setInitialDelay(initialDelay);
				future = taskScheduler.schedule(task, trigger);
			}

			if (future != null) {
				tasks.put(taskName, future);
			}
		}
	}

	Method findScheduledMethod(GroovyObject groovyObject) {
		for (Method method : groovyObject.getClass().getMethods()) {
			if (method.isAnnotationPresent(Scheduled.class)) {
				return method;
			}
		}
		return null;
	}

	@NotNull
	public List<Object> listCustomScripts() {
		return tasks
			.keySet()
			.stream()
			.collect(Collectors.toList());
	}
}
