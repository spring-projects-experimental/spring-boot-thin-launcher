/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.cli.compiler.dependencies;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;

/**
 * @author Dave Syer
 *
 */
public class SpringBootDependenciesDependencyManagement
		extends MavenModelDependencyManagement {

	@SuppressWarnings("serial")
	public SpringBootDependenciesDependencyManagement() {
		// Fake out the Spring Boot class with the same FQCN so that the spring boot
		// dependencies do not get loaded by default
		super(new Model() {
			@Override
			public DependencyManagement getDependencyManagement() {
				return new DependencyManagement();
			}
		});
	}

}
