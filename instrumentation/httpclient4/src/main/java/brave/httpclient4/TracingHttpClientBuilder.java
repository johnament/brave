package brave.httpclient4;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;

import static brave.ClientHandler.remoteEndpoint;

public final class TracingHttpClientBuilder extends HttpClientBuilder {

  public static TracingHttpClientBuilder create(HttpTracing httpTracing) {
    return new TracingHttpClientBuilder(httpTracing);
  }

  final Tracer tracer;
  final TraceContext.Injector<HttpMessage> injector;
  final HttpClientHandler<HttpRequestWrapper, CloseableHttpResponse> handler;

  TracingHttpClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    this.tracer = httpTracing.tracing().tracer();
    this.handler = new HttpClientHandler<>(new HttpAdapter(), httpTracing.clientParser());
    this.injector = httpTracing.tracing().propagation().injector(HttpMessage::setHeader);
  }

  /**
   * protocol exec is the second in the execution chain, so is invoked before a request is
   * provisioned. We provision and scope a span here, so that application interceptors can see
   * it via {@link Tracer#currentSpan()}.
   */
  @Override protected ClientExecChain decorateProtocolExec(ClientExecChain exec) {
    return (route, request, context, execAware) -> {
      Span next = tracer.nextSpan();
      context.setAttribute(SpanInScope.class.getName(), tracer.withSpanInScope(next));
      try {
        return exec.execute(route, request, context, execAware);
      } catch (IOException | HttpException | RuntimeException e) {
        context.getAttribute(SpanInScope.class.getName(), SpanInScope.class).close();
        throw e;
      }
    };
  }

  /**
   * main exec is the first in the execution chain, so last to execute. This creates a concrete http
   * request, so this is where the timing in the span occurs.
   *
   * <p>This ends the span (and scoping of it) created by {@link #decorateMainExec(ClientExecChain)}.
   */
  // TODO: add a redirect test and make sure this doesn't cause issues
  @Override protected ClientExecChain decorateMainExec(ClientExecChain exec) {
    return (route, request, context, execAware) -> {
      Span span = tracer.currentSpan();
      try {
        handler.handleSend(request, span);
        injector.inject(span.context(), request);

        CloseableHttpResponse response = exec.execute(route, request, context, execAware);

        HttpHost host = context.getTargetHost();
        if (host != null) remoteEndpoint(host.getAddress(), host.getPort(), span);

        return handler.handleReceive(response, span);
      } catch (IOException | HttpException | RuntimeException e) {
        handler.handleError(e, span);
        throw e;
      } finally {
        context.getAttribute(SpanInScope.class.getName(), SpanInScope.class).close();
      }
    };
  }

  static final class HttpAdapter
      extends brave.http.HttpAdapter<HttpRequestWrapper, CloseableHttpResponse> {
    @Override public String method(HttpRequestWrapper request) {
      return request.getRequestLine().getMethod();
    }

    @Override public String url(HttpRequestWrapper request) {
      return request.getRequestLine().getUri();
    }

    @Override public String requestHeader(HttpRequestWrapper request, String name) {
      Header result = request.getFirstHeader(name);
      return result != null ? result.getValue() : null;
    }

    @Override public Integer statusCode(CloseableHttpResponse response) {
      return response.getStatusLine().getStatusCode();
    }
  }
}
