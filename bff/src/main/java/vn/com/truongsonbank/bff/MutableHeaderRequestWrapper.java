package vn.com.truongsonbank.bff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MutableHeaderRequestWrapper extends HttpServletRequestWrapper {
    private final Map<String, String> headers = new LinkedHashMap<>();

    MutableHeaderRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    void putHeader(String name, String value) {
        headers.put(name, value == null ? "" : value);
    }

    @Override
    public String getHeader(String name) {
        String value = headers.get(name);
        return value == null ? super.getHeader(name) : value;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = headers.get(name);
        return value == null ? super.getHeaders(name) : Collections.enumeration(List.of(value));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            names.add(original.nextElement());
        }
        headers.keySet().forEach(name -> {
            if (!names.contains(name)) {
                names.add(name);
            }
        });
        return Collections.enumeration(names);
    }
}
