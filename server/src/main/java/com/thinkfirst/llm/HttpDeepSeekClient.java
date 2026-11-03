package com.thinkfirst.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class HttpDeepSeekClient implements DeepSeekClient {

    private final LlmProperties properties;
    private final RestClient client;

    public HttpDeepSeekClient(LlmProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(Math.min(45, Math.max(10, properties.getTimeoutSeconds())) * 1000);
        this.client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Completion complete(String apiKey, String system, String user,
                               double temperature, int maxTokens) {
        return completeAt(client, properties.getModel(), apiKey, system, user, temperature, maxTokens);
    }
    /** 备用接口使用独立超时，密钥只进入Authorization头，不写日志。 */
    public Completion completeAtUrl(String url,String model,String key,String system,String user,double temperature,int maxTokens) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);factory.setReadTimeout(45000);
        return completeAt(RestClient.builder().baseUrl(url).requestFactory(factory).build(),model,key,system,user,temperature,maxTokens);
    }
    private Completion completeAt(RestClient target,String model,String apiKey,String system,String user,double temperature,int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "stream", false
        );
        try {
            JsonNode response = target.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new LlmException("模型返回为空");
            }
            if ("length".equals(response.path("choices").path(0).path("finish_reason").asText())) {
                throw new LlmException("生成内容超出长度限制，请精简回答后重试");
            }
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new LlmException("模型返回为空");
            }
            if(system.contains("JSON") && !system.contains("Markdown")) {
                try {
                    String json=content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
                    if(!new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).isObject())throw new IllegalArgumentException();
                } catch(Exception invalid) {throw new LlmException("模型返回格式错误，正在尝试可用接口");}
            }
            JsonNode usage = response.path("usage");
            return new Completion(content,
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0));
        } catch (HttpClientErrorException e) {
            throw new LlmException(messageFor(e.getStatusCode().value()));
        } catch (RestClientException e) {
            Throwable cause=e;
            while(cause.getCause()!=null)cause=cause.getCause();
            throw new LlmException(cause instanceof java.net.SocketTimeoutException ? "模型响应超时，请稍后重试或在设置中配置备用接口" : "模型连接失败，请检查接口服务");
        }
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 401, 403 -> "API Key 无效或没有权限";
            case 402 -> "API Key 余额不足";
            case 429 -> "模型请求过于频繁";
            default -> "模型服务暂时不可用（HTTP " + status + "）";
        };
    }
}
