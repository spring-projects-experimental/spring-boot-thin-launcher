package org.springframework.cloud.deployer.thin;

import org.apache.catalina.Host;
import org.apache.catalina.connector.Connector;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnClass({ Connector.class, ServletContextInitializer.class, TomcatServletWebServerFactory.class })
@AutoConfigureBefore(ServletWebServerFactoryAutoConfiguration.class)
public class ThinJarTomcatAutoConfiguration {

	@Bean
	public TomcatServletWebServerFactory embeddedServletContainerFactory(
			final Environment environment) {
		return new TomcatServletWebServerFactory() {
			@Override
			protected void prepareContext(Host host,
					ServletContextInitializer[] initializers) {
				host.getParent()
						.setName(environment.getProperty("server.tomcat.jmx.domain",
								"Tomcat-" + environment
										.getProperty("random.int(1000,10000)", "xxxx")));
				super.prepareContext(host, initializers);
			}
		};
	}

}
