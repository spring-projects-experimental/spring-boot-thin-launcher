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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Manifest;

import org.springframework.boot.loader.archive.Archive;

/**
 * @author Dave Syer
 *
 */
public class UrlArchive implements Archive {

	private URL url;

	public UrlArchive(URL url) {
		this.url = url;
	}

	public UrlArchive(Archive url) {
		try {
			this.url = url.getUrl();
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException("Bad URL", e);
		}
	}

	@Override
	public Iterator<Entry> iterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public URL getUrl() throws MalformedURLException {
		return this.url;
	}

	@Override
	public Manifest getManifest() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Archive> getNestedArchives(EntryFilter filter) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		try {
			return getUrl().toString();
		}
		catch (Exception ex) {
			return "jar archive";
		}
	}
}
