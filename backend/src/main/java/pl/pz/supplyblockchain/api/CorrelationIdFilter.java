package pl.pz.supplyblockchain.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = "correlationId";
    public static final String HEADER = "X-Correlation-ID";
    private static final Pattern CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String value = request.getHeader(HEADER);
        if (value == null || !CORRELATION_ID.matcher(value).matches()) {
            value = UUID.randomUUID().toString();
        }
        request.setAttribute(ATTRIBUTE, value);
        response.setHeader(HEADER, value);
        filterChain.doFilter(request, response);
    }
}
