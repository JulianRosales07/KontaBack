package com.app.accounting.security.payload.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DecryptedHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] bodyBytes;

    public DecryptedHttpServletRequestWrapper(HttpServletRequest request, byte[] bodyBytes) {
        super(request);
        this.bodyBytes = bodyBytes != null ? bodyBytes : new byte[0];
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bodyBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return bodyBytes.length;
    }

    @Override
    public long getContentLengthLong() {
        return bodyBytes.length;
    }
}
