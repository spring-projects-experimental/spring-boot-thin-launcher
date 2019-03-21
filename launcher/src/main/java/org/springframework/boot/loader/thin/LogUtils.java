/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.loader.thin;

import java.util.logging.Logger;

/**
 * @author Dave Syer
 *
 */
class LogUtils {

	public static final String ROOT_LOGGER_NAME = "";

	public static void setLogLevel(Level level) {
		setLogLevel(ROOT_LOGGER_NAME, level);
	}

	private static void setLogLevel(String loggerName, Level level) {
		Logger logger = getLogger(loggerName);
		if (logger != null) {
			logger.setLevel(convert(level));
		}
	}

	private static java.util.logging.Level convert(Level level) {
		switch (level) {
		case OFF:
			return java.util.logging.Level.OFF;
		case ERROR:
			return java.util.logging.Level.SEVERE;
		case WARN:
			return java.util.logging.Level.WARNING;
		case DEBUG:
			return java.util.logging.Level.FINE;
		case TRACE:
			return java.util.logging.Level.FINEST;
		default:
			return java.util.logging.Level.INFO;
		}
	}

	private static Logger getLogger(String name) {
		return Logger.getLogger(name);
	}

}

enum Level {
	OFF, ERROR, WARN, INFO, DEBUG, TRACE;
}
