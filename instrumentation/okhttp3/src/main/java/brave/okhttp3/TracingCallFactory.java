package brave.okhttp3;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This internally adds an interceptor which runs before others. That means any interceptors in
 * the input can access the current span via {@link Tracer#currentSpan()}
 */
// NOTE: this is a call factory vs a normal interceptor because the current span can otherwise get
// lost when there's a backlog.
public final class TracingCallFactory implements Call.Factory {

  public static TracingCallFactory create(HttpTracing httpTracing, OkHttpClient ok) {
    return new TracingCallFactory(httpTracing, ok);
  }

  final Tracer tracer;
  final HttpClientHandler<Request, Response> handler;
  final TraceContext.Injector<Request.Builder> injector;
  final OkHttpClient ok;

  TracingCallFactory(HttpTracing httpTracing, OkHttpClient ok) {
    this.tracer = httpTracing.tracing().tracer();
    this.handler = new HttpClientHandler<>(new HttpAdapter(), httpTracing.clientParser());
    this.injector = httpTracing.tracing().propagation().injector(Request.Builder::addHeader);
    this.ok = ok;
  }

  @Override public Call newCall(Request request) {
    Span span = tracer.nextSpan();
    OkHttpClient.Builder b = ok.newBuilder();
    b.interceptors().add(0, chain -> {
      Request.Builder builder = request.newBuilder();
      handler.handleSend(request, span);
      injector.inject(span.context(), builder);
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        return handler.handleReceive(chain.proceed(builder.build()), span);
      } catch (IOException | RuntimeException e) {
        handler.handleError(e, span);
        throw e;
      }
    }); // TODO: possibly add network interceptor and create a client span for each attempt
    return b.build().newCall(request);
  }

  static final class HttpAdapter extends brave.http.HttpAdapter<Request, Response> {
    @Override public String method(Request request) {
      return request.method();
    }

    @Override public String url(Request request) {
      return request.url().toString();
    }

    @Override public String requestHeader(Request request, String name) {
      return request.header(name);
    }

    @Override public Integer statusCode(Response response) {
      return response.code();
    }
  }
}
