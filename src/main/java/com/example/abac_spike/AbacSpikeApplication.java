package com.example.abac_spike;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass= QueryAugmentingRepository.class)
public class AbacSpikeApplication {

	public static void main(String[] args) {
		SpringApplication.run(AbacSpikeApplication.class, args);
	}

	@Configuration
	public static class Config {

		@Bean
		public FilterRegistrationBean<RequestFilter> abacFilter(){
			FilterRegistrationBean<RequestFilter> registrationBean
					= new FilterRegistrationBean<>();

			registrationBean.setFilter(new AbacSpikeApplication.RequestFilter());
			registrationBean.addUrlPatterns("/*");

			return registrationBean;
		}
	}

	@Component
	public static class RequestFilter implements Filter {

		@Override
		public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
			HttpServletRequest request = (HttpServletRequest) servletRequest;
			HttpServletResponse response = (HttpServletResponse) servletResponse;

			String tenantID = request.getHeader("X-ABAC-Context");
			if (tenantID != null) {
				AbacContext.setCurrentAbacContext(tenantID);
			}

			filterChain.doFilter(servletRequest, servletResponse);

			AbacContext.clear();
		}
	}

	public static class AbacContext {

		private static ThreadLocal<String> currentAbacContext = new InheritableThreadLocal<>();

		public static String getCurrentAbacContext() {
			return currentAbacContext.get();
		}

		public static void setCurrentAbacContext(String tenant) {
			currentAbacContext.set(tenant);
		}

		public static void clear() {
			currentAbacContext.set(null);
		}
	}
}
