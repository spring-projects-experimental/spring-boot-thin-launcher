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
package com.example;

import java.io.File;

/**
 * @author Dave Syer
 *
 */
public class Utils {

	public static String javaCommand() {
		String javaHome = System.getProperty("java.home");
		if (javaHome == null) {
			return "java"; // Hope it's in PATH
		}
		File javaExecutable = new File(javaHome, "bin/java");
		if (javaExecutable.exists() && javaExecutable.canExecute()) {
			return javaExecutable.getAbsolutePath();
		}
		return "java";
	}

	public static String jarCommand() {
		String javaHome = System.getProperty("java.home");
		if (javaHome == null) {
			return "jar"; // Hope it's in PATH
		}
		File javaExecutable = new File(javaHome, "bin/jar");
		if (javaExecutable.exists() && javaExecutable.canExecute()) {
			return javaExecutable.getAbsolutePath();
		}
		return "jar";
	}

}
