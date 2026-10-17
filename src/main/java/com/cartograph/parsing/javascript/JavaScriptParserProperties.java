package com.cartograph.parsing.javascript;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration for enabling or disabling the JavaScript and TypeScript parser. */
@ConfigurationProperties(prefix = "cartograph.parser.javascript")
public record JavaScriptParserProperties(
        @DefaultValue("true") boolean enabled) {
    @ConstructorBinding
    public JavaScriptParserProperties {}

    public JavaScriptParserProperties() {
        this(true);
    }
}
