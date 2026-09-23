package xin.v5ai.nb.rag.core.parser;

import cn.hutool.json.JSONUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ParserHttpSupport {
    private ParserHttpSupport() {}

    static HttpClient client(long connectTimeoutMillis) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMillis)).build();
    }

    static HttpResponse<byte[]> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("解析服务请求被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("解析服务请求失败", e);
        }
    }

    static void requireSuccess(HttpResponse<?> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("解析服务返回 HTTP " + response.statusCode());
        }
    }

    static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    static String jsonString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String findText(Object value) {
        if (value instanceof java.util.Map<?, ?> map) {
            for (String key : new String[]{"md_content", "markdown", "md", "text", "content"}) {
                Object candidate = map.get(key);
                if (candidate instanceof String s && !s.isBlank()) return s;
            }
            for (Object child : map.values()) {
                String found = findText(child);
                if (found != null && !found.isBlank()) return found;
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                String found = findText(child);
                if (found != null && !found.isBlank()) return found;
            }
        }
        return null;
    }

    static String findTaskId(Object value) {
        if (value instanceof java.util.Map<?, ?> map) {
            for (String key : new String[]{"task_id", "taskId", "id"}) {
                Object candidate = map.get(key);
                if (candidate != null && !String.valueOf(candidate).isBlank()) return String.valueOf(candidate);
            }
            for (Object child : map.values()) {
                String found = findTaskId(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    static boolean isTerminal(Object value) {
        if (!(value instanceof java.util.Map<?, ?> map)) return false;
        String status = null;
        for (String key : new String[]{"status", "state", "task_status"}) {
            if (map.get(key) != null) { status = String.valueOf(map.get(key)); break; }
        }
        if (status == null) return false;
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("fail") || s.contains("error") || s.contains("cancel")) {
            throw new IllegalStateException("解析任务失败: " + status);
        }
        return s.contains("success") || s.contains("complete") || s.contains("done") || s.contains("finish")
                || s.equals("succeeded");
    }

    static byte[] multipart(String boundary, String filename, byte[] content, java.util.Map<String, String> fields) {
        return multipart(boundary, filename, content, fields.entrySet().stream()
                .map(entry -> new Part(entry.getKey(), entry.getValue())).toList());
    }

    static byte[] multipart(String boundary, String filename, byte[] content, List<Part> fields) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] newline = "\r\n".getBytes(StandardCharsets.UTF_8);
            for (var entry : fields) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + entry.name()
                        + "\"\r\n\r\n" + entry.value()).getBytes(StandardCharsets.UTF_8));
                out.write(newline);
            }
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                    + (filename == null ? "document.bin" : filename.replace("\"", ""))
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content == null ? new byte[0] : content);
            out.write(newline);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("构造 multipart 请求失败", e);
        }
    }

    record Part(String name, String value) {}

    static String zipText(byte[] bytes) {
        return zipEntryText(bytes, name -> name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".html"));
    }

    static String zipJson(byte[] bytes) {
        return zipEntryText(bytes, name -> name.endsWith(".json"));
    }

    private static String zipEntryText(byte[] bytes, java.util.function.Predicate<String> selector) {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (selector.test(name)) {
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
            return "";
        } catch (IOException e) {
            throw new IllegalStateException("解析 MinerU ZIP 结果失败", e);
        }
    }

    static Object json(String body) {
        return JSONUtil.parse(body);
    }
}
