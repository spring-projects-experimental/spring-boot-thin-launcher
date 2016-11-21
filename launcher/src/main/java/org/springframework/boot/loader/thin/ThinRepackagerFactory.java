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

package org.springframework.boot.loader.thin;

import java.io.File;
import java.io.IOException;

import org.springframework.boot.loader.tools.JarWriter;
import org.springframework.boot.loader.tools.Layout;
import org.springframework.boot.loader.tools.Repackager;
import org.springframework.boot.loader.tools.RepackagerFactory;

/**
 * @author Dave Syer
 *
 */
public class ThinRepackagerFactory implements RepackagerFactory {

	private final class RepackagerExtension extends Repackager {
		private RepackagerExtension(File source) {
			super(source);
			super.setLayout(new ThinLayout());
		}

		@Override
		protected void writeLoaderClasses(JarWriter writer) throws IOException {
			writer.writeLoaderClasses("META-INF/loader/spring-boot-thin-wrapper.jar");
		}

		@Override
		public void setLayout(Layout layout) {
		}
	}

	@Override
	public Repackager getRepackager(File source) {
		return new RepackagerExtension(source);
	}

}
