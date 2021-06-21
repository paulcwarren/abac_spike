package org.springframework.data.rest.webmvc.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.geo.GeoModule;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.querydsl.QuerydslUtils;
import org.springframework.data.querydsl.binding.QuerydslBindingsFactory;
import org.springframework.data.querydsl.binding.XenitQuerydslAwareRootResourceInformationHandlerMethodArgumentResolver;
import org.springframework.data.querydsl.binding.XenitQuerydslPredicateBuilder;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.StringToLdapNameConverter;
import org.springframework.data.rest.core.UriToEntityConverter;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.RepositoryResourceMappings;
import org.springframework.data.rest.webmvc.BasePathAwareHandlerMapping;
import org.springframework.data.rest.webmvc.RepositoryRestHandlerMapping;
import org.springframework.data.rest.webmvc.support.JpaHelper;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.hateoas.mediatype.MessageResolver;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.mvc.RepresentationModelProcessorInvoker;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fasterxml.jackson.databind.ObjectMapper;

public class XenitRepositoryRestMvcConfiguration extends RepositoryRestMvcConfiguration {

    ConversionService defaultConversionService;

    @Autowired ApplicationContext applicationContext;

    private ObjectProvider<PathPatternParser> parser;

    public XenitRepositoryRestMvcConfiguration(
            ApplicationContext context,
            ObjectFactory<ConversionService> conversionService,
            ObjectProvider<LinkRelationProvider> relProvider,
            ObjectProvider<CurieProvider> curieProvider,
            ObjectProvider<HalConfiguration> halConfiguration,
            ObjectProvider<ObjectMapper> objectMapper,
            ObjectProvider<RepresentationModelProcessorInvoker> invoker,
            ObjectProvider<MessageResolver> resolver,
            ObjectProvider<GeoModule> geoModule, //
            ObjectProvider<PathPatternParser> parser) {
        super(context,
              conversionService,
              relProvider,
              curieProvider,
              halConfiguration,
              objectMapper,
              invoker,
              resolver,
              geoModule,
              parser);

        this.parser = parser;
        this.defaultConversionService = new DefaultFormattingConversionService();
    }

    @Override
    @Bean
    @Qualifier
    public DefaultFormattingConversionService defaultConversionService(PersistentEntities persistentEntities,
            RepositoryInvokerFactory repositoryInvokerFactory, Repositories repositories,
            RepositoryRestConfigurerDelegate configurerDelegate) {

        DefaultFormattingConversionService conversionService = (DefaultFormattingConversionService) defaultConversionService;

        // Add Spring Data Commons formatters
        conversionService
                .addConverter(new UriToEntityConverter(persistentEntities, repositoryInvokerFactory, repositories));
        conversionService.addConverter(StringToLdapNameConverter.INSTANCE);
        addFormatters(conversionService);

        configurerDelegate.configureConversionService(conversionService);

        return conversionService;
    }

    @Override
    public RootResourceInformationHandlerMethodArgumentResolver repoRequestArgumentResolver(Repositories repositories, ResourceMetadataHandlerMethodArgumentResolver resourceMetadataHandlerMethodArgumentResolver, RepositoryInvokerFactory repositoryInvokerFactory) {

        if (QuerydslUtils.QUERY_DSL_PRESENT) {

            QuerydslBindingsFactory factory = applicationContext.getBean(QuerydslBindingsFactory.class);
            XenitQuerydslPredicateBuilder predicateBuilder = new XenitQuerydslPredicateBuilder(defaultConversionService, factory.getEntityPathResolver());

            return new XenitQuerydslAwareRootResourceInformationHandlerMethodArgumentResolver(repositories,
                    repositoryInvokerFactory, resourceMetadataHandlerMethodArgumentResolver, predicateBuilder, factory);
        }

        return new RootResourceInformationHandlerMethodArgumentResolver(repositories, repositoryInvokerFactory,
                resourceMetadataHandlerMethodArgumentResolver);
    }


    /*
     * We override restHandlerMapping and return our own XenitDelegatingHandlerMapping because
     * DelegatingHandlerMapping was made package-private.
     *
     * (see https://github.com/spring-projects/spring-data-rest/issues/1981)
     */
    @Override
    public XenitDelegatingHandlerMapping restHandlerMapping(Repositories repositories, RepositoryResourceMappings resourceMappings, Optional<JpaHelper> jpaHelper, RepositoryRestConfiguration repositoryRestConfiguration, CorsConfigurationAware corsRestConfiguration) {

        Map<String, CorsConfiguration> corsConfigurations = corsRestConfiguration.getCorsConfigurations();
        PathPatternParser parser = this.parser.getIfAvailable();

        RepositoryRestHandlerMapping repositoryMapping = new RepositoryRestHandlerMapping(resourceMappings,
                repositoryRestConfiguration, repositories);
        repositoryMapping.setJpaHelper(jpaHelper.orElse(null));
        repositoryMapping.setApplicationContext(applicationContext);
        repositoryMapping.setCorsConfigurations(corsConfigurations);
        repositoryMapping.setPatternParser(parser);
        repositoryMapping.afterPropertiesSet();

        BasePathAwareHandlerMapping basePathMapping = new BasePathAwareHandlerMapping(repositoryRestConfiguration);
        basePathMapping.setApplicationContext(applicationContext);
        basePathMapping.setCorsConfigurations(corsConfigurations);
        basePathMapping.setPatternParser(parser);
        basePathMapping.afterPropertiesSet();

        List<HandlerMapping> mappings = new ArrayList<>();
        mappings.add(basePathMapping);
        mappings.add(repositoryMapping);

        return new XenitDelegatingHandlerMapping(mappings, parser);
    }
}
