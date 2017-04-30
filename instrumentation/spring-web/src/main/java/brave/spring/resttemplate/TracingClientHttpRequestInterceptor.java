package brave.spring.resttemplate;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public final class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  public static TracingClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingClientHttpRequestInterceptor(httpTracing);
  }

  final Tracer tracer;
  final ClientHandler<HttpRequest, ClientHttpResponse> handler;
  final TraceContext.Injector<HttpHeaders> injector;

  TracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = new HttpClientHandler<>(new HttpAdapter(), httpTracing.clientParser());
    injector = httpTracing.tracing().propagation().injector(HttpHeaders::set);
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    Span span = tracer.nextSpan();
    handler.handleSend(request, span);
    injector.inject(span.context(), request.getHeaders());
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return handler.handleReceive(execution.execute(request, body), span);
    } catch (IOException | RuntimeException e) {
      handler.handleError(e, span);
      throw e;
    }
  }

  static final class HttpAdapter extends brave.http.HttpAdapter<HttpRequest, ClientHttpResponse> {
    @Override public String method(HttpRequest request) {
      return request.getMethod().name();
    }

    @Override public String url(HttpRequest request) {
      return request.getURI().toString();
    }

    @Override public String requestHeader(HttpRequest request, String name) {
      Object result = request.getHeaders().getFirst(name);
      return result != null ? result.toString() : null;
    }

    @Override public Integer statusCode(ClientHttpResponse response) {
      try {
        return response.getRawStatusCode();
      } catch (IOException e) {
        return null;
      }
    }
  }
}
