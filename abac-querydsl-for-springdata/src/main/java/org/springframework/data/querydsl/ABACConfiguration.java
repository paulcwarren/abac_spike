package org.springframework.data.querydsl;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Base64;

import javax.persistence.EntityManager;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.querydsl.binding.QuerydslBindingsFactory;
import org.springframework.data.querydsl.binding.XenitQuerydslAwareRootResourceInformationHandlerMethodArgumentResolver;
import org.springframework.data.querydsl.binding.XenitQuerydslPredicateBuilder;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.webmvc.config.ResourceMetadataHandlerMethodArgumentResolver;
import org.springframework.data.rest.webmvc.config.RootResourceInformationHandlerMethodArgumentResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.util.UrlPathHelper;

import be.heydari.lib.converters.protobuf.ProtobufUtils;
import be.heydari.lib.converters.protobuf.generated.PDisjunction;
import be.heydari.lib.expressions.Disjunction;
import internal.org.springframework.content.rest.utils.RepositoryUtils;

@Configuration
public class ABACConfiguration {

    private static final String QUERYDSL_REPO_REQUEST_ARGUMENT_RESOLVER = "querydslRepoRequestArgumentResolver";
    private static final String REPO_REQUEST_ARGUMENT_RESOLVER = "repoRequestArgumentResolver";

    @Bean
    public ABACExceptionHandler exceptionHandler() {
        return new ABACExceptionHandler();
    }

    @Bean
    public ABACRequestFilter abacFilter(Repositories repos, EntityManager em, PlatformTransactionManager tm) {
        return new ABACRequestFilter(repos, em, tm);
    }

    @Bean
    public FilterRegistrationBean<ABACRequestFilter> abacFilterRegistration(Repositories repos, EntityManager em, PlatformTransactionManager tm) {
        FilterRegistrationBean<ABACRequestFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(abacFilter(repos, em, tm));

        // TODO: these URIs need to be extracted
        registrationBean.addUrlPatterns("/accountStates/*");
        registrationBean.addUrlPatterns("/content/*");

        return registrationBean;
    }

    /////////////////////////////////////////////////////
    // QueryDSL repo request argument resolver activation

    @Bean
    public BeanFactoryPostProcessor beanFactoryPostProcessor() {

        return new BeanFactoryPostProcessor() {

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
                    throws BeansException {

                if (beanFactory.containsBean(REPO_REQUEST_ARGUMENT_RESOLVER) == false) {
                    throw new IllegalStateException(format("%s bean expected", REPO_REQUEST_ARGUMENT_RESOLVER));
                }

                ((DefaultListableBeanFactory) beanFactory).removeBeanDefinition(REPO_REQUEST_ARGUMENT_RESOLVER);
                ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(REPO_REQUEST_ARGUMENT_RESOLVER, beanFactory.getBeanDefinition(QUERYDSL_REPO_REQUEST_ARGUMENT_RESOLVER));
                ((DefaultListableBeanFactory) beanFactory).removeBeanDefinition(QUERYDSL_REPO_REQUEST_ARGUMENT_RESOLVER);
            }
        };
    }

    @Bean
    public RootResourceInformationHandlerMethodArgumentResolver querydslRepoRequestArgumentResolver(Repositories repositories, ResourceMetadataHandlerMethodArgumentResolver resourceMetadataHandlerMethodArgumentResolver, RepositoryInvokerFactory repositoryInvokerFactory, ApplicationContext applicationContext, ConversionService defaultConversionService) {

        QuerydslBindingsFactory factory = applicationContext.getBean(QuerydslBindingsFactory.class);
        XenitQuerydslPredicateBuilder predicateBuilder = new XenitQuerydslPredicateBuilder(defaultConversionService, factory.getEntityPathResolver());

        return new XenitQuerydslAwareRootResourceInformationHandlerMethodArgumentResolver(repositories,
                repositoryInvokerFactory, resourceMetadataHandlerMethodArgumentResolver, predicateBuilder, factory);
    }

    // QueryDSL repo request argument resolver activation
    /////////////////////////////////////////////////////

    public static class ABACRequestFilter implements Filter {

        private final Repositories repos;
        private final EntityManager em;
        private final PlatformTransactionManager tm;

        public ABACRequestFilter(Repositories repos, EntityManager em, PlatformTransactionManager tm) {
            this.repos = repos;
            this.em = em;
            this.tm = tm;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException,
                ServletException {

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

            EntityManagerContext.setCurrentEntityContext(em, tm);

            // Emad
            String abacContext = request.getHeader("X-ABAC-Context");
            if (abacContext != null) {
                byte[] abacContextProtobytes = Base64.getDecoder().decode(abacContext);
                PDisjunction pDisjunction = PDisjunction.newBuilder().mergeFrom(abacContextProtobytes).build();
                Disjunction disjunction = ProtobufUtils.to(pDisjunction, "");
                ABACContext.setCurrentAbacContext(disjunction);
            }

            filterChain.doFilter(servletRequest, servletResponse);

            ABACContext.clear();
            EntityContext.clear();
        }
    }
}
