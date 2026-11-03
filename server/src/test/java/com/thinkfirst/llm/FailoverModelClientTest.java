package com.thinkfirst.llm;
import com.thinkfirst.auth.*;
import com.thinkfirst.vault.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class FailoverModelClientTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void selectedQwenIsPreferredOverLegacyDeepSeek(){
  var http=mock(HttpDeepSeekClient.class);var repo=mock(ModelConnectionRepository.class);var crypto=mock(ApiKeyCrypto.class);var users=mock(UserRepository.class);
  ModelConnection connection=new ModelConnection();connection.setProvider("通义千问（北京）");connection.setPreferred(true);connection.setCipher("encrypted-qwen");
  when(repo.findByOwnerIdOrderByIdAsc(9L)).thenReturn(List.of(connection));when(crypto.decrypt("encrypted-qwen")).thenReturn("qwen-secret");
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(9L,null));
  var expected=new DeepSeekClient.Completion("千问回复",1,1);
  when(http.completeAtUrl(eq("https://dashscope.aliyuncs.com/compatible-mode/v1"),eq("qwen-plus"),eq("qwen-secret"),anyString(),anyString(),anyDouble(),anyInt())).thenReturn(expected);
  assertEquals(expected,new FailoverModelClient(http,repo,crypto,users).complete("legacy-key","任务","内容",0.1,100));
  verify(http,never()).complete(anyString(),anyString(),anyString(),anyDouble(),anyInt());
 }
 @Test void switchesToCurrentUsersEncryptedBackup(){
  var http=mock(HttpDeepSeekClient.class);var repo=mock(ModelConnectionRepository.class);var crypto=mock(ApiKeyCrypto.class);var users=mock(UserRepository.class);
  User user=new User();user.setApiKeyCipher("primary-cipher");when(users.findById(7L)).thenReturn(Optional.of(user));
  ModelConnection connection=new ModelConnection();connection.setProvider("备用接口1");connection.setCipher("encrypted");when(repo.findByOwnerIdOrderByIdAsc(7L)).thenReturn(List.of(connection));when(crypto.decrypt("encrypted")).thenReturn("backup-secret");
  var client=new FailoverModelClient(http,repo,crypto,users);ReflectionTestUtils.setField(client,"url1","https://example.com/v1");ReflectionTestUtils.setField(client,"model1","model-test");
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null));
  when(http.complete(anyString(),anyString(),anyString(),anyDouble(),anyInt())).thenThrow(new LlmException("模型超时"));
  var expected=new DeepSeekClient.Completion("成功",1,1);when(http.completeAtUrl(eq("https://example.com/v1"),eq("model-test"),eq("backup-secret"),anyString(),anyString(),anyDouble(),anyInt())).thenReturn(expected);
  assertEquals(expected,client.complete("primary-secret","任务","内容",0.1,100));verify(repo).findByOwnerIdOrderByIdAsc(7L);
 }
}
