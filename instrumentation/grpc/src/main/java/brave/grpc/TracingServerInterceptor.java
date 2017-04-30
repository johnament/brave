package brave.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class TracingServerInterceptor implements ServerInterceptor {

  /** Creates a tracing interceptor with defaults. Use {@link #newBuilder(Tracing)} to customize. */
  public static TracingServerInterceptor create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static TracingServerInterceptor.Builder newBuilder(Tracing tracing) {
    return new TracingServerInterceptor.Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    ServerHandler handler = new ServerHandler();

    Builder(Tracing tracing) { // intentionally hidden
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public Builder handler(ServerHandler handler) {
      if (handler == null) throw new NullPointerException("handler == null");
      this.handler = handler;
      return this;
    }

    public TracingServerInterceptor build() {
      return new TracingServerInterceptor(this);
    }
  }

  final Tracer tracer;
  final ServerHandler serverHandler;
  final TraceContext.Extractor<Metadata> extractor;

  TracingServerInterceptor(Builder builder) {
    tracer = builder.tracing.tracer();
    serverHandler = builder.handler;
    extractor = builder.tracing.propagationFactory().create(AsciiMetadataKeyFactory.INSTANCE)
        .extractor(new Propagation.Getter<Metadata, Metadata.Key<String>>() { // retrolambda no like
          @Override public String get(Metadata metadata, Metadata.Key<String> key) {
            return metadata.get(key);
          }
        });
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call,
      final Metadata requestHeaders, final ServerCallHandler<ReqT, RespT> next) {
    Span span = tracer.nextSpan(extractor, requestHeaders);
    return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
      @Override
      public void request(int numMessages) {
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
          serverHandler.handleReceive(call.getMethodDescriptor(), span);
          super.request(numMessages);
        } catch (RuntimeException e) {
          throw serverHandler.handleError(e, span);
        }
      }

      @Override
      public void close(Status status, Metadata trailers) {
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
          serverHandler.handleSend(status, span);
          super.close(status, trailers);
        } catch (RuntimeException e) {
          throw serverHandler.handleError(e, span);
        }
      }
    }, requestHeaders);
  }
}
