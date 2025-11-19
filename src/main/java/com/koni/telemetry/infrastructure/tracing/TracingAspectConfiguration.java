package com.koni.telemetry.infrastructure.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.DefaultNewSpanParser;
import io.micrometer.tracing.annotation.ImperativeMethodInvocationProcessor;
import io.micrometer.tracing.annotation.MethodInvocationProcessor;
import io.micrometer.tracing.annotation.NewSpanParser;
import io.micrometer.tracing.annotation.SpanAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for enabling tracing aspects.
 * This enables @NewSpan and @ContinueSpan annotations to work properly.
 */
@Configuration
@EnableAspectJAutoProxy
public class TracingAspectConfiguration {
    
    /**
     * Creates the SpanAspect bean that processes @NewSpan and @ContinueSpan annotations.
     * This aspect intercepts method calls and creates/continues spans accordingly.
     */
    @Bean
    public SpanAspect spanAspect(MethodInvocationProcessor methodInvocationProcessor) {
        return new SpanAspect(methodInvocationProcessor);
    }
    
    /**
     * Creates the NewSpanParser that parses @NewSpan annotations.
     */
    @Bean
    public NewSpanParser newSpanParser() {
        return new DefaultNewSpanParser();
    }
    
    /**
     * Creates the MethodInvocationProcessor that handles the actual span creation logic.
     */
    @Bean
    public MethodInvocationProcessor methodInvocationProcessor(
            NewSpanParser newSpanParser,
            Tracer tracer) {
        return new ImperativeMethodInvocationProcessor(newSpanParser, tracer);
    }
}
