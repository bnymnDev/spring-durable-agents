package io.github.bnymndev.durableagents.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.DispatcherServlet;

import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.approval.ApprovalController;
import io.github.bnymndev.durableagents.approval.ApprovalEventBridge;

/** Approval REST endpoints and their security, when {@code durable-agents-approval} is present in a servlet app. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ ApprovalController.class, DispatcherServlet.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DurableAgentsApprovalConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ApprovalController durableAgentsApprovalController(Approvals approvals, DurableAgentsProperties properties) {
		return new ApprovalController(approvals, properties.getApproval().isEnforceRoles());
	}

	@Bean
	@ConditionalOnMissingBean
	ApprovalEventBridge durableAgentsApprovalEventBridge(ApplicationEventPublisher publisher,
			DurableAgentsProperties properties) {
		return new ApprovalEventBridge(publisher, properties.getApproval().getBasePath());
	}

	/**
	 * A filter chain for the approval endpoints only, registered when the application defines no
	 * {@code SecurityFilterChain} itself: authenticated, HTTP Basic, no CSRF (the endpoints are an API).
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ SecurityFilterChain.class, HttpSecurity.class })
	@ConditionalOnProperty(prefix = "durable-agents.approval", name = "security-auto-configured", havingValue = "true",
			matchIfMissing = true)
	static class ApprovalSecurityConfiguration {

		@Bean
		@Order(50)
		@ConditionalOnMissingBean(SecurityFilterChain.class)
		SecurityFilterChain durableAgentsApprovalSecurityFilterChain(HttpSecurity http, DurableAgentsProperties properties)
				throws Exception {
			String basePath = properties.getApproval().getBasePath();
			http.securityMatcher(basePath, basePath + "/**")
				.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.csrf((csrf) -> csrf.disable());
			return http.build();
		}

	}

}
