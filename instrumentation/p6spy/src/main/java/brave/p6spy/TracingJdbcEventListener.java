package brave.p6spy;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import zipkin.Constants;
import zipkin.TraceKeys;

final class TracingJdbcEventListener extends SimpleJdbcEventListener {

  @Override public void onBeforeAnyExecute(StatementInformation info) {
    Tracer tracer = Tracing.currentTracer();
    if (tracer == null) return;

    Span span = tracer.nextSpan();
    // regardless of noop or not, set it in scope so that custom contexts can see it (like slf4j)
    if (!span.isNoop()) {
      String sql = info.getSql();
      span.kind(Span.Kind.CLIENT).name(sql.substring(0, sql.indexOf(' ')));
      span.tag(TraceKeys.SQL_QUERY, sql);
      remoteEndpoint(info.getConnectionInformation().getConnection(), span);
      span.start();
    }

    currentSpanInScope.set(tracer.withSpanInScope(span));
  }

  /**
   * There's no attribute namespace shared across request and response. Hence, we need to save off
   * a reference to the span in scope, so that we can close it in the response.
   */
  final ThreadLocal<Tracer.SpanInScope> currentSpanInScope = new ThreadLocal<>();

  @Override public void onAfterAnyExecute(StatementInformation info, long elapsed, SQLException e) {
    Tracer tracer = Tracing.currentTracer();
    if (tracer == null) return;

    Tracer.SpanInScope spanInScope = currentSpanInScope.get();
    if (spanInScope == null) return;
    Span span = tracer.currentSpan();
    spanInScope.close();

    if (e != null) {
      span.tag(Constants.ERROR, Integer.toString(e.getErrorCode()));
    }
    span.finish();
  }

  /**
   * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from {@code
   * jdbc:mysql://localhost:5555/isSampled}.
   */
  static void remoteEndpoint(Connection connection, Span span) {
    try {
      URI url = URI.create(connection.getMetaData().getURL().substring(5)); // strip "jdbc:"
      InetAddress address = Inet4Address.getByName(url.getHost());
      ClientHandler.remoteEndpoint(address, url.getPort(), span);
    } catch (Exception e) {
      // remote address is optional
    }
  }
}
