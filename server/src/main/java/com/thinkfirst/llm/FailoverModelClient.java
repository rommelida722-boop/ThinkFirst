package com.thinkfirst.llm;
import com.thinkfirst.vault.*;
import com.thinkfirst.auth.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
/** 按主接口、备用1、备用2依次尝试；只使用当前用户自己的加密密钥。 */
@Component @Primary
public class FailoverModelClient implements DeepSeekClient {
 private final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(8);
 private final HttpDeepSeekClient primary;private final ModelConnectionRepository connections;private final ApiKeyCrypto crypto;private final UserRepository users;
 @Value("${llm.backup-url-1:}")private String url1;@Value("${llm.backup-model-1:}")private String model1;
 @Value("${llm.backup-url-2:}")private String url2;@Value("${llm.backup-model-2:}")private String model2;
 public FailoverModelClient(HttpDeepSeekClient primary,ModelConnectionRepository connections,ApiKeyCrypto crypto,UserRepository users){this.primary=primary;this.connections=connections;this.crypto=crypto;this.users=users;}
 public Completion complete(String key,String system,String user,double temperature,int maxTokens){
  if(!capacity.tryAcquire())throw new LlmException("服务繁忙，请稍后重试");
  try {
  // 同一次HTTP请求中的字段生成、格式修复和后续提问共用时间预算。
  var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  long deadline=System.currentTimeMillis()+210000;
  if(attributes!=null){Object old=attributes.getAttribute("tf.model.deadline",0);if(old instanceof Long value)deadline=value;else attributes.setAttribute("tf.model.deadline",deadline,0);}
  if(System.currentTimeMillis()+45000>deadline)throw new LlmException("本次生成耗时过长，请稍后重试");
  var auth=SecurityContextHolder.getContext().getAuthentication();
  Long owner=auth!=null && auth.getPrincipal() instanceof Long id?id:null;
  LlmException last=null;
  var saved=owner==null?java.util.List.<ModelConnection>of():connections.findByOwnerIdOrderByIdAsc(owner);
  // 用户在设置中选择的厂商优先；旧版DeepSeek密钥仍保留为兼容备用。
  for(var row:saved)if(row.isPreferred() && BuiltinModels.supports(row.getProvider())) {
   try{return primary.completeAtUrl(BuiltinModels.url(row.getProvider()),BuiltinModels.model(row.getProvider()),crypto.decrypt(row.getCipher()),system,user,temperature,maxTokens);}
   catch(LlmException|KeyVaultUnavailableException e){last=new LlmException("所选厂商暂时不可用，备用接口也未成功，请检查设置");}
  }

  if(owner==null || users.findById(owner).map(u->u.getApiKeyCipher()!=null).orElse(false)){
   try{return primary.complete(key,system,user,temperature,maxTokens);}catch(LlmException e){last=e;}
  }
  if(owner!=null)for(var row:saved){
   if(row.isPreferred() && BuiltinModels.supports(row.getProvider()))continue;
   if(BuiltinModels.supports(row.getProvider())){
    if(System.currentTimeMillis()+45000>deadline)throw new LlmException("模型切换耗时过长，请稍后重试");
    try{return primary.completeAtUrl(BuiltinModels.url(row.getProvider()),BuiltinModels.model(row.getProvider()),crypto.decrypt(row.getCipher()),system,user,temperature,maxTokens);}catch(LlmException|KeyVaultUnavailableException e){last=new LlmException("已配置的模型暂时不可用，请稍后重试");}
    continue;
   }
   String url="备用接口1".equals(row.getProvider())?url1:url2;String model="备用接口1".equals(row.getProvider())?model1:model2;
   if(url==null || url.isBlank() || model==null || model.isBlank())continue;
   if(System.currentTimeMillis()+45000>deadline)throw new LlmException("模型切换耗时过长，请稍后重试");
   try{return primary.completeAtUrl(url,model,crypto.decrypt(row.getCipher()),system,user,temperature,maxTokens);}catch(LlmException|KeyVaultUnavailableException e){last=new LlmException("主接口及已配置的备用接口暂时不可用，请稍后重试");}
  }
  throw last!=null?last:new LlmException("请在“设置”中配置API Key或备用模型接口");
  } finally { capacity.release(); }
 }
}
