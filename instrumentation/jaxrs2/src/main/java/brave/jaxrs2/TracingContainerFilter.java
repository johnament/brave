package brave.jaxrs2;

import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.lang.annotation.Annotation;
import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import zipkin.Constants;

import static javax.ws.rs.RuntimeType.SERVER;

@Provider
@Priority(0) // to make the span in scope visible to other filters
@ConstrainedTo(SERVER)
public class TracingContainerFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static TracingContainerFilter create(HttpTracing httpTracing) {
    return new TracingContainerFilter(httpTracing);
  }

  final Tracer tracer;
  final ServerHandler<ContainerRequestContext, ContainerResponseContext> handler;
  final TraceContext.Extractor<ContainerRequestContext> extractor;

  TracingContainerFilter(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = new HttpServerHandler<>(new HttpAdapter(), httpTracing.serverParser());
    extractor = httpTracing.tracing().propagation()
        .extractor(ContainerRequestContext::getHeaderString);
  }

  /** Needed to determine if {@link #isAsyncResponse(ResourceInfo)} */
  @Context ResourceInfo resourceInfo;

  @Override public void filter(ContainerRequestContext context) {
    Span span = startSpan(context);
    if (isAsyncResponse(resourceInfo)) {
      context.setProperty(Span.class.getName(), span);
    } else {
      context.setProperty(SpanInScope.class.getName(), tracer.withSpanInScope(span));
    }
  }

  private Span startSpan(ContainerRequestContext context) {
    Span span = tracer.nextSpan(extractor, context);
    handler.handleReceive(context, span);
    return span;
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    Span span = (Span) request.getProperty(Span.class.getName());
    SpanInScope spanInScope = (SpanInScope) request.getProperty(SpanInScope.class.getName());
    if (span != null) { // asynchronous response
    } else if (spanInScope != null) { // synchronous response
      span = tracer.currentSpan();
      spanInScope.close();
    } else if (response.getStatus() == 404) {
      span = startSpan(request);
    } else {
      return; // unknown state
    }

    Response.StatusType statusInfo = response.getStatusInfo();
    if (statusInfo.getFamily() == Response.Status.Family.SERVER_ERROR) {
      span.tag(Constants.ERROR, statusInfo.getReasonPhrase());
    }
    handler.handleSend(response, span);
  }

  // TODO: add benchmark and cache if slow
  static boolean isAsyncResponse(ResourceInfo resourceInfo) {
    for (Annotation[] annotations : resourceInfo.getResourceMethod().getParameterAnnotations()) {
      for (Annotation annotation : annotations) {
        if (annotation.annotationType().equals(Suspended.class)) {
          return true;
        }
      }
    }
    return false;
  }

  static final class HttpAdapter
      extends brave.http.HttpAdapter<ContainerRequestContext, ContainerResponseContext> {
    @Override public String method(ContainerRequestContext request) {
      return request.getMethod();
    }

    @Nullable public String path(ContainerRequestContext request) {
      return request.getUriInfo().getRequestUri().getPath();
    }

    @Override public String url(ContainerRequestContext request) {
      return request.getUriInfo().getRequestUri().toString();
    }

    @Override public String requestHeader(ContainerRequestContext request, String name) {
      return request.getHeaderString(name);
    }

    @Override public Integer statusCode(ContainerResponseContext response) {
      return response.getStatus();
    }
  }
}
