package org.springframework.cloud.deployer.thin;

import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnClass(ThinJarAppDeployer.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class ThinJarAppDeployerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(AppDeployer.class)
	public AppDeployer appDeployer() {
		return new ThinJarAppDeployer();
	}

	@Bean
	@ConditionalOnMissingBean(TaskLauncher.class)
	public TaskLauncher taskLauncher() {
		return new ThinJarTaskLauncher();
	}
}