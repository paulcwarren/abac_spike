package com.example.demo;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.content.rest.config.ContentRestConfigurer;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.content.solr.AttributeProvider;
import org.springframework.content.solr.FilterQueryProvider;
import org.springframework.content.solr.SolrProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.geo.GeoModule;
import org.springframework.data.querydsl.ABACContext;
import org.springframework.data.querydsl.EnableAbac;
import org.springframework.data.querydsl.binding.XenitRepositoryRestMvcConfiguration;
import org.springframework.hateoas.mediatype.MessageResolver;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.mvc.RepresentationModelProcessorInvoker;
import org.springframework.web.context.annotation.RequestScope;

import com.fasterxml.jackson.databind.ObjectMapper;

import be.heydari.lib.converters.solr.SolrUtils;
import be.heydari.lib.expressions.Disjunction;

@SpringBootApplication
@EnableAbac
public class ABACSpike2Application {

	public static void main(String[] args) {
		SpringApplication.run(ABACSpike2Application.class, args);
	}

	@Configuration
    public static class Config extends XenitRepositoryRestMvcConfiguration {

	    public Config(ApplicationContext context, ObjectFactory<ConversionService> conversionService, Optional<LinkRelationProvider> relProvider, Optional<CurieProvider> curieProvider, Optional<HalConfiguration> halConfiguration, ObjectProvider<ObjectMapper> objectMapper, ObjectProvider<RepresentationModelProcessorInvoker> invoker, MessageResolver resolver, GeoModule geoModule) {
            super(context, conversionService, relProvider, curieProvider, halConfiguration, objectMapper, invoker, resolver, geoModule);
        }

	    @Bean
	    public SolrClient solrClient(SolrProperties props) {
	        props.setUser("solr");
	        props.setPassword("SolrRocks");
	        return new HttpSolrClient.Builder(props.getUrl()).build();
	    }

	    @Bean
	    public AttributeProvider<AccountState> syncer() {
	        return new AttributeProvider<AccountState>() {

	            @Override
	            public Map<String, String> synchronize(AccountState entity) {
	                Map<String,String> attrs = new HashMap<>();
	                attrs.put("broker.id", entity.getBroker().getId().toString());
	                return attrs;
	            }
	        };
	    }

	    @Bean
	    @RequestScope
	    public FilterQueryProvider fqProvider() {
	        return new FilterQueryProvider() {

	            private @Autowired HttpServletRequest request;

	            @Override
	            public String[] filterQueries(Class<?> entity) {
	                Disjunction abacContext = ABACContext.getCurrentAbacContext();
	                return SolrUtils.from(abacContext,"");
	            }
	        };
	    }

        @Bean
        public ContentRestConfigurer restConfigurer() {
            return new ContentRestConfigurer() {
                @Override
                public void configure(RestConfiguration config) {
                    config.setBaseUri(URI.create("/content"));
                }
            };
        }
   }
}
