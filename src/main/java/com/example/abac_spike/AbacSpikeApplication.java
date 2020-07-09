package com.example.abac_spike;

import internal.org.springframework.content.rest.utils.RepositoryUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.repository.support.Repositories;
import org.springframework.web.util.UrlPathHelper;

import javax.persistence.EntityManager;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableJpaRepositories()
public class AbacSpikeApplication {

	public static void main(String[] args) {
		SpringApplication.run(AbacSpikeApplication.class, args);
	}

	@Configuration
	public static class Config {

		@Bean
		public RequestFilter abacFilter(Repositories repos) {
			return new RequestFilter(repos);
		}

		@Bean
		public FilterRegistrationBean<RequestFilter> abacFilterRegistration(Repositories repos){
			FilterRegistrationBean<RequestFilter> registrationBean = new FilterRegistrationBean<>();

			registrationBean.setFilter(abacFilter(repos));
			registrationBean.addUrlPatterns("/*");

			return registrationBean;
		}

		@Bean
		public QueryAugmentingAspect documentRepoAbacAspect(EntityManager em) {
			return new QueryAugmentingAspect(JpaEntityInformationSupport.getEntityInformation(Document.class, em));
		}
	}

	public static class RequestFilter implements Filter {

		private final Repositories repos;

		public RequestFilter(Repositories repos) {
			this.repos = repos;
		}

		@Override
		public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
			HttpServletRequest request = (HttpServletRequest) servletRequest;

			String path = new UrlPathHelper().getLookupPathForRequest(request);
			String[] pathElements = path.split("/");
			Class<?> entityClass = RepositoryUtils.findRepositoryInformation(repos, pathElements[1]).getDomainType();
			if (entityClass != null) {
				EntityContext.setCurrentEntityContext(entityClass);
			}

			String tenantID = request.getHeader("X-ABAC-Context");
			if (tenantID != null) {
				AbacContext.setCurrentAbacContext(tenantID);
			}

			filterChain.doFilter(servletRequest, servletResponse);

			AbacContext.clear();
			EntityContext.clear();
		}
	}

	public static class EntityContext {

		private static ThreadLocal<Class<?>> currentEntityContext = new InheritableThreadLocal<>();

		public static Class<?> getCurrentEntityContext() {
			return currentEntityContext.get();
		}

		public static void setCurrentEntityContext(Class<?> entityClass) {
			currentEntityContext.set(entityClass);
		}

		public static void clear() {
			currentEntityContext.set(null);
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
