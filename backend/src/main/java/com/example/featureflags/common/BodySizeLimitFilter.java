package com.example.featureflags.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spec 9.1, 9.2: a request body above 65,536 bytes gets 413 {@code payload-too-large}. A declared
 * {@code Content-Length} is checked at once; a body without one is counted while it is read.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class BodySizeLimitFilter extends OncePerRequestFilter {

  public static final long MAX_BYTES = 65_536;
  private final ProblemWriter problems;

  public BodySizeLimitFilter(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      tooLarge(request, response);
      return;
    }
    if (request.getContentLengthLong() < 0 && isForm(request)) {
      // SF-C1: the container parses form bodies itself, past the counting stream below, and
      // drops a too-large one silently. Read a form of unknown length here and parse it.
      byte[] body;
      try {
        body = new Counting(request.getInputStream()).readAllBytes();
      } catch (PayloadTooLargeException e) {
        tooLarge(request, response);
        return;
      }
      chain.doFilter(new FormBody(request, body), response);
      return;
    }
    chain.doFilter(new Limited(request), response);
  }

  private void tooLarge(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    problems.write(
        request,
        response,
        HttpStatus.PAYLOAD_TOO_LARGE,
        "payload-too-large",
        "Payload too large",
        "Request body is larger than 65536 bytes");
  }

  private static boolean isForm(HttpServletRequest request) {
    String type = request.getContentType();
    return type != null
        && type.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
  }

  /** A form request whose body was already read: parameters from the query string, then body. */
  private static final class FormBody extends HttpServletRequestWrapper {
    private final byte[] body;
    private final Map<String, String[]> parameters;

    FormBody(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
      Charset charset =
          request.getCharacterEncoding() == null
              ? StandardCharsets.UTF_8
              : Charset.forName(request.getCharacterEncoding());
      Map<String, List<String>> values = new LinkedHashMap<>();
      parse(request.getQueryString(), charset, values);
      parse(new String(body, charset), charset, values);
      Map<String, String[]> map = new LinkedHashMap<>();
      values.forEach((k, v) -> map.put(k, v.toArray(String[]::new)));
      this.parameters = Collections.unmodifiableMap(map);
    }

    private static void parse(String text, Charset charset, Map<String, List<String>> into) {
      if (text == null || text.isEmpty()) {
        return;
      }
      for (String pair : text.split("&")) {
        if (pair.isEmpty()) {
          continue;
        }
        int eq = pair.indexOf('=');
        String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), charset);
        String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), charset);
        into.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
      }
    }

    @Override
    public String getParameter(String name) {
      String[] v = parameters.get(name);
      return v == null ? null : v[0];
    }

    @Override
    public Map<String, String[]> getParameterMap() {
      return parameters;
    }

    @Override
    public Enumeration<String> getParameterNames() {
      return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
      String[] v = parameters.get(name);
      return v == null ? null : v.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("blocking only");
        }
      };
    }
  }

  private static final class Limited extends HttpServletRequestWrapper {
    private ServletInputStream stream;

    Limited(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new Counting(super.getInputStream());
      }
      return stream;
    }
  }

  private static final class Counting extends ServletInputStream {
    private final ServletInputStream in;
    private long count;

    Counting(ServletInputStream in) {
      this.in = in;
    }

    private void add(long n) {
      if (n > 0) {
        count += n;
        if (count > MAX_BYTES) {
          throw new PayloadTooLargeException();
        }
      }
    }

    @Override
    public int read() throws IOException {
      int b = in.read();
      add(b < 0 ? 0 : 1);
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = in.read(b, off, len);
      add(n);
      return n;
    }

    @Override
    public boolean isFinished() {
      return in.isFinished();
    }

    @Override
    public boolean isReady() {
      return in.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      in.setReadListener(listener);
    }
  }
}
