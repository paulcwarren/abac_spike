package com.example.abac_spike;

import internal.org.springframework.content.rest.utils.RepositoryUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.content.rest.config.ContentRestConfigurer;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.util.UrlPathHelper;

import javax.persistence.EntityManager;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;

import static java.lang.String.format;

@SpringBootApplication
@EnableAspectJAutoProxy()
@EnableJpaRepositories()
public class ABACSpikeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ABACSpikeApplication.class, args);
	}

	@Configuration
	public static class Config {

		@Bean
		public ContentRestConfigurer restConfigurer() {
			return new ContentRestConfigurer() {
				@Override
				public void configure(RestConfiguration config) {
					config.setBaseUri(URI.create("/content"));
				}
			};
		}

		@Bean
		public ABACRequestFilter abacFilter(Repositories repos, EntityManager em) {
			return new ABACRequestFilter(repos, em);
		}

		@Bean
		public FilterRegistrationBean<ABACRequestFilter> abacFilterRegistration(Repositories repos, EntityManager em){
			FilterRegistrationBean<ABACRequestFilter> registrationBean = new FilterRegistrationBean<>();

			registrationBean.setFilter(abacFilter(repos, em));
			registrationBean.addUrlPatterns("/*");

			return registrationBean;
		}

		@Bean
		public QueryAugmentingABACAspect abacAspect(EntityManager em, PlatformTransactionManager ptm) {
			return new QueryAugmentingABACAspect(em, ptm);
		}
	}

	public static class ABACRequestFilter implements Filter {

		private final Repositories repos;
		private final EntityManager em;

		public ABACRequestFilter(Repositories repos, EntityManager em) {
			this.repos = repos;
			this.em = em;
		}

		@Override
		public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
			HttpServletRequest request = (HttpServletRequest) servletRequest;

			String path = new UrlPathHelper().getLookupPathForRequest(request);
			String[] pathElements = path.split("/");
			RepositoryInformation ri = RepositoryUtils.findRepositoryInformation(repos, pathElements[1]);
			if (ri == null) {
				ri = RepositoryUtils.findRepositoryInformation(repos, pathElements[2]);
			}
			if (ri == null) {
				throw new IllegalStateException(format("Unable to resolve entity class: %s", path));
			}
			Class<?> entityClass = ri.getDomainType();

			EntityInformation ei = JpaEntityInformationSupport.getEntityInformation(entityClass, em);
			if (entityClass != null) {
				EntityContext.setCurrentEntityContext(ei);
			}

			String tenantID = request.getHeader("X-ABAC-Context");
			if (tenantID != null) {
				ABACContext.setCurrentAbacContext(tenantID);
			}

			filterChain.doFilter(servletRequest, servletResponse);

			ABACContext.clear();
			EntityContext.clear();
		}
	}
}
