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
package org.springframework.boot.loader.thin.maven;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.google.common.io.LineReader;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.codehaus.plexus.util.IOUtil;

/**
 * @author Dave Syer
 *
 */
public class RelocatingAppendingResourceTransformer implements ResourceTransformer {

	// Set this in pom configuration with <resource>...</resource>
	private String resource;

	private Map<String, ServiceStream> serviceEntries = new HashMap<String, ServiceStream>();

	@Override
	public boolean canTransformResource(String resource) {
		if (this.resource != null && this.resource.equalsIgnoreCase(resource)) {
			return true;
		}

		return false;
	}

	@Override
	public void processResource(String resource, InputStream is,
			final List<Relocator> relocators) throws IOException {
		ServiceStream out = serviceEntries.get(resource);
		if (out == null) {
			out = new ServiceStream();
			serviceEntries.put(resource, out);
		}

		final ServiceStream fout = out;

		final String content = IOUtils.toString(is);
		StringReader reader = new StringReader(content);
		LineReader lineReader = new LineReader(reader);
		String line;
		while ((line = lineReader.readLine()) != null) {
			String relContent = line;
			for (Relocator relocator : relocators) {
				relContent = relocator.applyToSourceContent(relContent);
			}
			fout.append(relContent + "\n");
		}
	}

	@Override
	public boolean hasTransformedResource() {
		return serviceEntries.size() > 0;
	}

	@Override
	public void modifyOutputStream(JarOutputStream jos) throws IOException {
		for (Map.Entry<String, ServiceStream> entry : serviceEntries.entrySet()) {
			String key = entry.getKey();
			ServiceStream data = entry.getValue();

			jos.putNextEntry(new JarEntry(key));
			IOUtil.copy(data.toInputStream(), jos);
			data.reset();
		}
	}

	static class ServiceStream extends ByteArrayOutputStream {

		public ServiceStream() {
			super(1024);
		}

		public void append(String content) throws IOException {
			if (count > 0 && buf[count - 1] != '\n' && buf[count - 1] != '\r') {
				write('\n');
			}

			byte[] contentBytes = content.getBytes("UTF-8");
			this.write(contentBytes);
		}

		public InputStream toInputStream() {
			return new ByteArrayInputStream(buf, 0, count);
		}

	}

}
