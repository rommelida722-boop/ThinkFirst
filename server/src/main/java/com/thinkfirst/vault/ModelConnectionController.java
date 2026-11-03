package com.thinkfirst.vault;
import com.thinkfirst.auth.CurrentUser;
import com.thinkfirst.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@RestController @RequestMapping("/api/v1/user/models")
public class ModelConnectionController {
 @org.springframework.beans.factory.annotation.Autowired private com.thinkfirst.auth.UserRepository users;
 private final ModelConnectionRepository repository; private final ApiKeyCrypto crypto;private final CurrentUser user;
 @Value("${llm.backup-url-1:}") private String url1;
 @Value("${llm.backup-url-2:}") private String url2;
 @Value("${llm.backup-model-1:}") private String model1;
 @Value("${llm.backup-model-2:}") private String model2;
 public ModelConnectionController(ModelConnectionRepository repository,ApiKeyCrypto crypto,CurrentUser user){this.repository=repository;this.crypto=crypto;this.user=user;}
 public record KeyRequest(String provider,String apiKey){@Override public String toString(){return "模型配置（密钥已隐藏）";}}
 @GetMapping public ApiResponse<Map<String,Object>> list(){return ApiResponse.ok(Map.of("connections",repository.findByOwnerIdOrderByIdAsc(user.id()),"available",java.util.stream.Stream.of("DeepSeek","通义千问（北京）","通义千问（新加坡）",(url1.isBlank() || model1.isBlank())?"":"备用接口1",(url2.isBlank() || model2.isBlank())?"":"备用接口2").filter(x->!x.isBlank()).toList()));}
 @org.springframework.transaction.annotation.Transactional
 @PutMapping public ApiResponse<Void> save(@RequestBody KeyRequest request){
  if(request==null || !(com.thinkfirst.llm.BuiltinModels.supports(request.provider()) || "备用接口1".equals(request.provider())&&!url1.isBlank()&&!model1.isBlank() || "备用接口2".equals(request.provider())&&!url2.isBlank()&&!model2.isBlank()))throw new ApiKeyException("该接口尚未由服务器配置");
  String key=request.apiKey()==null?"":request.apiKey().trim();
  if(key.length()<8 || key.length()>500 || key.chars().anyMatch(c->c<33 || c>126))throw new ApiKeyException("请输入有效的API Key");
  ModelConnection row=repository.findByOwnerIdAndProvider(user.id(),request.provider()).orElseGet(ModelConnection::new);
  // 本次选择作为首选厂商；已有其他密钥保留作为备用，不进行明文迁移。
  for(var previous:repository.findByOwnerIdOrderByIdAsc(user.id())){previous.setPreferred(false);repository.save(previous);}
  row.setPreferred(true);
  row.setOwnerId(user.id());row.setProvider(request.provider());row.setCipher(crypto.encrypt(key));row.setMask("****"+key.substring(key.length()-4));repository.save(row);return ApiResponse.ok(null);
 }
 @org.springframework.transaction.annotation.Transactional
 @PostMapping("/{provider}/select") public ApiResponse<Void> select(@PathVariable String provider){
  ModelConnection row=repository.findByOwnerIdAndProvider(user.id(),provider).orElse(null);
  // 兼容旧版DeepSeek密钥，只复制密文，不将明文交给浏览器。
  if(row==null && "DeepSeek".equals(provider)){
   var owner=users.findById(user.id()).orElseThrow(()->new ApiKeyException("账号不存在"));
   if(owner.getApiKeyCipher()!=null){row=new ModelConnection();row.setOwnerId(user.id());row.setProvider(provider);row.setCipher(owner.getApiKeyCipher());row.setMask(owner.getApiKeyMask());}
  }
  if(row==null)throw new ApiKeyException("请先填写该厂商的API Key");
  for(var previous:repository.findByOwnerIdOrderByIdAsc(user.id())){previous.setPreferred(false);repository.save(previous);}
  row.setPreferred(true);repository.save(row);return ApiResponse.ok(null);
 }
 @DeleteMapping("/{provider}") public ApiResponse<Void> delete(@PathVariable String provider){repository.findByOwnerIdAndProvider(user.id(),provider).ifPresent(repository::delete);return ApiResponse.ok(null);}
}
