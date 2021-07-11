package com.example.demo;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.client.solrj.SolrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.content.rest.config.ContentRestConfigurer;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.content.solr.AttributeProvider;
import org.springframework.content.solr.FilterQueryProvider;
import org.springframework.content.solr.SolrProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.querydsl.ABACContext;
import org.springframework.data.querydsl.EnableAbac;
import org.springframework.web.context.annotation.RequestScope;

import com.example.demo.support.OPATestContainer;
import com.example.demo.support.SolrTestContainer;

import be.heydari.lib.converters.solr.SolrUtils;
import be.heydari.lib.expressions.Disjunction;

@SpringBootApplication
@EnableAbac
public class ABACSpike2Application {

	public static void main(String[] args) {
		SpringApplication.run(ABACSpike2Application.class, args);
	}

	@Configuration
    public static class Config {

	    @Bean
	    public SolrClient solrClient(SolrProperties props) {
	        props.setUser("solr");
	        props.setPassword("SolrRocks");
	        return SolrTestContainer.getSolrClient();
	    }

	    @Bean
	    public String opaUrl() {
	        return OPATestContainer.opaURL();
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
