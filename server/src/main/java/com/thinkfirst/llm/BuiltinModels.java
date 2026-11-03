package com.thinkfirst.llm;
/** 内置厂商地址只由代码和服务器配置控制，不接受浏览器提交任意URL。 */
public final class BuiltinModels {
 private BuiltinModels(){}
 public static boolean supports(String provider){return provider!=null && java.util.List.of("DeepSeek","通义千问（北京）","通义千问（新加坡）").contains(provider);}
 public static String url(String provider){return switch(provider){case "DeepSeek"->"https://api.deepseek.com";case "通义千问（北京）"->"https://dashscope.aliyuncs.com/compatible-mode/v1";case "通义千问（新加坡）"->"https://dashscope-intl.aliyuncs.com/compatible-mode/v1";default->throw new IllegalArgumentException("不支持的模型厂商");};}
 public static String model(String provider){return "DeepSeek".equals(provider)?"deepseek-chat":"qwen-plus";}
}
