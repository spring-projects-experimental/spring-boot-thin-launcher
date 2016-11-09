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

import org.springframework.boot.loader.tools.DefaultLayoutFactory;
import org.springframework.boot.loader.tools.Layout;
import org.springframework.boot.loader.tools.LayoutFactory;
import org.springframework.boot.loader.tools.LayoutType;

/**
 * @author Dave Syer
 *
 */
public class ThinLayoutFactory implements LayoutFactory {

	private LayoutFactory delegate = new DefaultLayoutFactory();

	@Override
	public Layout getLayout(LayoutType type) {
		if (type == LayoutType.JAR) {
			return new ThinLayout();
		}
		return delegate.getLayout(type);
	}

}
