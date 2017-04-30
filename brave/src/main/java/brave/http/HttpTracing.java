package brave.http;

import brave.Tracing;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class HttpTracing {
  public static HttpTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new AutoValue_HttpTracing.Builder()
        .tracing(tracing)
        .clientParser(new HttpClientParser())
        .serverParser(new HttpServerParser());
  }

  public abstract Tracing tracing();

  public abstract HttpClientParser clientParser();

  public abstract HttpServerParser serverParser();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder tracing(Tracing tracing);

    public abstract Builder clientParser(HttpClientParser clientParser);

    public abstract Builder serverParser(HttpServerParser serverParser);

    public abstract HttpTracing build();

    Builder() {
    }
  }

  HttpTracing() {
  }
}
