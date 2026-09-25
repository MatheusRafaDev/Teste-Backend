package com.deltaglobal.testebackend.config;

import com.deltaglobal.testebackend.domain.LogAuditoria;
import com.deltaglobal.testebackend.repository.LogAuditoriaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
public class LogAuditoriaFilter extends OncePerRequestFilter {

    private final LogAuditoriaRepository logAuditoriaRepository;

    public LogAuditoriaFilter(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().contains("/logs") || request.getMethod().equals("OPTIONS")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - start;

            String requestBody = new String(requestWrapper.getContentAsByteArray());
            String responseBody = new String(responseWrapper.getContentAsByteArray());

            // Apenas recorta os logs muito grandes
            if (requestBody.length() > 5000) requestBody = requestBody.substring(0, 5000) + "...";
            if (responseBody.length() > 5000) responseBody = responseBody.substring(0, 5000) + "...";

            LogAuditoria logApp = new LogAuditoria();
            logApp.setMetodo(request.getMethod());
            logApp.setEndpoint(request.getRequestURI());
            logApp.setStatusHttp(response.getStatus());
            logApp.setPayloadRequisicao(requestBody);
            logApp.setPayloadResposta(responseBody);
            logApp.setTempoExecucaoMs(duration);

            try {
                logAuditoriaRepository.save(logApp);
            } catch (Exception e) {
                // Ignore errors saving log
            }

            responseWrapper.copyBodyToResponse();
        }
    }
}
