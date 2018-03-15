package org.springframework.cloud.deployer.thin;

import org.apache.catalina.Host;
import org.apache.catalina.connector.Connector;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnClass({ Connector.class, ServletContextInitializer.class, TomcatEmbeddedServletContainerFactory.class })
@AutoConfigureBefore(EmbeddedServletContainerAutoConfiguration.class)
public class ThinJarTomcatAutoConfiguration {

	@Bean
	public TomcatEmbeddedServletContainerFactory embeddedServletContainerFactory(
			final Environment environment) {
		return new TomcatEmbeddedServletContainerFactory() {
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
