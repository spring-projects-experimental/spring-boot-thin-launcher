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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class PatternTests {

	Pattern pattern = // Pattern.compile("([^: ]+):([^: ]+)(:([^: ]+):([^: ]+))?(:([^:
						// ]+))?");
			Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?(:([^: ]+))?");

	@Test
	public void test() {
		String input = "foo:bar";
		Matcher matcher = pattern.matcher(input);
		assertThat(matcher.matches()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("foo");
		assertThat(matcher.group(2)).isEqualTo("bar");
	}

	@Test
	public void version() {
		String input = "foo:bar:spam";
		Matcher matcher = pattern.matcher(input);
		assertThat(matcher.matches()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("foo");
		assertThat(matcher.group(2)).isEqualTo("bar");
		assertThat(matcher.group(4)).isEqualTo("spam");
	}

	@Test
	public void versionless() {
		String input = "foo:bar:jar:test";
		Matcher matcher = pattern.matcher(input);
		assertThat(matcher.matches()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("foo");
		assertThat(matcher.group(2)).isEqualTo("bar");
		assertThat(matcher.group(4)).isEqualTo("jar");
		assertThat(matcher.group(6)).isEqualTo("test");
	}

	@Test
	public void all() {
		String input = "foo:bar:jar:test:spam";
		Matcher matcher = pattern.matcher(input);
		assertThat(matcher.matches()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("foo");
		assertThat(matcher.group(2)).isEqualTo("bar");
		assertThat(matcher.group(4)).isEqualTo("jar");
		assertThat(matcher.group(6)).isEqualTo("test");
		assertThat(matcher.group(8)).isEqualTo("spam");
	}

}
