package io.sentinelops.chaos;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers failure injection when {@code sentinelops.chaos.enabled=true}. Services
 * turn this on only through their {@code chaos} profile, so it can never be active by
 * accident in a real deployment.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnProperty(prefix = "sentinelops.chaos", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChaosProperties.class)
public class ChaosAutoConfiguration {

	@Bean
	ChaosEngine chaosEngine(ChaosProperties properties, ObjectProvider<DataSource> dataSource) {
		return new ChaosEngine(properties, dataSource::getIfAvailable);
	}

	@Bean
	ChaosFilter chaosFilter(ChaosEngine engine) {
		return new ChaosFilter(engine);
	}

	@Bean
	ChaosController chaosController(ChaosEngine engine) {
		return new ChaosController(engine);
	}

}
