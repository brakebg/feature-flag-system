package com.example.featureflags.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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
      problems.write(
          request,
          response,
          HttpStatus.PAYLOAD_TOO_LARGE,
          "payload-too-large",
          "Payload too large",
          "Request body is larger than 65536 bytes");
      return;
    }
    chain.doFilter(new Limited(request), response);
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
