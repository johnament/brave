package brave.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/** This interceptor traces outbound calls */
public final class TracingClientInterceptor implements ClientInterceptor {

  /** Creates a tracing interceptor with defaults. Use {@link #newBuilder(Tracing)} to customize. */
  public static TracingClientInterceptor create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    ClientHandler handler = new ClientHandler();

    Builder(Tracing tracing) { // intentionally hidden
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public Builder handler(ClientHandler handler) {
      if (handler == null) throw new NullPointerException("handler == null");
      this.handler = handler;
      return this;
    }

    public TracingClientInterceptor build() {
      return new TracingClientInterceptor(this);
    }
  }

  final Tracer tracer;
  final ClientHandler handler;
  final TraceContext.Injector<Metadata> injector;

  TracingClientInterceptor(Builder builder) {
    tracer = builder.tracing.tracer();
    handler = builder.handler;
    injector = builder.tracing.propagationFactory().create(AsciiMetadataKeyFactory.INSTANCE)
        .injector(new Propagation.Setter<Metadata, Metadata.Key<String>>() { // retrolambda no like
          @Override public void put(Metadata metadata, Metadata.Key<String> key, String value) {
            metadata.put(key, value);
          }
        });
  }

  /**
   * This sets as span in scope both for the interception and for the start of the request. It does
   * not set a span in scope during the response listener as it is unexpected it would be used at
   * that fine granularity. If users want access to the span in a response listener, they will need
   * to wrap the executor with one that's aware of the current context.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions,
      final Channel next) {
    Span span = tracer.nextSpan();
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          handler.handleSend(method, span);
          injector.inject(span.context(), headers);
          try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override public void onClose(Status status, Metadata trailers) {
                handler.handleReceive(status, span);
                super.onClose(status, trailers);
              }
            }, headers);
          }
        }
      };
    }
  }
}
