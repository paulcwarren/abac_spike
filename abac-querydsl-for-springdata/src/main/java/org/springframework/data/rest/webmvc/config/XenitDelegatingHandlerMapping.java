package org.springframework.data.rest.webmvc.config;

import java.util.List;

import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

public class XenitDelegatingHandlerMapping extends DelegatingHandlerMapping {

    public XenitDelegatingHandlerMapping(List<HandlerMapping> delegates, PathPatternParser parser) {
        super(delegates, parser);
    }

}
