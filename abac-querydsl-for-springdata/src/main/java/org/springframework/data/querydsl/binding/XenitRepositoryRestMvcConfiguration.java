package org.springframework.data.querydsl.binding;

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
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.StringToLdapNameConverter;
import org.springframework.data.rest.core.UriToEntityConverter;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurerDelegate;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.data.rest.webmvc.config.ResourceMetadataHandlerMethodArgumentResolver;
import org.springframework.data.rest.webmvc.config.RootResourceInformationHandlerMethodArgumentResolver;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.hateoas.mediatype.MessageResolver;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.mvc.RepresentationModelProcessorInvoker;

import com.fasterxml.jackson.databind.ObjectMapper;

public class XenitRepositoryRestMvcConfiguration extends RepositoryRestMvcConfiguration {

    ConversionService defaultConversionService;

    @Autowired ApplicationContext applicationContext;

    public XenitRepositoryRestMvcConfiguration(ApplicationContext context, ObjectFactory<ConversionService> conversionService, Optional<LinkRelationProvider> relProvider, Optional<CurieProvider> curieProvider, Optional<HalConfiguration> halConfiguration, ObjectProvider<ObjectMapper> objectMapper, ObjectProvider<RepresentationModelProcessorInvoker> invoker, MessageResolver resolver, GeoModule geoModule) {
        super(context, conversionService, relProvider, curieProvider, halConfiguration, objectMapper, invoker, resolver, geoModule);

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
}
