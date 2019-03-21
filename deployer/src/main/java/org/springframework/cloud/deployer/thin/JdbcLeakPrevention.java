/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.deployer.thin;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

/**
 * 
 * Clean up SQL drivers in the current classloader.
 * 
 * (Copied from Tomcat.)
 * 
 * @author Dave Syer
 *
 */
public class JdbcLeakPrevention {

	public List<String> clearJdbcDriverRegistrations() throws SQLException {
		List<String> driverNames = new ArrayList<>();

		/*
		 * DriverManager.getDrivers() has a nasty side-effect of registering drivers that
		 * are visible to this class loader but haven't yet been loaded. Therefore, the
		 * first call to this method a) gets the list of originally loaded drivers and b)
		 * triggers the unwanted side-effect. The second call gets the complete list of
		 * drivers ensuring that both original drivers and any loaded as a result of the
		 * side-effects are all de-registered.
		 */
		HashSet<Driver> originalDrivers = new HashSet<>();
		Enumeration<Driver> drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			originalDrivers.add(drivers.nextElement());
		}
		drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			Driver driver = drivers.nextElement();
			// Only unload the drivers this app loaded
			if (driver.getClass().getClassLoader() != this.getClass().getClassLoader()) {
				continue;
			}
			// Only report drivers that were originally registered. Skip any
			// that were registered as a side-effect of this code.
			if (originalDrivers.contains(driver)) {
				driverNames.add(driver.getClass().getCanonicalName());
			}
			DriverManager.deregisterDriver(driver);
		}
		return driverNames;
	}
}