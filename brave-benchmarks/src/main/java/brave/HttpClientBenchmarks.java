package brave;

import brave.http.HttpTracing;
import brave.httpclient4.TracingHttpClientBuilder;
import io.undertow.Undertow;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import zipkin.reporter.Reporter;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class HttpClientBenchmarks {
  Undertow server;
  CloseableHttpClient client;
  CloseableHttpClient tracedClient;
  HttpGet get;

  @Setup(Level.Trial) public void createSender() throws Exception {
    server = Undertow.builder()
        .addHttpListener(0, "127.0.0.1")
        .setHandler(exchange -> exchange.setStatusCode(202).endExchange()).build();
    server.start();

    get = new HttpGet("http://127.0.0.1:" + ((InetSocketAddress) server.getListenerInfo()
        .get(0)
        .getAddress()).getPort()
    );

    client = HttpClients.custom().disableAutomaticRetries().build();
    tracedClient = TracingHttpClientBuilder.create(HttpTracing.create(
        Tracing.newBuilder().reporter(Reporter.NOOP).build()
    )).disableAutomaticRetries().build();
  }

  @TearDown(Level.Trial) public void close() throws IOException {
    tracedClient.close();
    client.close();
    server.stop();
  }

  @Benchmark public void client_get() throws IOException {
    client.execute(get).close();
  }

  @Benchmark public void tracedClient_get() throws IOException {
    tracedClient.execute(get).close();
  }
}
