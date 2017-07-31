/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.loader.thin;

import java.security.CodeSource;
import java.security.ProtectionDomain;

import org.slf4j.ILoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * @author Dave Syer
 *
 */
class LogUtils {

	public static void setLogLevel(Level level) {
		setLogLevel(Logger.ROOT_LOGGER_NAME, level);
	}

	private static void setLogLevel(String loggerName, Level level) {
		ch.qos.logback.classic.Logger logger = getLogger(loggerName);
		if (logger != null) {
			logger.setLevel(level);
		}
	}

	private static ch.qos.logback.classic.Logger getLogger(String name) {
		LoggerContext factory = getLoggerContext();
		if (StringUtils.isEmpty(name)) {
			name = Logger.ROOT_LOGGER_NAME;
		}
		return factory.getLogger(name);

	}

	private static LoggerContext getLoggerContext() {
		ILoggerFactory factory = StaticLoggerBinder.getSingleton().getLoggerFactory();
		Assert.isInstanceOf(LoggerContext.class, factory,
				String.format(
						"LoggerFactory is not a Logback LoggerContext but Logback is on "
								+ "the classpath. Either remove Logback or the competing "
								+ "implementation (%s loaded from %s). If you are using "
								+ "WebLogic you will need to add 'org.slf4j' to "
								+ "prefer-application-packages in WEB-INF/weblogic.xml",
						factory.getClass(), getLocation(factory)));
		return (LoggerContext) factory;
	}

	private static Object getLocation(ILoggerFactory factory) {
		try {
			ProtectionDomain protectionDomain = factory.getClass().getProtectionDomain();
			CodeSource codeSource = protectionDomain.getCodeSource();
			if (codeSource != null) {
				return codeSource.getLocation();
			}
		}
		catch (SecurityException ex) {
			// Unable to determine location
		}
		return "unknown location";
	}

}
